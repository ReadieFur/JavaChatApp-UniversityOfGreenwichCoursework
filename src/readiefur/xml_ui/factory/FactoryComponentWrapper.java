package readiefur.xml_ui.factory;

import java.awt.Component;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.w3c.dom.Node;

import readiefur.xml_ui.attributes.ChildBuilderAttribute;
import readiefur.xml_ui.attributes.EventAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;
import readiefur.xml_ui.exceptions.InvalidXMLException;

public class FactoryComponentWrapper
{
    private static Object InvokeMethod(Component instance, Method method, Object... args)
    {
        try
        {
            if (args.length == 0)
                return method.invoke(instance);
            else
                return method.invoke(instance, args);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)
        {
            //Shouldn't happen but I'm including a throw here to make the compiler happy.
            throw new UnsupportedOperationException(ex);
        }
    }

    private Constructor<?> constructorMethod; //Must be defined.
    private Map<String, Method> setterMethods = new HashMap<>();
    private Map<String, Method> eventMethods = new HashMap<>();
    private Method childBuilderMethod = null; //Optional.

    public FactoryComponentWrapper(Class<?> cls)
    {
        if (!Component.class.isAssignableFrom(cls))
            throw new UnsupportedOperationException("The class '" + cls.getName() + "' does not extend Component.");

        //Find a parameterless constructor on the class.
        for (Constructor<?> constructor : cls.getDeclaredConstructors())
        {
            //Make sure that the method is parameterless.
            if (constructor.getParameterCount() != 0)
                continue;

            //Set the constructor method.
            constructor.setAccessible(true);
            constructorMethod = constructor;
        }
        if (constructorMethod == null)
            throw new UnsupportedOperationException("The class '" + cls.getName() + "' does not have a parameterless constructor.");

        for (Method method : cls.getDeclaredMethods())
        {
            for (Annotation annotation : method.getAnnotations())
            {
                if (annotation instanceof SetterAttribute)
                {
                    if (setterMethods.containsKey(((SetterAttribute)annotation).value()))
                        throw new UnsupportedOperationException(
                            "The class '" + cls.getName() + "' has multiple Setter methods for the property '" + ((SetterAttribute)annotation).value() + "'.");

                    //Make sure that the method constrains to the requirements of ({@see SetterAttribute}).
                    final String exceptionPrefix = "The Setter method in the class '" + cls.getName() + "' ";
                    if (Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be instanced.");
                    else if (method.getParameterCount() != 1)
                        throw new IllegalArgumentException(exceptionPrefix + "must take one parameter.");
                    else if (method.getParameterTypes()[0] != String.class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take a String as it's only parameter.");

                    //Add the method to the map.
                    method.setAccessible(true);
                    setterMethods.put(((SetterAttribute)annotation).value(), method);
                }
                else if (annotation instanceof EventAttribute)
                {
                    if (eventMethods.containsKey(((EventAttribute)annotation).value()))
                        throw new UnsupportedOperationException(
                            "The class '" + cls.getName() + "' has multiple Event methods for the event '" + ((EventAttribute)annotation).value() + "'.");

                    //Make sure that the method constrains to the requirements of ({@see EventAttribute}).
                    final String exceptionPrefix = "The Event method in the class '" + cls.getName() + "' ";
                    if (Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be instanced.");
                    else if (method.getParameterCount() != 1)
                        throw new IllegalArgumentException(exceptionPrefix + "must take one parameter.");
                    else if (method.getParameterTypes()[0] != Consumer.class)
                        // || method.getParameterTypes()[0].getTypeParameters()[0] != Object[].class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take an Consumer<Object[]> as it's only parameter.");

                    //Add the method to the map.
                    method.setAccessible(true);
                    eventMethods.put(((EventAttribute)annotation).value(), method);
                }
                else if (annotation instanceof ChildBuilderAttribute)
                {
                    //Make sure only one ChildBuilder method exists.
                    if (childBuilderMethod != null)
                        throw new UnsupportedOperationException("The class '" + cls.getName() + "' has multiple ChildBuilder methods.");

                    //Make sure that the method constrains to the requirements of ({@see ChildBuilderAttribute}).
                    final String exceptionPrefix = "The ChildBuilder method in the class '" + cls.getName() + "' ";
                    if (Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be instanced.");
                    else if (method.getParameterCount() != 2)
                        throw new IllegalArgumentException(exceptionPrefix + "must take two parameters.");
                    else if (method.getParameterTypes()[0] != UIBuilderFactory.class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take a UIBuilderFactory as it's first parameter.");
                    else if (method.getParameterTypes()[1] != java.util.List.class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take a List<Node> as it's second parameter.");

                    method.setAccessible(true);
                    childBuilderMethod = method;
                }
            }
        }

        if (constructorMethod == null)
            throw new UnsupportedOperationException("The class '" + cls.getName() + "' does not implement the CreateComponent method.");
    }

    public Component CreateComponent() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        return (Component)constructorMethod.newInstance();
    }

    public Set<String> GetSetterNames()
    {
        return setterMethods.keySet();
    }

    public Set<String> GetEventNames()
    {
        return eventMethods.keySet();
    }

    public Boolean TrySetAttribute(Component component, String name, String value)
    {
        if (!setterMethods.containsKey(name))
            return false;

        InvokeMethod(component, setterMethods.get(name), value);
        return true;
    }

    public Boolean TryBindEvent(Component component, String name, Consumer<Object[]> callback)
    {
        if (!eventMethods.containsKey(name))
            return false;

        InvokeMethod(component, eventMethods.get(name), callback);
        return true;
    }

    public void ParseChildTree(UIBuilderFactory factory, Component parent, List<Node> childNodes) throws InvalidXMLException
    {
        if (!childNodes.isEmpty() && childBuilderMethod == null)
            throw new InvalidXMLException("The component '" + parent.getClass().getName() + "' cannot have any children.");

        InvokeMethod(parent, childBuilderMethod, factory, childNodes);
    }
}
