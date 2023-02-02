import java.util.function.Consumer;

public class HostInterface extends Thread
{
    //Create a semaphore to prevent the thread from exiting.
    //Create an event to call back to the host on what to do next (this can possibly replace the semaphore).

    //This is used what would be in most application the message loop.
    private ResetEvent messageResetEvent = new ResetEvent(false);
    public String GetNextMessage()
    {
        messageResetEvent.WaitOne();
        messageResetEvent.Reset();
        return "exit";
    }
}
