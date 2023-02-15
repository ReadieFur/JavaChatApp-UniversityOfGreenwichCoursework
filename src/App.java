import java.io.IOException;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import readiefur.console.ELogLevel;
import readiefur.console.Logger;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import ui.ConfigurationUI;
import ui.EMessageBoxButtons;
import ui.MessageBox;

public class App
{
    public static void main(String[] args)
    {
        //#region Initialize the console log manager.
        // Logger.ConfigureConsole();
        Logger.logLevel = ELogLevel.TRACE;
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
                Logger.Critical("Failed to load configuration UI: " + e.getMessage());
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
            Logger.Critical("Invalid IP address: " + initialServerAddress);
            MessageBox.ShowDialog(
                "Chat App | Error",
                "Invalid IP address",
                "The IP address you have entered is invalid.",
                EMessageBoxButtons.OK);
            System.exit(1);
        }

        if (port < 0 || port > 65535)
        {
            Logger.Critical("Invalid port: " + port);
            MessageBox.ShowDialog(
                "Chat App | Error",
                "Invalid port",
                "The port you have entered is invalid.",
                EMessageBoxButtons.OK);
            System.exit(1);
        }

        if (username == null || username.isBlank())
        {
            Logger.Critical("Invalid username: " + username);
            MessageBox.ShowDialog(
                "Chat App | Error",
                "Invalid username",
                "The username you have entered is invalid.",
                EMessageBoxButtons.OK);
            System.exit(1);
        }
        //#endregion

        //#region Begin the chat manager.
        ChatManager chatManager = new ChatManager(initialServerAddress, port, username);
        chatManager.Begin();
        //#endregion

        //#region Create the chat UI.
        //TODO: Create the chat ui.
        //#endregion
    }
}
