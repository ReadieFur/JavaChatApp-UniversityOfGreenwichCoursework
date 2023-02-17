package chat_app.backend.net_data;

import java.io.Serializable;

/**
 * An generic message object that is used to communicate between peers.
 */
public class NetMessage<TPayload extends Serializable> implements Serializable
{
    /**
     * The message type.
     * @see EType
     */
    public EType type = EType.UNKNOWN;
    /**
     * The message payload that corresponds to the message type.
     */
    public TPayload payload = null;

    // /**
    //  * Creates a new {@link NetMessage} with the specified type and payload.
    //  * @param type The message type.
    //  * @param payload The message payload.
    //  */
    // public NetMessage(EType type, TPayload payload)
    // {
    //     this.type = type;
    //     this.payload = payload;
    // }

    // /**
    //  * Default constructor for (de)serialization.
    //  * @deprecated Do not use this constructor.
    //  */
    // @Deprecated
    // public NetMessage() {}
}
