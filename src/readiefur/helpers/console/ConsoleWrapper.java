package readiefur.helpers.console;

import java.io.PrintStream;
import java.util.Scanner;
import java.util.function.Function;

import readiefur.helpers.KeyValuePair;

public class ConsoleWrapper
{
    private static final Object lock = new Object();
    private static boolean hasInstanciated = false;

    private static final PrintStream stdOut = System.out;
    private static final PrintStream stdErr = System.err;
    private static final Scanner stdIn = new Scanner(System.in);

    public static Function<String, KeyValuePair<Boolean, String>> outPreprocessor = str -> new KeyValuePair<>(true, str);
    public static Function<String, KeyValuePair<Boolean, String>> errPreprocessor = str -> new KeyValuePair<>(true, str);

    public static void Instantiate()
    {
        synchronized (lock)
        {
            if (hasInstanciated)
                return;
            hasInstanciated = true;

            //Redirect the stdOut and stdErr streams to the console.
            System.setOut(new PrintStream(System.err)
            {
                @Override
                public void println(String message)
                {
                    KeyValuePair<Boolean, String> processedMessage = outPreprocessor.apply(message);
                        if (processedMessage.GetKey())
                            stdOut.println(processedMessage.GetValue());
                }
            });
            System.setErr(new PrintStream(System.err)
            {
                @Override
                public void println(String message)
                {
                    KeyValuePair<Boolean, String> processedMessage = errPreprocessor.apply(message);
                    if (processedMessage.GetKey())
                        stdErr.println(processedMessage.GetValue());
                }
            });
        }
    }

    public static void SyncWrite(String message)
    {
        synchronized (lock)
        {
            stdOut.print(message);
        }
    }

    public static void SyncWriteLine(String message)
    {
        synchronized (lock)
        {
            stdOut.println(message);
        }
    }

    public static String ReadLine()
    {
        synchronized (lock)
        {
            // ClearInputBuffer();
            return stdIn.nextLine();
        }
    }

    public static String ReadLine(String prompt)
    {
        synchronized (lock)
        {
            // ClearInputBuffer();
            System.out.print(prompt);
            return stdIn.nextLine();
        }
    }

    public static String Read()
    {
        synchronized (lock)
        {
            // ClearInputBuffer();
            return stdIn.next();
        }
    }

    public static String Read(String prompt)
    {
        synchronized (lock)
        {
            // ClearInputBuffer();
            System.out.print(prompt);
            return stdIn.next();
        }
    }

    private static void ClearInputBuffer()
    {
        // while (stdIn.hasNext())
        //     stdIn.next();
    }

    private ConsoleWrapper() {}
}
