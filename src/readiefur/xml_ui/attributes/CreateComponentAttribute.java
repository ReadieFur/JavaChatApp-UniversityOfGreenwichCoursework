package readiefur.xml_ui.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to indicate the method used to create a control. Must be implemented on all XMLComponent helpers.
 * <br><br/>
 * <b>Constraints:</b>
 * <ul>
 *  <li>Can only be attached to a {@code method}</li>
 *  <li>Must be {@code public}</li>
 *  <li>Must be {@code static}</li>
 *  <li>Must take no parameters</li>
 *  <li>Must return a {@link java.awt.Component}</li>
 * </ul>
*/
@Retention(RetentionPolicy.RUNTIME)
public @interface CreateComponentAttribute {}
