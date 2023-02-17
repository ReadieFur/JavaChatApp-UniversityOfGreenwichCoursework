package chat_app.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.plaf.InsetsUIResource;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import readiefur.sockets.ServerManager;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLUI;
import readiefur.xml_ui.attributes.BindingAttribute;
import readiefur.xml_ui.attributes.EventCallbackAttribute;
import readiefur.xml_ui.attributes.NamedComponentAttribute;
import readiefur.xml_ui.controls.Button;
import readiefur.xml_ui.controls.Scrollable;
import readiefur.xml_ui.controls.StackPanel;
import readiefur.xml_ui.controls.TextBlock;
import readiefur.xml_ui.controls.TextBox;
import readiefur.xml_ui.controls.Window;
import readiefur.xml_ui.exceptions.InvalidXMLException;

import chat_app.ChatManager;
import chat_app.Peer;
import chat_app.net_data.MessagePayload;

public class ChatUI extends XMLUI<Window>
{
    //#region UI fields
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_PRIMARY) private Observable<String> backgroundColourPrimary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_SECONDARY) private Observable<String> backgroundColourSecondary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_TERTIARY) private Observable<String> backgroundColourTertiary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_FOREGROUND_PRIMARY) private Observable<String> foregroundColourPrimary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_FOREGROUND_SECONDARY) private Observable<String> foregroundColourSecondary;
    @BindingAttribute(DefaultValue = "false") private Observable<String> connectedToServer;

    @NamedComponentAttribute private Scrollable clientListContainer;
    @NamedComponentAttribute private StackPanel clientList;
    @NamedComponentAttribute private Scrollable chatBoxContainer;
    @NamedComponentAttribute private StackPanel chatBox;
    @NamedComponentAttribute private TextBox inputBox;
    @NamedComponentAttribute private Button sendButton;
    //#endregion

    private final ChatManager chatManager;
    private final ConcurrentHashMap<UUID, ClientEntry> clientEntries = new ConcurrentHashMap<>();
    /*A slight flaw can occur here, if the server restarts, everyone will have a new UUID.
     *This is a problem because if I want to store chats by user ID, the chats won't be accessible after server resets.
     *And if I were to store the chats by username, after a server reset, the clients can "spoof" their name and impersonate the original sender.
     *So for these reasons, I will clear private messages between server resets, however the global message room will remain.*/
    //TODO: A future fix for this would be to have some sort of public/private key messaging to help with verification.
    private final ConcurrentHashMap<UUID, List<TextBlock>> messageGroups = new ConcurrentHashMap<>();
    private UUID activeChat = ServerManager.INVALID_UUID;

    public ChatUI(ChatManager chatManager)
        throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
    {
        super();

        this.chatManager = chatManager;

        //TODO: Add status information to the title bar.

        //Create the broadcast chat entry.
        messageGroups.put(ServerManager.INVALID_UUID, new ArrayList<>());
        ClientEntry broadcastClientEntry = new ClientEntry("Global", "", backgroundColourTertiary, foregroundColourPrimary);
        broadcastClientEntry.onMouseClicked.Add(e -> SetActiveChat(ServerManager.INVALID_UUID));
        clientEntries.put(ServerManager.INVALID_UUID, broadcastClientEntry);
        clientList.AddChild(broadcastClientEntry, broadcastClientEntry.containerConstraints);
        broadcastClientEntry.setBackground(Color.decode(Themes.ACCENT_PRIMARY));
        //No need to call the SetActiveChat as the active chat is already set to the broadcast chat and there will be no messages to add at this point.
        broadcastClientEntry.setEnabled(false);
        connectedToServer.AddListener(newValue -> broadcastClientEntry.setEnabled(Boolean.parseBoolean(newValue)));

        this.chatManager.onPeerConnected.Add(this::ChatManager_OnPeerConnected);
        this.chatManager.onPeerDisconnected.Add(this::ChatManager_OnPeerDisconnected);
        this.chatManager.onMessageReceived.Add(this::ChatManager_OnMessageReceived);

        //Normally in C# I would use the discard operator but Java doesn't have that.
        rootComponent.onWindowClosed.Add(e ->
        {
            //It is a good practice to unsubscribe from the events.
            this.chatManager.onPeerConnected.Remove(this::ChatManager_OnPeerConnected);
            this.chatManager.onPeerDisconnected.Remove(this::ChatManager_OnPeerDisconnected);
            this.chatManager.onMessageReceived.Remove(this::ChatManager_OnMessageReceived);
        });

        inputBox.onKeyPressed.Add(e ->
        {
            //If the enter key was pressed, send the message.
            //The keycode for enter is usually 13, however after debugging I found that it was 10.
            if (e.getKeyCode() == 10)
                SendButton_OnClick(null);
        });

        //Add existing clients to the client list (some will be missed between the time of the ChatManager starting and the UI being created).
        for (Peer peer : chatManager.GetPeers().values())
            ChatManager_OnPeerConnected(peer);
    }

    //#region Window methods
    public void Show()
    {
        rootComponent.Show();
    }

    public void ShowDialog()
    {
        rootComponent.ShowDialog();
    }
    //#endregion

    //#region Chat manager events
    private void ChatManager_OnPeerConnected(Peer peer)
    {
        if (peer.GetUUID().equals(ServerManager.SERVER_UUID))
        {
            if (chatManager.IsHost())
                CreateSystemMessage("Promoted to host.");
            else
                CreateSystemMessage("Connected to server.");

            //Changing this property will trigger the bindings on connectedToServer, which in my case will enable/disable UI elements.
            connectedToServer.Set("true");
        }

        CreateClientEntry(peer);
    }

    private void ChatManager_OnPeerDisconnected(Peer peer)
    {
        UUID peerID = peer.GetUUID();

        if (peerID.equals(ServerManager.SERVER_UUID))
        {
            CreateSystemMessage("Disconnected from server.");
            CreateSystemMessage("Reconnecting...");

            connectedToServer.Set("false");

            //Revert back to the broadcast chat.
            SetActiveChat(ServerManager.INVALID_UUID);

            for (UUID id : chatManager.GetPeers().keySet())
                RemoveClientEntry(id);

            //Clear the private chats, read the comment on the deceleration of messageGroups for more information.
            for (UUID id : messageGroups.keySet())
            {
                //If the ID is the broadcast ID (INVALID_UUID), don't remove it.
                if (id.equals(ServerManager.INVALID_UUID))
                    continue;

                for (TextBlock textBlock : messageGroups.get(id))
                {
                    //Unbind events.
                    backgroundColourTertiary.RemoveListener(newValue -> textBlock.setBackground(Color.decode(newValue)));
                    foregroundColourPrimary.RemoveListener(newValue -> textBlock.setForeground(Color.decode(newValue)));
                }

                messageGroups.remove(id);
            }
        }
        else
        {
            CreateSystemMessage(peer.GetUsername() + " disconnected.");
            RemoveClientEntry(peerID);
        }
    }

    private void ChatManager_OnMessageReceived(MessagePayload message)
    {
        UUID senderID = message.GetSender();

        TextBlock chatEntry = CreateChatMessage(chatManager.GetPeers().get(senderID).GetUsername(), message.GetMessage());

        //If the recipient is "invalid" (the broadcast ID), then set the group ID to the broadcast ID, otherwise set it to the sender ID.
        UUID groupID = message.GetRecipient().equals(ServerManager.INVALID_UUID) ? ServerManager.INVALID_UUID : senderID;
        if (!messageGroups.containsKey(groupID))
            messageGroups.put(groupID, new ArrayList<>());
        messageGroups.get(groupID).add(chatEntry);

        if (activeChat.equals(groupID))
        {
            AddChatEntry(chatEntry);
            RefreshChatBox();
        }
        else
        {
            //TODO: Notify that the chat has unreads.
        }
    }
    //#endregion

    //#region UI events
    @EventCallbackAttribute
    private void SendButton_OnClick(Object[] args)
    {
        String message = inputBox.getText();
        inputBox.setText("");

        //If the message has no content, don't send it.
        if (message.isEmpty())
            return;

        TextBlock chatEntry = CreateChatMessage(chatManager.GetPeers().get(chatManager.GetID()).GetUsername(), message);
        chatEntry.setForeground(Color.decode(foregroundColourSecondary.Get()));

        if (!messageGroups.containsKey(activeChat))
            messageGroups.put(activeChat, new ArrayList<>());
        messageGroups.get(activeChat).add(chatEntry);

        AddChatEntry(chatEntry);
        RefreshChatBox();

        Boolean success = chatManager.SendMessageSync(activeChat, message);

        if (success)
        {
            //If the message sent successfully, change the colour back to the default.
            //TODO: Move the message to the bottom of the chat box as this is where it will be for the rest of the clients.
            chatEntry.setForeground(Color.decode(foregroundColourPrimary.Get()));
        }
        else
        {
            chatEntry.setForeground(Color.decode("#FF0000"));
        }
    }
    //#endregion

    //#region Builders
    private ClientEntry CreateClientEntry(Peer peer)
    {
        synchronized (clientEntries)
        {
            UUID peerID = peer.GetUUID();

            //Prevent duplicate entries.
            if (clientEntries.containsKey(peerID))
                return clientEntries.get(peerID);

            String username = peer.GetUsername() + (peerID.equals(chatManager.GetID()) ? " (You)" : "");

            CreateSystemMessage(username + " connected.");

            ClientEntry entry = new ClientEntry(
                username,
                peer.GetIPAddress(),
                backgroundColourTertiary,
                foregroundColourPrimary);
            entry.ShowHostControls(chatManager.IsHost());
            entry.onMouseClicked.Add(e -> SetActiveChat(peerID));

            clientEntries.put(peerID, entry);
            clientList.AddChild(entry, entry.containerConstraints);

            //Refresh the scroller.
            clientListContainer.revalidate();

            return entry;
        }
    }

    private void RemoveClientEntry(UUID id)
    {
        synchronized (clientEntries)
        {
            if (!clientEntries.containsKey(id))
                return;

            ClientEntry clientEntry = clientEntries.get(id);
            clientEntries.remove(id);

            clientList.RemoveChild(clientEntry);
            clientEntry.Dispose();

            if (activeChat.equals(id))
                SetActiveChat(ServerManager.INVALID_UUID);

            clientListContainer.revalidate();
        }
    }

    private TextBlock CreateSystemMessage(String message)
    {
        TextBlock textBlock = CreateChatEntry("==== " + message + " ====");

        foregroundColourPrimary.RemoveListener(newValue -> textBlock.setForeground(Color.decode(newValue)));

        textBlock.setForeground(Color.decode(foregroundColourSecondary.Get()));
        foregroundColourSecondary.AddListener(newValue -> textBlock.setForeground(Color.decode(newValue)));

        //For now only log these messages into the global chat.
        messageGroups.get(ServerManager.INVALID_UUID).add(textBlock);
        if (activeChat.equals(ServerManager.INVALID_UUID))
            AddChatEntry(textBlock);

        return textBlock;
    }

    private TextBlock CreateChatMessage(String sender, String message)
    {
        return CreateChatEntry("[" + sender + "]: " + message);
    }

    private TextBlock CreateChatEntry(String message)
    {
        TextBlock textBlock = new TextBlock();
        textBlock.SetContent(message);
        textBlock.setEditable(false);
        textBlock.setLineWrap(true);
        textBlock.setBackground(Color.decode(backgroundColourTertiary.Get()));
        backgroundColourTertiary.AddListener(newValue -> textBlock.setBackground(Color.decode(newValue)));
        textBlock.setForeground(Color.decode(foregroundColourPrimary.Get()));
        foregroundColourPrimary.AddListener(newValue -> textBlock.setForeground(Color.decode(newValue)));

        return textBlock;
    }

    private void AddChatEntry(TextBlock entry)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new InsetsUIResource(0, 0, 2, 0);
        chatBox.AddChild(entry, constraints);
    }

    private void RefreshChatBox()
    {
        //It is also important to refresh the scroll pane so that if needed, the scroll bar will be updated. Then also scroll to the bottom.
        chatBoxContainer.revalidate();
        chatBoxContainer.getVerticalScrollBar().setValue(chatBoxContainer.getVerticalScrollBar().getMaximum());
    }

    private void SetActiveChat(UUID id)
    {
        //If the chat is already active or the desired new chat is us, don't do anything.
        if (activeChat.equals(id) || id.equals(chatManager.GetID()))
            return;

        //Update the old chat entry's background colour.
        if (clientEntries.containsKey(activeChat))
            clientEntries.get(activeChat).setBackground(Color.decode(backgroundColourTertiary.Get()));

        activeChat = id;
        chatBox.removeAll();

        //Update the new chat entry's background colour.
        if (clientEntries.containsKey(activeChat))
            clientEntries.get(activeChat).setBackground(Color.decode(Themes.ACCENT_PRIMARY));

        //Add the messages for the selected client.
        if (messageGroups.containsKey(id))
        {
            for (TextBlock entry : messageGroups.get(id))
                AddChatEntry(entry);
        }

        //We need to redraw it in this case as we have removed items.
        chatBox.repaint();
        //Refresh the chat box container, we don't do this during the removal of the entries as it would just be wasting CPU cycles.
        RefreshChatBox();
    }
    //#endregion
}
