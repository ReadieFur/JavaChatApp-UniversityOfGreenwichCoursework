package chat_app.net_data;

/**
 * The type of message for a {@link NetMessage}.
 */
public enum EType
{
    /**
     * Used for uninitialized values or unknown requests.
     * <br></br>
     * Payload: {@link EmptyPayload}
     */
    UNKNOWN,
    /**
     * Used for the initial handshake between peers, clients should send a nickname and the server will respond with a UUID and corrected nickname.
     * <br></br>
     * Payload: {@link chat_app.Peer}
     */
    //Possibly include the client ID in the server response?
    HANDSHAKE,
    /**
     * Used to request a pong from a peer.
     * <br></br>
     * Payload: {@link EmptyPayload}
     */
    PING,
    /**
     * Used to respond to a ping request.
     * <br></br>
     * Payload: {@link EmptyPayload}
     */
    PONG,
    /**
     * Used to request a list of peers from the server or indicates that a payload contains a list of peers.
     * <br></br>
     * Payload: {@link EmptyPayload} or {@link PeersPayload}
     */
    PEERS,
    /**
     * Used to request that the message payload should be broadcast to all peers.
     * <br></br>
     * Payload: {@code NOT_YET_IMPLEMENTED}
     */
    BROADCAST,
    /**
     * Used to request that the message payload should be sent to a specific peer.
     * <br></br>
     * Payload: {@code NOT_YET_IMPLEMENTED}
     */
    MESSAGE
}
