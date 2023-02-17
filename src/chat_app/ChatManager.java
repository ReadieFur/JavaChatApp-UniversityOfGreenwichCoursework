package chat_app;

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

import chat_app.net_data.EPeerStatus;
import chat_app.net_data.EType;
import chat_app.net_data.EmptyPayload;
import chat_app.net_data.MessagePayload;
import chat_app.net_data.NetMessage;
import chat_app.net_data.PeersPayload;

public class ChatManager implements IDisposable
{
    //#region Fields
    private Boolean isDisposed = false;
    private final Object lock = new Object();

    //Constant properties (doesn't change between calling Restart()).
    private final String fallbackServerIPAddress;
    private final int port;
    private final String desiredUsername;

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
        //Server related.
        if (serverManager != null)
        {
            serverManager.Dispose();
            serverManager = null;
        }
        if (pingPong != null)
        {
            pingPong.interrupt();
            pingPong = null;
        }

        //Client related.
        if (client != null)
        {
            client.Dispose();
            client = null;
        }

        //Shared.
        id = null;
        peers.clear();
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

            /*Add some random time between 0 and 1000ms, this is to help prevent two servers trying to be created at the same time.
            *While I do catch this issue if the server and client are on the same machine, if they are not then it is possible that,
            *Two servers get made and the clients are split across servers which is not desirable.*/
            /*In the future I think I would like to have this delay be based on the connection index (or GUID).
            *Or if the server had a clean exit, tell which clients who will be the next host.*/
            //We can skip this wait of the peersList was empty.
            if (oldPeers.isEmpty())
            {
                try { Thread.sleep((long)(Math.random() * 1000)); }
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
                Peer serverPeer = new ServerPeer(
                    ServerManager.SERVER_UUID,
                    serverAddress,
                    desiredUsername,
                    EPeerStatus.CONNECTED);
                peers.put(ServerManager.SERVER_UUID, serverPeer);

                serverManager.start();
                Logger.Trace(GetLogPrefix() + "Server started.");

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

                client.start();
                Logger.Trace(GetLogPrefix() + "Client started.");
            }

            Logger.Info("[CHAT_MANAGER] Promoted to " + (isHost ? "host" : "client") + ".");
        }
    }

    private Boolean FindHost(String ipAddress, int port)
    {
        Logger.Trace("Looking for host at " + ipAddress + ":" + port + "...");

        ManualResetEvent resetEvent = new ManualResetEvent(false);

        Client dummyClient = new Client(ipAddress, port);
        dummyClient.onConnect.Add(nul -> resetEvent.Set());
        dummyClient.start();

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
        /*It is important that the OnNetConnect and OnNetClose events are synchronized as they modify the peers list in a long running method.
         *And yes while this does hurt performance, fortunately this app is low-traffic enough that it shouldn't be a problem.*/
        synchronized (peers)
        {
            if (isDisposed)
                return;

            Logger.Trace(GetLogPrefix() + "Connection opened: " + uuid);

            if (isHost)
            {
                /*When a new client connects, we add them to the list of peers however we don't,
                *indicate that the client is ready yet, we must wait for the handshake first.*/
                peers.put(uuid, new ServerPeer(
                    uuid,
                    serverManager.GetClientHosts().get(uuid).GetSocket().getLocalAddress().getHostAddress(),
                    desiredUsername,
                    EPeerStatus.UNINITIALIZED));
            }
            else
            {
                /*If the uuid is equal to SERVER_UUID then the event was fired due to this instance connecting to the server,
                *in which case we should handshake.*/
                if (uuid.equals(ServerManager.SERVER_UUID))
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
                    //The new peer will have been added in the OnNetMessage > Host > HANDSHAKE message.
                    Logger.Info(GetLogPrefix() + "Peer connected: " + peers.get(uuid).GetUsername());
                }
            }

            onPeerConnected.Invoke(peers.get(uuid));
        }
    }

    private void OnNetMessage(Pair<UUID, Object> data)
    {
        if (isDisposed)
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

                        //If we (the server) are the sender then remove the message from the queue.
                        //Otherwise invoke the OnMessageReceived event.
                        if (inPayload.GetSender().equals(ServerManager.SERVER_UUID))
                            ClearPendingMessage(inPayload.GetMessageID());
                        else
                            onMessageReceived.Invoke(inPayload);
                    }
                    else if (peers.containsKey(recipient) && peers.get(recipient).GetStatus() == EPeerStatus.CONNECTED)
                    {
                        //If the sender is us, remove the message from the queue.
                        if (inPayload.GetSender().equals(ServerManager.SERVER_UUID))
                            ClearPendingMessage(inPayload.GetMessageID());
                        //Else if the recipient is us, invoke the OnMessageReceived event.
                        else if (recipient.equals(ServerManager.SERVER_UUID))
                            onMessageReceived.Invoke(inPayload);
                        //Otherwise forward the message to the specified peer.
                        else
                            serverManager.SendMessage(recipient, netMessage);
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

                            //If the client wasn't in the list then fire the connect event (unless it is us or the server).
                            Boolean isNewPeer = !payloadID.equals(id) && !payloadID.equals(ServerManager.SERVER_UUID) && !peers.containsKey(payloadID);

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

                        //If the client wasn't in the list then fire the connect event (unless it is us or the server).
                        if (!peerUUID.equals(id) && !peerUUID.equals(ServerManager.SERVER_UUID) && !oldPeers.containsKey(peerUUID))
                            OnNetConnect(peer.GetUUID());
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
        synchronized (peers)
        {
            if (isDisposed)
                return;

            Logger.Trace(GetLogPrefix() + "Connection closed: " + uuid);
            Peer oldPeer = peers.get(uuid);

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
    }

    private void OnNetError(Pair<UUID, Exception> error)
    {
        if (isHost)
        {
            if (error.item2 instanceof BindException)
            {
                /*A bind error can occur when another server is already running on the same port
                 *if this happens, assume another server instance is running.*/
                Restart();
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
        }

        //If we are not sending the message synchronously, return true here.
        if (!sendSync)
            return true;

        //Otherwise wait for the server to acknowledge the message (within the time limit, currently hardcoded).
        try
        {
            messageSentEvent.WaitOne(5000);
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
