package readiefur.helpers.sockets;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import readiefur.helpers.Event;
import readiefur.helpers.IDisposable;

public class Client extends Thread implements IDisposable
{
    private final Object lock = new Object();
    private Boolean isDisposed = false;
    private String address;
    private int port;
    private Socket socket = null;
    private ObjectInputStream inputStream = null;
    private ObjectOutputStream outputStream = null;

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
        //Prevent race conditions.
        synchronized (lock)
        {
            /*This can be called twice if the Dispose method is called and the exiting thread then tries to call this method.
             *If that happens, we can safely ignore it.*/
            if (socket == null)
                return;

            //End the thread (if it's still running).
            try
            {
                if (this.isAlive())
                    this.interrupt();
            }
            catch (Exception ex) { onError.Invoke(ex); }

            //Close the streams.
            try { inputStream.close(); }
            catch (Exception ex) { onError.Invoke(ex); }
            inputStream = null;

            try { outputStream.close(); }
            catch (Exception ex) { onError.Invoke(ex); }
            outputStream = null;

            //Close the socket.
            try { socket.close(); }
            catch (Exception ex) { onError.Invoke(ex); }
            socket = null;

            onClose.Invoke(null);
        }
    }

    public void Dispose()
    {
        if (isDisposed)
            return;
        isDisposed = true;

        Close();
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

        try
        {
            if (inputStream == null)
                inputStream = new ObjectInputStream(socket.getInputStream());
        }
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
            catch (EOFException ex)
            {
                //Occurs when the SERVER disconnects (as opposed to the client closing the connection).
                break;
            }
            catch (Exception ex) { onError.Invoke(ex); }
        }

        //When the connection is closed, we want to reset the state rather than dispose as we may want to reuse this client.
        Close();
    }

    public void SendMessage(Object message)
    {
        if (isDisposed || socket == null || socket.isClosed())
            return;

        try
        {
            if (outputStream == null)
                outputStream = new ObjectOutputStream(socket.getOutputStream());

            outputStream.writeObject(message);
        }
        catch (Exception ex) { onError.Invoke(ex); }
    }
}
