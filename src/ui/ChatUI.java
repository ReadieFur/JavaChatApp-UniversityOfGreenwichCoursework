package ui;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLUI;
import readiefur.xml_ui.attributes.BindingAttribute;
import readiefur.xml_ui.attributes.EventCallbackAttribute;
import readiefur.xml_ui.controls.Window;
import readiefur.xml_ui.exceptions.InvalidXMLException;

public class ChatUI extends XMLUI<Window>
{
    @BindingAttribute(DefaultValue = "#FFFFFF")
    private Observable<String> backgroundColourPrimary;

    @BindingAttribute(DefaultValue = "#DADADA")
    private Observable<String> backgroundColourSecondary;

    @BindingAttribute(DefaultValue = "#BDBDBD")
    private Observable<String> backgroundColourTertiary;

    @BindingAttribute(DefaultValue = "#000000")
    private Observable<String> foregroundColour;

    public ChatUI(ChatManager chatManager)
        throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
    {
        super();
    }

    public void Show()
    {
        rootComponent.Show();
    }

    public void ShowDialog()
    {
        rootComponent.ShowDialog();
    }

    @EventCallbackAttribute
    private void SendButton_OnClick(Object[] args)
    {
    }
}
