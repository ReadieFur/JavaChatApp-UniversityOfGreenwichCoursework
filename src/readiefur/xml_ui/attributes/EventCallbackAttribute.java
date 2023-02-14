package readiefur.xml_ui.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to indicate a method that should be called when an event occurs.
 * <br><br/>
 * <b>Constraints:</b>
 * <ul>
 *  <li>Can only be attached to a {@code method}</li>
 *  <li>Must take one parameter:
 *  <ul>
 *      <li>Args: {@link java.lang.Object}[]</li>
 *  </ul>
 *  </li>
 * </ul>
 */
//We use an Object[] as opposed to a List<Object> because we want each callback to have their own array.
@Retention(RetentionPolicy.RUNTIME)
public @interface EventCallbackAttribute {}
