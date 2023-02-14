package xml_ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A class that wraps a value which can be observed.
 */
public class Observable<T>
{
    //Consumer allows for side effects which is what we want for this class.
    private final List<Consumer<T>> listeners = new ArrayList<>();

    private T value;

    public Observable(T value)
    {
        this.value = value;
    }

    public T Get()
    {
        return value;
    }

    public void Set(T value)
    {
        this.value = value;
        for (Consumer<T> listener : listeners)
            listener.accept(value);
    }

    public void AddListener(Consumer<T> listener)
    {
        listeners.add(listener);
    }

    public void RemoveListener(Consumer<T> listener)
    {
        listeners.remove(listener);
    }
}
