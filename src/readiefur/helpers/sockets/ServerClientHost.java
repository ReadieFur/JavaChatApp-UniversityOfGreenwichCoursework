package readiefur.helpers.sockets;

import java.net.Socket;
public class ServerClientHost extends ASocket
{
    public ServerClientHost(Socket socket)
    {
        super(socket);
        start();
    }
}
