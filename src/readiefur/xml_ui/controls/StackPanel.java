package readiefur.xml_ui.controls;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.w3c.dom.Node;

import readiefur.xml_ui.attributes.ChildBuilderAttribute;
import readiefur.xml_ui.attributes.CreateComponentAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import readiefur.xml_ui.factory.UIBuilderFactory;

/**
 * Converts an XML {@code StackPanel} component into a {@link javax.swing.JPanel} component configured to to stack it's children.
 */
public class StackPanel
{
    private static final String ORIENTATION = "Orientation";
    private static final String ORIENTATION_LEFT_TO_RIGHT = "LeftToRight";
    private static final String ORIENTATION_TOP_TO_BOTTOM = "TopToBottom";
    private static final String ORIENTATION_RIGHT_TO_LEFT = "RightToLeft";
    private static final String ORIENTATION_BOTTOM_TO_TOP = "BottomToTop";
    private static final String FILLER_COMPONENT = "fillerComponent";

    private StackPanel(){}

    @CreateComponentAttribute
    public static JPanel Create()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setOpaque(false);

        //After some short researching, I found that you can store custom properties on a component which will help this method out A LOT.
        //The reason I thought of doing this is because C# has a similar feature for its WPF framework.

        return panel;
    }

    @SetterAttribute(ORIENTATION)
    public static JPanel SetOrientation(JPanel panel, String orientation) throws InvalidXMLException
    {
        if (orientation.equals(ORIENTATION_LEFT_TO_RIGHT)
            || orientation.equals(ORIENTATION_TOP_TO_BOTTOM)
            || orientation.equals(ORIENTATION_RIGHT_TO_LEFT)
            || orientation.equals(ORIENTATION_BOTTOM_TO_TOP))
            panel.putClientProperty(ORIENTATION, orientation);
        else
            throw new InvalidXMLException("Invalid orientation: " + orientation);

        return panel;
    }

    @SetterAttribute("Background")
    public static void SetBackground(JPanel panel, String colour)
    {
        panel.setOpaque(true);
        panel.setBackground(Color.decode(colour));
    }

    @ChildBuilderAttribute
    public static void AddChildren(UIBuilderFactory builder, JPanel panel, List<Node> children) throws InvalidXMLException
    {
        String orientation = GetOrientation(panel);

        for (int i = 0; i < children.size(); i++)
        {
            final Node child = children.get(i);

            builder.ReplaceResourceReferences(child);

            //Build constraints for the child.
            GridBagConstraints constraints = GetConstraintsForOrientation(orientation, i);
            Grid.SetMarginFromNode(constraints, child);

            panel.add(builder.ParseXMLNode(child), constraints);
        }

        //We only need to call the ComputeFiller at this stage as calling it for any of the other methods would be redundant.
        ComputeFiller(panel);

        //Panel validation is not required at this stage as the panel will not be visible yet abd so will be validated when shown.
    }

    /**
     * Adds a child to the panel and refreshes it.
     */
    public static void AddChild(JPanel panel, Component child)
    {
        //Add the child to the panel.
        panel.add(child, GetConstraintsForOrientation(GetOrientation(panel), GetChildCount(panel)));

        //Update the filler component.
        ComputeFiller(panel);

        //This method should automatically refresh the panel.
        panel.validate();
    }

    private static GridBagConstraints GetConstraintsForOrientation(String orientation, int index)
    {
        GridBagConstraints constraints = new GridBagConstraints();

        /*If we are using an "inverted" alignment, then we need to increment the index by 1.
         *This step just helps with the padding step later on.
         */
        if (orientation.equals(ORIENTATION_RIGHT_TO_LEFT) || orientation.equals(ORIENTATION_BOTTOM_TO_TOP))
            index++;

        if (orientation.equals(ORIENTATION_TOP_TO_BOTTOM) || orientation.equals(ORIENTATION_BOTTOM_TO_TOP))
        {
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.gridx = 0;
            constraints.gridy = index;
        }
        else if (orientation.equals(ORIENTATION_LEFT_TO_RIGHT) || orientation.equals(ORIENTATION_RIGHT_TO_LEFT))
        {
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.weighty = 1.0;
            constraints.gridx = index;
            constraints.gridy = 0;
        }
        return constraints;
    }

    /**
     * Removes a child from the panel and refreshes it.
     */
    public static void RemoveChild(JPanel panel, Component child)
    {
        panel.remove(child);
        ComputeFiller(panel);
        panel.validate();
    }

    /**
     * Gets the number of direct children on this panel, excluding the filler component.
     */
    public static int GetChildCount(JPanel panel)
    {
        Object fillerComponent = panel.getClientProperty(FILLER_COMPONENT);

        int childCount = 0;
        for (Component child : panel.getComponents())
            if (child != fillerComponent)
                childCount++;

        return childCount;
    }

    /**
     * Adds or updates a filler component to the panel to pad any extra space.
     * <br></br>
     * NOTE: This method does not re-render the panel.
     */
    public static void ComputeFiller(JPanel panel)
    {
        Object _fillerComponent = panel.getClientProperty(FILLER_COMPONENT);
        if (!(_fillerComponent instanceof Component))
        {
            //Add a basic component which will be used to fill any extra space.
            _fillerComponent = new JLabel();
            panel.putClientProperty(FILLER_COMPONENT, _fillerComponent);
        }
        Component fillerComponent = (Component)_fillerComponent;

        String orientation = GetOrientation(panel);

        int childCount = GetChildCount(panel);

        GridBagConstraints constraints = new GridBagConstraints();
        if (orientation.equals(ORIENTATION_TOP_TO_BOTTOM))
        {
            constraints.gridx = 0;
            constraints.gridy = childCount; //Place after the last child.
            constraints.weighty = 1.0; //Fill any extra space along the y axis.
        }
        else if (orientation.equals(ORIENTATION_LEFT_TO_RIGHT))
        {
            constraints.gridx = childCount; //Place after the last child.
            constraints.gridy = 0;
            constraints.weightx = 1.0; //Fill any extra space along the x axis.
        }
        else if (orientation.equals(ORIENTATION_BOTTOM_TO_TOP))
        {
            constraints.gridx = 0;
            constraints.gridy = 0; //Place before the "first" child.
            constraints.weighty = 1.0; //Fill any extra space along the y axis.
        }
        else if (orientation.equals(ORIENTATION_RIGHT_TO_LEFT))
        {
            constraints.gridx = 0; //Place before the "first" child.
            constraints.gridy = 0;
            constraints.weightx = 1.0; //Fill any extra space along the x axis.
        }

        //Remove the old filler component if it exists.
        panel.remove(fillerComponent);
        panel.add(fillerComponent, constraints);
    }

    public static String GetOrientation(JPanel panel)
    {
        Object orientation = panel.getClientProperty(ORIENTATION);
        if (!(orientation instanceof String))
        {
            //Default to using a vertical layout.
            orientation = ORIENTATION_TOP_TO_BOTTOM;
            panel.putClientProperty(ORIENTATION, orientation);
        }
        return (String)orientation;
    }
}
