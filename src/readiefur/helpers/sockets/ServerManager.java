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
import readiefur.helpers.IDisposable;
import readiefur.helpers.KeyValuePair;

//This is taking inspiration from my CSharpTools.Pipes project as the way Java handles networking is similar: https://github.com/ReadieFur/CSharpTools/blob/main/src/CSharpTools.Pipes
//TODO: Add a dispose method.
public class ServerManager extends Thread implements IDisposable
{
    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Object lock = new Object();
    private int port;
    protected Boolean isDisposed = false;
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

    public void Dispose()
    {
        if (isDisposed)
            return;
        Close();
    }

    @Override
    public void run()
    {
        try
        {
            server = new ServerSocket(port);
            while (!isDisposed && !server.isClosed())
            {
                try
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
                catch (Exception ex)
                {
                    if (isDisposed || server == null || server.isClosed())
                        break;
                    onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex));
                }
            }
        }
        catch (IOException ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }

        //We do not want to dispose of the server when it fails because we may want to reuse it.
        Close();
    }

    public List<UUID> GetClients()
    {
        return new ArrayList<>(servers.keySet());
    }

    private void Close()
    {
        //Prevent race conditions.
        synchronized (lock)
        {
            /*This can be called twice if the Close method is called and the exiting thread then tries to call this method.
             *If that happens, we can safely ignore it.*/
            if (server == null)
                return;

            //Close all clients.
            for (ServerClientHost serverClientHost : servers.values())
            {
                try { serverClientHost.Dispose(); }
                catch (Exception ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }
            }

            //Close the server.
            try { server.close(); }
            catch (Exception ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }
            server = null;

            //Stop the thread.
            try { this.interrupt(); }
            catch (Exception ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }

            //Clear the list of clients.
            servers.clear();

            onClose.Invoke(SERVER_UUID);
        }
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
        onClose.Invoke(uuid);
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
