package chat_app;

import java.io.Serializable;
import java.util.UUID;

public class Peer implements Serializable
{
    private String uuid; //UUID is not serializable so we must store it as an alternate type (i.e. String).
    private String ipAddress;
    private Boolean isReady = false; //I didn't want this value to be serialized but the transient attribute wasn't working.
    public String nickname = null;

    public Peer(UUID uuid, String ipAddress)
    {
        this.uuid = uuid.toString();
        this.ipAddress = ipAddress;
    }

    //Used for the client handshake process.
    public Peer(String desiredUsername)
    {
        this.uuid = null;
        this.ipAddress = null;
        this.nickname = desiredUsername;
    }

    public UUID GetUUID()
    {
        return UUID.fromString(uuid);
    }

    public String GetIPAddress()
    {
        return ipAddress;
    }

    public void SetIsReady()
    {
        isReady = true;
    }

    public Boolean GetIsReady()
    {
        return isReady;
    }
}
