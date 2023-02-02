import java.util.Scanner;
import java.util.regex.Pattern;

public class App
{
    private static Boolean canExit = false;
    private static HostInterface task;

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

        if (!Server.FindServer(initialServerAddress, port))
            task = new Server(port);
        else
            task = new Client(initialServerAddress, port);

        task.start();

        while (true)
        {
            // String message = task.GetNextMessage();
            // if (message == "exit")
            //     break;

            //For now wait indefinitely.
            try { Thread.sleep(100); }
            catch (InterruptedException e) {}
        }
    }
}
