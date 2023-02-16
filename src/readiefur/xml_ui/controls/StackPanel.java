package readiefur.xml_ui.controls;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.w3c.dom.Node;

import readiefur.xml_ui.attributes.ChildBuilderAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import readiefur.xml_ui.factory.UIBuilderFactory;

public class StackPanel extends JPanel
{
    private static final String ORIENTATION = "Orientation";
    private static final String ORIENTATION_LEFT_TO_RIGHT = "LeftToRight";
    private static final String ORIENTATION_TOP_TO_BOTTOM = "TopToBottom";
    private static final String ORIENTATION_RIGHT_TO_LEFT = "RightToLeft";
    private static final String ORIENTATION_BOTTOM_TO_TOP = "BottomToTop";
    private static final String FILLER_COMPONENT = "fillerComponent";

    public StackPanel()
    {
        super();

        setLayout(new GridBagLayout());
        setOpaque(false);

        //After some short researching, I found that you can store custom properties on a component which will help this method out A LOT.
        //The reason I thought of doing this is because C# has a similar feature for its WPF framework.
    }

    @SetterAttribute(ORIENTATION)
    public void SetOrientation(String orientation) throws InvalidXMLException
    {
        if (orientation.equals(ORIENTATION_LEFT_TO_RIGHT)
            || orientation.equals(ORIENTATION_TOP_TO_BOTTOM)
            || orientation.equals(ORIENTATION_RIGHT_TO_LEFT)
            || orientation.equals(ORIENTATION_BOTTOM_TO_TOP))
            putClientProperty(ORIENTATION, orientation);
        else
            throw new InvalidXMLException("Invalid orientation: " + orientation);
    }

    @ChildBuilderAttribute
    public void AddChildren(UIBuilderFactory builder, List<Node> children) throws InvalidXMLException
    {
        String orientation = GetOrientation();

        for (int i = 0; i < children.size(); i++)
        {
            final Node child = children.get(i);

            builder.ReplaceResourceReferences(child);

            //Build constraints for the child.
            GridBagConstraints constraints = GetConstraintsForOrientation(orientation, i);
            Grid.SetMarginFromNode(constraints, child);

            add(builder.ParseXMLNode(child), constraints);
        }

        //We only need to call the ComputeFiller at this stage as calling it for any of the other methods would be redundant.
        ComputeFiller();

        //Panel validation is not required at this stage as the panel will not be visible yet abd so will be validated when shown.
    }

    /**
     * Adds a child to the panel and refreshes it.
     * @param child
     * @param additionalConstraints Any additional constraints to apply to the child, set to null if none.
     */
    public void AddChild(Component child, GridBagConstraints additionalConstraints)
    {
        //Get the base constraints for the child.
        GridBagConstraints constraints = GetConstraintsForOrientation(GetOrientation(), GetChildCount());

        //Add the additional constraints to the constraints.
        if (additionalConstraints != null)
        {
            constraints.insets = additionalConstraints.insets;
        }

        //Add the child to the panel.
        add(child, constraints);

        //Update the filler component.
        ComputeFiller();

        //This method should automatically refresh the panel.
        validate();
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
    public void RemoveChild(Component child)
    {
        remove(child);
        ComputeFiller();
        validate();
    }

    /**
     * Gets the number of direct children on this panel, excluding the filler component.
     */
    public int GetChildCount()
    {
        Object fillerComponent = getClientProperty(FILLER_COMPONENT);

        int childCount = 0;
        for (Component child : getComponents())
            if (child != fillerComponent)
                childCount++;

        return childCount;
    }

    /**
     * Adds or updates a filler component to the panel to pad any extra space.
     * <br></br>
     * NOTE: This method does not re-render the panel.
     */
    public void ComputeFiller()
    {
        Object _fillerComponent = getClientProperty(FILLER_COMPONENT);
        if (!(_fillerComponent instanceof Component))
        {
            //Add a basic component which will be used to fill any extra space.
            _fillerComponent = new JLabel();
            putClientProperty(FILLER_COMPONENT, _fillerComponent);
        }
        Component fillerComponent = (Component)_fillerComponent;

        String orientation = GetOrientation();

        int childCount = GetChildCount();

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
        remove(fillerComponent);
        add(fillerComponent, constraints);
    }

    public String GetOrientation()
    {
        Object orientation = getClientProperty(ORIENTATION);
        if (!(orientation instanceof String))
        {
            //Default to using a vertical layout.
            orientation = ORIENTATION_TOP_TO_BOTTOM;
            putClientProperty(ORIENTATION, orientation);
        }
        return (String)orientation;
    }
}
