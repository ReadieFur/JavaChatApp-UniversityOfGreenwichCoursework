package chat_app.backend.net_data;

import java.io.Serializable;
import java.util.UUID;

/**
 * A payload containing a message to send.
 */
public class MessagePayload implements Serializable
{
    /**
     * An ID used to identify the message.
     */
    private String messageID;
    /**
     * The sender's UUID (resolved by the server).
     */
    private String sender;
    /**
     * The recipient's UUID or {@code readiefur.sockets.ServerManager.INVALID_UUID} for broadcast.
     */
    private String recipient;
    /**
     * The message to send.
     */
    private String message;

    public MessagePayload(UUID recipient, String message)
    {
        this.messageID = UUID.randomUUID().toString();
        this.sender = null;
        this.recipient = recipient.toString();
        this.message = message;
    }

    public UUID GetMessageID()
    {
        return UUID.fromString(this.messageID);
    }

    //TODO: Hide this for server use only.
    public void SetSender(UUID sender)
    {
        this.sender = sender.toString();
    }

    public UUID GetSender()
    {
        return UUID.fromString(this.sender);
    }

    public UUID GetRecipient()
    {
        return UUID.fromString(this.recipient);
    }

    public String GetMessage()
    {
        return this.message;
    }
}
