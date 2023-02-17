package readiefur.xml_ui.controls;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JTextField;

import readiefur.misc.Event;
import readiefur.xml_ui.attributes.SetterAttribute;

public class TextBox extends JTextField
{
    public final Event<KeyEvent> onKeyTyped = new Event<>();
    public final Event<KeyEvent> onKeyPressed = new Event<>();
    public final Event<KeyEvent> onKeyReleased = new Event<>();

    public TextBox()
    {
        super();

        addKeyListener(new KeyListener()
        {
            @Override
            public void keyTyped(KeyEvent e) { OnKeyTyped(e); }

            @Override
            public void keyPressed(KeyEvent e) { OnKeyPressed(e); }

            @Override
            public void keyReleased(KeyEvent e) { OnKeyReleased(e); }
        });
    }

    @SetterAttribute("Value")
    public void SetValue(String value)
    {
        setText(value);
    }

    protected void OnKeyTyped(KeyEvent e) { onKeyTyped.Invoke(e); }

    protected void OnKeyPressed(KeyEvent e) { onKeyPressed.Invoke(e); }

    protected void OnKeyReleased(KeyEvent e) { onKeyReleased.Invoke(e); }
}
