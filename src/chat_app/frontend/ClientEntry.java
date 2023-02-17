package chat_app.frontend;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.plaf.InsetsUIResource;

import readiefur.misc.IDisposable;
import readiefur.xml_ui.Observable;
import readiefur.xml_ui.controls.Grid;
import readiefur.xml_ui.controls.Label;
import readiefur.xml_ui.controls.StackPanel;
import readiefur.xml_ui.exceptions.InvalidXMLException;

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
    public final Label ipLabel;
    public final Label unreadsLabel;

    private Boolean isDisposed = false;
    private int unreads = 0;

    public ClientEntry(String username, String ipAddress, Observable<String> backgroundColour, Observable<String> foregroundColour)
    {
        super();

        this.backgroundColour = backgroundColour;
        this.foregroundColour = foregroundColour;

        //#region Container
        //TODO: Get XML pages working and expose more generic setter methods on the Control classes.
        //The grid container is this object.
        setOpaque(true);
        setBackground(Color.decode(this.backgroundColour.Get()));
        backgroundColour.AddListener(newValue -> setBackground(Color.decode(newValue)));

        containerConstraints = new GridBagConstraints();
        containerConstraints.insets = new InsetsUIResource(4, 4, 2, 4);

        GridBagConstraints childConstraints = new GridBagConstraints();
        childConstraints.weightx = 1;
        childConstraints.weighty = 1;
        childConstraints.fill = GridBagConstraints.VERTICAL;
        childConstraints.insets = new InsetsUIResource(4, 4, 4, 4);
        //#endregion

        //#region Username
        usernameLabel = new Label();
        usernameLabel.setText(username);
        usernameLabel.setForeground(Color.decode(this.foregroundColour.Get()));
        this.foregroundColour.AddListener(newValue -> usernameLabel.setForeground(Color.decode(newValue)));
        childConstraints.anchor = GridBagConstraints.WEST;
        add(usernameLabel, childConstraints);
        //#endregion

        //#region Stats container
        StackPanel statsPanel = new StackPanel();
        try { statsPanel.SetOrientation(StackPanel.ORIENTATION_RIGHT_TO_LEFT); }
        catch (InvalidXMLException ex) { /*This won't be reached.*/ }
        childConstraints.anchor = GridBagConstraints.EAST;
        add(statsPanel, childConstraints);

        //IP label.
        ipLabel = new Label();
        ipLabel.setText("[ " + ipAddress + " ]");
        ipLabel.setForeground(Color.decode(this.foregroundColour.Get()));
        this.foregroundColour.AddListener(newValue -> ipLabel.setForeground(Color.decode(newValue)));
        statsPanel.AddChild(ipLabel, null);

        //Unreads label.
        unreadsLabel = new Label();
        unreadsLabel.setForeground(Color.decode("#FF0000"));
        statsPanel.AddChild(unreadsLabel, null);
        //#endregion

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
            this.foregroundColour.RemoveListener(newValue -> ipLabel.setForeground(Color.decode(newValue)));
        }
    }

    public void ShowHostControls(Boolean show)
    {
        ipLabel.setVisible(show);
    }

    public void IncrementUnreads()
    {
        unreads++;
        //Before I was using a margin to pad the left of this object, however when hidden the padding would still be present, so I will use spaces instead.
        unreadsLabel.setText("  " + String.valueOf(unreads));
        unreadsLabel.setVisible(true);
    }

    public void ClearUnreads()
    {
        unreads = 0;
        unreadsLabel.setVisible(false);
    }
}
