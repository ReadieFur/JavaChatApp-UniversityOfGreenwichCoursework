package chat_app.net_data;

import java.io.Serializable;

public class NetMessage<TPayload extends Serializable> implements Serializable
{
    public EType type = EType.UNKNOWN;
    public TPayload payload = null;
}
