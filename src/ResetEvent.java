public class ResetEvent
{
    private boolean isSet = false;
    private Object lock = new Object();

    public ResetEvent(boolean initialState)
    {
        if (initialState)
            Set();
    }

    public void Set()
    {
        synchronized (lock)
        {
            isSet = true;
            lock.notifyAll();
        }
    }

    public void Reset()
    {
        synchronized (lock)
        {
            isSet = false;
        }
    }

    public void WaitOne()
    {
        synchronized (lock)
        {
            while (!isSet)
            {
                try
                {
                    lock.wait();
                }
                catch (InterruptedException ex)
                {
                    //Do nothing.
                }
            }
        }
    }
}
