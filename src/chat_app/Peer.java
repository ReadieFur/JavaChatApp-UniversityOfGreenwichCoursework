package chat_app;

import java.io.Serializable;
import java.util.UUID;

public class Peer implements Serializable
{
    private UUID uuid;
    private String ipAddress;
    public String name = null;

    public Peer(UUID uuid, String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    public UUID GetUUID()
    {
        return uuid;
    }

    public String GetIPAddress()
    {
        return ipAddress;
    }
}
