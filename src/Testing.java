import readiefur.console.ELogLevel;
import readiefur.console.Logger;

public class Testing
{
    public static void main(String[] args)
    {
        Logger.ConfigureConsole();
        Logger.logLevel = ELogLevel.TRACE;
    }
}
