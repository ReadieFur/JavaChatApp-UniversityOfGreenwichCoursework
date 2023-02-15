package readiefur.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Event<T>
{
    private List<Consumer<T>> event = new ArrayList<>();

    public void Add(Consumer<T> event)
    {
        this.event.add(event);
    }

    public void Remove(Consumer<T> event)
    {
        this.event.remove(event);
    }

    public void Invoke(T message)
    {
        for (Consumer<T> e : event)
            e.accept(message);
    }
}
