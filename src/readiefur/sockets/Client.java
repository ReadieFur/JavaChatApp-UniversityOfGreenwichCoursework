package readiefur.sockets;

import java.net.Socket;

import readiefur.misc.ManualResetEvent;

public class Client extends ASocket
{
    private String address;
    private int port;
    private ManualResetEvent startEvent = new ManualResetEvent(false);

    public Client(String address, int port)
    {
        super(null); //We initialize the socket to null as we will be creating it in the thread. While this isn't great practice, we are fortunate enough that the virtual methods have no critical uses for this variable being initialized (that is before we start the thread method which we will override).
        this.address = address;
        this.port = port;
    }

    public Boolean Start()
    {
        super.start();
        startEvent.WaitOne();
        return socket != null;
    }

    @Override
    public void run()
    {
        //Try to set the thread name to the class name, not required but useful for debugging.
        try { setName(getClass().getSimpleName()); }
        catch (Exception e) {}

        if (isDisposed)
            return;

        try { socket = new Socket(address, port); }
        catch (Exception ex)
        {
            socket = null;
            onError.Invoke(ex);
            Dispose();
        }
        finally { startEvent.Set(); }

        if (socket != null)
            super.run();
    }
}
