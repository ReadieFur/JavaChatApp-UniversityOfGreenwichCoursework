package chat_app.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.plaf.InsetsUIResource;

import readiefur.misc.IDisposable;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.controls.Grid;
import readiefur.xml_ui.controls.Label;

/**
 * This class is used to contain and expose elements for a ClientEntry.
 * This class only creates the elements, external modifications will have to be made, e.g. addition to a parent.
 */
public class ClientEntry extends Grid implements IDisposable
{
    private final Observable<String> backgroundColour;
    private final Observable<String> foregroundColour;

    public final GridBagConstraints containerConstraints;
    public final Label usernameLabel;
    public final Label statusLabel;

    private Boolean isDisposed = false;
    private Boolean showHostControls = false;

    public ClientEntry(String username, String status, Observable<String> backgroundColour, Observable<String> foregroundColour)
    {
        super();

        this.backgroundColour = backgroundColour;
        this.foregroundColour = foregroundColour;

        //TODO: Get XML pages working and expose more generic setter methods on the Control classes.
        //The grid container is this object.
        setOpaque(true);
        setBackground(Color.decode(this.backgroundColour.Get()));
        backgroundColour.AddListener(newValue -> setBackground(Color.decode(newValue)));

        containerConstraints = new GridBagConstraints();
        containerConstraints.insets = new InsetsUIResource(4, 4, 2, 4);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.weightx = 1;
        labelConstraints.weighty = 1;
        labelConstraints.fill = GridBagConstraints.VERTICAL;
        labelConstraints.insets = new InsetsUIResource(4, 4, 4, 4);

        usernameLabel = new Label();
        usernameLabel.setText(username);
        usernameLabel.setForeground(Color.decode(this.foregroundColour.Get()));
        this.foregroundColour.AddListener(newValue -> usernameLabel.setForeground(Color.decode(newValue)));
        labelConstraints.anchor = GridBagConstraints.WEST;
        add(usernameLabel, labelConstraints);

        statusLabel = new Label();
        statusLabel.setText(status);
        statusLabel.setForeground(Color.decode(this.foregroundColour.Get()));
        this.foregroundColour.AddListener(newValue -> statusLabel.setForeground(Color.decode(newValue)));
        labelConstraints.anchor = GridBagConstraints.EAST;
        add(statusLabel, labelConstraints);

        //Hide the host controls by default.
        ShowHostControls(false);
    }

    public void Dispose()
    {
        synchronized (this)
        {
            if (isDisposed)
                return;
            isDisposed = true;

            //Unbind the events.
            this.backgroundColour.RemoveListener(newValue -> setBackground(Color.decode(newValue)));
            this.foregroundColour.RemoveListener(newValue -> usernameLabel.setForeground(Color.decode(newValue)));
            this.foregroundColour.RemoveListener(newValue -> statusLabel.setForeground(Color.decode(newValue)));
        }
    }

    public void ShowHostControls(Boolean show)
    {
        showHostControls = show;
        statusLabel.setVisible(show);
    }
}
