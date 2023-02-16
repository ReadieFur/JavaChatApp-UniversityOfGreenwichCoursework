package ui;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import chat_app.Peer;
import chat_app.net_data.MessagePayload;
import readiefur.console.Logger;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLUI;
import readiefur.xml_ui.attributes.BindingAttribute;
import readiefur.xml_ui.attributes.EventCallbackAttribute;
import readiefur.xml_ui.controls.Window;
import readiefur.xml_ui.exceptions.InvalidXMLException;

public class ChatUI extends XMLUI<Window>
{
    @BindingAttribute(DefaultValue = "#FFFFFF") private Observable<String> backgroundColourPrimary;
    @BindingAttribute(DefaultValue = "#DADADA") private Observable<String> backgroundColourSecondary;
    @BindingAttribute(DefaultValue = "#BDBDBD") private Observable<String> backgroundColourTertiary;
    @BindingAttribute(DefaultValue = "#000000") private Observable<String> foregroundColour;

    private final ChatManager chatManager;

    public ChatUI(ChatManager chatManager)
        throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
    {
        super();

        this.chatManager = chatManager;

        this.chatManager.onPeerConnected.Add(this::ChatManager_OnPeerConnected);
        this.chatManager.onPeerDisconnected.Add(this::ChatManager_OnPeerDisconnected);
        this.chatManager.onMessageReceived.Add(this::ChatManager_OnMessageReceived);
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
        Logger.Info("Message received from: " + chatManager.GetPeers().get(message.GetSender()).GetUsername() + " - " + message.GetMessage());
    }

    @EventCallbackAttribute
    private void SendButton_OnClick(Object[] args)
    {
    }
}
