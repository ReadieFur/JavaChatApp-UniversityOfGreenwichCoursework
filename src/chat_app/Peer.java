package chat_app;

import java.io.Serializable;
import java.util.UUID;

import chat_app.net_data.EPeerStatus;
import readiefur.sockets.ServerManager;

public class Peer implements Serializable
{
    protected String uuid = ServerManager.INVALID_UUID.toString(); //UUID is not serializable so we must store it as an alternate type (i.e. String).
    protected String ipAddress = "";
    protected EPeerStatus status = EPeerStatus.UNINITIALIZED;
    protected String username = "";

    /**
     * This hidden constructor is used for the deserialization.
     */
    protected Peer(){}

    /**
     * This constructor is used for the server handshake process.
     */
    public Peer(String desiredUsername)
    {
        this.username = desiredUsername;
    }

    public UUID GetUUID()
    {
        return UUID.fromString(uuid);
    }

    public String GetIPAddress()
    {
        return ipAddress;
    }

    public EPeerStatus GetStatus()
    {
        return status;
    }

    public String GetUsername()
    {
        return username;
    }
}
