import java.io.IOException;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import readiefur.helpers.KeyValuePair;
import readiefur.helpers.console.ConsoleColour;
import readiefur.helpers.console.ConsoleWrapper;
import ui.ConfigurationUI;
import xml_ui.exceptions.InvalidXMLException;

public class App
{
    public static void main(String[] args)
    {
        //#region Initialize the console manager.
        ConsoleWrapper.Instantiate();
        ConsoleWrapper.errPreprocessor = str -> new KeyValuePair<Boolean, String>(true, ConsoleColour.RED + "[ERROR] " + str + ConsoleColour.RESET);
        //#endregion

        //#region Parse command line arguments.
        String initialServerAddress = args.length > 0 ? args[0] : null;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : -1;
        String username = args.length > 2 ? args[2] : null;
        //#endregion

        //#region Get the desired user configuration via the UI (if the parameters are not specified on the command line).
        if (initialServerAddress == null || port == -1 || username == null)
        {
            ConfigurationUI configurationWindow;
            try { configurationWindow = new ConfigurationUI("127.0.0.1", 8080, "Anonymous"); }
            catch (IllegalArgumentException | IllegalAccessException | IOException | ParserConfigurationException | SAXException | InvalidXMLException e)
            {
                System.err.println("Failed to load configuration UI: " + e.getMessage());
                System.exit(1);
                return; //Required to satisfy the compiler.
            }
            configurationWindow.ShowDialog();

            initialServerAddress = configurationWindow.GetServerAddress();
            port = configurationWindow.GetPort();
            username = configurationWindow.GetUsername();
        }
        //#endregion

        //#region Validate the user configuration.
        //Using a simple regex to check if the IP is valid, it is not completely foolproof.
        if (initialServerAddress == null || !Pattern.compile("^(([0-9].{1,3}){3}.[0-9]{1,3})$")
            .matcher(initialServerAddress)
            .matches())
        {
            System.err.println("Invalid IP address.");
            System.exit(1);
        }

        if (port < 0 || port > 65535)
        {
            System.err.println("Invalid port.");
            System.exit(1);
        }

        if (username == null || username.isBlank())
        {
            System.err.println("Invalid username.");
            System.exit(1);
        }
        //#endregion

        //Begin the chat manager.
        ChatManager chatManager = new ChatManager(initialServerAddress, port, username);
        // chatManager.Begin();

        //Create the chat UI.
        //TODO: Create the chat ui.
    }
}
