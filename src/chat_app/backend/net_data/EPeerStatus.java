package chat_app.backend.net_data;

public enum EPeerStatus
{
    /**
     * Used to indicate that the peer has not yet been initialized (default).
     */
    UNINITIALIZED,
    /**
     * Used to indicate that a peer is connected and ready to receive messages.
     */
    CONNECTED,
    /**
     * Used to indicate that a peer is disconnected.
     */
    DISCONNECTED
}
