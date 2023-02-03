import java.util.Scanner;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import readiefur.helpers.ManualResetEvent;
import readiefur.helpers.sockets.Client;
import readiefur.helpers.sockets.ServerManager;

public class App
{
    private static Boolean isHost = false;
    private static String serverIPAddress;
    private static int port;

    public static void main(String[] args)
    {
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

        serverIPAddress = initialServerAddress;
        App.port = port;

        Begin();
    }

    private static Boolean FindHost(String ipAddress, int port)
    {
        ManualResetEvent resetEvent = new ManualResetEvent(false);

        Client client = new Client(ipAddress, port);
        client.onConnect.Add(nul -> resetEvent.Set());
        client.start();

        try { resetEvent.WaitOne(1000); }
        catch (TimeoutException e) {}

        Boolean hostFound = client.IsConnected();

        client.Dispose();

        return hostFound;
    }

    private static void Begin()
    {
        //Look for a host.
        isHost = !FindHost(serverIPAddress, port);

        //If a host was not found, begin hosting.
        if (isHost)
        {
            //Start the server.
            ServerManager serverManager = new ServerManager(port);
            serverManager.start();
        }
        else
        {
            //Connect to the server.
            Client client = new Client(serverIPAddress, port);
            client.start();
        }
    }
}
