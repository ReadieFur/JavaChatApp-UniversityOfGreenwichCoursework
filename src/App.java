import java.util.Scanner;
import java.util.regex.Pattern;

import readiefur.helpers.sockets.Client;
import readiefur.helpers.sockets.ServerManager;

public class App
{
    public static void main(String[] args)
    {
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

        //Development only.
        RunTests(initialServerAddress, port);
    }

    private static void RunTests(String serverAddress, int port)
    {
        //#region Testing
        ServerManager serverManager = new ServerManager(port);
        serverManager.onConnect.Add(guid -> System.out.println("[SERVER] '" + guid + "' connected."));
        serverManager.onMessage.Add(kvp -> System.out.println("[SERVER] Message from '" + kvp.GetKey() + "': " + kvp.GetValue()));
        serverManager.onClose.Add(guid -> System.out.println("[SERVER] '" + guid + "' disconnected."));
        serverManager.onError.Add(kvp -> System.out.println("[SERVER] Error at '" + kvp.GetKey() + "': " + kvp.GetValue().getMessage()));
        serverManager.start();

        Client client1 = new Client(serverAddress, port);
        client1.onConnect.Add(nul -> System.out.println("[CLIENT1] Connected to server."));
        client1.onMessage.Add(message -> System.out.println("[CLIENT1] Message received from server: " + message));
        client1.onClose.Add(nul -> System.out.println("[CLIENT1] Disconnected from server."));
        client1.onError.Add(ex -> System.out.println("[CLIENT1] Error: " + ex.getMessage()));
        client1.start();

        Client client2 = new Client(serverAddress, port);
        client2.onConnect.Add(nul -> System.out.println("[CLIENT2] Connected to server."));
        client2.onMessage.Add(message -> System.out.println("[CLIENT2] Message received from server: " + message));
        client2.onClose.Add(nul -> System.out.println("[CLIENT2] Disconnected from server."));
        client2.onError.Add(ex -> System.out.println("[CLIENT2] Error: " + ex.getMessage()));
        client2.start();

        //Give the server and client a moment to connect.
        try { Thread.sleep(500); }
        catch (InterruptedException e) {}

        serverManager.BroadcastMessage("Hello from the server!");
        serverManager.SendMessage(serverManager.GetClients().get(0), "Private message to client1.");
        client1.SendMessage("Hello from client1!");
        // client2.SendMessage("Hello from client2!");

        //Thread count should be start+4.
        client1.Dispose();
        serverManager.Dispose();
        //To test closure, I will not manually close the client2 socket, this should be done automatically when the server is disposed.
        // client2.Dispose();
        //Thread count should be start+0 (program should be able to naturally exit).

        // //For now, wait indefinitely.
        // while (true)
        // {
        //     try { Thread.sleep(100); }
        //     catch (InterruptedException e) {}
        // }

        //I realized accidentally that java doesn't exit when the main thread ends if other threads are running?
        // System.exit(0);
        //#endregion
    }
}
