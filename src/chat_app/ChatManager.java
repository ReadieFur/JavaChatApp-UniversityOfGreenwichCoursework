package chat_app;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import chat_app.net_data.EType;
import chat_app.net_data.NetMessage;
import chat_app.net_data.PeersPayload;
import readiefur.helpers.KeyValuePair;
import readiefur.helpers.ManualResetEvent;
import readiefur.helpers.sockets.Client;
import readiefur.helpers.sockets.ServerManager;

public class ChatManager
{
    private static ManualResetEvent exitEvent = new ManualResetEvent(false);

    //The port however will never change.
    private static int port;

    private static Boolean isHost = false;
    private static ServerManager serverManager = null;
    private static Client client = null;

    private static HashMap<UUID, Peer> peers = new HashMap<>();
    private static PingPong pingPong = null;

    private ChatManager() {}

    //This is a blocking method that will not return until the application is closed.
    public static void Begin(String initialServerAddress, int port)
    {
        ChatManager.port = port;

        Restart(initialServerAddress);

        //Wait indefinitely until signaled to exit.
        exitEvent.WaitOne();

        //Cleanup.
        if (isHost)
        {
            serverManager.Dispose();
            serverManager = null;

            pingPong.interrupt();
            pingPong = null;
        }
        else
        {
            client.Dispose();
            client = null;
        }
    }

    private static void Restart(String fallbackServerIPAddress)
    {
        //Cleanup.
        if (serverManager != null)
        {
            serverManager.Dispose();
            serverManager = null;

            pingPong.interrupt();
            pingPong = null;
        }
        if (client != null)
        {
            client.Dispose();
            client = null;
        }

        List<Peer> oldPeers = new ArrayList<>(peers.values());
        peers.clear();

        String hostAddress = null;
        for (Peer peer : oldPeers)
        {
            //Look for a host.
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
                peers.put(ServerManager.SERVER_UUID,
                    new Peer(ServerManager.SERVER_UUID, Inet4Address.getLocalHost().getHostAddress()));
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

    //UUIDs are null for client connections.
    private static void OnNetConnect(UUID uuid)
    {
        if (isHost)
        {
            System.out.println("[SERVER] Client connected: " + uuid + ""); //Remove this message from here once the handshake is implemented.
            peers.put(uuid, new Peer(uuid, serverManager.GetClientHosts().get(uuid).GetSocket().getInetAddress().getHostAddress()));
        }
        else
        {
            System.out.println("[CLIENT] Connected to server.");
            //Ask the server for a list of peers.
            NetMessage<EmptyPayload> message = new NetMessage<>();
            message.type = EType.PEERS;
            message.payload = new EmptyPayload();
            client.SendMessage(message);
        }
    }

    private static void OnNetMessage(KeyValuePair<UUID, Object> message)
    {
        NetMessage<?> netMessage = (NetMessage<?>)message.GetValue();

        if (isHost)
        {
            switch (netMessage.type)
            {
                case PEERS:
                    //Send the list of peers to the client.
                    NetMessage<PeersPayload> response = new NetMessage<>();
                    response.type = EType.PEERS;
                    response.payload = new PeersPayload();
                    response.payload.peers = peers.values().toArray(new Peer[peers.size()]);
                    serverManager.SendMessage(message.GetKey(), response);
                    break;
                default:
                    //Invalid message type, ignore the request.
                    return;
            }
        }
        else
        {
            switch (netMessage.type)
            {
                case PEERS:
                    //Update the list of peers.
                    PeersPayload payload = (PeersPayload)netMessage.payload;
                    for (Peer peer : payload.peers)
                        peers.putIfAbsent(peer.GetUUID(), peer);
                    break;
                case PING:
                    //Send a pong response.
                    NetMessage<EmptyPayload> response = new NetMessage<>();
                    response.type = EType.PONG;
                    response.payload = new EmptyPayload();
                    client.SendMessage(response);
                    break;
                default:
                    //Invalid message type, ignore the request.
                    return;
            }
        }
    }

    private static void OnNetClose(UUID uuid)
    {
        if (isHost)
        {
            // if (!peers.containsKey(uuid))
            //     return;

            System.out.println("[SERVER] Client disconnected: " + uuid + "");
            peers.remove(uuid);
        }
        else
        {
            System.out.println("[CLIENT] Disconnected from server.");
            // Restart(null);
        }
    }

    private static void OnNetError(KeyValuePair<UUID, Exception> error)
    {
        System.out.println("[ERROR] " + error.GetValue().getMessage());

        if (isHost)
        {
        }
        else
        {
        }
    }
}
