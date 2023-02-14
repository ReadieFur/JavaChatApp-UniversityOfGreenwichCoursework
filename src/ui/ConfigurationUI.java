package ui;

import java.io.IOException;

import javax.swing.JTextField;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import readiefur.helpers.ManualResetEvent;
import xml_ui.attributes.EventCallbackAttribute;
import xml_ui.attributes.NamedComponentAttribute;
import xml_ui.controls.Window;
import xml_ui.exceptions.InvalidXMLException;

public class ConfigurationUI extends Window
{
    private ManualResetEvent dialogueResetEvent = new ManualResetEvent(false);

    @NamedComponentAttribute
    private JTextField serverAddress;

    @NamedComponentAttribute
    private JTextField port;

    @NamedComponentAttribute
    private JTextField username;

    public ConfigurationUI() throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
    {
        super();

        //Auto-size the window.
        rootComponent.pack();
    }

    public String GetServerAddress()
    {
        final String DEFAULT_VALUE = "127.0.0.1";
        String value = serverAddress.getText();
        if (value == null || value.isEmpty())
            return DEFAULT_VALUE;
        return value;
    }

    public int GetPort()
    {
        final int DEFAULT_VALUE = 8080;
        String value = port.getText();
        if (value == null || value.isEmpty())
            return DEFAULT_VALUE;

        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return DEFAULT_VALUE; }
    }

    public String GetUsername()
    {
        final String DEFAULT_VALUE = "Anonymous";
        String value = username.getText();
        if (value == null || value.isEmpty())
            return DEFAULT_VALUE;
        return value;
    }

    @EventCallbackAttribute
    private void Connect_Click(Object[] args)
    {
    }
}
