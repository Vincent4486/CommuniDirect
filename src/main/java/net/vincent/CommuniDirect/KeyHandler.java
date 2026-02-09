package net.vincent.CommuniDirect;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * {@code KeyHandler} is an {@link ActionListener} implementation that listens for
 * command input events from the {@link Command} text field in the CommuniDirect application.
 * <p>
 * When a user presses Enter in the command field, this handler triggers the execution
 * of the entered command by calling {@code execCommand()} on the {@code Command} instance.
 * </p>
 * @author Vincent
 * @version 1.0
 */
public class KeyHandler implements ActionListener, KeyListener {

    /** Reference to the main CommuniDirect application instance. */
    CommuniDirect communiDirect;

    /**
     * Constructs a {@code KeyHandler} and associates it with the given CommuniDirect instance.
     *
     * @param communiDirect The main application instance to interact with.
     */
    @EntryPoint(EntryType.BACKGROUND)
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
        src.setText(null);
    }

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		switch(e.getKeyCode()) {
		case KeyEvent.VK_UP:
			
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}
}