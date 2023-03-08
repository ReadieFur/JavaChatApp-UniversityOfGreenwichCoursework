//A nice little easter-egg I like to put into my projects source files (typically into web consoles, so this console class seemed fitting):
/* !   /\                   ,'|
   o--'O `.                /  /
    `--.   `-----------._,' ,'
       \                ,--'
        ) )    _,--(    |
       /,^.---'     )/ \\
      ((   \\      ((   \\
       \)   \)      \)  (/
   -What are you doing here?*/

package readiefur.console;

import java.io.PrintStream;
import java.util.Scanner;
import java.util.function.Function;

import readiefur.misc.Pair;

public class ConsoleWrapper
{
    private static final Object lock = new Object();
    private static boolean hasInstantiated = false;

    private static final PrintStream stdOut = System.out;
    private static final PrintStream stdErr = System.err;
    private static final Scanner stdIn = new Scanner(System.in);

    public static Function<String, Pair<Boolean, String>> outPreprocessor = str -> new Pair<>(true, str);
    public static Function<String, Pair<Boolean, String>> errPreprocessor = str -> new Pair<>(true, str);

    public static void Instantiate()
    {
        synchronized (lock)
        {
            if (hasInstantiated)
                return;
            hasInstantiated = true;

            //Redirect the stdOut and stdErr streams to the console.
            System.setOut(new PrintStream(System.err)
            {
                @Override
                public void println(String message)
                {
                    Pair<Boolean, String> processedMessage = outPreprocessor.apply(message);
                        if (processedMessage.item1)
                            stdOut.println(processedMessage.item2);
                }
            });

            System.setErr(new PrintStream(System.err)
            {
                @Override
                public void println(String message)
                {
                    Pair<Boolean, String> processedMessage = errPreprocessor.apply(message);
                    if (processedMessage.item1)
                        stdErr.println(processedMessage.item2);
                }
            });
        }
    }

    public static PrintStream GetStdOut()
    {
        return stdOut;
    }

    public static PrintStream GetStdErr()
    {
        return stdErr;
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

    // private static void ClearInputBuffer()
    // {
    //     while (stdIn.hasNext())
    //         stdIn.next();
    // }

    private ConsoleWrapper() {}
}
