package xml_ui.factory;

import java.awt.Component;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.w3c.dom.Node;

import xml_ui.attributes.ChildBuilderAttribute;
import xml_ui.attributes.CreateComponentAttribute;
import xml_ui.attributes.EventAttribute;
import xml_ui.attributes.SetterAttribute;

public class FactoryComponentWrapper
{
    private static Object InvokeMethod(Method method, Object... args)
    {
        try
        {
            if (args.length == 0)
                return method.invoke(null);
            else
                return method.invoke(null, args);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex)
        {
            //Shouldn't happen but I'm including a throw here to make the compiler happy.
            throw new UnsupportedOperationException(ex);
        }
    }

    private Method createComponentMethod; //Must be defined.
    private Map<String, Method> setterMethods = new HashMap<>();
    private Map<String, Method> eventMethods = new HashMap<>();
    private Method childBuilderMethod = null; //Optional.

    public FactoryComponentWrapper(Class<?> cls)
    {
        //We want to use public methods only for this.
        for (Method method : cls.getMethods())
        {
            for (Annotation annotation : method.getAnnotations())
            {
                if (annotation instanceof CreateComponentAttribute)
                {
                    //Make sure only one CreateComponent method exists.
                    if (createComponentMethod != null)
                        throw new UnsupportedOperationException("The class '" + cls.getName() + "' has multiple CreateComponent methods.");

                    //Make sure that the method constrains to the requirements of ({@see CreateComponentAttribute}).
                    final String exceptionPrefix = "The CreateComponent method in the class '" + cls.getName() + "' ";
                    if (!Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be static.");
                    else if (method.getParameterCount() != 0)
                        throw new IllegalArgumentException(exceptionPrefix + "must take no parameters.");
                    else if (!Component.class.isAssignableFrom(method.getReturnType()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must return a Component.");

                    createComponentMethod = method;
                }
                else if (annotation instanceof SetterAttribute)
                {
                    if (setterMethods.containsKey(((SetterAttribute)annotation).value()))
                        throw new UnsupportedOperationException(
                            "The class '" + cls.getName() + "' has multiple Setter methods for the property '" + ((SetterAttribute)annotation).value() + "'.");

                    //Make sure that the method constrains to the requirements of ({@see SetterAttribute}).
                    final String exceptionPrefix = "The Setter method in the class '" + cls.getName() + "' ";
                    if (!Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be static.");
                    else if (method.getParameterCount() != 2)
                        throw new IllegalArgumentException(exceptionPrefix + "must take two parameters.");
                    else if (!Component.class.isAssignableFrom(method.getParameterTypes()[0]))
                        throw new IllegalArgumentException(exceptionPrefix + "must take a Component as it's first parameter.");
                    else if (method.getParameterTypes()[1] != String.class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take a String as it's second parameter.");

                    //Add the method to the map.
                    setterMethods.put(((SetterAttribute)annotation).value(), method);
                }
                else if (annotation instanceof EventAttribute)
                {
                    if (eventMethods.containsKey(((EventAttribute)annotation).value()))
                        throw new UnsupportedOperationException(
                            "The class '" + cls.getName() + "' has multiple Event methods for the event '" + ((EventAttribute)annotation).value() + "'.");

                    //Make sure that the method constrains to the requirements of ({@see EventAttribute}).
                    final String exceptionPrefix = "The Event method in the class '" + cls.getName() + "' ";
                    if (!Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be static.");
                    else if (method.getParameterCount() != 2)
                        throw new IllegalArgumentException(exceptionPrefix + "must take two parameters.");
                    else if (!Component.class.isAssignableFrom(method.getParameterTypes()[0]))
                        throw new IllegalArgumentException(exceptionPrefix + "must take a Component as it's first parameter.");
                    else if (method.getParameterTypes()[1] != Consumer.class)
                        // || method.getParameterTypes()[1].getTypeParameters()[0] != Object[].class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take an Consumer<Object[]> as it's second parameter.");

                    //Add the method to the map.
                    eventMethods.put(((EventAttribute)annotation).value(), method);
                }
                else if (annotation instanceof ChildBuilderAttribute)
                {
                    //Make sure only one ChildBuilder method exists.
                    if (childBuilderMethod != null)
                        throw new UnsupportedOperationException("The class '" + cls.getName() + "' has multiple ChildBuilder methods.");

                    //Make sure that the method constrains to the requirements of ({@see ChildBuilderAttribute}).
                    final String exceptionPrefix = "The ChildBuilder method in the class '" + cls.getName() + "' ";
                    if (!Modifier.isStatic(method.getModifiers()))
                        throw new UnsupportedOperationException(exceptionPrefix + "must be static.");
                    else if (method.getParameterCount() != 3)
                        throw new IllegalArgumentException(exceptionPrefix + "must take three parameters.");
                    else if (method.getParameterTypes()[0] != UIBuilderFactory.class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take a UIBuilderFactory as it's first parameter.");
                    else if (!Component.class.isAssignableFrom(method.getParameterTypes()[1]))
                        throw new IllegalArgumentException(exceptionPrefix + "must take a Component as it's second parameter.");
                    else if (method.getParameterTypes()[2] != java.util.List.class)
                        throw new IllegalArgumentException(exceptionPrefix + "must take a List<Node> as it's third parameter.");

                    childBuilderMethod = method;
                }
            }
        }

        if (createComponentMethod == null)
            throw new UnsupportedOperationException("The class '" + cls.getName() + "' does not implement the CreateComponent method.");
    }

    public Component CreateComponent()
    {
        return (Component)InvokeMethod(createComponentMethod);
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

        InvokeMethod(setterMethods.get(name), component, value);
        return true;
    }

    public Boolean TryBindEvent(Component component, String name, Consumer<Object[]> callback)
    {
        if (!eventMethods.containsKey(name))
            return false;

        InvokeMethod(eventMethods.get(name), component, callback);
        return true;
    }

    public void ParseChildTree(UIBuilderFactory factory, Component parent, List<Node> childNodes)
    {
        if (childBuilderMethod == null)
            return;

        InvokeMethod(childBuilderMethod, factory, parent, childNodes);
    }
}
