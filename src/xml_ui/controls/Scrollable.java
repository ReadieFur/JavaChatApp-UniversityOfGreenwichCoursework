package xml_ui.controls;

import java.util.List;

import javax.swing.JScrollPane;

import org.w3c.dom.Node;

import xml_ui.attributes.ChildBuilderAttribute;
import xml_ui.attributes.CreateComponentAttribute;
import xml_ui.attributes.SetterAttribute;
import xml_ui.exceptions.InvalidXMLException;
import xml_ui.factory.UIBuilderFactory;

/**
 * Converts an XML {@code Scrollable} component into a {@link javax.swing.JScrollPane} component.
 */
public class Scrollable
{
    private Scrollable(){}

    @CreateComponentAttribute
    public static JScrollPane Create()
    {
        JScrollPane scrollPane = new JScrollPane();

        //Default scroll policy.
        SetHorizontalScroll(scrollPane, "Never");
        SetVerticalScroll(scrollPane, "AsNeeded");

        return scrollPane;
    }

    @SetterAttribute("HorizontalScroll")
    public static void SetHorizontalScroll(JScrollPane scrollPane, String scroll)
    {
        if (scroll.equals("Always"))
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        else if (scroll.equals("Never"))
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        else if (scroll.equals("AsNeeded"))
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    @SetterAttribute("VerticalScroll")
    public static void SetVerticalScroll(JScrollPane scrollPane, String scroll)
    {
        if (scroll.equals("Always"))
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        else if (scroll.equals("Never"))
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        else if (scroll.equals("AsNeeded"))
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    }

    @ChildBuilderAttribute
    public static void AddChild(UIBuilderFactory builder, JScrollPane scrollPane, List<Node> children) throws InvalidXMLException
    {
        Boolean addedChild = false;
        for (Node child : children)
        {
            //A window can only have one child.
            if (addedChild)
                throw new InvalidXMLException("A '" + Scrollable.class.getSimpleName() + "' can only have one child.");

            //If the child is a resource dictionary, skip it.
            if (child.getNodeName().equals(Window.class.getSimpleName() + ".Resources"))
                continue;

            //Add the child to the window.
            scrollPane.setViewportView(builder.ParseXMLNode(child));
            addedChild = true;
        }
    }
}
