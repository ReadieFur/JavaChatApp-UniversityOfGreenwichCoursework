package readiefur.xml_ui.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to indicate that a method is for subscribing events.
 * <br><br/>
 * <b>Constraints:</b>
 * <ul>
 *  <li>Can only be attached to a {@code method}</li>
 *  <li>Must be {@code public}</li>
 *  <li>Must be {@code static}</li>
 *  <li>Must take two parameters:
 *  <ul>
 *      <li>Component: {@link java.awt.Component}</li>
 *      <li>Callback: {@link java.util.function.Consumer}<{@link java.lang.Object}[]></li>
 *  </ul>
 *  </li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface EventAttribute
{
    /**
     * The name of the XML property.
     */
    String value();
}
