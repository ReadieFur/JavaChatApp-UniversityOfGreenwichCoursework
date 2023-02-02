package readiefur.helpers;

public class KeyValuePair<TKey, TValue>
{
    private TKey key;
    private TValue value;

    public KeyValuePair(TKey key, TValue value)
    {
        this.key = key;
        this.value = value;
    }

    public TKey GetKey()
    {
        return key;
    }

    public TValue GetValue()
    {
        return value;
    }
}
