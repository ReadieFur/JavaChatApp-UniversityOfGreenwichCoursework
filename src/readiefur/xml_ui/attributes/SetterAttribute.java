package readiefur.xml_ui.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that the method is a setter method for a property.
 * <br><br/>
 * <b>Constraints:</b>
 * <ul>
 *  <li>Can only be attached to a {@code method}</li>
 *  <li>Must be {@code public}</li>
 *  <li>Must be {@code static}</li>
 *  <li>Must take two parameters:
 *  <ul>
 *      <li>Component: {@link java.awt.Component}</li>
 *      <li>Value: {@link java.lang.String}</li>
 *  </ul>
 *  </li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SetterAttribute
{
    /**
     * The name of the XML property.
     */
    String value();
}
