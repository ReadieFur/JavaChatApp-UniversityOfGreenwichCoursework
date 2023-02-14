package chat_app;

import java.io.Serializable;
import java.util.UUID;

import chat_app.net_data.EPeerStatus;
import readiefur.sockets.ServerManager;

public class Peer implements Serializable
{
    protected String uuid = ServerManager.INVALID_UUID.toString(); //UUID is not serializable so we must store it as an alternate type (i.e. String).
    protected String ipAddress = null;
    protected EPeerStatus status = EPeerStatus.UNINITIALIZED;
    protected String nickname;

    protected Peer() {}

    /**
     * Used for the client handshake process.
     */
    public Peer(String desiredUsername)
    {
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

    public EPeerStatus GetStatus()
    {
        return status;
    }
}
