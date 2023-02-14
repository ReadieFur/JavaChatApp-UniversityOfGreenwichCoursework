package ui;

import java.io.IOException;

import javax.swing.JTextField;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import xml_ui.attributes.EventCallbackAttribute;
import xml_ui.attributes.NamedComponentAttribute;
import xml_ui.controls.Window;
import xml_ui.exceptions.InvalidXMLException;

public class ConfigurationUI extends Window
{
    @NamedComponentAttribute
    private JTextField serverAddress;
    @NamedComponentAttribute
    private JTextField port;
    @NamedComponentAttribute
    private JTextField username;

    private String defaultServerAddress;
    private int defaultPort;
    private String defaultUsername;

    public ConfigurationUI(String defaultServerAddress, int defaultPort, String defaultUsername)
        throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
    {
        super();

        //Auto-size the window.
        rootComponent.pack();

        //Set the default values.
        this.defaultServerAddress = defaultServerAddress;
        this.defaultPort = defaultPort;
        this.defaultUsername = defaultUsername;

        //Fill out the default values.
        serverAddress.setText(defaultServerAddress);
        port.setText(Integer.toString(defaultPort));
        username.setText(defaultUsername);
    }

    public String GetServerAddress()
    {
        String value = serverAddress.getText();
        if (value == null || value.isEmpty())
            return defaultServerAddress;
        return value;
    }

    public int GetPort()
    {
        String value = port.getText();
        if (value == null || value.isEmpty())
            return defaultPort;

        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return defaultPort; }
    }

    public String GetUsername()
    {
        String value = username.getText();
        if (value == null || value.isEmpty())
            return defaultUsername;
        return value;
    }

    @EventCallbackAttribute
    private void Connect_Click(Object[] args)
    {
        //Close the window.
        rootComponent.dispose();
    }
}
