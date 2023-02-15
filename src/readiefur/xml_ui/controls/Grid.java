package readiefur.xml_ui.controls;

import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.plaf.InsetsUIResource;

import org.w3c.dom.Node;

import readiefur.misc.Pair;
import readiefur.xml_ui.Helpers;
import readiefur.xml_ui.attributes.ChildBuilderAttribute;
import readiefur.xml_ui.exceptions.InvalidXMLException;
import readiefur.xml_ui.factory.UIBuilderFactory;

public class Grid extends JPanel
{
    public Grid()
    {
        super();

        setLayout(new GridBagLayout());
        setOpaque(false);
    }

    //TODO: XML binding for grid layout. (Currently resources work, binding does not).
    @ChildBuilderAttribute
    public void AddChildren(UIBuilderFactory builder, List<Node> children) throws InvalidXMLException
    {
        //#region Get the grid property nodes and child nodes
        List<Pair<Integer, Float>> rowDefinitions = new ArrayList<>();
        List<Pair<Integer, Float>> columnDefinitions = new ArrayList<>();
        List<Node> childrenToAdd = new ArrayList<>();

        for (Node child : children)
        {
            builder.ReplaceResourceReferences(child);

            if (child.getNodeName().equals("Grid.RowDefinitions"))
            {
                //I would normally make these one liners without braces but in this instance I have kept some to improve readability.
                for (Node rowDefinition : Helpers.GetElementNodes(child))
                {
                    builder.ReplaceResourceReferences(rowDefinition);
                    rowDefinitions.add(GetWeightValue(rowDefinition, "RowDefinition", "Height"));
                }
            }
            else if (child.getNodeName().equals("Grid.ColumnDefinitions"))
            {
                for (Node columnDefinition : Helpers.GetElementNodes(child))
                {
                    builder.ReplaceResourceReferences(columnDefinition);
                    columnDefinitions.add(GetWeightValue(columnDefinition, "ColumnDefinition", "Width"));
                }
            }
            else
            {
                childrenToAdd.add(child);
            }
        }

        //Correct the row and column definitions
        //Values are passed by reference so this operation is fine.
        CorrectAutoWeights(rowDefinitions);
        CorrectAutoWeights(columnDefinitions);
        //#endregion

        //#region Add the children to the grid
        for (Node child : childrenToAdd)
        {
            //Get/set the desired constraints for the child.
            GridBagConstraints constraints = new GridBagConstraints();

            SetAlignmentFromNode(constraints, child);
            SetMarginFromNode(constraints, child);

            if (child.hasAttributes())
            {
                Node rowAttribute = child.getAttributes().getNamedItem("Grid.Row");
                if (rowAttribute != null && !rowDefinitions.isEmpty())
                {
                    //Parse the row attribute and make sure it is in range.
                    int row = Integer.parseInt(rowAttribute.getNodeValue());
                    if (row < 0)
                        row = 0;
                    else if (row >= rowDefinitions.size())
                        row = rowDefinitions.size() - 1;

                    //Set the row.
                    constraints.gridy = row;

                    //Set the row weight.
                    Pair<Integer, Float> rowWeight = rowDefinitions.get(row);
                    switch (rowWeight.item1)
                    {
                        case 1: //Pixels.
                            constraints.ipady = rowWeight.item2.intValue();
                            break;
                        case 2: //Percentage.
                            constraints.weighty = rowWeight.item2;
                            break;
                        default: //Shouldn't be reached.
                            break;
                    }
                }
                else
                {
                    //If no value is specified, use the default settings which is "Stretch".
                    constraints.weighty = 1;
                }

                Node columnAttribute = child.getAttributes().getNamedItem("Grid.Column");
                if (columnAttribute != null && !columnDefinitions.isEmpty())
                {
                    //Parse the column attribute and make sure it is in range.
                    int column = Integer.parseInt(columnAttribute.getNodeValue());
                    if (column < 0)
                        column = 0;
                    if (column >= columnDefinitions.size())
                        column = columnDefinitions.size() - 1;

                    //Set the column.
                    constraints.gridx = column;

                    //Set the column weight.
                    Pair<Integer, Float> columnWeight = columnDefinitions.get(column);
                    switch (columnWeight.item1)
                    {
                        case 1: //Pixels.
                            constraints.ipadx = columnWeight.item2.intValue();
                            break;
                        case 2: //Percentage.
                            constraints.weightx = columnWeight.item2;
                            break;
                        default: //Shouldn't be reached.
                            break;
                    }
                }
                else
                {
                    constraints.weightx = 1;
                }

                Node rowSpanAttribute = child.getAttributes().getNamedItem("Grid.RowSpan");
                if (rowSpanAttribute != null)
                {
                    //Parse the row span attribute and make sure it is in range.
                    int rowSpan = Integer.parseInt(rowSpanAttribute.getNodeValue()) + 1;
                    if (rowSpan < 1)
                        rowSpan = 1;
                    else if (rowSpan > rowDefinitions.size())
                        rowSpan = rowDefinitions.size();

                    //Set the row span.
                    constraints.gridheight = rowSpan;
                }

                Node columnSpanAttribute = child.getAttributes().getNamedItem("Grid.ColumnSpan");
                if (columnSpanAttribute != null)
                {
                    //Parse the column span attribute and make sure it is in range.
                    int columnSpan = Integer.parseInt(columnSpanAttribute.getNodeValue()) + 1;
                    if (columnSpan < 1)
                        columnSpan = 1;
                    else if (columnSpan > columnDefinitions.size())
                        columnSpan = columnDefinitions.size();

                    //Set the column span.
                    constraints.gridwidth = columnSpan;
                }

                add(builder.ParseXMLNode(child), constraints);
            }
        }
        //#endregion
    }

    /**
     * Gets the weight value of a node.
     * @return A pair containing the weight type and the weight value. The weight types are as follows:
     * 0: No weight (aka auto).
     * 1: Weight in pixels.
     * 2: Weight as a percentage (0-1).
     */
    private static Pair<Integer, Float> GetWeightValue(Node node, String subNodeKey, String attributeName) throws InvalidXMLException
    {
        if (!node.getNodeName().equals(subNodeKey))
            throw new InvalidXMLException(subNodeKey + "s nodes must contain only " + subNodeKey + " nodes.");

        if (node.getAttributes() == null || node.getAttributes().getNamedItem(attributeName) == null)
            return new Pair<>(0, null);

        String value = node.getAttributes().getNamedItem(attributeName).getNodeValue();
        if (value == null)
        {
            return new Pair<>(0, null);
        }
        else if (value.endsWith("px"))
        {
            return new Pair<>(1, Float.parseFloat(value.substring(0, value.length() - 2)));
        }
        else
        {
            //If this fails to parse then an invalid value has been provided.
            try
            {
                return new Pair<>(2, Float.parseFloat(value));
            }
            catch (NumberFormatException e)
            {
                throw new InvalidXMLException("Invalid value for " + attributeName + " attribute.");
            }
        }
    }

    private static void CorrectAutoWeights(List<Pair<Integer, Float>> definitions)
    {
        //For the definitions that are not set (i.e. should be auto), we need to calculate their weight.
        //Pixel set definitions are ignored.
        int autoDefinitions = 0;
        float percentageUsed = 0;
        for (Pair<Integer, Float> definition : definitions)
        {
            switch (definition.item1)
            {
                case 0: //Auto.
                    autoDefinitions++;
                    break;
                case 1: //Pixels.
                    break;
                case 2: //Percentage.
                    percentageUsed += definition.item2;
                    break;
                default: //Shouldn't be reached.
                    break;
            }
        }

        if (autoDefinitions == 0)
            return;

        float autoWeight = (1 - percentageUsed) / autoDefinitions;

        //Update the auto definitions.
        for (int i = 0; i < definitions.size(); i++)
        {
            Pair<Integer, Float> definition = definitions.get(i);
            if (definition.item1 == 0)
            {
                definitions.set(i, new Pair<>(2, autoWeight));
            }
        }
    }

    public static void SetAlignmentFromNode(GridBagConstraints constraints, Node node) throws InvalidXMLException
    {
        if (!node.hasAttributes())
        {
            //Default values.
            constraints.anchor = GridBagConstraints.CENTER;
            constraints.fill = GridBagConstraints.BOTH;
            return;
        }

        //Get the vertical alignment.
        int verticalAlignment = GridBagConstraints.VERTICAL;
        Node verticalAlignmentAttribute = node.getAttributes().getNamedItem("VerticalAlignment");
        if (verticalAlignmentAttribute != null)
        {
            switch (verticalAlignmentAttribute.getNodeValue())
            {
                case "Top":
                    verticalAlignment = GridBagConstraints.NORTH;
                    break;
                case "Center":
                    verticalAlignment = GridBagConstraints.CENTER;
                    break;
                case "Bottom":
                    verticalAlignment = GridBagConstraints.SOUTH;
                    break;
                case "Stretch":
                    verticalAlignment = GridBagConstraints.VERTICAL;
                    break;
                default:
                    throw new InvalidXMLException("Invalid value for VerticalAlignment attribute.");
            }
        }

        //Get the horizontal alignment.
        int horizontalAlignment = GridBagConstraints.HORIZONTAL;
        Node horizontalAlignmentAttribute = node.getAttributes().getNamedItem("HorizontalAlignment");
        if (horizontalAlignmentAttribute != null)
        {
            switch (horizontalAlignmentAttribute.getNodeValue())
            {
                case "Left":
                    horizontalAlignment = GridBagConstraints.WEST;
                    break;
                case "Center":
                    horizontalAlignment = GridBagConstraints.CENTER;
                    break;
                case "Right":
                    horizontalAlignment = GridBagConstraints.EAST;
                    break;
                case "Stretch":
                    horizontalAlignment = GridBagConstraints.HORIZONTAL;
                    break;
                default:
                    throw new InvalidXMLException("Invalid value for HorizontalAlignment attribute.");
            }
        }

        //Combine the alignments.
        //This could've been so much easier if the constraints could've been OR'd together to get the correct value.

        switch (verticalAlignment)
        {
            case GridBagConstraints.NORTH:
                switch (horizontalAlignment)
                {
                    case GridBagConstraints.WEST:
                        constraints.anchor = GridBagConstraints.NORTHWEST;
                        break;
                    case GridBagConstraints.CENTER:
                        constraints.anchor = GridBagConstraints.NORTH;
                        break;
                    case GridBagConstraints.EAST:
                        constraints.anchor = GridBagConstraints.NORTHEAST;
                        break;
                    case GridBagConstraints.HORIZONTAL:
                        constraints.anchor = GridBagConstraints.NORTH;
                        constraints.fill = GridBagConstraints.HORIZONTAL;
                        break;
                    default:
                        //Shouldn't be reached due to previous checks.
                        break;
                }
                break;
            case GridBagConstraints.CENTER:
                switch (horizontalAlignment)
                {
                    case GridBagConstraints.WEST:
                        constraints.anchor = GridBagConstraints.WEST;
                        break;
                    case GridBagConstraints.CENTER:
                        constraints.anchor = GridBagConstraints.CENTER;
                        break;
                    case GridBagConstraints.EAST:
                        constraints.anchor = GridBagConstraints.EAST;
                        break;
                    case GridBagConstraints.HORIZONTAL:
                        constraints.fill = GridBagConstraints.HORIZONTAL;
                        break;
                    default:
                        break;
                }
                break;
            case GridBagConstraints.SOUTH:
                switch (horizontalAlignment)
                {
                    case GridBagConstraints.WEST:
                        constraints.anchor = GridBagConstraints.SOUTHWEST;
                        break;
                    case GridBagConstraints.CENTER:
                        constraints.anchor = GridBagConstraints.SOUTH;
                        break;
                    case GridBagConstraints.EAST:
                        constraints.anchor = GridBagConstraints.SOUTHEAST;
                        break;
                    case GridBagConstraints.HORIZONTAL:
                        constraints.anchor = GridBagConstraints.SOUTH;
                        constraints.fill = GridBagConstraints.HORIZONTAL;
                        break;
                    default:
                        break;
                }
                break;
            case GridBagConstraints.VERTICAL:
                switch (horizontalAlignment)
                {
                    case GridBagConstraints.WEST:
                        constraints.anchor = GridBagConstraints.WEST;
                        constraints.fill = GridBagConstraints.VERTICAL;
                        break;
                    case GridBagConstraints.CENTER:
                        constraints.anchor = GridBagConstraints.CENTER;
                        constraints.fill = GridBagConstraints.VERTICAL;
                        break;
                    case GridBagConstraints.EAST:
                        constraints.anchor = GridBagConstraints.EAST;
                        constraints.fill = GridBagConstraints.VERTICAL;
                        break;
                    case GridBagConstraints.HORIZONTAL:
                        constraints.fill = GridBagConstraints.BOTH;
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
    }

    public static void SetMarginFromNode(GridBagConstraints constraints, Node node) throws InvalidXMLException
    {
        if (!node.hasAttributes())
        {
            constraints.insets = new InsetsUIResource(0, 0, 0, 0);
            return;
        }

        Node marginAttribute = node.getAttributes().getNamedItem("Margin");
        if (marginAttribute == null)
        {
            constraints.insets = new InsetsUIResource(0, 0, 0, 0);
            return;
        }

        String[] marginParts = marginAttribute.getNodeValue().split(",");
        if (marginParts.length != 4)
            throw new InvalidXMLException("Invalid margin string. Must be in the format 'top,left,bottom,right'.");

        constraints.insets = new InsetsUIResource(
            Integer.parseInt(marginParts[0]),
            Integer.parseInt(marginParts[1]),
            Integer.parseInt(marginParts[2]),
            Integer.parseInt(marginParts[3]));
    }
}
