package chat_app.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;

import javax.swing.plaf.InsetsUIResource;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import chat_app.Peer;
import chat_app.net_data.MessagePayload;
import readiefur.sockets.ServerManager;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLUI;
import readiefur.xml_ui.attributes.BindingAttribute;
import readiefur.xml_ui.attributes.EventCallbackAttribute;
import readiefur.xml_ui.attributes.NamedComponentAttribute;
import readiefur.xml_ui.controls.Grid;
import readiefur.xml_ui.controls.Label;
import readiefur.xml_ui.controls.StackPanel;
import readiefur.xml_ui.controls.TextBlock;
import readiefur.xml_ui.controls.TextBox;
import readiefur.xml_ui.controls.Window;
import readiefur.xml_ui.exceptions.InvalidXMLException;

public class ChatUI extends XMLUI<Window>
{
    //#region UI fields
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_PRIMARY) private Observable<String> backgroundColourPrimary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_SECONDARY) private Observable<String> backgroundColourSecondary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_TERTIARY) private Observable<String> backgroundColourTertiary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_FOREGROUND) private Observable<String> foregroundColour;

    @NamedComponentAttribute private StackPanel clientList;
    @NamedComponentAttribute private StackPanel chatBox;
    @NamedComponentAttribute private TextBox inputBox;
    //#endregion

    private final ChatManager chatManager;

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
        CreateClientEntry(peer);
    }

    private void ChatManager_OnPeerDisconnected(Peer peer)
    {
    }

    private void ChatManager_OnMessageReceived(MessagePayload message)
    {
        CreateChatEntry(chatManager.GetPeers().get(message.GetSender()).GetUsername(), message.GetMessage());
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

        TextBlock chatEntry = CreateChatEntry(chatManager.GetPeers().get(chatManager.GetID()).GetUsername(), message);
        chatEntry.setForeground(Color.decode(backgroundColourSecondary.Get()));

        //TODO: Message contexts (i.e. private messages).
        Boolean success = chatManager.SendMessageSync(ServerManager.INVALID_UUID, message);

        if (success)
        {
            //If the message sent successfully, change the colour back to the default.
            //TODO: Move the message to the bottom of the chat box as this is where it will be for the rest of the clients.
            chatEntry.setForeground(Color.decode(foregroundColour.Get()));
        }
        else
        {
            chatEntry.setForeground(Color.decode("#FF0000"));
        }
    }
    //#endregion

    //#region Builders
    private Grid CreateClientEntry(Peer peer)
    {
        //TODO: Get XML pages working and expose more generic setter methods on the Control classes.
        Grid container = new Grid();
        container.setOpaque(true);
        container.setBackground(Color.decode(backgroundColourTertiary.Get()));
        backgroundColourTertiary.AddListener(newValue -> container.setBackground(Color.decode(newValue)));

        GridBagConstraints containerConstraints = new GridBagConstraints();
        containerConstraints.insets = new InsetsUIResource(4, 4, 2, 4);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.weightx = 1;
        labelConstraints.weighty = 1;
        labelConstraints.fill = GridBagConstraints.VERTICAL;
        labelConstraints.insets = new InsetsUIResource(4, 4, 4, 4);

        Label usernameLabel = new Label();
        usernameLabel.setText(peer.GetUsername());
        usernameLabel.setForeground(Color.decode(foregroundColour.Get()));
        foregroundColour.AddListener(newValue -> usernameLabel.setForeground(Color.decode(newValue)));
        labelConstraints.anchor = GridBagConstraints.WEST;
        container.add(usernameLabel, labelConstraints);

        Label statusLabel = new Label();
        statusLabel.setText(peer.GetIPAddress());
        statusLabel.setForeground(Color.decode(foregroundColour.Get()));
        foregroundColour.AddListener(newValue -> statusLabel.setForeground(Color.decode(newValue)));
        labelConstraints.anchor = GridBagConstraints.EAST;
        container.add(statusLabel, labelConstraints);

        //Hide the status label by default.
        statusLabel.setVisible(false);

        clientList.AddChild(container, containerConstraints);

        return container;
    }

    private TextBlock CreateChatEntry(String sender, String message)
    {
        TextBlock textBlock = new TextBlock();
        textBlock.SetContent("[" + sender + "]: " + message);
        textBlock.setEditable(false);
        textBlock.setLineWrap(true);
        textBlock.setBackground(Color.decode(backgroundColourTertiary.Get()));
        backgroundColourTertiary.AddListener(newValue -> textBlock.setBackground(Color.decode(newValue)));
        textBlock.setForeground(Color.decode(foregroundColour.Get()));
        foregroundColour.AddListener(newValue -> textBlock.setForeground(Color.decode(newValue)));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new InsetsUIResource(0, 0, 2, 0);

        chatBox.AddChild(textBlock, constraints);

        return textBlock;
    }
    //#endregion
}
