package net.vincent.communidirect;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * A text-field component for entering and executing console-style
 * commands within the CommuniDirect application.
 *
 * <p>Supported commands:
 * <ul>
 *   <li>{@code port --changedefault <value>} — change and save the default port</li>
 *   <li>{@code port --changecurrent <value>} — change the port and restart the server</li>
 *   <li>{@code send <ip> <port> <message>} — send a message to a remote server</li>
 *   <li>{@code quit} — exit the application</li>
 * </ul>
 *
 * @author Vincent
 * @version 1.1
 */
public class Command extends JTextField {

    /** Reference to the main application instance. */
    private final CommuniDirect communiDirect;

    /**
     * Creates a command input field wired to the given application.
     *
     * @param communiDirect the main application instance
     */
    public Command(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;

        setPreferredSize(new Dimension(250, 30));
        setToolTipText("Enter a command");
        setBorder(BorderFactory.createLoweredBevelBorder());
        addActionListener(new KeyHandler(communiDirect));
    }

    /**
     * Parses and executes the given command string.
     *
     * @param cmd the raw command text
     */
    void execCommand(String cmd) {
        String[] tokens = cmd.split(" ");
        if (tokens.length == 0) return;

        switch (tokens[0]) {
            case "port":
                handlePort(tokens);
                break;
            case "send":
                handleSend(tokens);
                break;
            case "quit":
                System.exit(0);
                break;
            default:
                communiDirect.logClient("Unknown command: " + tokens[0]);
                break;
        }
    }

    // ------------------------------------------------------------------
    //  Command handlers
    // ------------------------------------------------------------------

    /**
     * Handles the {@code port} command.
     *
     * <p>Flags:
     * <ul>
     *   <li>{@code --changedefault <port>} — update and persist the default port</li>
     *   <li>{@code --changecurrent <port>} — switch the running server to a new port</li>
     * </ul>
     *
     * @param tokens the tokenised command
     */
    private void handlePort(String[] tokens) {
        for (int i = 1; i < tokens.length; i++) {
            String flag = tokens[i].toLowerCase();

            switch (flag) {
                case "--changedefault":
                    if (i + 1 >= tokens.length) {
                        communiDirect.logClient("Missing port value after " + flag);
                        break;
                    }
                    try {
                        int port = Integer.parseInt(tokens[++i]);
                        CommuniDirect.port = port;
                        communiDirect.getPropertiesData().save();
                        communiDirect.logClient("Default port changed to " + port);
                    } catch (NumberFormatException e) {
                        communiDirect.logClient("Invalid port for " + flag + ": " + e.getMessage());
                    }
                    break;

                case "--changecurrent":
                    if (i + 1 >= tokens.length) {
                        communiDirect.logClient("Missing port value after " + flag);
                        break;
                    }
                    try {
                        int port = Integer.parseInt(tokens[++i]);
                        communiDirect.stopServer();
                        CommuniDirect.port = port;
                        communiDirect.logClient("Switching to port " + port);
                        new Thread(() -> communiDirect.startServer(port)).start();
                    } catch (NumberFormatException e) {
                        communiDirect.logClient("Invalid port for " + flag + ": " + e.getMessage());
                    }
                    break;

                default:
                    communiDirect.logClient("Unknown flag: " + flag);
                    break;
            }
        }
    }

    /**
     * Handles the {@code send} command.
     *
     * <p>Usage: {@code send <ip> <port> <message>}
     *
     * @param tokens the tokenised command
     */
    private void handleSend(String[] tokens) {
        if (tokens.length < 4) {
            communiDirect.logClient("Usage: send <ip> <port> <message>");
            communiDirect.logClient("You provided " + tokens.length
                    + " argument(s): " + Arrays.toString(tokens));
            return;
        }

        try {
            int port = Integer.parseInt(tokens[2]);
            communiDirect.sendMessage(tokens[1], port, tokens[3]);
            communiDirect.logClient("Sending message...");
        } catch (NumberFormatException e) {
            communiDirect.logClient("Port must be a number. Got: '" + tokens[2] + "'");
        } catch (Exception e) {
            communiDirect.logClient("Unexpected error: " + e.getMessage());
        }
    }
}
