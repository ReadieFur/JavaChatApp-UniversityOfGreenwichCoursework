package chat_app.backend.net_data;

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
     * Used to send the status of a peer.
     * <br></br>
     * Payload: {@link PeersPayload}
     */
    PEER,
    /**
     * Used to request a list of peers from the server or indicates that a payload contains a list of peers.
     * <br></br>
     * Payload: {@link EmptyPayload} or {@link PeersPayload}
     */
    PEERS,
    /**
     * Used to request that the message payload should be sent to the specified peer (or broadcast).
     * <br></br>
     * Payload: {@link MessagePayload}
     */
    MESSAGE
}
