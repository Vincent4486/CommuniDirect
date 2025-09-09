package net.vincent.CommuniDirect;

import javax.swing.*;
import java.awt.*;

/**
 * The {@code Command} class is a custom text field component that allows users to enter
 * and execute textual commands within the CommuniDirect application.
 * <p>
 * Supported commands include:
 * <ul>
 *   <li><b>port --changedefault [value]</b>: Changes the default port and saves it to properties</li>
 *   <li><b>port --changecurrent [value]</b>: Changes the current port and restarts the server</li>
 *   <li><b>quit</b>: Exits the application</li>
 * </ul>
 *
 * <p>This class integrates with {@link KeyHandler} to handle input events and interacts
 * with the {@link CommuniDirect} instance to perform command actions.
 *
 * @author Vincent
 * @version 1.0
 */
public class Command extends JTextField {

    /** Reference to the main CommuniDirect application instance. */
    CommuniDirect communiDirect;

    /**
     * Constructs a {@code Command} input field and sets up its appearance and behavior.
     *
     * @param communiDirect The main application instance to interact with.
     */
    public Command(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;

        setPreferredSize(new Dimension(250, 30));
        setToolTipText("Enter your command");
        setBorder(BorderFactory.createLoweredBevelBorder());
        addActionListener(new KeyHandler(communiDirect));
    }

    /**
     * Executes a command string entered by the user.
     * Delegates to specific command handlers based on the first token.
     *
     * @param cmd The raw command string.
     */
    public void execCommand(String cmd) {
        String[] commandArray = tokenize(cmd);

        switch (commandArray[0]) {
            case "port":
                portCMD(commandArray);
                break;
            case "quit":
                System.exit(0);
                break;
        }
    }

    /**
     * Tokenizes a command string into an array of arguments.
     *
     * @param cmd The raw command string.
     * @return An array of command tokens.
     */
    public String[] tokenize(String cmd) {
        return cmd.split(" ");
    }

    /**
     * Handles the {@code port} command and its flags.
     * <p>Supported flags:
     * <ul>
     *   <li>{@code --changedefault [port]}: Updates the default port and saves it</li>
     *   <li>{@code --changecurrent [port]}: Changes the current port and restarts the server</li>
     * </ul>
     *
     * @param cmd The tokenized command array.
     */
    public void portCMD(String[] cmd) {
        for (int i = 1; i < cmd.length; i++) {
            String flag = cmd[i].toLowerCase();

            switch (flag) {
                case "--changedefault":
                    if (i + 1 >= cmd.length) {
                        communiDirect.logClient("Missing port value after " + flag);
                        break;
                    }
                    try {
                        int port = Integer.parseInt(cmd[++i]);
                        CommuniDirect.port_ = port;
                        communiDirect.propertiesData.save();
                        communiDirect.logClient("Successfully changed default port to " + port);
                    } catch (NumberFormatException e) {
                        communiDirect.logClient("Invalid port for " + flag + ": " + e.getMessage());
                    }
                    break;

                case "--changecurrent":
                    if (i + 1 >= cmd.length) {
                        communiDirect.logClient("Missing port value after " + flag);
                        break;
                    }
                    try {
                        int port = Integer.parseInt(cmd[++i]);
                        communiDirect.stopServer();
                        CommuniDirect.port_ = port;
                        communiDirect.logClient("Changing current port to " + port);
                        new Thread(() -> communiDirect.startServer(port)).start();
                    } catch (NumberFormatException e) {
                        communiDirect.logClient("Invalid port for " + flag + ": " + e.getMessage());
                    }
                    break;

                default:
                    // Unrecognized flag
                    break;
            }
        }
    }
}