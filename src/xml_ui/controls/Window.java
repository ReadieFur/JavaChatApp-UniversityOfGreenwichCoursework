package xml_ui.controls;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import javax.swing.JFrame;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

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
    protected Window() throws IOException, ParserConfigurationException, SAXException, InvalidXMLException, IllegalArgumentException, IllegalAccessException
    {
        super();
    }

    public void Show()
    {
        rootComponent.setVisible(true);
    }
    //#endregion
}
