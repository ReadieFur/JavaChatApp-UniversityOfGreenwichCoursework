package readiefur.helpers.sockets;

import java.net.Socket;
import java.util.UUID;
public class ServerClientHost extends ASocket
{
    //used for debugging thread names.
    private UUID uuid;

    public ServerClientHost(Socket socket, UUID uuid)
    {
        super(socket);
        this.uuid = uuid;
        start();
    }

    @Override
    public void run()
    {
        //Try to set the thread name to the class name, not required but useful for debugging.
        try { setName(getClass().getSimpleName() + "_" + uuid); }
        catch (Exception e) {}

        super.run();
    }
}
