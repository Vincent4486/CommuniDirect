package net.vincent.CommuniDirect;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * The {@code Command} class is a custom text field component that allows users to enter
 * and execute textual commands within the CommuniDirect application.
 * <p>
 * Supported commands include:
 * <ul>
 *   <li><b>port --changedefault [value]</b>: Changes the default port and saves it to properties</li>
 *   <li><b>port --changecurrent [value]</b>: Changes the current port and restarts the server</li>
 *   <li><b>send [ip] [port] [message]</b>: Sends a message the same as the GUI at the right</li>
 *   <li><b>quit</b>: Exits the application</li>
 * </ul>
 *
 * <p>This class integrates with {@link KeyHandler} to handle input events and interacts
 * with the {@link CommuniDirect} instance to perform command actions.
 *
 * @author Vincent
 * @version 1.0
 */
@SuppressWarnings("serial")
public class Command extends JTextField {

    /** Reference to the main CommuniDirect application instance. */
    CommuniDirect communiDirect;

    /**
     * Constructs a {@code Command} input field and sets up its appearance and behavior.
     *
     * @param communiDirect The main application instance to interact with.
     */
    @EntryPoint(EntryType.COMMAND)
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
    @EntryPoint(EntryType.COMMAND)
    protected void execCommand(String cmd) {
        String[] commandArray = tokenize(cmd);

        switch (commandArray[0]) {
            case "port":
                portCMD(commandArray);
                break;
            case "send":
                sendCMD(commandArray);
                break;
            case "quit":
                System.exit(0);
                break;
            case "exit":
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
    private String[] tokenize(String cmd) {
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
    private void portCMD(String[] cmd) {
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

    /**
     * Handles the {@code send} command and its flags.
     * <p>Required flags:
     * <ul>
     *   <li>{@code [ip]}: Sets the IP address of the target</li>
     *   <li>{@code [port]}: Sets the port to send to the target</li>
     *   <li>{@code [message]}: Sets the message to send to the target</li>
     * </ul>
     * @param cmd The tokenized command array.
     */
    private void sendCMD(String[] cmd){

        if (cmd.length < 4) {
            communiDirect.logClient("❌ Missing arguments for 'send' command.");
            communiDirect.logClient("✅ Usage: send <IP> <port> <msg>");
            communiDirect.logClient("📝 You provided " + cmd.length + " argument(s): " + Arrays.toString(cmd));
        } else {
            try {
                int port = Integer.parseInt(cmd[2]);
                communiDirect.sendMessage(cmd[1], port, cmd[3]);
                communiDirect.logClient("📤 Attempting to send message...");
            } catch (NumberFormatException e) {
                communiDirect.logClient("❌ Port must be a number. You entered: '" + cmd[2] + "'");
            } catch (Exception e) {
                communiDirect.logClient("⚠️ Unexpected error: " + e.getMessage());
            }

            // Continue running the program
            communiDirect.logClient("🔄 Ready for next command.");
        }
    }
}