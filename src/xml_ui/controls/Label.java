package xml_ui.controls;

import java.awt.Color;

import javax.swing.JLabel;

import xml_ui.attributes.CreateComponentAttribute;
import xml_ui.attributes.SetterAttribute;

/**
 * Converts an XML {@code Label} component into a {@link javax.swing.JLabel} component.
 */
public class Label
{
    private Label(){}

    @CreateComponentAttribute
    public static JLabel Create()
    {
        return new JLabel();
    }

    @SetterAttribute("Text")
    public static void SetText(JLabel label, String text)
    {
        label.setText(text);
    }

    @SetterAttribute("Background")
    public static void SetBackground(JLabel label, String colour)
    {
        label.setOpaque(true);
        label.setBackground(Color.decode(colour));
    }

    @SetterAttribute("Foreground")
    public static void SetForeground(JLabel label, String colour)
    {
        label.setForeground(Color.decode(colour));
    }
}
