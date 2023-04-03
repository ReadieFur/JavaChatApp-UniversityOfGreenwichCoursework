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
        //Create an event to be triggered when the peer connects.
        ManualResetEvent connected = new ManualResetEvent(false);
        Consumer<Peer> onPeerConnected = peer ->
        {
            //If the peer that connected was the server then we will trigger the reset event.
            if (peer.GetUUID().equals(ServerManager.SERVER_UUID))
                connected.Set();
        };
        chatManager.onPeerConnected.Add(onPeerConnected);

        chatManager.Begin();

        //Wait for the peer to connect using the previously created reset event.
        try { connected.WaitOne(LONG_TIMEOUT); }
        catch (TimeoutException ex) { Assert.fail("Instance did not connect to server in time."); }
        if (chatManager.IsHost() != host)
            Assert.fail("Instance did not start up as " + (host ? " host" : " client") + ".");

        //Remove the event (cleanup).
        chatManager.onPeerConnected.Remove(onPeerConnected);
    }

    @Test
    public void SingleClientTest()
    {
        //Create two instances of the ChatManager class.
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        //Configure each chat manager, one to be a host and the other as a client.
        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client, false);

        //Dispose of the instances (cleanup).
        client.Dispose();
        server.Dispose();
    }

    @Test
    public void HostMigrationTest()
    {
        //Create three instances of the ChatManager class.
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        //Configure each chat manager, one to be a host and the others as clients.
        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);
        InstanceChatManagerAs(client2, false);

        //Create an event callback to be used later by the clients.
        Consumer<Pair<ManualResetEvent, Peer>> serverResetEvent = pair ->
        {
            //If the (dis)connect event subject was the server then we will trigger the reset event.
            if (pair.item2.GetUUID().equals(ServerManager.SERVER_UUID))
                pair.item1.Set();
        };

        //Configure two reset events to be triggered when the client disconnects from the server.
        ManualResetEvent client1Disconnected = new ManualResetEvent(false);
        ManualResetEvent client2Disconnected = new ManualResetEvent(false);
        client1.onPeerDisconnected.Add(peer -> serverResetEvent.accept(new Pair<>(client1Disconnected, peer)));
        client2.onPeerDisconnected.Add(peer -> serverResetEvent.accept(new Pair<>(client2Disconnected, peer)));

        //Configure two reset events to be triggered when the client reconnects to the server.
        ManualResetEvent client1Reconnected = new ManualResetEvent(false);
        ManualResetEvent client2Reconnected = new ManualResetEvent(false);
        client1.onPeerConnected.Add(peer -> serverResetEvent.accept(new Pair<>(client1Reconnected, peer)));
        client2.onPeerConnected.Add(peer -> serverResetEvent.accept(new Pair<>(client2Reconnected, peer)));

        //Dispose of the server instance, causing the clients to disconnect.
        server.Dispose();
        try
        {
            //Wait for the clients to trigger their disconnect events.
            client1Disconnected.WaitOne(LONG_TIMEOUT);
            client2Disconnected.WaitOne(LONG_TIMEOUT);
        }
        catch (TimeoutException ex)
        {
            //If the clients didn't disconnect in time then we will fail the test.
            Assert.fail("One of the clients did not disconnect from the server in time.");
        }

        try
        {
            //One of the clients should become the new host at this point so we will wait for both clients to reconnect.
            client1Reconnected.WaitOne(LONG_TIMEOUT);
            client2Reconnected.WaitOne(LONG_TIMEOUT);
        }
        catch (TimeoutException ex)
        {
            //If the clients fail to reconnect to one another in time then we will fail the test.
            Assert.fail("One of the clients did not reconnect to the new server in time.");
        }

        //Dispose of the other peers (cleanup).
        client1.Dispose();
        client2.Dispose();
    }

    @Test
    public void BroadcastMessageTest()
    {
        //Create three instances of the ChatManager class.
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        //Configure each chat manager, one to be a host and the others as clients.
        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);
        InstanceChatManagerAs(client2, false);

        final String message = "This is a broadcast message!";

        //Configure an event callback to be used by the server and client2.
        Consumer<Pair<ManualResetEvent, MessagePayload>> messageEvent = pair ->
        {
            /*If all of the following are true, then we can determine that the message was sent to all peers:
            * The sender is client1.
            * The message is a broadcast message.
            * The message is the same as our test message above.
            */
            if (pair.item2.GetSender().equals(client1.GetID())
                && pair.item2.GetRecipient().equals(ServerManager.INVALID_UUID)
                && pair.item2.GetMessage().equals(message))
                pair.item1.Set();
        };
        ManualResetEvent serverReceived = new ManualResetEvent(false);
        ManualResetEvent client2Received = new ManualResetEvent(false);
        server.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(serverReceived, payload)));
        client2.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(client2Received, payload)));

        //Have client 1 send a broadcast message.
        client1.SendMessage(ServerManager.INVALID_UUID, message);

        try
        {
            //Wait for the server peer and client2 peer to receive the message.
            serverReceived.WaitOne(LONG_TIMEOUT);
            client2Received.WaitOne(LONG_TIMEOUT);
        }
        catch (TimeoutException ex)
        {
            //If the peers fail to receive the message in time then we will fail the test.
            Assert.fail("One of the peers did not receive the broadcast message in time.");
        }

        //Dispose of the instances (cleanup).
        client2.Dispose();
        client1.Dispose();
        server.Dispose();
    }

    @Test
    public void PrivateMessageTest()
    {
        //Create three instances of the ChatManager class.
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);
        ChatManager client2 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        //Configure each chat manager, one to be a host and the others as clients.
        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);
        InstanceChatManagerAs(client2, false);

        final String message = "This is a private message!";

        //Configure an event callback to be used by the server and client2.
        Consumer<Pair<ManualResetEvent, MessagePayload>> messageEvent = pair ->
        {
            /*If all of the following are true, then we can determine that the message was sent to the intended recipient:
            * The sender is client1.
            * The recipient is client2.
            * The message is the same as our test message above.
            */
            if (pair.item2.GetSender().equals(client1.GetID())
                && pair.item2.GetRecipient().equals(client2.GetID())
                && pair.item2.GetMessage().equals(message))
                pair.item1.Set();
        };
        ManualResetEvent serverReceived = new ManualResetEvent(false);
        ManualResetEvent client2Received = new ManualResetEvent(false);
        server.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(serverReceived, payload)));
        client2.onMessageReceived.Add(payload -> messageEvent.accept(new Pair<>(client2Received, payload)));

        //Have client 1 send a private message to client 2.
        client1.SendMessage(client2.GetID(), message);

        //Wait for the client2 peer to receive the message. If it didn't receive it in time then we will fail the test.
        try { client2Received.WaitOne(LONG_TIMEOUT); }
        catch (TimeoutException ex) { Assert.fail("The client did not receive the private message in time."); }

        //Make sure that the server didn't receive the message. If it did then we will fail the test.
        if (serverReceived.IsSet())
            Assert.fail("The server received a private message that was not intended for it.");

        //Dispose of the instances (cleanup).
        client2.Dispose();
        client1.Dispose();
        server.Dispose();
    }

    @Test
    public void TimeoutTest()
    {
        //Create two instances of the ChatManager class.
        ChatManager server = new ChatManager(ADDRESS, PORT, SERVER_USERNAME);
        ChatManager client1 = new ChatManager(ADDRESS, PORT, CLIENT_USERNAME);

        //Configure each chat manager, one to be a host and the others as a client.
        InstanceChatManagerAs(server, true);
        InstanceChatManagerAs(client1, false);

        //Configure a reset event to be used to determine if the client1 peer disconnected.
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

        //Dispose of the instances (cleanup).
        client1.Dispose();
        server.Dispose();
    }
}
