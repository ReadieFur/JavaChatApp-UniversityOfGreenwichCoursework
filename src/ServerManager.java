import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Flow.Subscriber;

//This is taking inspiration from my CSharpTools.Pipes project as the way Java handles networking is similar: https://github.com/ReadieFur/CSharpTools/blob/main/src/CSharpTools.Pipes
public class ServerManager
{
    //#region Instance
    private int port;
    private ServerSocket server = null;
    private Map<String, ServerClientHost> servers = new HashMap<>();
    //TODO: Accessability modifiers.
    public Event<String> onClientConnected = new Event<>();
    public Event<KeyValuePair<String, Object>> onClientMessage = new Event<>();
    public Event<String> onClientClose = new Event<>();
    public Event<KeyValuePair<String, Exception>> onClientError = new Event<>();

    public ServerManager(int port)
    {
        this.port = port;
    }

    public void Start() throws IOException
    {
        server = new ServerSocket(port);
        while (true)
        {
            Socket socket = server.accept();
            //Use IP for now but use a GUID later.
            String guid = socket.getInetAddress().getHostAddress();
            ServerClientHost serverClientHost = new ServerClientHost(socket);
            servers.put(guid, serverClientHost);
            serverClientHost.onMessage.Add(obj -> OnClientMessage(guid, obj)); //This may need encapsulating to maintain access to instance variables.
            serverClientHost.onClose.Add(nul -> OnClientClose(guid));
            serverClientHost.onError.Add(ex -> OnClientError(guid, ex));
            onClientConnected.Invoke(guid);
        }
    }

    private void OnClientMessage(String guid, Object data)
    {
        onClientMessage.Invoke(new KeyValuePair<>(guid, data));
    }

    private void OnClientClose(String guid)
    {
        servers.remove(guid);
    }

    private void OnClientError(String guid, Exception ex)
    {
        onClientError.Invoke(new KeyValuePair<>(guid, ex));
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
