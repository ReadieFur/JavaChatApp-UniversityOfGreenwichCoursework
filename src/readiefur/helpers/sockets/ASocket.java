package readiefur.helpers.sockets;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import readiefur.helpers.Event;
import readiefur.helpers.IDisposable;

//Most of the abstract class contains more virtual members, though it is still abstract and should therefore not be instantiated on it's own.
public abstract class ASocket extends Thread implements IDisposable
{
    protected final Object lock = new Object();
    protected Boolean isDisposed = false;
    protected Socket socket;
    protected Boolean threadHasRun = false;
    protected ObjectInputStream inputStream = null;
    protected ObjectOutputStream outputStream = null;

    public final Event<Void> onConnect = new Event<>();
    public final Event<Object> onMessage = new Event<>();
    public final Event<Void> onClose = new Event<>();
    public final Event<Exception> onError = new Event<>();

    protected ASocket(Socket socket)
    {
        this.socket = socket;
    }

    @Override
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
                if (threadHasRun)
                    onClose.Invoke(null);
            }

            //While GC will close the streams when this object goes out of scope, closing them manually is always more efficient.
            if (inputStream != null)
            {
                try { inputStream.close(); }
                catch (SocketException ex) { /*Ignore, this is expected when the socket is closed from the other end.*/ }
                catch (Exception ex) { onError.Invoke(ex); }
                inputStream = null;
            }

            if (outputStream != null)
            {
                try { outputStream.close(); }
                catch (SocketException ex) { /*See above.*/ }
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

    public Boolean IsConnected()
    {
        return !isDisposed && socket != null && socket.isConnected();
    }

    @Override
    public void run()
    {
        if (isDisposed || threadHasRun)
            return;
        threadHasRun = true;

        //We should never reach this state with a null socket, if we do then something has gone wrong.
        if (socket == null)
            throw new IllegalStateException("Socket is null.");

        try
        {
            //This should not be set yet.
            inputStream = new ObjectInputStream(socket.getInputStream());

            //If we managed to get the input stream then we can fire the onConnect event.
            onConnect.Invoke(null);
        }
        catch (EOFException ex) { /*Can occur when the other end of the socket has already closed.*/ }
        catch (Exception ex) { onError.Invoke(ex); }
        //If either of the above exceptions occur, the following while loop will not be executed and the dispose method will be called thereafter.

        //While the socket is open, read messages from the input stream.
        while (!isDisposed && !socket.isClosed())
        {
            try
            {
                Object message = inputStream.readObject();
                onMessage.Invoke(message);
            }
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
            }
        }

        /*In my previous tests I had this close the connection and thread as opposed to disposing of the this instance.
         *The reason I don't do this anymore is because after a quick look, I found you cannot reuse threads in java.
         *I could work around this by creating another wrapper instance but that can be done later if needs be.
         *I will be leaving the code for closure in the deconstructor though for good practice and if I need to reimplement it again later.*/
        //TL;DR: When a connection ends, we DO want to dispose of it as a socket cannot be reused.
        Dispose();
    }

    public Socket GetSocket()
    {
        return socket;
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
