package chat_app;

import readiefur.helpers.Event;
import readiefur.helpers.IDisposable;
import readiefur.helpers.console.ConsoleWrapper;

//Singleton class.
public class ConsoleInputThread extends Thread implements IDisposable
{
    private static final Object lock = new Object();
    private static ConsoleInputThread instance = null;

    public static ConsoleInputThread GetInstance()
    {
        synchronized (lock)
        {
            if (instance == null)
                instance = new ConsoleInputThread();
        }
        return instance;
    }

    private boolean isDisposed = false;
    public final Event<String> onInput = new Event<>();

    private ConsoleInputThread()
    {
        start();
    }

    @Override
    public void run()
    {
        //Try to set the thread name to the class name, not required but useful for debugging.
        try { setName(getClass().getSimpleName()); }
        catch (Exception e) {}

        while (!isDisposed)
        {
            onInput.Invoke(ConsoleWrapper.ReadLine(">"));
        }
    }

    @Override
    public void Dispose()
    {
        if (isDisposed)
            return;
        isDisposed = true;

        //I am having an extremely difficult time wrapping my head around resolving the issue of the thread staying alive until a console read is complete.
        interrupt();
    }
}
