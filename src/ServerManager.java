import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server extends HostInterface
{
    //#region Static
    public static boolean FindServer(String serverAddress, int port)
    {
        return FindServer(serverAddress, port, 100, 5);
    }

    public static boolean FindServer(String serverAddress, int port, int interval, int attempts)
    {
        for (int i = 0; i < attempts; i++)
        {
            try
            {
                new Socket(serverAddress, port).close();
                return true;
            }
            catch (IOException ex) {}

            try { Thread.sleep(interval); }
            catch (InterruptedException ex) {}
        }

        return false;
    }
    //#endregion

    //#region Instance
    private int port;

    public Server(int port)
    {
        this.port = port;
    }

    @Override
    public void run()
    {
    }
    //#endregion
}
