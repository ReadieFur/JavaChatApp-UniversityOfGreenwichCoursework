package chat_app;

import java.lang.reflect.Method;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import chat_app.attributes.CommandAttribute;
import chat_app.attributes.CommandParameterAttribute;
import chat_app.attributes.CommandParameterAttributes;
import chat_app.net_data.EType;
import chat_app.net_data.EmptyPayload;
import chat_app.net_data.NetMessage;
import chat_app.net_data.PeersPayload;
import readiefur.helpers.KeyValuePair;
import readiefur.helpers.ManualResetEvent;
import readiefur.helpers.console.ConsoleWrapper;
import readiefur.helpers.sockets.Client;
import readiefur.helpers.sockets.ServerManager;

public class ChatManager
{
    //#region Properties
    private static final ManualResetEvent exitEvent = new ManualResetEvent(false);

    //"Constant" properties (doesn't change between calling Restart()).
    private static String fallbackServerIPAddress;
    private static int port;
    private static String desiredUsername;

    //Server specific properties.
    private static ServerManager serverManager = null;
    private static PingPong pingPong = null;

    //Client specific properties.
    private static Client client = null;
    private static UUID clientID = null;

    //Shared properties.
    private static Boolean isHost = true;
    private static ConcurrentHashMap<UUID, Peer> peers = new ConcurrentHashMap<>();
    //#endregion

    //#region Initialization and Cleanup
    //Hide the constructor.
    private ChatManager() {}

    //This is a blocking method that will not return until the application is closed.
    public static void Begin(String initialServerAddress, int port, String desiredUsername)
    {
        fallbackServerIPAddress = initialServerAddress;
        ChatManager.port = port;
        ChatManager.desiredUsername = desiredUsername; //If null, will be resolved to "Anonymous" later on.

        //Start the console input thread.

        //Start the manager.
        Restart();

        //Wait indefinitely until signaled to exit.
        exitEvent.WaitOne();

        Cleanup();
    }

    private static void Cleanup()
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
        clientID = null;

        //Shared.
        peers.clear();
    }
    //#endregion

    //#region Setup methods
    private static void Restart()
    {
        UUID oldClientID = clientID;
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
            if (peerUUID == ServerManager.INVALID_UUID //We don't include the server ID as a check as we may have accidentally lost connection.
                || (oldClientID != null && peerUUID == oldClientID)) //We don't check ourself.
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
        if (hostAddress == null)
        {
            //Start the server.
            serverManager = new ServerManager(port);
            serverManager.onConnect.Add(ChatManager::OnNetConnect);
            serverManager.onMessage.Add(ChatManager::OnNetMessage);
            serverManager.onClose.Add(ChatManager::OnNetClose);
            serverManager.onError.Add(ChatManager::OnNetError);

            isHost = true;
            try
            {
                Peer serverPeer = new Peer(ServerManager.SERVER_UUID, Inet4Address.getLocalHost().getHostAddress());
                serverPeer.SetIsReady();
                //TODO: Server username.
                peers.put(ServerManager.SERVER_UUID, serverPeer);
            }
            catch (UnknownHostException e)
            {
                //TODO: Handle invalid host address.
            }

            serverManager.start();

            pingPong = new PingPong(serverManager);
            pingPong.start();
        }
        else
        {
            //Connect to the server.
            client = new Client(hostAddress, port);
            client.onConnect.Add(nul -> OnNetConnect(null));
            client.onMessage.Add(data -> OnNetMessage(new KeyValuePair<>(null, data)));
            client.onClose.Add(nul -> OnNetClose(null));
            client.onError.Add(error -> OnNetError(new KeyValuePair<>(null, error)));

            isHost = false;

            client.start();
        }
    }

    private static Boolean FindHost(String ipAddress, int port)
    {
        ManualResetEvent resetEvent = new ManualResetEvent(false);

        Client client = new Client(ipAddress, port);
        client.onConnect.Add(nul -> resetEvent.Set());
        client.start();

        try { resetEvent.WaitOne(1000); }
        catch (TimeoutException e) {}

        Boolean hostFound = client.IsConnected();

        client.Dispose();

        return hostFound;
    }
    //#endregion

    //#region Network Events
    //UUIDs are null for client connections.
    private static void OnNetConnect(UUID uuid)
    {
        if (isHost)
        {
            /*When a new client connects, we add them to the list of peers however we don't,
             *indicate that the client is ready yet, we must wait for the handshake first.*/
            peers.put(uuid, new Peer(uuid, serverManager.GetClientHosts().get(uuid).GetSocket().getInetAddress().getHostAddress()));
        }
        else
        {
            //Send the handshake.
            NetMessage<Peer> message = new NetMessage<>();
            message.type = EType.HANDSHAKE;
            //TODO: Client username.
            message.payload = new Peer(desiredUsername);
            client.SendMessage(message);
        }
    }

    private static void OnNetMessage(KeyValuePair<UUID, Object> data)
    {
        NetMessage<?> netMessage = (NetMessage<?>)data.GetValue();

        if (isHost)
        {
            switch (netMessage.type)
            {
                //I like to use {} on my switch case statements for two reasons, it scopes each case and it increases readability.
                case HANDSHAKE:
                {
                    Peer inPayload = (Peer)netMessage.payload;

                    String nickname = inPayload.nickname == null || inPayload.nickname.isBlank() ? "Anonymous" : inPayload.nickname;
                    int duplicateCount = 0;

                    //Check for duplicate nicknames.
                    //(This could've been done ine like 2 lines in C# but Java's inability to pass local variables makes it a bit more complicated).
                    while (true)
                    {
                        boolean duplicateFound = false;
                        for (Peer peer : peers.values())
                        {
                            //It seems like string == string is not the same as string.equals(string) in Java.
                            if (peer.GetIsReady() && peer.nickname != null && peer.nickname.equals(nickname))
                            {
                                duplicateFound = true;
                                break;
                            }
                        }
                        if (!duplicateFound)
                            break;

                        nickname = inPayload.nickname + ++duplicateCount;
                    }

                    //Indicate that the client is ready.
                    Peer peer = peers.get(data.GetKey());
                    peer.nickname = nickname;
                    peer.SetIsReady();

                    System.out.println("[SERVER] Client connected: " + data.GetKey() + " (" + peer.nickname + ")");

                    //Return the server-validated handshake data back to the client.
                    NetMessage<Peer> response = new NetMessage<>();
                    response.type = EType.HANDSHAKE;
                    response.payload = peer;
                    serverManager.SendMessage(data.GetKey(), response);
                    break;
                }
                case PEERS:
                {
                    //Send the list of peers to the client.
                    NetMessage<PeersPayload> response = new NetMessage<>();
                    response.type = EType.PEERS;
                    response.payload = new PeersPayload();
                    response.payload.peers = GetReadyPeers().toArray(new Peer[peers.size()]);
                    serverManager.SendMessage(data.GetKey(), response);
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
            switch (netMessage.type)
            {
                case HANDSHAKE:
                {
                    Peer inPayload = (Peer)netMessage.payload;
                    clientID = inPayload.GetUUID();

                    System.out.println("[CLIENT] Connected to server.");

                    //Occurs when the handshake has been acknowledged by the server.
                    //Request a list of peers.
                    NetMessage<EmptyPayload> response = new NetMessage<>();
                    response.type = EType.PEERS;
                    response.payload = new EmptyPayload();
                    client.SendMessage(response);
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
                case PEERS:
                {
                    //Occurs when the server has sent a list of peers.
                    //Replace the list of peers with the new list.
                    peers.clear();
                    for (Peer peer : ((PeersPayload)netMessage.payload).peers)
                        if (peer != null) //In my testing it seemed like the payload would include one extra null value.
                            peers.put(peer.GetUUID(), peer);
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

    private static void OnNetClose(UUID uuid)
    {
        if (isHost)
        {
            //Server has closed, handled in OnNetError.
            if (uuid == ServerManager.SERVER_UUID)
                return;

            //Client has disconnected.
            if (peers.get(uuid).GetIsReady())
                System.out.println("[SERVER] Client disconnected: " + uuid + "");
            peers.remove(uuid);
        }
        else
        {
            System.out.println("[CLIENT] Disconnected from server.");
            Restart();
        }
    }

    private static void OnNetError(KeyValuePair<UUID, Exception> error)
    {
        if (isHost)
        {
            if (error.GetValue() instanceof BindException)
            {
                /*A bind error can occur when another server is already running on the same port
                 *if this happens, assume another server instance is running.*/
                Restart();
                return;
            }
            System.out.println("[SERVER | ERROR] " + error.GetValue().getMessage());
        }
        else
        {
            System.out.println("[CLIENT | ERROR] " + error.GetValue().getMessage());
        }
    }

    private static Collection<Peer> GetReadyPeers()
    {
        return peers.values().stream().filter(Peer::GetIsReady).collect(Collectors.toList());
    }
    //#endregion

    //#region User interactive
    @CommandAttribute(description = "Prints a list of available commands.", availableInMode = 0)
    private static void Help()
    {
        //This method is modeled after my C# implementation of this attribute based command system: https://github.com/ReadieFur/CreateProcessAsUser/blob/fd80746a175c52bd64edc40a5c1e590c65c171d5/src/CreateProcessAsUser.Service/UserInteractive.cs#L387-L401

        String helpMessage = "Command usage: [Command] <Parameter...>";

        //Reflect on this method and get all of the commands.
        for (Method method : ChatManager.class.getDeclaredMethods())
        {
            CommandAttribute commandAttribute = method.getAnnotation(CommandAttribute.class);
            if (commandAttribute == null)
                continue;

            helpMessage += "\n\t" + method.getName();

            if (commandAttribute.description() != null && !commandAttribute.description().isBlank())
                helpMessage += "\n\t\t" + commandAttribute.description();

            //Get the parameters of the method.
            CommandParameterAttributes parameters = method.getAnnotation(CommandParameterAttributes.class);
            if (parameters != null)
            {
                for (CommandParameterAttribute parameter : parameters.value())
                {
                    helpMessage += "\n\t\t\t" + parameter.name();

                    //The description is optional. Each newline on the description will be replaced by a new line and indentation.
                    if (parameter.description() != null && !parameter.description().isBlank())
                        helpMessage += "\n\t\t\t\t" + parameter.description().replace("\n", "\n\t\t\t\t");
                }
            }
        }

        ConsoleWrapper.SyncWriteLine(helpMessage);
    }
    //#endregion
}
