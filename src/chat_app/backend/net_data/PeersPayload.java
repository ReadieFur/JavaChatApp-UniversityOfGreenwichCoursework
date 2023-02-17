package chat_app.backend.net_data;

import java.io.Serializable;

import chat_app.backend.Peer;

/**
 * A payload containing a list of peers.
 */
public class PeersPayload implements Serializable
{
    public Peer[] peers = new Peer[0];
}
