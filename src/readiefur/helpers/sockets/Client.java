package readiefur.helpers.sockets;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import readiefur.helpers.Event;

public class Client extends Thread
{
    private Boolean isDisposed = false;
    private String address;
    private int port;
    private Socket socket = null;

    public Event<Void> onConnect = new Event<>();
    public Event<Object> onMessage = new Event<>();
    public Event<Void> onClose = new Event<>();
    public Event<Exception> onError = new Event<>();

    public Client(String address, int port)
    {
        this.address = address;
        this.port = port;
    }

    private void Close()
    {
        //Close the socket.
        try { socket.close(); }
        catch (Exception ex) { onError.Invoke(ex); }

        onClose.Invoke(null);
    }

    //TODO: Needs fixing.
    public void Dispose()
    {
        if (isDisposed)
            return;
        isDisposed = true;

        Close();

        //If the thread is still running, interrupt it.
        try
        {
            if (this.isAlive())
                this.interrupt();
        }
        catch (Exception ex) { onError.Invoke(ex); }
    }

    @Override
    public void run()
    {
        if (isDisposed)
        {
            this.interrupt();
            return; //I don't think this is needed if the thread interrupt is called.
        }

        try { socket = new Socket(address, port); }
        catch (Exception ex)
        {
            onError.Invoke(ex);
            return;
        }

        onConnect.Invoke(null);

        ObjectInputStream inputStream;
        try { inputStream = new ObjectInputStream(socket.getInputStream()); }
        catch (Exception ex)
        {
            onError.Invoke(ex);
            Close();
            return;
        }

        while (!isDisposed && !socket.isClosed())
        {
            try
            {
                Object message = inputStream.readObject();
                onMessage.Invoke(message);
            }
            catch (Exception ex) { onError.Invoke(ex); }
        }

        try { socket.close(); }
        catch (Exception ex) { onError.Invoke(ex); }
    }

    public void SendMessage(Object message)
    {
        if (isDisposed || socket == null || socket.isClosed())
            return;

        try
        {
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.writeObject(message);
            outputStream.flush();
        }
        catch (Exception ex) { onError.Invoke(ex); }
    }
}
