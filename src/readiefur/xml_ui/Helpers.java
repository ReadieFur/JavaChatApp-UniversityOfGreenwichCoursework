package readiefur.xml_ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Node;

import readiefur.xml_ui.exceptions.InvalidXMLException;

public class Helpers
{
    private Helpers(){}

    public static List<Node> GetElementNodes(Node node)
    {
        List<Node> elementNodes = new ArrayList<>();
        for (int i = 0; i < node.getChildNodes().getLength(); i++)
        {
            Node childNode = node.getChildNodes().item(i);
            if (childNode.getNodeType() == Node.ELEMENT_NODE)
                elementNodes.add(childNode);
        }
        return elementNodes;
    }

    public static Class<?> GetClassForXMLComponent(Node xmlNode, Map<String, String> xmlNamespaces) throws InvalidXMLException
    {
        String[] rootComponentNameParts = xmlNode.getNodeName().split(":", 2);
        String xmlComponentClassPath;
        if (rootComponentNameParts.length == 2)
        {
            String namespaceKey = rootComponentNameParts[0];
            String namespaceValue = xmlNamespaces.get(namespaceKey);
            if (namespaceValue == null)
                throw new InvalidXMLException("The namespace key '" + namespaceKey + "' is not defined.");

            xmlComponentClassPath = namespaceValue + "." + rootComponentNameParts[1];
        }
        else
        {
            String namespaceValue = xmlNamespaces.get("");
            if (namespaceValue == null)
                throw new InvalidXMLException("The default namespace is not defined.");

            xmlComponentClassPath = namespaceValue + "." + rootComponentNameParts[0];
        }

        Class<?> xmlComponentClass;
        try { xmlComponentClass = Class.forName(xmlComponentClassPath); }
        catch (ClassNotFoundException e) { throw new InvalidXMLException("The root XML component '" + xmlComponentClassPath + "' does not exist."); }

        return xmlComponentClass;
    }
}
