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

    private static String GetStackStringInternal()
    {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stackTrace[4];
        String callerClass = caller.getClassName();
        String callerMethod = caller.getMethodName();
        int callerLine = caller.getLineNumber();

        return callerClass + "::" + callerMethod + ":" + callerLine;
    }

    private static void Log(String message, int _logLevel)
    {
        if (_logLevel < logLevel)
            return;

        String prefix;
        switch (_logLevel)
        {
            case ELogLevel.TRACE:
                prefix = ConsoleColour.GREEN + "[TRACE";
                break;
            case ELogLevel.DEBUG:
                prefix = ConsoleColour.CYAN + "[DEBUG";
                break;
            case ELogLevel.INFO:
                prefix = ConsoleColour.WHITE + "[INFO";
                break;
            case ELogLevel.WARN:
                prefix = ConsoleColour.YELLOW + "[WARN";
                break;
            case ELogLevel.ERROR:
                prefix = ConsoleColour.RED + "[ERROR";
                break;
            case ELogLevel.CRITICAL:
                prefix = ConsoleColour.MAGENTA + "[FATAL";
                break;
            default:
                prefix = ConsoleColour.WHITE + "[INFO";
                break;
        }
        if (logLevel == ELogLevel.TRACE)
            prefix += " | " + GetStackStringInternal();
        prefix += "] ";

        String completeMessage = prefix + message + ConsoleColour.RESET;

        if (_logLevel < ELogLevel.WARN)
            ConsoleWrapper.GetStdOut().println(completeMessage);
        else
            ConsoleWrapper.GetStdErr().println(completeMessage);
    }

    public static void Trace(String message)
    {
        Log(message, ELogLevel.TRACE);
    }

    public static void Debug(String message)
    {
        Log(message, ELogLevel.DEBUG);
    }

    public static void Info(String message)
    {
        Log(message, ELogLevel.INFO);
    }

    public static void Warn(String message)
    {
        Log(message, ELogLevel.WARN);
    }

    public static void Error(String message)
    {
        Log(message, ELogLevel.ERROR);
    }

    public static void Critical(String message)
    {
        Log(message, ELogLevel.CRITICAL);
    }
}
