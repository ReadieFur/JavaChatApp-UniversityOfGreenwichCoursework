package readiefur.misc;

public class Pair<TKey, TValue>
{
    public TKey item1;
    public TValue item2;

    public Pair(TKey key, TValue value)
    {
        this.item1 = key;
        this.item2 = value;
    }
}
