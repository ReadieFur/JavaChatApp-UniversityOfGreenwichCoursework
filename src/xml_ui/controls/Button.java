package xml_ui.controls;

import java.awt.Color;
import java.util.function.Consumer;

import javax.swing.JButton;

import xml_ui.attributes.CreateComponentAttribute;
import xml_ui.attributes.EventAttribute;
import xml_ui.attributes.SetterAttribute;

/**
 * Converts an XML {@code Button} component into a {@link javax.swing.JButton} component.
 */
public class Button
{
    private Button(){}

    @CreateComponentAttribute
    public static JButton Create()
    {
        return new JButton();
    }

    @SetterAttribute("Content")
    public static void SetText(JButton button, String text)
    {
        button.setText(text);
    }

    @SetterAttribute("Enabled")
    public static void SetEnabled(JButton button, String enabled)
    {
        button.setEnabled(Boolean.parseBoolean(enabled));
    }

    @SetterAttribute("ToolTip")
    public static void SetToolTip(JButton button, String toolTip)
    {
        button.setToolTipText(toolTip);
    }

    @SetterAttribute("Background")
    public static void SetBackground(JButton button, String colour)
    {
        button.setBackground(Color.decode(colour));
    }

    @SetterAttribute("Foreground")
    public static void SetForeground(JButton button, String colour)
    {
        button.setForeground(Color.decode(colour));
    }

    @EventAttribute("Click")
    public static void SetClick(JButton button, Consumer<Object[]> callback)
    {
        button.addActionListener(e -> callback.accept(new Object[]{}));
    }
}
