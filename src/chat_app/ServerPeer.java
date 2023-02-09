package chat_app;

import java.util.UUID;

import chat_app.net_data.EPeerStatus;

/**
 * Allows for setting of properties that are read-only in the base class.
 */
public class ServerPeer extends Peer
{
    /**
     * Used by the server to create a new peer.
     */
    public ServerPeer(UUID uuid, String ipAddress, String username)
    {
        this.uuid = uuid.toString();
        this.ipAddress = ipAddress;
        this.nickname = username;
    }

    public void SetStatus(EPeerStatus status)
    {
        this.status = status;
    }

    public void SetNickname(String nickname)
    {
        this.nickname = nickname;
    }
}
