package net.vincent.CommuniDirect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * {@code KeyHandler} is an {@link ActionListener} implementation that listens for
 * command input events from the {@link Command} text field in the CommuniDirect application.
 * <p>
 * When a user presses Enter in the command field, this handler triggers the execution
 * of the entered command by calling {@code execCommand()} on the {@code Command} instance.
 *
 * @author Vincent
 * @version 1.0
 */
public class KeyHandler implements ActionListener {

    /** Reference to the main CommuniDirect application instance. */
    CommuniDirect communiDirect;

    /**
     * Constructs a {@code KeyHandler} and associates it with the given CommuniDirect instance.
     *
     * @param communiDirect The main application instance to interact with.
     */
    public KeyHandler(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;
    }

    /**
     * Called when an action event is triggered (e.g., user presses Enter in the command field).
     * Executes the command entered in the {@link Command} text field.
     *
     * @param e The action event triggered by the user.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Command src = (Command) e.getSource();
        src.execCommand(src.getText());
    }
}