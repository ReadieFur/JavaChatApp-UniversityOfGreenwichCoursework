package readiefur.xml_ui.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to indicate the child builder for a control.
 * <br><br/>
 * <b>Constraints:</b>
 * <ul>
 *  <li>Can only be attached to a {@code method}</li>
 *  <li>Must be {@code public}</li>
 *  <li>Must take two parameters:
 *  <ul>
 *      <li>UI Builder Factory: {@link readiefur.xml_ui.factory.UIBuilderFactory}</li>
 *      <li>Child nodes: {@link java.util.List}<{@link org.w3c.dom.Node}></li>
 *  </ul>
 *  </li>
 *  <li>Must return {@code void}</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ChildBuilderAttribute {}
