package readiefur.helpers.sockets;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import readiefur.helpers.Event;
import readiefur.helpers.KeyValuePair;

//This is taking inspiration from my CSharpTools.Pipes project as the way Java handles networking is similar: https://github.com/ReadieFur/CSharpTools/blob/main/src/CSharpTools.Pipes
//TODO: Add a dispose method.
public class ServerManager extends Thread
{
    //#region Instance
    private int port;
    private ServerSocket server = null;
    private Map<String, ServerClientHost> servers = new HashMap<>();
    //TODO: Accessability modifiers.
    public Event<String> onConnect = new Event<>();
    public Event<KeyValuePair<String, Object>> onMessage = new Event<>();
    public Event<String> onClose = new Event<>();
    public Event<KeyValuePair<String, Exception>> onError = new Event<>();

    public ServerManager(int port)
    {
        this.port = port;
    }

    @Override
    public void run()
    {
        try
        {
            server = new ServerSocket(port);
            while (true)
            {
                Socket socket = server.accept();
                //Use IP for now but use a GUID later.
                String guid = socket.getInetAddress().getHostAddress();
                ServerClientHost serverClientHost = new ServerClientHost(socket);
                servers.put(guid, serverClientHost);
                serverClientHost.onMessage.Add(obj -> OnMessage(guid, obj)); //This may need encapsulating to maintain access to instance variables.
                serverClientHost.onClose.Add(nul -> OnClose(guid));
                serverClientHost.onError.Add(ex -> OnError(guid, ex));
                onConnect.Invoke(guid);
            }
        }
        catch (IOException ex)
        {
            //TODO: Handle this better.
            onError.Invoke(new KeyValuePair<>("-1", ex));
        }
    }

    private void OnMessage(String guid, Object data)
    {
        onMessage.Invoke(new KeyValuePair<>(guid, data));
    }

    private void OnClose(String guid)
    {
        servers.remove(guid);
    }

    private void OnError(String guid, Exception ex)
    {
        onError.Invoke(new KeyValuePair<>(guid, ex));
    }

    public void SendMessage(String guid, Object data)
    {
        servers.get(guid).SendMessage(data);
    }

    public void BroadcastMessage(Object data)
    {
        for (ServerClientHost serverClientHost : servers.values())
            serverClientHost.SendMessage(data);
    }
    //#endregion
}
