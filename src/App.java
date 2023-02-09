import java.util.Scanner;
import java.util.regex.Pattern;

import chat_app.ChatManager;
import readiefur.helpers.console.ConsoleWrapper;

public class App
{
    public static void main(String[] args)
    {
        //Initialize the console manager.
        ConsoleWrapper.Instantiate();

        //#region Parse command line arguments
        String initialServerAddress = ""; //Value required to satisfy the compiler.
        if (args.length > 0)
        {
            initialServerAddress = args[0];
        }
        else
        {
            System.out.println("Please specify the initial server address (must be in IP form not domain name):");
            Scanner userInput = new Scanner(System.in);
            try { initialServerAddress = userInput.nextLine(); }
            catch (Exception e)
            {
                System.out.println("Invalid IP address.");
                System.exit(1);
            }
            finally { userInput.close(); }
        }
        //Using a simple regex to check if the IP is valid, it is not completely foolproof.
        if (!Pattern.compile("^(([0-9].{1,3}){3}.[0-9]{1,3})$")
            .matcher(initialServerAddress)
            .matches())
        {
            System.out.println("Invalid IP address.");
            System.exit(1);
        }

        int port = 0;
        if (args.length > 1)
        {
            port = Integer.parseInt(args[1]);
        }
        else
        {
            System.out.println("Please specify the port:");
            Scanner userInput = new Scanner(System.in);
            try
            {
                port = Integer.parseInt(userInput.nextLine());
            }
            catch (NumberFormatException e)
            {
                System.out.println("Invalid port.");
                System.exit(1);
            }
            finally
            {
                userInput.close();
            }
        }
        if (port < 0 || port > 65535)
        {
            System.out.println("Invalid port.");
            System.exit(1);
        }
        //#endregion

        ChatManager.Begin(initialServerAddress, port);
    }
}
