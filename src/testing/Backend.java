package testing;

import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.Assert;

import chat_app.backend.ChatManager;
import chat_app.backend.Peer;
import readiefur.misc.ManualResetEvent;
import readiefur.misc.Pair;
import readiefur.sockets.ServerManager;

public class Backend
{
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 8080;
    public static final String SERVER_USERNAME = "Server";
    public static final String CLIENT_USERNAME = "Client";
    public static final int SHORT_TIMEOUT = 500;
    public static final int LONG_TIMEOUT = 5000;

    private void InstanceAs(ChatManager chatManager, boolean host)
    {
        ManualResetEvent connected = new ManualResetEvent(false);
        Consumer<Peer> onPeerConnected = peer ->
        {
            if (peer.GetUUID().equals(ServerManager.SERVER_UUID))
                connected.Set();
        };

        chatManager.onPeerConnected.Add(onPeerConnected);
        chatManager.Begin();
        try { connected.WaitOne(LONG_TIMEOUT); }
        catch (TimeoutException ex) { Assert.fail("Instance did not connect to server in time."); }
        if (chatManager.IsHost() != host)
            Assert.fail("Instance did not start up as " + (host ? " host" : " client") + ".");

        chatManager.onPeerConnected.Remove(onPeerConnected);
    }

    @Test
    public void SingleClientTest()
    {
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        InstanceAs(server, true);
        InstanceAs(client, false);

        client.Dispose();
        server.Dispose();
    }

    @Test
    public void HostMigrationTest()
    {
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        InstanceAs(server, true);
        InstanceAs(client1, false);
        InstanceAs(client2, false);

        Consumer<Pair<ManualResetEvent, Peer>> serverResetEvent = pair ->
        {
            if (pair.item2.GetUUID().equals(ServerManager.SERVER_UUID))
                pair.item1.Set();
        };

        ManualResetEvent client1Disconnected = new ManualResetEvent(false);
        ManualResetEvent client2Disconnected = new ManualResetEvent(false);
        client1.onPeerDisconnected.Add(peer -> serverResetEvent.accept(new Pair<>(client1Disconnected, peer)));
        client2.onPeerDisconnected.Add(peer -> serverResetEvent.accept(new Pair<>(client2Disconnected, peer)));

        ManualResetEvent client1Reconnected = new ManualResetEvent(false);
        ManualResetEvent client2Reconnected = new ManualResetEvent(false);
        client1.onPeerConnected.Add(peer -> serverResetEvent.accept(new Pair<>(client1Reconnected, peer)));
        client2.onPeerConnected.Add(peer -> serverResetEvent.accept(new Pair<>(client2Reconnected, peer)));

        server.Dispose();
        try
        {
            client1Disconnected.WaitOne(LONG_TIMEOUT);
            client2Disconnected.WaitOne(LONG_TIMEOUT);
        }
        catch (TimeoutException ex)
        {
            Assert.fail("One of the clients did not disconnect from the server in time.");
        }

        try
        {
            client1Reconnected.WaitOne(LONG_TIMEOUT);
            client2Reconnected.WaitOne(LONG_TIMEOUT);
        }
        catch (TimeoutException ex)
        {
            Assert.fail("One of the clients did not reconnect to the new server in time.");
        }

        client1.Dispose();
        client2.Dispose();
    }
}
