package readiefur.xml_ui.controls;

import java.awt.event.ActionEvent;
import java.util.function.Consumer;

import javax.swing.JCheckBox;

import readiefur.misc.Event;
import readiefur.xml_ui.attributes.EventAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;

public class CheckBox extends JCheckBox
{
    public final Event<ActionEvent> onClick = new Event<>();

    public CheckBox()
    {
        super();

        addActionListener(this::OnClick);
    }

    @SetterAttribute("Text")
    public void SetText(String text)
    {
        setText(text);
    }

    @SetterAttribute("ToolTip")
    public void SetToolTip(String toolTip)
    {
        setToolTipText(toolTip);
    }

    @SetterAttribute("Checked")
    public void SetChecked(String checked)
    {
        setSelected(Boolean.parseBoolean(checked));
    }

    @EventAttribute("Click")
    public void SetClick(Consumer<Object[]> callback)
    {
        onClick.Add(e -> callback.accept(new Object[]{ e }));
    }

    protected void OnClick(ActionEvent e) { onClick.Invoke(e); }
}
