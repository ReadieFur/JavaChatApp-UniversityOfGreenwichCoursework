package readiefur.xml_ui.controls;

import java.util.List;

import javax.swing.JScrollPane;

import org.w3c.dom.Node;

import readiefur.xml_ui.attributes.ChildBuilderAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import readiefur.xml_ui.factory.UIBuilderFactory;

public class Scrollable extends JScrollPane
{
    private Boolean addedChild = false;

    public Scrollable()
    {
        super();

        //Default scroll policy.
        SetHorizontalScroll("Never");
        SetVerticalScroll("AsNeeded");
    }

    @SetterAttribute("HorizontalScroll")
    public void SetHorizontalScroll(String scroll)
    {
        if (scroll.equals("Always"))
            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        else if (scroll.equals("Never"))
            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        else if (scroll.equals("AsNeeded"))
            setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    @SetterAttribute("VerticalScroll")
    public void SetVerticalScroll(String scroll)
    {
        if (scroll.equals("Always"))
            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        else if (scroll.equals("Never"))
            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        else if (scroll.equals("AsNeeded"))
            setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    }

    @ChildBuilderAttribute
    public void AddChild(UIBuilderFactory builder, List<Node> children) throws InvalidXMLException
    {
        for (Node child : children)
        {
            //A window can only have one child.
            if (addedChild)
                throw new InvalidXMLException("A '" + Scrollable.class.getSimpleName() + "' can only have one child.");

            //If the child is a resource dictionary, skip it.
            if (child.getNodeName().equals(Window.class.getSimpleName() + ".Resources"))
                continue;

            //Add the child to the window.
            setViewportView(builder.ParseXMLNode(child));
            addedChild = true;
        }
    }

    public void RemoveChild()
    {
        setViewportView(null);
        addedChild = false;
    }
}
