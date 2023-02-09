package chat_app;

import java.lang.reflect.InvocationTargetException;
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
import chat_app.net_data.EPeerStatus;
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

    //Shared properties.
    private static Boolean isHost = true;
    private static UUID id = null;
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
        ChatManager.desiredUsername = desiredUsername == null || desiredUsername.isBlank() ? "Anonymous" : desiredUsername;

        //Start the manager.
        Restart();

        //Start the console input thread.
        ConsoleInputThread consoleInputThread = ConsoleInputThread.GetInstance();
        consoleInputThread.onInput.Add(ChatManager::OnConsoleInput);

        //Wait indefinitely until signaled to exit.
        exitEvent.WaitOne();

        consoleInputThread.Dispose();
        Cleanup();

        //[TEMPORARY] Please read "ConsoleInputManagerThread.java:51".
        System.exit(0);
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

        //Shared.
        id = null;
        peers.clear();
    }
    //#endregion

    //#region Setup methods
    private static void Restart()
    {
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
        isHost = hostAddress == null;
        if (isHost)
        {
            //Start the server.
            serverManager = new ServerManager(port);
            serverManager.onConnect.Add(ChatManager::OnNetConnect);
            serverManager.onMessage.Add(ChatManager::OnNetMessage);
            serverManager.onClose.Add(ChatManager::OnNetClose);
            serverManager.onError.Add(ChatManager::OnNetError);

            try
            {
                ServerPeer serverPeer = new ServerPeer(
                    ServerManager.SERVER_UUID,
                    Inet4Address.getLocalHost().getHostAddress(),
                    desiredUsername);
                serverPeer.SetStatus(EPeerStatus.CONNECTED);
                peers.put(ServerManager.SERVER_UUID, serverPeer);
                id = ServerManager.SERVER_UUID;
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
            peers.put(uuid, new ServerPeer(
                uuid,
                serverManager.GetClientHosts().get(uuid).GetSocket().getInetAddress().getHostAddress(),
                null));
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
                    //Check if the client has already sent a handshake.
                    if (peers.get(data.GetKey()).GetStatus() == EPeerStatus.CONNECTED)
                        return;

                    Peer inPayload = (Peer)netMessage.payload;

                    String nickname = inPayload.nickname;
                    int duplicateCount = 0;

                    //Check for duplicate nicknames.
                    //(This could've been done ine like 2 lines in C# but Java's inability to pass local variables makes it a bit more complicated).
                    while (true)
                    {
                        boolean duplicateFound = false;
                        for (Peer peer : peers.values())
                        {
                            //It seems like string == string is not the same as string.equals(string) in Java.
                            if (peer.GetStatus() == EPeerStatus.CONNECTED && peer.nickname != null && peer.nickname.equals(nickname))
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
                    ServerPeer peer = (ServerPeer)peers.get(data.GetKey());
                    peer.SetNickname(nickname);
                    peer.SetStatus(EPeerStatus.CONNECTED);

                    System.out.println("[SERVER] Client connected: " + data.GetKey() + " (" + peer.nickname + ")");

                    //Return the server-validated handshake data back to the client.
                    NetMessage<Peer> handshakeResponse = new NetMessage<>();
                    handshakeResponse.type = EType.HANDSHAKE;
                    handshakeResponse.payload = peer;
                    serverManager.SendMessage(data.GetKey(), handshakeResponse);

                    //Also broadcast the new peer to all other clients.
                    NetMessage<Peer> broadcastMessage = new NetMessage<>();
                    broadcastMessage.type = EType.PEER;
                    broadcastMessage.payload = peer;
                    serverManager.BroadcastMessage(broadcastMessage);
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
                    id = inPayload.GetUUID();

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
                case PEER:
                {
                    //Occurs when the server has sent a status update for a peer.
                    Peer inPayload = (Peer)netMessage.payload;

                    //Ignore self.
                    if (inPayload.GetUUID() == id)
                        return;

                    switch (inPayload.GetStatus())
                    {
                        case CONNECTED:
                        {
                            System.out.println("[CLIENT] Peer connected: " + inPayload.GetUUID() + " (" + inPayload.nickname + ")");
                            peers.putIfAbsent(inPayload.GetUUID(), inPayload);
                            break;
                        }
                        case DISCONNECTED:
                        {
                            System.out.println("[CLIENT] Peer disconnected: " + inPayload.GetUUID() + " (" + inPayload.nickname + ")");
                            if (peers.containsKey(inPayload.GetUUID()))
                                peers.remove(inPayload.GetUUID());
                            break;
                        }
                        default:
                        {
                            break;
                        }
                    }
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

            if (!peers.containsKey(uuid))
            {
                //Can occur for server lookups.
                return;
            }

            ServerPeer oldPeer = (ServerPeer)peers.get(uuid);
            peers.remove(uuid);

            //Client has disconnected.
            System.out.println("[SERVER] Client disconnected: " + uuid + "");

            oldPeer.SetStatus(EPeerStatus.DISCONNECTED);

            //Broadcast the disconnected peer to all other clients.
            NetMessage<Peer> broadcastMessage = new NetMessage<>();
            broadcastMessage.type = EType.PEER;
            broadcastMessage.payload = oldPeer;
            serverManager.BroadcastMessage(broadcastMessage);
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
        }
        else
        {
        }

        System.err.println(error.GetValue().getMessage());
    }

    private static Collection<Peer> GetReadyPeers()
    {
        return peers.values().stream().filter(p -> p.status == EPeerStatus.CONNECTED).collect(Collectors.toList());
    }
    //#endregion

    //#region User interactive
    private static void OnConsoleInput(String data)
    {
        String[] splitData = data.split(" ");

        if (splitData.length == 0)
            return;

        String command = splitData[0];

        //Reflect on this method and get all of the commands.
        Method matchingMethod = null;
        CommandAttribute matchingCommandAttribute = null;
        for (Method method : ChatManager.class.getDeclaredMethods())
        {
            //Get the command attribute (if applicable).
            CommandAttribute commandAttribute = method.getAnnotation(CommandAttribute.class);

            //Check if the command matches the command attribute.
            if (commandAttribute == null || !method.getName().toLowerCase().equals(command))
                continue;

            matchingMethod = method;
            matchingCommandAttribute = commandAttribute;
        }
        if (matchingMethod == null)
        {
            ConsoleWrapper.SyncWriteLine("Unknown command. Type 'help' for a list of commands.");
            return;
        }

        //Check if the command is available in the current mode.
        if (matchingCommandAttribute.availableInMode() != 0 && matchingCommandAttribute.availableInMode() != (isHost ? 1 : 2))
        {
            ConsoleWrapper.SyncWriteLine("Command not available in this mode.");
            return;
        }

        //Check if the command has the correct number of parameters.
        CommandParameterAttributes parameters = matchingMethod.getAnnotation(CommandParameterAttributes.class);
        if (splitData.length - 1 != (parameters == null ? 0 : parameters.value().length))
        {
            ConsoleWrapper.SyncWriteLine("Invalid number of parameters.");
            return;
        }

        //Invoke the command.
        try
        {
            Object[] params = new Object[splitData.length - 1];
            for (int i = 1; i < splitData.length; i++)
                params[i - 1] = splitData[i];
            matchingMethod.invoke(null, params);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {}
        catch (Exception ex)
        {
            System.err.println(ex.getMessage());
        }
    }

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

    @CommandAttribute(description = "Exits the application.", availableInMode = 0)
    private static void Exit()
    {
        exitEvent.Set();
    }

    @CommandAttribute(description = "Lists the available peers.", availableInMode = 0)
    private static void ListPeers()
    {
        Collection<Peer> peers = GetReadyPeers();

        if (peers.isEmpty())
        {
            ConsoleWrapper.SyncWriteLine("No peers available.");
            return;
        }

        String peersMessage = "Available peers:";
        for (Peer peer : peers)
        {
            peersMessage += "\n\t";

            if (peer.GetUUID().equals(id))
                peersMessage += "[You] ";

            peersMessage += peer.nickname;

            if (isHost)
                peersMessage += " (" + peer.GetUUID() + " | " + peer.GetIPAddress() + ")";
        }
        ConsoleWrapper.SyncWriteLine(peersMessage);
    }
    //#endregion
}
