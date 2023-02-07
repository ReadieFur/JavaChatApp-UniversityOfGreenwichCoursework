package readiefur.helpers.sockets;

import java.net.Socket;

public class Client extends ASocket
{
    private String address;
    private int port;

    public Client(String address, int port)
    {
        super(null); //We initialize the socket to null as we will be creating it in the thread. While this isn't great practice, we are fortunate enough that the virtual methods have no critical uses for this variable being initialized (that is before we start the thread method which we will override).
        this.address = address;
        this.port = port;
    }

    @Override
    public void run()
    {
        if (isDisposed)
            return;

        try { socket = new Socket(address, port); }
        catch (Exception ex)
        {
            onError.Invoke(ex);
            Dispose();
        }

        if (socket != null)
            super.run();
    }
}
