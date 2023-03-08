package chat_app.backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import readiefur.console.Logger;
import readiefur.misc.Event;
import readiefur.misc.IDisposable;
import readiefur.misc.ManualResetEvent;
import readiefur.misc.Pair;
import readiefur.sockets.Client;
import readiefur.sockets.ServerManager;

import chat_app.backend.net_data.EPeerStatus;
import chat_app.backend.net_data.EType;
import chat_app.backend.net_data.EmptyPayload;
import chat_app.backend.net_data.MessagePayload;
import chat_app.backend.net_data.NetMessage;
import chat_app.backend.net_data.PeersPayload;

//TODO: Clean this class up as it is beginning to get a bit too messy, refractor the reuse of methods for two different purposes.
public class ChatManager implements IDisposable
{
    //#region Fields
    private static final int COMMON_TIMEOUT = 1500;

    private Boolean isDisposed = false;
    private final Object lock = new Object();

    //Constant properties (doesn't change between calling Restart()).
    private final String fallbackServerIPAddress;
    private final int port;
    private final String desiredUsername;
    private int failedRestarts = 0;
    private Boolean isCleaningUp = false; //Required due to event loops in cleanup.

    //Server specific properties.
    private ServerManager serverManager = null;
    private PingPong pingPong = null;

    //Client specific properties.
    private Client client = null;
    private UUID id = null;

    //Shared properties.
    private Boolean isHost = true;
    private ConcurrentHashMap<UUID, Peer> peers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, ManualResetEvent> pendingMessages = new ConcurrentHashMap<>();

    //Events.
    public final Event<Peer> onPeerConnected = new Event<>();
    public final Event<Peer> onPeerDisconnected = new Event<>();
    public final Event<MessagePayload> onMessageReceived = new Event<>();
    //#endregion

    //#region Startup/Shutdown
    public ChatManager(String initialServerAddress, int port, String desiredUsername)
    {
        fallbackServerIPAddress = initialServerAddress;
        this.port = port;
        this.desiredUsername = desiredUsername; //If null, will be resolved to "Anonymous" later on.
    }

    @Override
    public void Dispose()
    {
        synchronized (lock)
        {
            if (isDisposed)
                return;
            isDisposed = true;

            Cleanup();
        }
    }

    /**
     * Starts the chat manager.
     * Does not block the current thread.
     */
    public void Begin()
    {
        //Start the manager.
        Restart();
    }

    private void Cleanup()
    {
        isCleaningUp = true;

        //Server related.
        if (serverManager != null)
        {
            serverManager.onConnect.Remove(this::OnNetConnect);
            serverManager.onMessage.Remove(this::OnNetMessage);
            serverManager.onClose.Remove(this::OnNetClose);
            serverManager.onError.Remove(this::OnNetError);

            serverManager.Dispose();

            // //Wait for the thread to finish.
            // try { serverManager.join(); }
            // catch (InterruptedException e) { Logger.Error("Failed to join server manager thread."); }

            serverManager = null;
        }
        if (pingPong != null)
        {
            pingPong.interrupt();

            // //Wait for the thread to finish.
            // try { pingPong.join(); }
            // catch (InterruptedException e) { Logger.Error("Failed to join ping pong thread."); }

            pingPong = null;
        }

        //Client related.
        if (client != null)
        {
            client.onConnect.Remove(nul -> OnNetConnect(ServerManager.SERVER_UUID));
            client.onMessage.Remove(data -> OnNetMessage(new Pair<>(ServerManager.SERVER_UUID, data)));
            client.onClose.Remove(nul -> OnNetClose(ServerManager.SERVER_UUID));
            client.onError.Remove(error -> OnNetError(new Pair<>(ServerManager.SERVER_UUID, error)));

            client.Dispose();

            // //Wait for the thread to finish.
            // try { client.join(); }
            // catch (InterruptedException e) { Logger.Error("Failed to join client thread."); }

            client = null;
        }

        //Shared.
        id = null;
        peers.clear();

        isCleaningUp = false;
    }

    private void Restart()
    {
        synchronized (lock)
        {
            if (isDisposed)
                return;

            Logger.Trace("Restarting...");

            UUID oldClientID = id;
            List<Peer> oldPeers = new ArrayList<>(peers.values());

            Cleanup();

            /*Add some random time between x and y ms, this is to help prevent two servers trying to be created at the same time.
            *While I do catch this issue if the server and client are on the same machine, if they are not then it is possible that,
            *Two servers get made and the clients are split across servers which is not desirable.*/
            /*In the future I think I would like to have this delay be based on the connection index (or GUID).
            *Or if the server had a clean exit, tell which clients who will be the next host.*/
            //We can skip this wait of the peersList was empty.
            if (!oldPeers.isEmpty() && oldClientID != null)
            {
                //In order for the calculations below to work properly, at least one of the values needs to be a float.
                //To make things easier however, I will make them all floats.
                final float MIN_HASH_CODE = 0f;
                final float MAX_HASH_CODE = Integer.MAX_VALUE;
                final float MIN_SLEEP_TIME = 0f;
                final float MAX_SLEEP_TIME = COMMON_TIMEOUT;

                float clientHashCode = oldClientID.hashCode();
                //We cannot work with negative numbers (and seeming as the randomness is great enough) we need to invert the number.
                if (clientHashCode < 0)
                    clientHashCode = -clientHashCode;

                //Convert the hash code to a value between MIN_HASH_CODE and MAX_HASH_CODE.
                //https://stackoverflow.com/questions/929103/convert-a-number-range-to-another-range-maintaining-ratio
                //NewValue = (((OldValue - OldMin) * (NewMax - NewMin)) / (OldMax - OldMin)) + NewMin
                float sleepTime = (((clientHashCode - MIN_HASH_CODE) * (MAX_SLEEP_TIME - MIN_SLEEP_TIME)) / (MAX_HASH_CODE - MIN_HASH_CODE)) + MIN_SLEEP_TIME;

                //Sonarlint wants me to replace this sleep call with a call to <lock>.wait(), however that is not suitable for this situation.
                try { Thread.sleep((int)sleepTime); }
                catch (InterruptedException e) {}
            }

            String hostAddress = null;
            //Look for a host.
            for (Peer peer : oldPeers)
            {
                UUID peerUUID = peer.GetUUID();
                if (peerUUID.equals(ServerManager.INVALID_UUID) //We don't include the server ID as a check as we may have accidentally lost connection.
                    || (oldClientID != null && peerUUID.equals(oldClientID))) //We don't check ourself.
                    continue;

                String peerAddress = peer.GetIPAddress();
                if (FindHost(hostAddress, port))
                {
                    hostAddress = peerAddress;
                    break;
                }
            }

            //If a host was not found on one of the previous peers, check if a fallback server was passed and is valid.
            if (hostAddress == null && fallbackServerIPAddress != null && FindHost(fallbackServerIPAddress, port))
                hostAddress = fallbackServerIPAddress;

            //If a host was not found, begin hosting.
            isHost = hostAddress == null;
            if (isHost)
            {
                Logger.Trace("No host found, starting server...");

                //Start the server.
                serverManager = new ServerManager(port);
                serverManager.onConnect.Add(this::OnNetConnect);
                serverManager.onMessage.Add(this::OnNetMessage);
                serverManager.onClose.Add(this::OnNetClose);
                serverManager.onError.Add(this::OnNetError);

                String serverAddress;
                try { serverAddress = Inet4Address.getLocalHost().getHostAddress(); }
                catch (UnknownHostException e) { serverAddress = fallbackServerIPAddress; }

                id = ServerManager.SERVER_UUID;
                ServerPeer serverPeer = new ServerPeer(
                    ServerManager.SERVER_UUID,
                    serverAddress,
                    desiredUsername,
                    EPeerStatus.CONNECTED);
                peers.put(ServerManager.SERVER_UUID, serverPeer);

                if (!serverManager.Start())
                {
                    failedRestarts++;
                    Logger.Trace(GetLogPrefix() + "Failed to start server: " + failedRestarts + "/3");

                    if (failedRestarts >= 3)
                        throw new RuntimeException("Failed to start server.");

                    Restart();
                    return;
                }
                Logger.Trace(GetLogPrefix() + "Server started.");
                onPeerConnected.Invoke(ServerPeer.ToPeer(serverPeer));

                //TODO: Ensure this gets enabled when finished with debugging.
                pingPong = new PingPong(serverManager);
                pingPong.start();
                Logger.Trace(GetLogPrefix() + "PingPong started.");
            }
            else
            {
                Logger.Trace("Host found at " + hostAddress + ":" + port + ". Connecting...");

                //Connect to the server.
                client = new Client(hostAddress, port);
                client.onConnect.Add(nul -> OnNetConnect(ServerManager.SERVER_UUID));
                client.onMessage.Add(data -> OnNetMessage(new Pair<>(ServerManager.SERVER_UUID, data)));
                client.onClose.Add(nul -> OnNetClose(ServerManager.SERVER_UUID));
                client.onError.Add(error -> OnNetError(new Pair<>(ServerManager.SERVER_UUID, error)));

                if (!client.Start())
                {
                    failedRestarts++;
                    Logger.Trace(GetLogPrefix() + "Failed to start client: " + failedRestarts + "/3");

                    if (failedRestarts >= 3)
                        throw new RuntimeException("Failed to start client.");

                    Restart();
                    return;
                }

                Logger.Trace(GetLogPrefix() + "Client started.");
            }

            Logger.Info("[CHAT_MANAGER] Promoted to " + (isHost ? "host" : "client") + ".");

            failedRestarts = 0;
        }
    }

    private Boolean FindHost(String ipAddress, int port)
    {
        if (ipAddress == null)
            return false;

        Logger.Trace("Looking for host at " + ipAddress + ":" + port + "...");

        ManualResetEvent resetEvent = new ManualResetEvent(false);

        Client dummyClient = new Client(ipAddress, port);
        dummyClient.onConnect.Add(nul -> resetEvent.Set());
        if (!dummyClient.Start())
            return false;

        try { resetEvent.WaitOne(1000); }
        catch (TimeoutException e) {}

        Boolean hostFound = dummyClient.IsConnected();

        dummyClient.Dispose();

        return hostFound;
    }
    //#endregion

    //#region Network Events
    private void OnNetConnect(UUID uuid)
    {
        if (isDisposed || isCleaningUp)
            return;

        Logger.Trace(GetLogPrefix() + "Connection opened: " + uuid);

        if (isHost)
        {
            /*When a new client connects, we add them to the list of peers however we don't,
             *indicate that the client is ready yet, we must wait for the handshake first.*/
            if (!peers.containsKey(uuid))
            {
                peers.put(uuid, new ServerPeer(
                    uuid,
                    serverManager.GetClientHosts().get(uuid).GetSocket().getLocalAddress().getHostAddress(),
                    desiredUsername,
                    EPeerStatus.UNINITIALIZED));
            }
            else
            {
                Peer peer = peers.get(uuid);
                if (peer == null)
                    return;

                //If the client was already in the list then the event will be fired for the client handshaking.
                onPeerConnected.Invoke(peer);
            }
        }
        else
        {
            /*If the uuid is equal to SERVER_UUID and the ID isn't in the peers list
             *then the event was fired due to this instance connecting to the server, in which case we should handshake.*/
            if (uuid.equals(ServerManager.SERVER_UUID) && !peers.containsKey(uuid))
            {
                //Send the handshake.
                NetMessage<Peer> message = new NetMessage<>();
                message.type = EType.HANDSHAKE;
                message.payload = new Peer(desiredUsername);
                client.SendMessage(message);
                ///See: OnNetMessage > Host > HANDSHAKE
            }
            else
            {
                Peer peer = peers.get(uuid);
                if (peer == null)
                    return;

                //The new peer will have been added in the OnNetMessage > Host > HANDSHAKE message.
                Logger.Info(GetLogPrefix() + "Peer connected: " + peer.GetUsername() + (uuid.equals(id) ? " (You)" : ""));
                onPeerConnected.Invoke(peer);
            }
        }
    }

    private void OnNetMessage(Pair<UUID, Object> data)
    {
        if (isDisposed || isCleaningUp)
            return;

        NetMessage<?> netMessage = (NetMessage<?>)data.item2;

        Logger.Trace(GetLogPrefix() + "Message received: " + data.item1 + " | " + netMessage.type);

        if (isHost)
        {
            switch (netMessage.type)
            {
                //I like to use {} on my switch case statements for two reasons, it scopes each case and it increases readability.
                case HANDSHAKE:
                {
                    //From: OnNetConnect > Client

                    Peer inPayload = (Peer)netMessage.payload;

                    ServerPeer peer = (ServerPeer)peers.get(data.item1);

                    //If the peer isn't found then ignore the request || If the peer has already handshaked (connected), ignore the request.
                    if (peer == null || peer.GetStatus() == EPeerStatus.CONNECTED)
                        return;

                    String nickname = inPayload.username == null || inPayload.username.isBlank() ? "Anonymous" : inPayload.username;
                    int duplicateCount = 0;

                    /*Check for duplicate nicknames.
                     *(This could've been done ine like 2 lines in C# but Java's inability to pass local variables makes it a bit more complicated).*/
                    while (true)
                    {
                        boolean duplicateFound = false;
                        for (Peer existingPeer : peers.values())
                        {
                            //It seems like string == string is not the same as string.equals(string) in Java.
                            if (existingPeer.GetStatus() == EPeerStatus.CONNECTED
                                && existingPeer.username != null
                                && existingPeer.username.equals(nickname))
                            {
                                duplicateFound = true;
                                break;
                            }
                        }
                        if (!duplicateFound)
                            break;

                        nickname = inPayload.username + ++duplicateCount;
                    }

                    //Indicate that the client is ready.
                    //All peers should be typeof ServerPeer at this level.
                    peer.username = nickname;
                    peer.SetStatus(EPeerStatus.CONNECTED);

                    Logger.Debug(GetLogPrefix() + "Client connected: " + data.item1 + " (" + peer.username + ")");
                    Logger.Info(GetLogPrefix() + "Client connected: " + peer.username);

                    //Return the server-validated handshake data back to the client.
                    NetMessage<Peer> response = new NetMessage<>();
                    response.type = EType.HANDSHAKE;
                    response.payload = peer;
                    serverManager.SendMessage(data.item1, response);
                    ///See: OnNetMessage > Client > HANDSHAKE

                    //Broadcast the new peer to all other peers.
                    NetMessage<Peer> peerBroadcast = new NetMessage<>();
                    peerBroadcast.type = EType.PEER;
                    peerBroadcast.payload = peer;
                    serverManager.BroadcastMessage(peerBroadcast);
                    ///See: OnNetMessage > Client > PEER

                    OnNetConnect(data.item1);

                    break;
                }
                case PEERS:
                {
                    //(Typically) From: OnNetMessage > Client > HANDSHAKE

                    //Send the list of peers to the client.
                    NetMessage<PeersPayload> response = new NetMessage<>();
                    response.type = EType.PEERS;
                    response.payload = new PeersPayload();
                    response.payload.peers = GetReadyPeers();
                    serverManager.SendMessage(data.item1, response);

                    ///See: OnNetMessage > Client > PEERS

                    break;
                }
                case MESSAGE:
                {
                    //Occurs when a client sends a message to be processed by the server.

                    //Ignore messages from clients who have not connected yet.
                    if (peers.get(data.item1).GetStatus() != EPeerStatus.CONNECTED)
                        return;

                    MessagePayload inPayload = (MessagePayload)netMessage.payload;
                    inPayload.SetSender(data.item1);
                    UUID recipient = inPayload.GetRecipient();

                    /*If the recipient is `INVALID_UUID` then broadcast the message to all peers.
                     *Otherwise check if the message is available to be sent to the specified peer.*/
                    if (recipient.equals(ServerManager.INVALID_UUID))
                    {
                        //Broadcast the message to all peers.
                        NetMessage<MessagePayload> message = new NetMessage<>();
                        message.type = EType.MESSAGE;
                        message.payload = inPayload;
                        serverManager.BroadcastMessage(message);
                        //See: OnNetMessage > Host/Client > MESSAGE

                        //If we (the server) are the sender then remove the message from the queue.
                        //Otherwise invoke the OnMessageReceived event.
                        if (inPayload.GetSender().equals(ServerManager.SERVER_UUID))
                            ClearPendingMessage(inPayload.GetMessageID());
                        else
                            onMessageReceived.Invoke(inPayload);
                    }
                    else if (peers.containsKey(recipient) && peers.get(recipient).GetStatus() == EPeerStatus.CONNECTED)
                    {
                        //If the sender is us, forward the message to the recipient and remove the message from the queue.
                        if (inPayload.GetSender().equals(ServerManager.SERVER_UUID))
                        {
                            serverManager.SendMessage(recipient, netMessage);
                            //See: OnNetMessage > Client > MESSAGE > else

                            ClearPendingMessage(inPayload.GetMessageID());
                            return;
                        }

                        //Else if the recipient is us, invoke the OnMessageReceived event.
                        //Otherwise forward the message to the specified peer.
                        if (recipient.equals(ServerManager.SERVER_UUID))
                        {
                            onMessageReceived.Invoke(inPayload);
                        }
                        else
                        {
                            serverManager.SendMessage(recipient, netMessage);
                            //See: OnNetMessage > Client > MESSAGE > else
                        }

                        //Also send the message back to the sender to indicate that the message has been acknowledged.
                        serverManager.SendMessage(data.item1, data.item2);
                        //See: OnNetMessage > Client > MESSAGE > if
                    }
                    //Otherwise ignore the request.
                    break;
                }
                default:
                {
                    //Invalid message type, ignore the request.
                    break;
                }
            }
        }
        else
        {
            /*If the message isn't a EType.MESSAGE and the sender isn't the server,
             *ignore the request as clients should not be allowed to send other events to each other.*/
            if (netMessage.type != EType.MESSAGE && !data.item1.equals(ServerManager.SERVER_UUID))
                return;

            switch (netMessage.type)
            {
                case HANDSHAKE:
                {
                    //From: OnNetMessage > Host > HANDSHAKE

                    Peer inPayload = (Peer)netMessage.payload;
                    id = inPayload.GetUUID();

                    Logger.Trace("Connected to server and assigned ID: " + id);
                    Logger.Info(GetLogPrefix() + "Connected to server.");

                    //Occurs when the handshake has been acknowledged by the server.
                    //Request a list of peers.
                    NetMessage<EmptyPayload> response = new NetMessage<>();
                    response.type = EType.PEERS;
                    response.payload = new EmptyPayload();
                    client.SendMessage(response);

                    ///See: OnNetMessage > Host > PEERS

                    break;
                }
                case PING:
                {
                    //Occurs when the server has sent a ping request.
                    //TODO: Add a timer to check if the server has timed out.
                    //Return a pong.
                    NetMessage<EmptyPayload> response = new NetMessage<>();
                    response.type = EType.PONG;
                    response.payload = new EmptyPayload();
                    client.SendMessage(response);
                    break;
                }
                case PEER:
                {
                    //Occurs when the server has sent a status update of a peer.
                    Peer inPayload = (Peer)netMessage.payload;
                    switch (inPayload.GetStatus())
                    {
                        case CONNECTED:
                        {
                            UUID payloadID = inPayload.GetUUID();

                            //If the client wasn't in the list then fire the connect event (this includes us connecting to the server).
                            Boolean isNewPeer = !peers.containsKey(payloadID) && !payloadID.equals(ServerManager.SERVER_UUID);

                            //Update the peer in the list (this should be added before the OnNetConnect event is fired).
                            /*Notice how there is no synchronize block here, this is because I am using a ConcurrentHashMap which is synchronized
                             *and I am not performing any long operations here so there is no need use a synchronize block here.*/
                            //TODO: Move this peers.put into the OnNetConnect, due to my current design of that method, it is not possible.
                            peers.put(payloadID, inPayload);

                            if (isNewPeer)
                                OnNetConnect(payloadID);

                            break;
                        }
                        case DISCONNECTED:
                        {
                            UUID payloadID = inPayload.GetUUID();

                            //Remove the peer from the list and fire the disconnect event if we had the peer.
                            if (!peers.containsKey(payloadID))
                                return;

                            //In this case the event must be fired before the peer is removed from the list.
                            OnNetClose(payloadID);

                            break;
                        }
                        default:
                        {
                            //Ignore the message.
                            break;
                        }
                    }
                    break;
                }
                case PEERS:
                {
                    //From: OnNetMessage > Host > PEERS

                    //Occurs when the server has sent a new list of peers.
                    /*The reason for having a separate PEER and PEERS message is so that the server can send a list of peers,
                     *which is more efficient than sending a PEER message for each peer.*/

                    //Replace the list of peers with the new list.
                    HashMap<UUID, Peer> oldPeers = new HashMap<>(peers);
                    peers.clear();

                    for (Peer peer : ((PeersPayload)netMessage.payload).peers)
                    {
                        //In my testing it seemed like the payload would include one extra null value.
                        if (peer == null)
                            continue;

                        UUID peerUUID = peer.GetUUID();

                        peers.put(peerUUID, peer);

                        //We don't need to check the state of the peer as they should always be connected at this point.
                        if (!oldPeers.containsKey(peerUUID))
                        {
                            OnNetConnect(peerUUID);
                        }
                    }

                    /*If the client was in the old list but not the new one, fire the disconnect event.
                     *While this shouldn't strictly be relied on for disconnect events (as the peer message should send this status update
                     *it is a good idea to check it here just incase the message is missed.*/
                    for (UUID peerUUID : oldPeers.keySet())
                    {
                        if (!peerUUID.equals(id) && !peerUUID.equals(ServerManager.SERVER_UUID) && !peers.containsKey(peerUUID))
                            OnNetClose(peerUUID);
                    }
                    break;
                }
                case MESSAGE:
                {
                    //Occurs when the server has sent a message to the client.
                    MessagePayload inPayload = (MessagePayload)netMessage.payload;

                    UUID messageID = inPayload.GetMessageID();
                    UUID sender = inPayload.GetSender();

                    //If the sender is us, we can use this response to verify that the message was sent.
                    //Otherwise we can invoke the message event.
                    if (sender.equals(id))
                        ClearPendingMessage(messageID);
                    else
                        onMessageReceived.Invoke(inPayload);

                    break;
                }
                default:
                {
                    //Invalid message type, ignore the request.
                    break;
                }
            }
        }
    }

    private void OnNetClose(UUID uuid)
    {
        if (isDisposed || isCleaningUp)
            return;

        Logger.Trace(GetLogPrefix() + "Connection closed: " + uuid);
        Peer oldPeer = peers.get(uuid);
        if (oldPeer == null)
            return;

        if (isHost)
        {
            //Server has closed, handled in OnNetError || Can occur for server lookups.
            if (uuid.equals(ServerManager.SERVER_UUID) || !peers.containsKey(uuid))
                return;

            //Client has disconnected.
            peers.remove(uuid);

            //If the client wasn't a connected peer then don't broadcast the disconnect (this can occur for handshake requests).
            if (oldPeer.GetStatus() != EPeerStatus.CONNECTED)
                return;

            //The oldPeer variable will be a ServerPeer object at this level.
            ((ServerPeer)oldPeer).SetStatus(EPeerStatus.DISCONNECTED);

            Logger.Debug(GetLogPrefix() + "Client disconnected: " + uuid + " (" + oldPeer.GetUsername() + ")");
            Logger.Info(GetLogPrefix() + "Client disconnected: " + oldPeer.GetUsername());

            //Broadcast the disconnected peer to all other clients.
            NetMessage<Peer> peerBroadcast = new NetMessage<>();
            peerBroadcast.type = EType.PEER;
            peerBroadcast.payload = ServerPeer.ToPeer(((ServerPeer)oldPeer));
            serverManager.BroadcastMessage(peerBroadcast);
        }
        else
        {
            //If the server has disconnected, restart the client.
            if (uuid.equals(ServerManager.SERVER_UUID))
            {
                Logger.Info(GetLogPrefix() + "Disconnected from server.");
                //In this case we need to invoke the disconnect event before restarting.
                onPeerDisconnected.Invoke(oldPeer);
                Restart();
                return;
            }

            //Otherwise a client has disconnected, in which case we check if they were in our peers list, if they were then we remove them.
            if (!peers.containsKey(uuid))
                return;
            peers.remove(uuid);

            Logger.Trace(GetLogPrefix() + "Peer disconnected: " + uuid + " (" + oldPeer.GetUsername() + ")");
            Logger.Info(GetLogPrefix() + "Peer disconnected: " + oldPeer.GetUsername());
        }

        //If the above code hasn't returned, then we can invoke the disconnect event.
        onPeerDisconnected.Invoke(oldPeer);
    }

    private void OnNetError(Pair<UUID, Exception> error)
    {
        if (isHost)
        {
            if (error.item2 instanceof BindException)
            {
                /*A bind error can occur when another server is already running on the same port
                 *if this happens, assume another server instance is running.
                 *The restart will be called by the false returned Start method, so we don't call a restart here.*/
                return;
            }
        }
        else
        {
        }

        //Print the stack trace to a custom stream.
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(byteStream);
        error.item2.printStackTrace(stream);

        //Get the stack trace as a string.
        String stackTrace = byteStream.toString();

        //Dispose of the streams.
        stream.close();
        try { byteStream.close(); }
        catch (IOException e) {}

        //Log the error.
        Logger.Error(GetLogPrefix() + "Error in connection: " + error.item1 + " | " + stackTrace);
    }
    //#endregion

    //#region Dispatched events
    //#endregion

    //#region Misc
    private Peer[] GetReadyPeers()
    {
        List<Peer> readyPeers = new ArrayList<>();
        for (Peer peer : peers.values())
        {
            if (peer.GetStatus() != EPeerStatus.CONNECTED)
                continue;

            if (peer instanceof ServerPeer)
                readyPeers.add(ServerPeer.ToPeer((ServerPeer)peer));
            else
                readyPeers.add(peer);
        }

        return readyPeers.toArray(new Peer[readyPeers.size()]);
    }

    private String GetLogPrefix()
    {
        return "[" + (isHost ? "SERVER" : "CLIENT") + "] ";
    }

    /**
     * Returns a copy of the current peers list.
     * @return
     */
    public Map<UUID, Peer> GetPeers()
    {
        return new HashMap<>(peers);
    }

    /**
     * Returns whether or not we are currently the host.
     */
    public Boolean IsHost()
    {
        return isHost;
    }

    public UUID GetID()
    {
        return id;
    }

    /**
     * Returns whether or not the server has been disposed.
     */
    public Boolean IsDisposed()
    {
        return isDisposed;
    }

    /**
     * Sends a message to the specified recipient and waits for the server to acknowledge the message.
     * @return {@code true} if the message was sent successfully, otherwise {@code false}.
     */
    public Boolean SendMessageSync(UUID recipient, String message)
    {
        return SendMessageInternal(recipient, message, true);
    }

    /**
     * Sends a message to the specified recipient and does not wait for the server to acknowledge the message.
     */
    public void SendMessage(UUID recipient, String message)
    {
        SendMessageInternal(recipient, message, false);
    }

    private Boolean SendMessageInternal(UUID recipient, String message, Boolean sendSync)
    {
        MessagePayload payload = new MessagePayload(recipient, message);
        NetMessage<MessagePayload> netMessage = new NetMessage<>();
        netMessage.type = EType.MESSAGE;
        netMessage.payload = payload;

        //If we want to send the message synchronously, setup the ManualResetEvent to wait on.
        ManualResetEvent messageSentEvent = null; //Required to satisfy the compiler, the bang nullable operator would've been nice here (C# feature).
        if (sendSync)
        {
            messageSentEvent = new ManualResetEvent(false);
            pendingMessages.put(payload.GetMessageID(), messageSentEvent);
        }

        if (isHost)
        {
            //If we are the server, we have no way of "sending messages to ourself", so we can just call the OnNetMessage method directly.
            OnNetMessage(new Pair<>(ServerManager.SERVER_UUID, netMessage));
        }
        else
        {
            client.SendMessage(netMessage);
            ///See: OnNetMessage > Host > MESSAGE
        }

        //If we are not sending the message synchronously, return true here.
        if (!sendSync)
            return true;

        //Otherwise wait for the server to acknowledge the message (within the time limit, currently hardcoded).
        try
        {
            messageSentEvent.WaitOne(COMMON_TIMEOUT);
            return true;
        }
        catch (TimeoutException e)
        {
            Logger.Warn("Failed to send message: " + payload.GetMessageID());
            return false;
        }
        finally
        {
            //Remove the message from the pending messages list.
            pendingMessages.remove(payload.GetMessageID());
        }
    }

    private void ClearPendingMessage(UUID messageID)
    {
        if (!pendingMessages.containsKey(messageID))
            return;
        pendingMessages.get(messageID).Set();
        pendingMessages.remove(messageID);
    }
    //#endregion
}
