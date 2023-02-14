package readiefur.xml_ui.exceptions;

public class InvalidXMLException extends Exception
{
    public InvalidXMLException(String message)
    {
        super(message);
    }

    public InvalidXMLException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public InvalidXMLException(Throwable cause)
    {
        super(cause);
    }

    public InvalidXMLException()
    {
        super();
    }
}
