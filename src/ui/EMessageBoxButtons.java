package ui;

//Not an enum but I will use it as one (mimic the enum functionality).
public abstract class EMessageBoxButtons
{
    private EMessageBoxButtons() {}

    public static final int NONE = (int)Math.pow(2, 0);
    public static final int YES = (int)Math.pow(2, 1);
    public static final int NO = (int)Math.pow(2, 2);
    public static final int OK = (int)Math.pow(2, 3);
    public static final int CANCEL = (int)Math.pow(2, 4);
}
