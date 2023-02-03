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
    private Boolean hasBeenConnected = false;
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

    public void Dispose()
    {
        //Prevent race conditions.
        synchronized (lock)
        {
            if (isDisposed)
                return;
            isDisposed = true;

            //Close the socket.
            if (socket != null)
            {
                try { socket.close(); }
                catch (Exception ex) { onError.Invoke(ex); }
                socket = null;

                //If the socket was open, we can fire the onClose event.
                if (hasBeenConnected)
                    onClose.Invoke(null);
            }

            //Close the streams.
            if (inputStream != null)
            {
                try { inputStream.close(); }
                catch (Exception ex) { onError.Invoke(ex); }
                inputStream = null;
            }

            //The output stream may not have been initialized if the client never sent a message.
            if (outputStream != null)
            {
                try { outputStream.close(); }
                catch (Exception ex) { onError.Invoke(ex); }
                outputStream = null;
            }

            //End the thread (if it's still running).
            if (this.isAlive())
            {
                try { this.interrupt(); }
                catch (Exception ex) { onError.Invoke(ex); }
            }
        }
    }

    @Override
    public void run()
    {
        try { socket = new Socket(address, port); }
        catch (Exception ex)
        {
            //It is possible that we end up disposing here before receiving a message, so return early.
            if (!isDisposed)
                onError.Invoke(ex);
            return;
        }

        hasBeenConnected = true;
        onConnect.Invoke(null);

        try
        {
            if (inputStream == null)
                inputStream = new ObjectInputStream(socket.getInputStream());
        }
        catch (Exception ex)
        {
            onError.Invoke(ex);
            Dispose();
            return; //Needed in the case of a race condition.
        }

        while (!isDisposed && !socket.isClosed())
        {
            try
            {
                Object message = inputStream.readObject();
                onMessage.Invoke(message);
            }
            catch (Exception ex)
            {
                //EOFException occurs when the SERVER disconnects (as opposed to the client closing the connection).
                //Note how we don't check if the socket is closed here, this is because connection may have unexpectedly ended.
                //We can safely these exceptions.
                if (ex instanceof EOFException || isDisposed || socket == null)
                    break;

                onError.Invoke(ex);
            }
        }

        /*In my previous tests I had this close the connection and thread as opposed to disposing of the this instance.
         *The reason I don't do this anymore is because after a quick look, I found you cannot reuse threads in java.
         *I could work around this by creating another wrapper instance but that can be done later if needs be.
         *I will be leaving the code for closure in the deconstructor though for good practice and if I need to reimplement it again later.*/
        Dispose();
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
