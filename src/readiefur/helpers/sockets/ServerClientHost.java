package readiefur.helpers.sockets;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import readiefur.helpers.Event;
import readiefur.helpers.IDisposable;

public class ServerClientHost extends Thread implements IDisposable
{
    private final Object lock = new Object();
    private Boolean isDisposed = false;
    private Socket socket;
    private Boolean hasBeenConnected = false;
    //Initialized when required (I couldn't seem to figure out why it would hang if I tried to set this in the constructor).
    //I presume it may have been because the stream wasn't ready yet?
    //The reason for reusing these objects is I had read that only one object stream should be instantiated per stream, see: https://stackoverflow.com/questions/2393179/streamcorruptedexception-invalid-type-code-ac
    //I may have now figured out why the getStream hangs, it is because it waits until the first stream is ready, i.e. waits for the first message.
    //This is why an error can occur in the run method if the socket is closed before a message is received.
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

            //While GC will close the streams when this object goes out of scope, closing them manually is always more efficient.
            if (inputStream != null)
            {
                try { inputStream.close(); }
                catch (SocketException ex) { /*Ignore, this is expected when the client disconnects.*/ }
                catch (Exception ex) { onError.Invoke(ex); }
                inputStream = null;
            }

            if (outputStream != null)
            {
                try { outputStream.close(); }
                catch (SocketException ex) { /*Ignore, this is expected when the client disconnects.*/ }
                catch (Exception ex) { onError.Invoke(ex); }
                outputStream = null;
            }

            //If the thread is still running, interrupt it.
            if (this.isAlive())
            {
                try { this.interrupt(); }
                catch (Exception ex) { onError.Invoke(ex); }
            }
        }
    }

    //Called immediately after construction.
    @Override
    public void run()
    {
        try
        {
            if (inputStream == null)
                inputStream = new ObjectInputStream(socket.getInputStream());
        }
        catch (EOFException ex) { /*Can occur when the client disconnects quickly after joining.*/ }
        catch (Exception ex)
        {
            //It is possible that we end up disposing here before receiving a message, so return early.
            if (isDisposed)
                return;

            onError.Invoke(ex);
        }
        hasBeenConnected = true;
        //Connect event is handled externally.

        while (!isDisposed && !socket.isClosed())
        {
            Object data;
            try { data = inputStream.readObject(); }
            catch (SocketException | EOFException | NullPointerException ex)
            {
                //The above exceptions are expected and will be ignored, they can occur for the following reasons:
                //SocketException: Occurs when the client disconnects.
                //EOFException: Occurs when the SERVER disconnects (as opposed to the client closing the connection).
                //NullPointerException: Occurs when the client disconnects.
                break;
            }
            catch (Exception ex)
            {
                //Any other exception is unexpected and should be handled.
                onError.Invoke(ex);
                continue;
            }

            onMessage.Invoke(data);
        }

        //When a connection ends, we DO want to dispose of it as a socket cannot be reused.
        Dispose();
    }

    public Socket GetSocket()
    {
        return socket;
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
        catch (SocketException | NullPointerException ex)
        {
            //The above exceptions are expected and will be ignored, they can occur for the following reasons:
            //SocketException: Occurs when the client disconnects.
            //NullPointerException: Occurs when the client disconnects.
        }
        catch (Exception ex)
        {
            onError.Invoke(ex);
        }
    }
}
