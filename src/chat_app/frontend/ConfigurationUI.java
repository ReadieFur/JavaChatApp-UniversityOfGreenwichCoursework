package chat_app.frontend;

import java.io.IOException;

import javax.swing.JTextField;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLUI;
import readiefur.xml_ui.attributes.BindingAttribute;
import readiefur.xml_ui.attributes.EventCallbackAttribute;
import readiefur.xml_ui.attributes.NamedComponentAttribute;
import readiefur.xml_ui.controls.Window;
import readiefur.xml_ui.exceptions.InvalidXMLException;

public class ConfigurationUI extends XMLUI<Window>
{
    @NamedComponentAttribute
    private JTextField serverAddress;
    @NamedComponentAttribute
    private JTextField port;
    @NamedComponentAttribute
    private JTextField username;

    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_PRIMARY)
    private Observable<String> backgroundColour;
    @BindingAttribute(DefaultValue = Themes.LIGHT_BACKGROUND_SECONDARY)
    private Observable<String> backgroundColourAlt;
    @BindingAttribute(DefaultValue = Themes.LIGHT_FOREGROUND_PRIMARY)
    private Observable<String> foregroundColour;

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

    public void Show()
    {
        rootComponent.Show();
    }

    public void ShowDialog()
    {
        rootComponent.ShowDialog();
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
