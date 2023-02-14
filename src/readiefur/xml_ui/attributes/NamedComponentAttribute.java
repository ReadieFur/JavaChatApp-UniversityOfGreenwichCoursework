package readiefur.xml_ui.attributes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates that the attached field should be used as a named component.
 * If the component is not found in the XML document, an {@link readiefur.xml_ui.exceptions.InvalidXMLException} will be thrown.
 * <br><br/>
 * <b>Constraints:</b>
 * <ul>
 * <li>Can only be attached to a {@code field}</li>
 * <li>Must extend {@link java.awt.Component}</li>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NamedComponentAttribute {}
