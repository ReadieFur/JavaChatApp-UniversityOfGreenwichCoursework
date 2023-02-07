package chat_app.net_data;

import java.io.Serializable;

import chat_app.Peer;

public class PeersPayload implements Serializable
{
    public Peer[] peers = new Peer[0];
}
