package readiefur.helpers.sockets;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import readiefur.helpers.Event;
import readiefur.helpers.IDisposable;

public class ServerClientHost extends Thread implements IDisposable
{
    private Boolean isDisposed = false;
    private Socket socket;
    //Initialized when required (I couldn't seem to figure out why it would hang if I tried to set this in the constructor).
    //I presume it may have been because the stream wasn't ready yet?
    //The reason for reusing these objects is I had read that only one object stream should be instantiated per stream, see: https://stackoverflow.com/questions/2393179/streamcorruptedexception-invalid-type-code-ac
    private ObjectInputStream inputStream = null;
    private ObjectOutputStream outputStream = null;

    //TODO: Accessability modifiers.
    public Event<Object> onMessage = new Event<>();
    public Event<Void> onClose = new Event<>();
    public Event<Exception> onError = new Event<>();

    public ServerClientHost(Socket socket)
    {
        this.socket = socket;
        start();
    }

    //TODO: Needs fixing.
    public void Dispose()
    {
        if (isDisposed)
            return;
        isDisposed = true;

        //Close the socket.
        try { socket.close(); }
        catch (Exception ex) { onError.Invoke(ex); }
        socket = null;
        onClose.Invoke(null);

        //If the thread is still running, interrupt it.
        try
        {
            if (this.isAlive())
                this.interrupt();
        }
        catch (Exception ex) { onError.Invoke(ex); }
    }

    //Called immediately after construction.
    @Override
    public void run()
    {
        if (isDisposed)
        {
            this.interrupt();
            return; //I don't think this is needed if the thread interrupt is called.
        }

        try
        {
            if (inputStream == null)
                inputStream = new ObjectInputStream(socket.getInputStream());
        }
        catch (Exception ex)
        {
            onError.Invoke(ex);
            return;
        }

        while (!isDisposed && !socket.isClosed())
        {
            Object data;
            try { data = inputStream.readObject(); }
            catch (EOFException ex)
            {
                //Occurs when the CLIENT disconnects (as opposed to the server closing the connection).
                break;
            }
            catch (Exception ex)
            {
                onError.Invoke(ex);
                continue;
            }

            onMessage.Invoke(data);
        }

        Dispose();
    }

    public void SendMessage(Object data)
    {
        if (isDisposed)
            return;

        try
        {
            if (outputStream == null)
                outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.writeObject(data);
        }
        catch (Exception ex)
        {
            onError.Invoke(ex);
        }
    }
}
