package readiefur.xml_ui.controls;

import java.awt.Color;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.w3c.dom.Node;

import readiefur.misc.Event;
import readiefur.misc.ManualResetEvent;
import readiefur.xml_ui.attributes.ChildBuilderAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import readiefur.xml_ui.factory.UIBuilderFactory;
import readiefur.xml_ui.interfaces.IRootComponent;

public class Window extends JFrame implements IRootComponent
{
    private Boolean addedChild = false;
    private ManualResetEvent dialogueResetEvent = new ManualResetEvent(false);

    public final Event<WindowEvent> onWindowClosed = new Event<>();
    public final Event<WindowEvent> onWindowClosing = new Event<>();
    public final Event<WindowEvent> onWindowOpened = new Event<>();
    public final Event<WindowEvent> onWindowIconified = new Event<>();
    public final Event<WindowEvent> onWindowDeiconified = new Event<>();
    public final Event<WindowEvent> onWindowActivated = new Event<>();
    public final Event<WindowEvent> onWindowDeactivated = new Event<>();

    public Window()
    {
        super();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        //https://docs.oracle.com/javase/7/docs/api/java/awt/event/WindowListener.html
        addWindowListener(new WindowListener()
        {
            @Override
            public void windowClosing(WindowEvent e) { OnWindowClosing(e); }

            @Override
            public void windowClosed(WindowEvent e) { OnWindowClosed(e); }

            @Override
            public void windowOpened(WindowEvent e) { OnWindowOpened(e); }

            @Override
            public void windowIconified(WindowEvent e) { OnWindowIconified(e); }

            @Override
            public void windowDeiconified(WindowEvent e) { OnWindowDeiconified(e); }

            @Override
            public void windowActivated(WindowEvent e) { OnWindowActivated(e); }

            @Override
            public void windowDeactivated(WindowEvent e) { OnWindowDeactivated(e); }
        });
    }

    @SetterAttribute("Title")
    public void SetTitle(String title)
    {
        setTitle(title);
    }

    @SetterAttribute("Width")
    public void SetWidth(String width)
    {
        setSize(Integer.parseInt(width), getHeight());
    }

    @SetterAttribute("Height")
    public void SetHeight(String height)
    {
        setSize(getWidth(), Integer.parseInt(height));
    }

    @SetterAttribute("MinWidth")
    public void SetMinWidth(String minWidth)
    {
        setMinimumSize(new java.awt.Dimension(Integer.parseInt(minWidth), getMinimumSize().height));
    }

    @SetterAttribute("MinHeight")
    public void SetMinHeight(String minHeight)
    {
        setMinimumSize(new java.awt.Dimension(getMinimumSize().width, Integer.parseInt(minHeight)));
    }

    @SetterAttribute("MaxWidth")
    public void SetMaxWidth(String maxWidth)
    {
        setMaximumSize(new java.awt.Dimension(Integer.parseInt(maxWidth), getMaximumSize().height));
    }

    @SetterAttribute("MaxHeight")
    public void SetMaxHeight(String maxHeight)
    {
        setMaximumSize(new java.awt.Dimension(getMaximumSize().width, Integer.parseInt(maxHeight)));
    }

    @SetterAttribute("Resizable")
    public void SetResizable(String resizable)
    {
        setResizable(Boolean.parseBoolean(resizable));
    }

    @SetterAttribute("Background")
    public void SetBackground(String colour)
    {
        getContentPane().setBackground(Color.decode(colour));
    }

    @ChildBuilderAttribute
    public void AddChild(UIBuilderFactory builder, List<Node> children) throws InvalidXMLException
    {
        for (Node child : children)
        {
            //A window can only have one child.
            if (addedChild)
                throw new InvalidXMLException("A '" + Window.class.getSimpleName() + "' can only have one child.");

            //If the child is a resource dictionary, skip it.
            if (child.getNodeName().equals(Window.class.getSimpleName() + ".Resources"))
                continue;

            //Add the child to the window.
            add(builder.ParseXMLNode(child));
            addedChild = true;
        }
    }

    public void RemoveChild()
    {
        removeAll();
        addedChild = false;
    }

    /**
     * Shows the window and does not wait for it to be closed.
     */
    public void Show()
    {
        setVisible(true);
    }

    /**
     * Shows the window and waits for it to be closed.
     */
    public void ShowDialog()
    {
        setVisible(true);
        dialogueResetEvent.WaitOne();
    }

    protected void OnWindowClosing(WindowEvent e) { onWindowClosing.Invoke(e); }

    protected void OnWindowClosed(WindowEvent e)
    {
        dialogueResetEvent.Set();
        onWindowClosing.Invoke(e);
    }

    protected void OnWindowOpened(WindowEvent e) { onWindowOpened.Invoke(e); }

    protected void OnWindowIconified(WindowEvent e) { onWindowIconified.Invoke(e); }

    protected void OnWindowDeiconified(WindowEvent e) { onWindowDeiconified.Invoke(e); }

    protected void OnWindowActivated(WindowEvent e) { onWindowActivated.Invoke(e); }

    protected void OnWindowDeactivated(WindowEvent e) { onWindowDeactivated.Invoke(e); }
}
