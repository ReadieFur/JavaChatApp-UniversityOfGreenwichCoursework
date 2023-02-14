package xml_ui.controls;

import java.awt.Color;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import xml_ui.ManualResetEvent;
import xml_ui.XMLRootComponent;
import xml_ui.attributes.ChildBuilderAttribute;
import xml_ui.attributes.CreateComponentAttribute;
import xml_ui.attributes.SetterAttribute;
import xml_ui.exceptions.InvalidXMLException;
import xml_ui.factory.UIBuilderFactory;

/**
 * Converts an XML {@code Window} component into a {@link javax.swing.JFrame} component.
 */
public class Window extends XMLRootComponent<JFrame>
{
    //#region XML Helpers (static)
    @CreateComponentAttribute
    public static JFrame Create()
    {
        return new JFrame();
    }

    @SetterAttribute("Title")
    public static void SetTitle(JFrame frame, String title)
    {
        frame.setTitle(title);
    }

    @SetterAttribute("Width")
    public static void SetWidth(JFrame frame, String width)
    {
        frame.setSize(Integer.parseInt(width), frame.getHeight());
    }

    @SetterAttribute("Height")
    public static void SetHeight(JFrame frame, String height)
    {
        frame.setSize(frame.getWidth(), Integer.parseInt(height));
    }

    @SetterAttribute("MinWidth")
    public static void SetMinWidth(JFrame frame, String minWidth)
    {
        frame.setMinimumSize(new java.awt.Dimension(Integer.parseInt(minWidth), frame.getMinimumSize().height));
    }

    @SetterAttribute("MinHeight")
    public static void SetMinHeight(JFrame frame, String minHeight)
    {
        frame.setMinimumSize(new java.awt.Dimension(frame.getMinimumSize().width, Integer.parseInt(minHeight)));
    }

    @SetterAttribute("MaxWidth")
    public static void SetMaxWidth(JFrame frame, String maxWidth)
    {
        frame.setMaximumSize(new java.awt.Dimension(Integer.parseInt(maxWidth), frame.getMaximumSize().height));
    }

    @SetterAttribute("MaxHeight")
    public static void SetMaxHeight(JFrame frame, String maxHeight)
    {
        frame.setMaximumSize(new java.awt.Dimension(frame.getMaximumSize().width, Integer.parseInt(maxHeight)));
    }

    @SetterAttribute("Resizable")
    public static void SetResizable(JFrame frame, String resizable)
    {
        frame.setResizable(Boolean.parseBoolean(resizable));
    }

    @SetterAttribute("Background")
    public static void SetBackground(JFrame frame, String colour)
    {
        frame.getContentPane().setBackground(Color.decode(colour));
    }

    @ChildBuilderAttribute
    public static void AddChild(UIBuilderFactory builder, JFrame frame, List<Node> children) throws InvalidXMLException
    {
        Boolean addedChild = false;
        for (Node child : children)
        {
            //A window can only have one child.
            if (addedChild)
                throw new InvalidXMLException("A '" + Window.class.getSimpleName() + "' can only have one child.");

            //If the child is a resource dictionary, skip it.
            if (child.getNodeName().equals(Window.class.getSimpleName() + ".Resources"))
                continue;

            //Add the child to the window.
            frame.add(builder.ParseXMLNode(child));
            addedChild = true;
        }
    }
    //#endregion

    //#region Instance methods
    private ManualResetEvent dialogueResetEvent = new ManualResetEvent(false);

    protected Window() throws IOException, ParserConfigurationException, SAXException, InvalidXMLException, IllegalArgumentException, IllegalAccessException
    {
        super();

        rootComponent.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        //https://docs.oracle.com/javase/7/docs/api/java/awt/event/WindowListener.html
        rootComponent.addWindowListener(new WindowListener()
        {
            @Override
            public void windowClosed(WindowEvent e)
            {
                dialogueResetEvent.Set();
            }

            //Other methods required for the WindowListener interface.
            @Override
            public void windowClosing(WindowEvent e) {}

            @Override
            public void windowOpened(WindowEvent e) {}

            @Override
            public void windowIconified(WindowEvent e) {}

            @Override
            public void windowDeiconified(WindowEvent e) {}

            @Override
            public void windowActivated(WindowEvent e) {}

            @Override
            public void windowDeactivated(WindowEvent e) {}
        });
    }

    /**
     * Shows the window and does not wait for it to be closed.
     */
    public void Show()
    {
        rootComponent.setVisible(true);
    }

    /**
     * Shows the window and waits for it to be closed.
     */
    public void ShowDialog()
    {
        rootComponent.setVisible(true);
        dialogueResetEvent.WaitOne();
    }
    //#endregion
}
