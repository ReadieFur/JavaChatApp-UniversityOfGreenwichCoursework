import java.io.IOException;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import chat_app.ChatManager;
import readiefur.console.ELogLevel;
import readiefur.console.Logger;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import ui.ChatUI;
import ui.ConfigurationUI;
import ui.EMessageBoxButtons;
import ui.MessageBox;

public class App
{
    //With how this program has been made, it is possible to run multiple chats using the same process, though that has not been implemented.
    public static void main(String[] args)
        throws IllegalArgumentException, IllegalAccessException, IOException, ParserConfigurationException, SAXException, InvalidXMLException
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
            Logger.Trace("Starting configuration UI...");

            //I wish I could've used the ?? null condition operator that C# has here.
            ConfigurationUI configurationWindow = new ConfigurationUI(
                initialServerAddress != null ? initialServerAddress : "127.0.0.1",
                port != -1 ? port : 8080,
                username != null ? username : "Anonymous");

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
            MessageBox.Show(
                "Chat App | Error",
                "Invalid IP address",
                "The IP address you have entered is invalid.",
                EMessageBoxButtons.OK);
            System.exit(1);
        }

        if (port < 0 || port > 65535)
        {
            Logger.Critical("Invalid port: " + port);
            MessageBox.Show(
                "Chat App | Error",
                "Invalid port",
                "The port you have entered is invalid.",
                EMessageBoxButtons.OK);
            System.exit(1);
        }

        if (username == null || username.isBlank())
        {
            Logger.Critical("Invalid username: " + username);
            MessageBox.Show(
                "Chat App | Error",
                "Invalid username",
                "The username you have entered is invalid.",
                EMessageBoxButtons.OK);
            System.exit(1);
        }
        //#endregion

        //#region Begin the chat manager.
        Logger.Trace("Starting chat manager...");

        ChatManager chatManager = new ChatManager(initialServerAddress, port, username);
        chatManager.Begin();
        //#endregion

        //#region Create the chat UI.
        Logger.Trace("Starting chat UI...");

        /*I will let this method throw exceptions as any that may occur at this level are critical
         *and my console log manager will format the exception appropriately and then print it to the console,
         *at which point the program will exit with an unsuccessful code.*/
        ChatUI chatUI = new ChatUI(chatManager);

        Logger.Debug("Program started.");

        //This method will show the UI and block the current thread until the UI is closed.
        chatUI.ShowDialog();
        //#endregion

        //#region Cleanup.
        Logger.Debug("Exiting...");

        chatManager.Dispose();
        //#endregion
    }
}
