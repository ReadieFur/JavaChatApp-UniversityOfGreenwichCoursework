package ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.io.IOException;

import javax.swing.plaf.InsetsUIResource;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import chat_app.Peer;
import chat_app.net_data.MessagePayload;
import readiefur.console.Logger;
import readiefur.sockets.ServerManager;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLUI;
import readiefur.xml_ui.attributes.BindingAttribute;
import readiefur.xml_ui.attributes.EventCallbackAttribute;
import readiefur.xml_ui.attributes.NamedComponentAttribute;
import readiefur.xml_ui.controls.StackPanel;
import readiefur.xml_ui.controls.TextBlock;
import readiefur.xml_ui.controls.TextBox;
import readiefur.xml_ui.controls.Window;
import readiefur.xml_ui.exceptions.InvalidXMLException;

public class ChatUI extends XMLUI<Window>
{
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_PRIMARY) private Observable<String> backgroundColourPrimary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_SECONDARY) private Observable<String> backgroundColourSecondary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_TERTIARY) private Observable<String> backgroundColourTertiary;
    @BindingAttribute(DefaultValue = Themes.LIGHT_FOREGROUND) private Observable<String> foregroundColour;

    @NamedComponentAttribute private StackPanel chatBox;
    @NamedComponentAttribute private TextBox inputBox;

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

    public void Show()
    {
        rootComponent.Show();
    }

    public void ShowDialog()
    {
        rootComponent.ShowDialog();
    }

    private void ChatManager_OnPeerConnected(Peer peer)
    {
        Logger.Info("Peer connected: " + peer.GetUsername());
    }

    private void ChatManager_OnPeerDisconnected(Peer peer)
    {
        Logger.Info("Peer disconnected: " + peer.GetUsername());
    }

    private void ChatManager_OnMessageReceived(MessagePayload message)
    {
        CreateChatEntry(chatManager.GetPeers().get(message.GetSender()).GetUsername(), message.GetMessage());
    }

    @EventCallbackAttribute
    private void SendButton_OnClick(Object[] args)
    {
        String message = inputBox.getText();
        inputBox.setText("");

        //If the message has no content, don't send it.
        if (message.isEmpty())
            return;

        //TODO: Message contexts (i.e. private messages).
        Boolean success = chatManager.SendMessageSync(ServerManager.INVALID_UUID, message);

        if (success)
        {
            CreateChatEntry(chatManager.GetPeers().get(chatManager.GetID()).GetUsername(), message);
        }
        else
        {
            Logger.Warn("Failed to send message.");
        }
    }

    private void CreateChatEntry(String sender, String message)
    {
        //TODO: Dynamic status colour.
        //TODO: Get XML pages working and expose more generic setter methods on the Control classes.
        TextBlock textBlock = new TextBlock();
        textBlock.SetContent("[" + sender + "]: " + message);
        textBlock.setEditable(false);
        textBlock.setLineWrap(true);
        textBlock.setBackground(Color.decode(backgroundColourTertiary.Get()));
        backgroundColourTertiary.AddListener(newValue -> textBlock.setBackground(Color.decode(newValue)));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new InsetsUIResource(0, 0, 2, 0);

        chatBox.AddChild(textBlock, constraints);
    }
}
