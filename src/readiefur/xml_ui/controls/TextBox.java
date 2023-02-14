package readiefur.xml_ui.controls;

import java.awt.Color;

import javax.swing.JTextField;

import readiefur.xml_ui.attributes.CreateComponentAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;

/**
 * Converts an XML {@code TextBox} component into a {@link javax.swing.JTextField} component.
 */
public class TextBox
{
    private TextBox(){}

    @CreateComponentAttribute
    public static JTextField Create()
    {
        return new JTextField();
    }

    @SetterAttribute("Background")
    public static void SetBackground(JTextField textBox, String colour)
    {
        textBox.setBackground(Color.decode(colour));
    }

    @SetterAttribute("Foreground")
    public static void SetForeground(JTextField textBox, String colour)
    {
        textBox.setForeground(Color.decode(colour));
    }

    @SetterAttribute("Value")
    public static void SetValue(JTextField textBox, String value)
    {
        textBox.setText(value);
    }

    @SetterAttribute("Enabled")
    public static void SetEnabled(JTextField textBox, String value)
    {
        textBox.setEnabled(Boolean.parseBoolean(value));
    }
}
