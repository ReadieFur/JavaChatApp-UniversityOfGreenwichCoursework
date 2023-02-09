import java.util.regex.Pattern;

import chat_app.ChatManager;
import readiefur.helpers.KeyValuePair;
import readiefur.helpers.console.ConsoleColour;
import readiefur.helpers.console.ConsoleWrapper;

public class App
{
    public static void main(String[] args)
    {
        //Initialize the console manager.
        ConsoleWrapper.Instantiate();

        //Format stdErr.
        ConsoleWrapper.errPreprocessor = str ->
            new KeyValuePair<Boolean, String>(true, ConsoleColour.RED + "[ERROR] " + str + ConsoleColour.RESET);

        //#region Parse command line arguments
        //IP address.
        String initialServerAddress = args.length > 0
            ? args[0]
            : ConsoleWrapper.ReadLine("Please specify the initial server address (must be in IP form not domain name):\n>");
        //Using a simple regex to check if the IP is valid, it is not completely foolproof.
        if (!Pattern.compile("^(([0-9].{1,3}){3}.[0-9]{1,3})$")
            .matcher(initialServerAddress)
            .matches())
        {
            System.err.println("Invalid IP address.");
            System.exit(1);
        }

        //Port.
        int port = 0;
        if (args.length > 1)
        {
            port = Integer.parseInt(args[1]);
        }
        else
        {
            try
            {
                port = Integer.parseInt(ConsoleWrapper.ReadLine("Please specify the server port:\n>"));
            }
            catch (NumberFormatException e)
            {
                System.err.println("Invalid port.");
                System.exit(1);
            }
        }
        if (port < 0 || port > 65535)
        {
            System.err.println("Invalid port.");
            System.exit(1);
        }

        //Username.
        String username = args.length > 2
            ? args[2]
            : ConsoleWrapper.ReadLine("Please specify your username (leave blank to log on as anonymous):\n>");
        if (username.isBlank())
            username = null;

        //#endregion

        ChatManager.Begin(initialServerAddress, port, username);
    }
}
