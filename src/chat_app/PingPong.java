package chat_app;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import chat_app.net_data.EType;
import chat_app.net_data.NetMessage;
import readiefur.helpers.KeyValuePair;
import readiefur.helpers.sockets.ServerClientHost;
import readiefur.helpers.sockets.ServerManager;

public class PingPong extends Thread
{
    private ServerManager serverManager;
    private ConcurrentHashMap<UUID, Boolean> pongedPeers = new ConcurrentHashMap<>();

    public PingPong(ServerManager serverManager)
    {
        this.serverManager = serverManager;
        serverManager.onMessage.Add(this::OnMessage);
    }

    @Override
    public void run()
    {
        while (true)
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (Exception e) {}
            if (serverManager.IsDisposed() || !this.isAlive())
                break;

            //Disconnect any peers that haven't responded to a ping.
            pongedPeers.forEach((UUID peerID, Boolean ponged) ->
            {
                if (!ponged)
                {
                    try { serverManager.DisconnectClient(peerID); }
                    catch (Exception ex) {}
                }
            });

            //Reset tje pongedPeers dictionary.
            pongedPeers.clear();
            //We only want to add peers for the current frame.
            serverManager.GetClientHosts().forEach((UUID peerID, ServerClientHost serverClientHost) ->
            {
                pongedPeers.put(peerID, false);
            });

            //Send a new ping.
            NetMessage<EmptyPayload> message = new NetMessage<>();
            message.type = EType.PING;
            message.payload = new EmptyPayload();
            serverManager.BroadcastMessage(message);
        }
    }

    private void OnMessage(KeyValuePair<UUID, Object> message)
    {
        NetMessage<?> netMessage = (NetMessage<?>)message.GetValue();

        if (netMessage.type != EType.PONG)
            return;

        if (pongedPeers.containsKey(message.GetKey()))
            pongedPeers.put(message.GetKey(), true);
    }
}
