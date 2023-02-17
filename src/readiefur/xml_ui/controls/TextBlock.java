package readiefur.xml_ui.controls;

import javax.swing.JTextArea;

import readiefur.xml_ui.attributes.SetterAttribute;

public class TextBlock extends JTextArea
{
    public TextBlock()
    {
        super();
    }

    @SetterAttribute("Content")
    public void SetContent(String value)
    {
        //Replace all newline breaks with new lines.
        value = value.replace("\\n", "\n");
        setText(value);
    }

    @SetterAttribute("Wrap")
    public void SetWrap(String value)
    {
        setLineWrap(Boolean.parseBoolean(value));
    }

    @SetterAttribute("IsReadOnly")
    public void SetIsReadOnly(String value)
    {
        setEditable(!Boolean.parseBoolean(value));
    }
}
