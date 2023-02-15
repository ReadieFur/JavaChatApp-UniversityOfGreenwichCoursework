package chat_app;

import java.util.UUID;

import chat_app.net_data.EPeerStatus;

/**
 * Allows for setting of properties that are read-only in the base class.
 */
//This should be an internal class but Java has no such directive.
public class ServerPeer extends Peer
{
    /**
     * Used by the server to create a new peer.
     */
    public ServerPeer(UUID uuid, String ipAddress, String username, EPeerStatus status)
    {
        this.uuid = uuid.toString();
        this.ipAddress = ipAddress;
        this.nickname = username;
        this.status = status;
    }

    public void SetStatus(EPeerStatus status)
    {
        this.status = status;
    }

    public void SetNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public static Peer ToPeer(ServerPeer serverPeer)
    {
        Peer peer = new Peer();
        peer.uuid = serverPeer.uuid;
        peer.ipAddress = serverPeer.ipAddress;
        peer.status = serverPeer.status;
        peer.nickname = serverPeer.nickname;
        return peer;
    }
}
