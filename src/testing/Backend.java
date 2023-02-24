package testing;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.Assert;

import chat_app.backend.ChatManager;
import chat_app.backend.Peer;
import chat_app.backend.PingPong;
import chat_app.backend.net_data.MessagePayload;
import readiefur.console.ELogLevel;
import readiefur.console.Logger;
import readiefur.misc.ManualResetEvent;
import readiefur.misc.Pair;
import readiefur.sockets.Client;
import readiefur.sockets.ServerManager;

public class Backend
{
    public static final String ADDRESS = "127.0.0.1";
    public static final int PORT = 8080;
    public static final String SERVER_USERNAME = "Server";
    public static final String CLIENT_USERNAME = "Client";
    public static final int SHORT_TIMEOUT = 500;
    public static final int LONG_TIMEOUT = 5000;

    public Backend()
    {
        Logger.logLevel = ELogLevel.TRACE;
    }

    public void InstanceChatManagerAs(ChatManager chatManager, boolean host)
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

        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client, false);

        client.Dispose();
        server.Dispose();
    }

    @Test
    public void HostMigrationTest()
    {
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);
        InstanceChatManagerAs(client2, false);

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

    @Test
    public void BroadcastMessageTest()
    {
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);
        InstanceChatManagerAs(client2, false);

        final String message = "This is a broadcast message!";

        Consumer<Pair<ManualResetEvent, MessagePayload>> messageEvent = pair ->
        {
            if (pair.item2.GetSender().equals(client1.GetID())
                && pair.item2.GetRecipient().equals(ServerManager.INVALID_UUID)
                && pair.item2.GetMessage().equals(message))
                pair.item1.Set();
        };
        ManualResetEvent serverReceived = new ManualResetEvent(false);
        ManualResetEvent client2Received = new ManualResetEvent(false);
        server.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(serverReceived, payload)));
        client2.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(client2Received, payload)));

        client1.SendMessage(ServerManager.INVALID_UUID, message);

        try
        {
            serverReceived.WaitOne(LONG_TIMEOUT);
            client2Received.WaitOne(LONG_TIMEOUT);
        }
        catch (TimeoutException ex)
        {
            Assert.fail("One of the clients did not receive the broadcast message in time.");
        }

        client2.Dispose();
        client1.Dispose();
        server.Dispose();
    }

    @Test
    public void PrivateMessageTest()
    {
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);
        InstanceChatManagerAs(client2, false);

        final String message = "This is a private message!";

        ManualResetEvent serverReceived = new ManualResetEvent(false);
        ManualResetEvent client2Received = new ManualResetEvent(false);

        Consumer<Pair<ManualResetEvent, MessagePayload>> messageEvent = pair ->
        {
            if (pair.item2.GetSender().equals(client1.GetID())
                && pair.item2.GetRecipient().equals(client2.GetID())
                && pair.item2.GetMessage().equals(message))
                pair.item1.Set();
        };

        server.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(serverReceived, payload)));
        client2.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(client2Received, payload)));

        client1.SendMessage(client2.GetID(), message);

        try { client2Received.WaitOne(LONG_TIMEOUT); }
        catch (TimeoutException ex) { Assert.fail("The client did not receive the private message in time."); }

        //Make sure that the server didn't receive the message.
        if (serverReceived.IsSet())
            Assert.fail("The server received a private message that was not intended for it.");

        client2.Dispose();
        client1.Dispose();
        server.Dispose();
    }

    @Test
    public void TimeoutTest()
    {
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);

        ManualResetEvent serverOnClient1Disconnected = new ManualResetEvent(false);
        server.onPeerDisconnected.Add(peer ->
        {
            if (peer.GetUUID().equals(client1.GetID()))
                serverOnClient1Disconnected.Set();
        });

        //Use reflection to get the onMessageReceived property on the client1 instance.
        Client clientInstanceProperty = null;
        try
        {
            Field field = client1.getClass().getDeclaredField("client");
            field.setAccessible(true);
            clientInstanceProperty = (Client)field.get(client1);
        }
        catch (NoSuchFieldException | IllegalAccessException ex)
        {
            Assert.fail("Could not get the onMessageReceived property from the client1 instance.");
        }

        //Now we need to get the event list on the onMessage property.
        List<Consumer<Object>> clientMessageEvents = null;
        try
        {
            Field field = clientInstanceProperty.onMessage.getClass().getDeclaredField("event");
            field.setAccessible(true);
            // @SuppressWarnings("unchecked")
            //Create a copy of the list so that concurrent modification exceptions don't occur.
            //This exception WILL occur if we don't create a copy of the list because we are modifying the list while iterating over it.
            clientMessageEvents = new ArrayList<>((List<Consumer<Object>>)field.get(clientInstanceProperty.onMessage));
        }
        catch (NoSuchFieldException | IllegalAccessException ex)
        {
            Assert.fail("Could not get the event list from the onMessage property.");
        }

        //Now that we have the required properties
        //I will remove all callbacks from it so that the client can't PONG the servers PING messages.
        for (Consumer<Object> consumer : clientMessageEvents)
            clientInstanceProperty.onMessage.Remove(consumer);

        //Wait for the server to disconnect the client.
        //The timeout should be set to double that of the PING interval.
        try { serverOnClient1Disconnected.WaitOne(PingPong.PING_PONG_INTERVAL_MS * 2); }
        catch (TimeoutException ex) { Assert.fail("The server did not disconnect the client in time."); }

        client1.Dispose();
        server.Dispose();
    }
}
