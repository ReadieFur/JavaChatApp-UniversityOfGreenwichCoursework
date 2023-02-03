package readiefur.helpers.sockets;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import readiefur.helpers.Event;
import readiefur.helpers.IDisposable;
import readiefur.helpers.KeyValuePair;

//This is taking inspiration from my CSharpTools.Pipes project as the way Java handles networking is similar: https://github.com/ReadieFur/CSharpTools/blob/main/src/CSharpTools.Pipes
public class ServerManager extends Thread implements IDisposable
{
    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Object lock = new Object();
    private int port;
    protected Boolean isDisposed = false;
    protected ServerSocket server = null;
    protected ConcurrentHashMap<UUID, ServerClientHost> servers = new ConcurrentHashMap<>();
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
        //Prevent race conditions.
        synchronized (lock)
        {
            if (isDisposed)
                return;
            isDisposed = true;

            //Disconnect all clients.
            for (ServerClientHost serverClientHost : servers.values())
            {
                try { serverClientHost.Dispose(); }
                catch (Exception ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }
            }
            servers.clear();

            //Close the server.
            if (server != null)
            {
                try { server.close(); }
                catch (Exception ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }
                server = null;
            }

            //Stop the thread.
            if (this.isAlive())
            {
                try { this.interrupt(); }
                catch (Exception ex) { onError.Invoke(new KeyValuePair<>(SERVER_UUID, ex)); }
            }

            //Clear the list of clients.
            servers.clear();

            onClose.Invoke(SERVER_UUID);
        }
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
                    if (servers.putIfAbsent(uuid, serverClientHost) != null)
                    {
                        socket.close();
                        onError.Invoke(new KeyValuePair<>(SERVER_UUID, new Exception("Failed to add client to list.")));
                    }

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

        /*In my previous tests I had this close the connection and thread as opposed to disposing of the this instance.
         *The reason I don't do this anymore is because after a quick look, I found you cannot reuse threads in java.
         *I could work around this by creating another wrapper instance but that can be done later if needs be.
         *I will be leaving the code for closure in the deconstructor though for good practice and if I need to reimplement it again later.*/
        Dispose();
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
        try { servers.remove(uuid); }
        catch (NullPointerException ex) { /*Ignore as a possible race condition was met.*/ }
        onClose.Invoke(uuid);
    }

    private void OnError(UUID uuid, Exception ex)
    {
        onError.Invoke(new KeyValuePair<>(uuid, ex));
    }

    //A NullPointerException can occur if the guid is not found or a race condition occurs.
    public void SendMessage(UUID uuid, Object data) throws NullPointerException
    {
        ServerClientHost serverClientHost = servers.getOrDefault(uuid, null);
        if (serverClientHost == null)
            throw new NullPointerException("The client was not found.");
        serverClientHost.SendMessage(data);
    }

    public void BroadcastMessage(Object data)
    {
        for (ServerClientHost serverClientHost : servers.values())
            serverClientHost.SendMessage(data);
    }
}
