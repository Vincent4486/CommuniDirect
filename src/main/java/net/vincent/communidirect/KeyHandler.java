package net.vincent.communidirect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Listens for action events on the {@link Command} text field
 * (e.g. the user pressing Enter) and delegates to the command
 * executor.
 *
 * @author Vincent
 * @version 1.1
 */
public class KeyHandler implements ActionListener {

    /** Reference to the main application instance. */
    private final CommuniDirect communiDirect;

    /**
     * Creates a handler linked to the given application.
     *
     * @param communiDirect the main application instance
     */
    public KeyHandler(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;
    }

    /**
     * Invoked when the user submits a command.
     * Extracts the text from the source {@link Command} field
     * and executes it.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Command source = (Command) e.getSource();
        source.execCommand(source.getText());
    }
}
