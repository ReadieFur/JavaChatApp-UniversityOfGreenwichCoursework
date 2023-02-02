import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ServerClientHost extends Thread
{
    private Boolean isDisposed = false;
    private Socket socket;

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
        if (isDisposed)
            return;
        isDisposed = true;

        //Close the socket.
        try { socket.close(); }
        catch (Exception ex) { onError.Invoke(ex); }

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
        while (!isDisposed)
        {
            try
            {
                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                while (!isDisposed)
                {
                    Object data = input.readObject();
                    onMessage.Invoke(data);

                    //Check if the socket is closed.
                    if (socket.isClosed())
                    {
                        onClose.Invoke(null);
                        Dispose();
                        return; //Shouldn't be needed as the thread should interrupt itself (to be tested).
                    }
                }
            }
            catch (Exception ex)
            {
                onError.Invoke(ex);
            }
        }
    }

    public void SendMessage(Object data)
    {
        if (isDisposed)
            return;

        try
        {
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
            output.writeObject(data);
        }
        catch (Exception ex)
        {
            onError.Invoke(ex);
        }
    }
}
