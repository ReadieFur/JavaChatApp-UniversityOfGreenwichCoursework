package xml_ui;

import java.awt.Component;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import xml_ui.attributes.BindingAttribute;
import xml_ui.attributes.EventCallbackAttribute;
import xml_ui.attributes.NamedComponentAttribute;
import xml_ui.exceptions.InvalidXMLException;
import xml_ui.factory.UIBuilderFactory;

public class XMLRootComponent<TRootComponent extends Component>
{
    private final Map<String, Component> namedComponents;

    protected TRootComponent rootComponent;

    protected XMLRootComponent() throws IOException, ParserConfigurationException, SAXException, InvalidXMLException, IllegalArgumentException, IllegalAccessException
    {
        Map<String, String> xmlNamespaces = new HashMap<>();
        Map<String, String> resources = new HashMap<>();
        Map<String, Observable<String>> bindableMembers = new HashMap<>();
        Map<String, Consumer<Object[]>> eventCallbacks = new HashMap<>();

        //Gets the intermediate path to the class file which we will use to load the XML file.
        InputStream xmlFileStream = this.getClass().getResourceAsStream(this.getClass().getSimpleName() + ".xml");

        //#region Load the XML file
        Element xmlRootElement;
        try
        {
            //Setup the XML parser.
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            //Parse the XML file.
            Document document = builder.parse(xmlFileStream);

            //Get the root element.
            xmlRootElement = document.getDocumentElement();
        }
        finally
        {
            xmlFileStream.close();
        }
        //#endregion

        if (xmlRootElement.getAttributes() == null)
            throw new IOException("The root element of the XML file does not have any attributes. (The root element must have a namespace defined).");

        //#region Get the namespaces
        for (int i = 0; i < xmlRootElement.getAttributes().getLength(); i++)
        {
            String attributeName = xmlRootElement.getAttributes().item(i).getNodeName();
            if (attributeName.startsWith("xmlns"))
            {
                String namespaceName;
                if (attributeName.startsWith("xmlns:"))
                {
                    namespaceName = attributeName.substring(6);
                    if (namespaceName.isEmpty())
                        throw new InvalidXMLException("A key'd namespace cannot be empty.");
                }
                else
                {
                    namespaceName = "";
                }

                String namespaceValue = xmlRootElement.getAttributes().item(i).getNodeValue();

                if (xmlNamespaces.containsKey(namespaceName))
                    throw new InvalidXMLException("The namespace '" + namespaceName + "' is defined more than once.");
                xmlNamespaces.put(namespaceName, namespaceValue);
            }
        }
        //#endregion

        //#region Verify that the root XML component can be used as a root component
        Class<?> xmlComponentClass = Helpers.GetClassForXMLComponent(xmlRootElement, xmlNamespaces);
        if (!XMLRootComponent.class.isAssignableFrom(xmlComponentClass))
            throw new InvalidXMLException("The root XML component '" + xmlComponentClass.getCanonicalName() + "' cannot be used as a root component.");
        //#endregion

        //#region Get the resources
        for (Node node : Helpers.GetElementNodes(xmlRootElement))
        {
            if (!node.getNodeName().equals(xmlRootElement.getNodeName() + ".Resources"))
                continue;

            for (Node resourceNode : Helpers.GetElementNodes(node))
            {
                if (!resourceNode.getNodeName().equals("Resource"))
                    throw new InvalidXMLException("The Resources group can only contain 'Resource' elements.");

                if (!resourceNode.hasAttributes())
                    throw new InvalidXMLException("The Resource element does not have any attributes.");

                if (resourceNode.hasChildNodes())
                    throw new InvalidXMLException("The Resource element cannot have any child nodes.");

                Node resourceKey = resourceNode.getAttributes().getNamedItem("Key");
                Node resourceValue = resourceNode.getAttributes().getNamedItem("Value");

                if (resourceKey == null || resourceKey.getNodeValue() == null
                    || resourceValue == null || resourceValue.getNodeValue() == null)
                    throw new InvalidXMLException("The Resource element must have a 'Key' and 'Value' attribute.");

                String resourceKeyString = resourceKey.getNodeValue();
                String resourceValueString = resourceValue.getNodeValue();

                if (resources.containsKey(resourceKeyString))
                    throw new InvalidXMLException("The resource '" + resourceKeyString + "' is defined more than once.");
                resources.put(resourceKeyString, resourceValueString);
            }

            break;
        }
        //#endregion

        //#region Preprocess class fields
        for (Field field : this.getClass().getDeclaredFields())
        {
            Annotation[] attributes = field.getAnnotations();

            //We won't need to check for duplicate attributes because by default only one is allowed per member.
            //Attributes that can be repeated appear under their own @Repeatable annotation.
            for (Annotation attribute : attributes)
            {
                //Get the bindable members.
                if (attribute instanceof BindingAttribute)
                {
                    //Make sure that the method constrains to the requirements of ({@see BindingAttribute}).
                    if (field.getType() != Observable.class)
                        // || !field.getGenericType().getTypeName().equals(Observable.class.getCanonicalName() + "<" + String.class.getCanonicalName() + ">"))
                        throw new IllegalArgumentException(
                            "Binding fields must be of type Observable<String>. (" + this.getClass().getSimpleName() + "::" + field.getName() + ")");

                    //We must also construct them at this stage as in Java class members are initialized after the constructor is called (unlike C#).
                    field.setAccessible(true);
                    field.set(this, new Observable<String>(((BindingAttribute)attribute).DefaultValue()));

                    //Add the bindable member to the dictionary.
                    bindableMembers.put(field.getName(), (Observable<String>)field.get(this));
                }
            }
        }
        //#endregion

        //#region Preprocess class methods
        for (Method method : this.getClass().getDeclaredMethods())
        {
            Annotation[] attributes = method.getAnnotations();

            for (Annotation attribute : attributes)
            {
                if (attribute instanceof EventCallbackAttribute)
                {
                    //Make sure that the method constrains to the requirements of ({@see EventCallbackAttribute}).
                    if (method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != Object[].class)
                        throw new IllegalArgumentException(
                            "Event callback methods must have exactly one parameter of type Object[]. (" + this.getClass().getSimpleName() + "::" + method.getName() + ")");

                    method.setAccessible(true);

                    //Capture the variable so that it can be used in the lambda expression.
                    final Method capturedMethod = method;
                    eventCallbacks.put(method.getName(), args ->
                    {
                        /*If the object array is passed "as is", the values will get unwrapped and cause an "wrong number of arguments" exception.
                         * So we wrap the object array in another array to prevent this.
                         */
                        try { capturedMethod.invoke(this, new Object[] { args }); }
                        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)
                        {
                            throw new RuntimeException(ex);
                        }
                    });
                }
            }
        }
        //#endregion

        //#region Build the UI tree.
        UIBuilderFactory uiBuilderFactory = new UIBuilderFactory(
            xmlNamespaces,
            resources,
            bindableMembers,
            eventCallbacks);
        uiBuilderFactory.SetDoRootComponentCheckForNextCall(false);
        rootComponent = (TRootComponent)uiBuilderFactory.ParseXMLNode(xmlRootElement);
        namedComponents = uiBuilderFactory.GetNamedComponents();
        //#endregion

        //#region Set the class named components
        for (Field field : this.getClass().getDeclaredFields())
        {
            Annotation[] attributes = field.getAnnotations();

            for (Annotation attribute : attributes)
            {
                if (!(attribute instanceof NamedComponentAttribute))
                    continue;

                if (!Component.class.isAssignableFrom(field.getType()))
                    throw new IllegalArgumentException(
                        "Named component fields must be of type Component. (" + this.getClass().getSimpleName() + "::" + field.getName() + ")");

                if (!namedComponents.containsKey(field.getName()))
                    throw new InvalidXMLException("The named component '" + field.getName() + "' is not defined in the XML document.");

                field.setAccessible(true);
                field.set(this, namedComponents.get(field.getName()));
            }
        }
        //#endregion
    }

    protected <T extends Component> T GetNamedComponent(String componentName, Class<T> componentClass)
    {
        return (T)namedComponents.get(componentName);
    }

    protected Component GetNamedComponent(String componentName)
    {
        return GetNamedComponent(componentName, Component.class);
    }

    /**
     * Shorthand for {@link #GetNamedComponent(String, Class)}.
     */
    protected <T extends Component> T Get(String componentName, Class<T> componentClass)
    {
        return GetNamedComponent(componentName, componentClass);
    }

    /**
     * Shorthand for {@link #GetNamedComponent(String)}.
     */
    protected Component Get(String componentName)
    {
        return GetNamedComponent(componentName);
    }
}
