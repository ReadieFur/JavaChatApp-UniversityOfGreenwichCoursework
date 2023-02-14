package readiefur.xml_ui.factory;

import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.w3c.dom.Node;

import readiefur.xml_ui.Helpers;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.XMLRootComponent;
import readiefur.xml_ui.exceptions.InvalidXMLException;

/**
 * A factory class that is used to build a Component tree from an XML node.
 * This class should NOT be reused for multiple XML files.
 */
public class UIBuilderFactory
{
    /**
     * A map of XML namespaces to their corresponding Java package names.
     * Key: XML namespace.
     * Value: Java package name.
     */
    private final Map<String, String> xmlNamespaces;
    /**
     * A map of XML resources and their constant values.
     */
    private final Map<String, String> resources;
    /**
     * A map of XML binding names to their corresponding {@link Observable} values.
     * Key: XML property name.
     * Value: {@link Observable} value.
     */
    private final Map<String, Observable<String>> bindableMembers;
    /**
     * A map of XML event names to their corresponding {@link Method} callbacks.
     * Key: XML property name.
     * Value: {@link java.util.function.Consumer}<{@link java.lang.Object}> callback.
     */
    private final Map<String, Consumer<Object[]>> eventCallbacks;
    /**
     * Key: {@link Class}.
     * Value: {@link FactoryComponentWrapper}.
     */
    private final Map<Class<?>, FactoryComponentWrapper> xmlComponentWrappers = new HashMap<>();
    /**
     * Used primarily for the first iteration of the recursive method to make sure that root components cannot be used as children.
     * By default, this is set to true.
     */
    private Boolean doRootComponentCheck = true;
    /**
     * A map of XML component names to their corresponding {@link Component} instances.
     */
    private final Map<String, Component> namedComponents;

    public UIBuilderFactory(
        //Leave these up to the caller to provide.
        Map<String, String> xmlNamespaces,
        Map<String, String> resources,
        Map<String, Observable<String>> bindableMembers,
        Map<String, Consumer<Object[]>> eventCallbacks)
    {
        this.xmlNamespaces = xmlNamespaces;
        this.resources = resources;
        this.bindableMembers = bindableMembers;
        this.eventCallbacks = eventCallbacks;
        this.namedComponents = new HashMap<>();
    }

    /*The reason behind having a wrapper class for these properties is that it would be more efficient than querying the class
     * every time it is needed, but it would be a waste of memory to keep the data throughout the entire lifetime of the program,
     * hence the data is instanced per-factory.
     */
    private FactoryComponentWrapper GetXMLComponentWrapperForClass(Class<?> cls)
    {
        if (xmlComponentWrappers.containsKey(cls))
            return xmlComponentWrappers.get(cls);

        FactoryComponentWrapper componentWrapper = new FactoryComponentWrapper(cls);
        xmlComponentWrappers.put(cls, componentWrapper);
        return componentWrapper;
    }

    public void SetDoRootComponentCheckForNextCall(Boolean doRootComponentCheck)
    {
        this.doRootComponentCheck = doRootComponentCheck;
    }

    public Map<String, Component> GetNamedComponents()
    {
        //Return a copy of the map.
        return new HashMap<>(namedComponents);
    }

    /**
     * Recursively parses the XML node and all of it's children.
     * @param xmlNode The XML node to parse.
     * @return The root component.
     * @throws InvalidXMLException
     */
    public Component ParseXMLNode(Node xmlNode) throws InvalidXMLException
    {
        Class<?> cls = Helpers.GetClassForXMLComponent(xmlNode, xmlNamespaces);

        if (doRootComponentCheck)
        {
            if (XMLRootComponent.class.isAssignableFrom(cls))
                throw new InvalidXMLException("Cannot use '" + cls.getName() + "' at this level.");
        }
        else
        {
            doRootComponentCheck = true;
        }

        final FactoryComponentWrapper componentWrapper = GetXMLComponentWrapperForClass(cls);

        Component component = componentWrapper.CreateComponent();

        //Parse the attributes.
        ReplaceResourceReferences(xmlNode);
        if (xmlNode.hasAttributes())
        {
            Set<String> setterNames = componentWrapper.GetSetterNames();
            Set<String> eventNames = componentWrapper.GetEventNames();

            for (int i = 0; i < xmlNode.getAttributes().getLength(); i++)
            {
                Node attribute = xmlNode.getAttributes().item(i);
                String attributeName = attribute.getNodeName();
                String attributeValue = attribute.getNodeValue();

                //Try to set the attribute as a named component.
                if (attributeName.equals("Name"))
                {
                    if (namedComponents.containsKey(attributeValue))
                        throw new InvalidXMLException("The XML component name '" + attributeValue + "' is already in use.");

                    namedComponents.put(attributeValue, component);
                }
                else if (attributeName.equals("Visible"))
                {
                    component.setVisible(Boolean.parseBoolean(attributeValue));
                }
                //Try to set the attribute as a setter.
                else if (setterNames.contains(attributeName))
                {
                    //Check if we should bind to a value or just set the value.
                    if (attributeValue.startsWith("{Binding ") && attributeValue.endsWith("}"))
                    {
                        //Try to bind to a value in the context.
                        String xmlBindingName = attributeValue.substring(9, attributeValue.length() - 1);
                        if (bindableMembers.containsKey(xmlBindingName))
                        {
                            final Observable<String> capturedObservable = bindableMembers.get(xmlBindingName);
                            componentWrapper.TrySetAttribute(component, attributeName, capturedObservable.Get());
                            capturedObservable.AddListener(newValue -> componentWrapper.TrySetAttribute(component, attributeName, newValue));
                        }
                        else
                        {
                            throw new InvalidXMLException("The XML binding '" + xmlBindingName + "' does not exist in the context.");
                        }
                    }
                    else
                    {
                        componentWrapper.TrySetAttribute(component, attributeName, attributeValue);
                    }
                }
                //Try to set the attribute as an event.
                else if (eventNames.contains(attributeName))
                {
                    //Check if the context has a callback for the event.
                    if (!eventCallbacks.containsKey(attributeValue))
                        throw new InvalidXMLException("The XML event '" + attributeValue + "' does not exist in the context.");

                    componentWrapper.TryBindEvent(component, attributeName, eventCallbacks.get(attributeValue));
                }
                /*Otherwise the attribute is unknown.
                 *We don't want to throw an exception here because some attributes may be for parent nodes to read.*/
            }
        }

        //Parse the children.
        List<Node> childElementNodes = Helpers.GetElementNodes(xmlNode);
        if (!childElementNodes.isEmpty())
            componentWrapper.ParseChildTree(this, component, childElementNodes);

        return component;
    }

    /**
     * Replaces all resource references in the XML node and all of it's children.
     * @param xmlNode The XML node to replace the resource references in.
     */
    //I ideally wouldn't want to do this as it would mean looping over properties twice but I need it for child preprocessing.
    //This method is also "destructive" in that it changes the XML node's attributes which I don't like but it's also not a problem.
    public void ReplaceResourceReferences(Node xmlNode)
    {
        if (!xmlNode.hasAttributes())
            return;

        for (int i = 0; i < xmlNode.getAttributes().getLength(); i++)
        {
            Node attribute = xmlNode.getAttributes().item(i);
            String attributeValue = attribute.getNodeValue();

            //If the attribute is a resource, replace it with the resource value.
            if (!(attributeValue.startsWith("{Resource ") && attributeValue.endsWith("}")))
                continue;

            String resourceName = attributeValue.substring(10, attributeValue.length() - 1);
            if (resources.containsKey(resourceName))
                attribute.setNodeValue(resources.get(resourceName));
        }
    }
}
