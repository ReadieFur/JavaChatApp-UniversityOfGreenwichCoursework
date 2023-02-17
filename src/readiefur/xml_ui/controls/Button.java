package readiefur.xml_ui.controls;

import java.awt.event.ActionEvent;
import java.util.function.Consumer;

import javax.swing.JButton;

import readiefur.misc.Event;
import readiefur.xml_ui.attributes.EventAttribute;
import readiefur.xml_ui.attributes.SetterAttribute;

public class Button extends JButton
{
    public final Event<ActionEvent> onClick = new Event<>();

    public Button()
    {
        super();

        addActionListener(this::OnClick);
    }

    @SetterAttribute("Content")
    public void SetText(String text)
    {
        setText(text);
    }

    @SetterAttribute("ToolTip")
    public void SetToolTip(String toolTip)
    {
        setToolTipText(toolTip);
    }

    @EventAttribute("Click")
    public void SetClick(Consumer<Object[]> callback)
    {
        onClick.Add(e -> callback.accept(new Object[]{ e }));
    }

    protected void OnClick(ActionEvent e) { onClick.Invoke(e); }
}
