package readiefur.helpers.sockets;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import readiefur.helpers.Event;
import readiefur.helpers.KeyValuePair;

//This is taking inspiration from my CSharpTools.Pipes project as the way Java handles networking is similar: https://github.com/ReadieFur/CSharpTools/blob/main/src/CSharpTools.Pipes
//TODO: Add a dispose method.
public class ServerManager extends Thread
{
    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private int port;
    protected ServerSocket server = null;
    protected Map<UUID, ServerClientHost> servers = new HashMap<>();
    /*I can use the final keyword here to make the instance readonly,
     *The only reason I wouldn't like to do this is inherited classes wouldn't be able to override this I don't believe.*/
    /*You will also notice that I haven't fully capitalized these variables as while they are "constant",
     *(not a primitive, known, type at compile time so they're not really), I am using it as a readonly modifier.*/
    public final Event<UUID> onConnect = new Event<>();
    public final Event<KeyValuePair<UUID, Object>> onMessage = new Event<>();
    public final Event<UUID> onClose = new Event<>();
    public final Event<KeyValuePair<UUID, Exception>> onError = new Event<>();

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

                final UUID uuid = GenerateUUID();

                ServerClientHost serverClientHost = new ServerClientHost(socket);
                servers.put(uuid, serverClientHost);

                //These may need encapsulating to maintain access to instance variables.
                /*A new limitation has been found, I don't think java has such encapsulation
                 *and so reading a scoped variable that is not readonly causes an error.
                 *To work around this I have made the UUID final and then created an external method that generates the UUID as required.*/
                serverClientHost.onMessage.Add(obj -> OnMessage(uuid, obj));
                serverClientHost.onClose.Add(nul -> OnClose(uuid));
                serverClientHost.onError.Add(ex -> OnError(uuid, ex));

                onConnect.Invoke(uuid);
            }
        }
        catch (IOException ex)
        {
            onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex));
        }
    }

    public List<UUID> GetClients()
    {
        return new ArrayList<>(servers.keySet());
    }

    private UUID GenerateUUID()
    {
        UUID uuid;
        do { uuid = UUID.randomUUID(); }
        while (servers.containsKey(uuid));
        return uuid;
    }

    private void OnMessage(UUID uuid, Object data)
    {
        onMessage.Invoke(new KeyValuePair<>(uuid, data));
    }

    private void OnClose(UUID uuid)
    {
        servers.remove(uuid);
    }

    private void OnError(UUID uuid, Exception ex)
    {
        onError.Invoke(new KeyValuePair<>(uuid, ex));
    }

    //A NullPointerException can occur if the guid is not found or a race condition occurs.
    public void SendMessage(UUID uuid, Object data) throws NullPointerException
    {
        servers.get(uuid).SendMessage(data);
    }

    public void BroadcastMessage(Object data)
    {
        for (ServerClientHost serverClientHost : servers.values())
            serverClientHost.SendMessage(data);
    }
}
