package chat_app.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.io.IOException;
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

    public ChatUI(ChatManager chatManager)
        throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
    {
        super();

        this.chatManager = chatManager;

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

        //If we are the server, show host-only properties, otherwise hide them.
        //Currently not required as the host-only properties get redrawn upon restart/reconnect.
        // if (chatManager.IsHost())
        // {
        //     //TODO: Show server-only properties.
        // }
        // else
        // {
        //     //TODO: Hide server-only properties.
        // }

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

            for (UUID id : chatManager.GetPeers().keySet())
                RemoveClientEntry(id);
        }
        else
        {
            RemoveClientEntry(peerID);
        }
    }

    private void ChatManager_OnMessageReceived(MessagePayload message)
    {
        CreateChatMessage(chatManager.GetPeers().get(message.GetSender()).GetUsername(), message.GetMessage());
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

        //TODO: Message contexts (i.e. private messages).
        Boolean success = chatManager.SendMessageSync(ServerManager.INVALID_UUID, message);

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
            clientList.AddChild(entry, entry.containerConstraints);

            //Refresh the scroller.
            clientListContainer.revalidate();

            clientEntries.put(peerID, entry);
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
            clientListContainer.revalidate();
        }
    }

    private TextBlock CreateSystemMessage(String message)
    {
        TextBlock textBlock = CreateChatEntry("==== " + message + " ====");

        foregroundColourPrimary.RemoveListener(newValue -> textBlock.setForeground(Color.decode(newValue)));

        textBlock.setForeground(Color.decode(foregroundColourSecondary.Get()));
        foregroundColourSecondary.AddListener(newValue -> textBlock.setForeground(Color.decode(newValue)));

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

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new InsetsUIResource(0, 0, 2, 0);

        chatBox.AddChild(textBlock, constraints);

        //It is also important to refresh the scroll pane so that if needed, the scroll bar will be updated. Then also scroll to the bottom.
        chatBoxContainer.revalidate();
        chatBoxContainer.getVerticalScrollBar().setValue(chatBoxContainer.getVerticalScrollBar().getMaximum());

        return textBlock;
    }
    //#endregion
}
