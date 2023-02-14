package xml_ui.controls;

import java.awt.Color;

import javax.swing.JTextArea;

import xml_ui.attributes.CreateComponentAttribute;
import xml_ui.attributes.SetterAttribute;

/**
 * Converts an XML {@code TextBlock} component into a {@link javax.swing.JTextArea} component.
 */
public class TextBlock
{
    private TextBlock(){}

    @CreateComponentAttribute
    public static JTextArea Create()
    {
        return new JTextArea();
    }

    @SetterAttribute("Background")
    public static void SetBackground(JTextArea textBlock, String colour)
    {
        textBlock.setBackground(Color.decode(colour));
    }

    @SetterAttribute("Foreground")
    public static void SetForeground(JTextArea textBlock, String colour)
    {
        textBlock.setForeground(Color.decode(colour));
    }

    @SetterAttribute("Content")
    public static void SetContent(JTextArea textBlock, String value)
    {
        //Replace all newline breaks with new lines.
        value = value.replace("\\n", "\n");
        textBlock.setText(value);
    }

    @SetterAttribute("Enabled")
    public static void SetEnabled(JTextArea textBlock, String value)
    {
        textBlock.setEnabled(Boolean.parseBoolean(value));
    }

    @SetterAttribute("Wrap")
    public static void SetWrap(JTextArea textBlock, String value)
    {
        textBlock.setLineWrap(Boolean.parseBoolean(value));
    }

    @SetterAttribute("IsReadOnly")
    public static void SetIsReadOnly(JTextArea textBlock, String value)
    {
        textBlock.setEditable(!Boolean.parseBoolean(value));
    }
}
