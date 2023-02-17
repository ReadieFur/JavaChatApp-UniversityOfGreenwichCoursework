package readiefur.sockets;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import readiefur.misc.Event;
import readiefur.misc.IDisposable;
import readiefur.misc.ManualResetEvent;
import readiefur.misc.Pair;

//This is taking inspiration from my CSharpTools.Pipes project as the way Java handles networking is similar: https://github.com/ReadieFur/CSharpTools/blob/main/src/CSharpTools.Pipes
public class ServerManager extends Thread implements IDisposable
{
    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final UUID INVALID_UUID = UUID.fromString("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF");

    private final Object lock = new Object();
    private int port;
    private ManualResetEvent startEvent = new ManualResetEvent(false);

    protected Boolean isDisposed = false;
    protected ServerSocket server = null;
    protected ConcurrentHashMap<UUID, ServerClientHost> servers = new ConcurrentHashMap<>();
    /*I can use the final keyword here to make the instance readonly,
     *The only reason I wouldn't like to do this is inherited classes wouldn't be able to override this I don't believe.*/
    /*You will also notice that I haven't fully capitalized these variables as while they are "constant",
     *(not a primitive, known, type at compile time so they're not really), I am using it as a readonly modifier.*/
    public final Event<UUID> onConnect = new Event<>();
    public final Event<Pair<UUID, Object>> onMessage = new Event<>();
    public final Event<UUID> onClose = new Event<>();
    public final Event<Pair<UUID, Exception>> onError = new Event<>();

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
                catch (Exception ex) { onError.Invoke(new Pair<>(SERVER_UUID, ex)); }
            }
            servers.clear();

            //Close the server.
            if (server != null)
            {
                try { server.close(); }
                catch (Exception ex) { onError.Invoke(new Pair<>(SERVER_UUID, ex)); }
                server = null;
            }

            //Stop the thread.
            if (this.isAlive())
            {
                try { this.interrupt(); }
                catch (Exception ex) { onError.Invoke(new Pair<>(SERVER_UUID, ex)); }
            }

            //Clear the list of clients.
            servers.clear();

            onClose.Invoke(SERVER_UUID);
        }
    }

    @Override
    public void run()
    {
        //Try to set the thread name to the class name, not required but useful for debugging.
        try { setName(getClass().getSimpleName()); }
        catch (Exception e) {}

        try
        {
            try { server = new ServerSocket(port); }
            catch (Exception ex)
            {
                server = null;
                onError.Invoke(new Pair<>(SERVER_UUID, ex));
                //If the server fails to start, then cancel the startup (done in the while loop below).
            }
            finally
            {
                startEvent.Set();
            }

            while (server != null && !isDisposed && !server.isClosed())
            {
                try
                {
                    Socket socket = server.accept();

                    final UUID uuid = GenerateUUID();

                    ServerClientHost serverClientHost = new ServerClientHost(socket, uuid);

                    if (servers.putIfAbsent(uuid, serverClientHost) != null)
                    {
                        socket.close();
                        onError.Invoke(new Pair<>(SERVER_UUID, new Exception("Failed to add client to list.")));
                    }

                    //We manually fire this event as the constructor for the ServerClientHost class starts the thread immediately and so the registration of the event could be missed.
                    onConnect.Invoke(uuid);

                    //These may need encapsulating to maintain access to instance variables.
                    /*A new limitation has been found, I don't think java has such encapsulation
                    *and so reading a scoped variable that is not readonly causes an error.
                    *To work around this I have made the UUID final and then created an external method that generates the UUID as required.*/
                    serverClientHost.onMessage.Add(obj -> OnMessage(uuid, obj));
                    serverClientHost.onClose.Add(nul -> OnClose(uuid));
                    serverClientHost.onError.Add(ex -> OnError(uuid, ex));
                }
                catch (Exception ex)
                {
                    if (isDisposed || server == null || server.isClosed())
                        break;
                    onError.Invoke(new Pair<>(SERVER_UUID, ex));
                }
            }
        }
        catch (Exception ex) { onError.Invoke(new Pair<>(SERVER_UUID, ex)); }

        /*In my previous tests I had this close the connection and thread as opposed to disposing of the this instance.
         *The reason I don't do this anymore is because after a quick look, I found you cannot reuse threads in java.
         *I could work around this by creating another wrapper instance but that can be done later if needs be.
         *I will be leaving the code for closure in the deconstructor though for good practice and if I need to reimplement it again later.*/
        Dispose();
    }

    public Boolean Start()
    {
        super.start();
        startEvent.WaitOne();
        return server != null;
    }

    public Boolean IsDisposed()
    {
        return isDisposed;
    }

    public ConcurrentHashMap<UUID, ServerClientHost> GetClientHosts()
    {
        return servers;
    }

    private UUID GenerateUUID()
    {
        UUID uuid;
        do { uuid = UUID.randomUUID(); }
        while (servers.containsKey(uuid) || uuid.equals(SERVER_UUID) || uuid.equals(INVALID_UUID));
        return uuid;
    }

    private void OnMessage(UUID uuid, Object data)
    {
        onMessage.Invoke(new Pair<>(uuid, data));
    }

    private void OnClose(UUID uuid)
    {
        try { servers.remove(uuid); }
        catch (NullPointerException ex) { /*Ignore as a possible race condition was met.*/ }
        onClose.Invoke(uuid);
    }

    private void OnError(UUID uuid, Exception ex)
    {
        onError.Invoke(new Pair<>(uuid, ex));
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

    public void DisconnectClient(UUID uuid) throws NullPointerException
    {
        ServerClientHost serverClientHost = servers.getOrDefault(uuid, null);
        if (serverClientHost == null)
            throw new NullPointerException("The client was not found.");
        serverClientHost.Dispose();
        //The client will be removed from the servers dictionary in the OnClose method.
    }
}
