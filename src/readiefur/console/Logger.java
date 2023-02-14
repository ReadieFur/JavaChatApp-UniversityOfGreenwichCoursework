package readiefur.console;

import readiefur.helpers.KeyValuePair;

public class Logger
{
    private Logger(){}

    public static int logLevel = ELogLevel.INFO;

    public static void ConfigureConsole()
    {
        ConsoleWrapper.Instantiate();
        ConsoleWrapper.outPreprocessor = str -> new KeyValuePair<Boolean, String>(true, ConsoleColour.WHITE + "[INFO] " + str + ConsoleColour.RESET);
        ConsoleWrapper.errPreprocessor = str -> new KeyValuePair<Boolean, String>(true, ConsoleColour.RED + "[ERROR] " + str + ConsoleColour.RESET);
    }

    public static void Trace(String message)
    {
        if (logLevel <= ELogLevel.TRACE)
            ConsoleWrapper.GetStdOut().println(ConsoleColour.GREEN + "[TRACE] " + message + ConsoleColour.RESET);
    }

    public static void Debug(String message)
    {
        if (logLevel <= ELogLevel.DEBUG)
            ConsoleWrapper.GetStdOut().println(ConsoleColour.CYAN + "[DEBUG] " + message + ConsoleColour.RESET);
    }

    public static void Info(String message)
    {
        if (logLevel <= ELogLevel.INFO)
            ConsoleWrapper.GetStdOut().println(ConsoleColour.WHITE + "[INFO] " + message + ConsoleColour.RESET);
    }

    public static void Warn(String message)
    {
        if (logLevel <= ELogLevel.WARN)
            ConsoleWrapper.GetStdOut().println(ConsoleColour.YELLOW + "[WARN] " + message + ConsoleColour.RESET);
    }

    public static void Error(String message)
    {
        if (logLevel <= ELogLevel.ERROR)
            ConsoleWrapper.GetStdErr().println(ConsoleColour.RED + "[ERROR] " + message + ConsoleColour.RESET);
    }

    public static void Critical(String message)
    {
        if (logLevel <= ELogLevel.CRITICAL)
            ConsoleWrapper.GetStdErr().println(ConsoleColour.MAGENTA + "[FATAL] " + message + ConsoleColour.RESET);
    }
}
