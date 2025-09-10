package net.vincent.CommuniDirect;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * {@code Window} is the main graphical user interface for the CommuniDirect application.
 * It provides both server-side logging and client-side message sending capabilities.
 *
 * <p>The window is split into two panels:
 * <ul>
 *   <li><b>Server Panel</b>: Displays incoming messages and includes a command input field</li>
 *   <li><b>Client Panel</b>: Allows users to send messages to a specified IP and port</li>
 * </ul>
 *
 * <p>It also includes a dynamic port selection dialog via {@link #setPort(Consumer)}.
 *
 * @author Vincent
 * @version 1.0
 */
public class Window extends JFrame {

    /** Reference to the main CommuniDirect application instance. */
    CommuniDirect communiDirect;

    /** Text area for displaying server logs and messages. */
    JTextArea serverArea;

    /** Scroll pane wrapping the server log area. */
    JScrollPane serverScrollPane;

    /**
     * Constructs the main application window with server and client panels.
     *
     * @param communiDirect The main application instance.
     */
    @EntryPoint(EntryType.UI)
    public Window(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;

        // === SERVER AREA ===
        serverArea = new JTextArea();
        serverArea.setEditable(false);

        serverScrollPane = new JScrollPane(serverArea);
        serverScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        serverScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        serverScrollPane.setBorder(BorderFactory.createLoweredBevelBorder());

        JPanel server = new JPanel(new BorderLayout());
        server.add(serverScrollPane, BorderLayout.CENTER);
        server.add(new Command(communiDirect), BorderLayout.SOUTH);

        // === CLIENT FORM ===
        JLabel msgLabel = new JLabel("Message");
        JLabel ipLabel = new JLabel("IP");
        JLabel portLabel = new JLabel("Port");

        JTextField msgF = new JTextField();
        msgF.setBorder(BorderFactory.createLoweredBevelBorder());
        JTextField ipF = new JTextField();
        ipF.setBorder(BorderFactory.createLoweredBevelBorder());
        JTextField portF = new JTextField();
        portF.setBorder(BorderFactory.createLoweredBevelBorder());

        JButton sendButton = new JButton("Send");
        sendButton.setBorder(BorderFactory.createRaisedBevelBorder());

        // Send button action
        sendButton.addActionListener(e -> {
            String message = msgF.getText().trim();
            String ip = ipF.getText().trim();
            int port = communiDirect.parsePort(portF.getText().trim(), CommuniDirect.defaultPort);

            if (!message.isEmpty() && !ip.isEmpty()) {
                sendButton.setEnabled(false);
                new Thread(() -> {
                    communiDirect.sendMessage(ip, port, message);
                    SwingUtilities.invokeLater(() -> sendButton.setEnabled(true));
                }).start();
            } else {
                communiDirect.logClient("⚠ Please enter IP and message before sending.");
            }

            msgF.setText("");
            ipF.setText("");
            portF.setText("");
        });

        JPanel client = new JPanel(new GridBagLayout());
        client.setBorder(BorderFactory.createLoweredBevelBorder());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Layout rows
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.anchor = GridBagConstraints.EAST;
        client.add(msgLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        client.add(msgF, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        client.add(ipLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        client.add(ipF, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        client.add(portLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        client.add(portF, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 0; gbc.anchor = GridBagConstraints.CENTER;
        client.add(sendButton, gbc);

        // === MAIN FRAME ===
        this.setSize(500, 400);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLayout(new GridLayout(1, 2));
        this.setTitle("CommuniDirect");

        this.add(server);
        this.add(client);
        this.setVisible(true);
    }

    /**
     * Displays a dialog allowing the user to select a port number.
     * Once selected, the provided callback is invoked with the chosen port.
     *
     * @param onPortSelected A {@code Consumer<Integer>} that receives the selected port.
     */
    @EntryPoint(EntryType.NETWORK)
    public void setPort(Consumer<Integer> onPortSelected) {
        JFrame frame = new JFrame("Choose Your Port");
        frame.setLayout(new GridLayout(1, 3));

        JLabel label = new JLabel("Choose port to listen");
        JTextField field = new JTextField();
        JButton button = new JButton("Select");

        button.addActionListener(e -> {
            String portStr = field.getText();
            int port = portStr.isEmpty()
                    ? CommuniDirect.defaultPort
                    : communiDirect.parsePort(portStr, CommuniDirect.defaultPort);
            frame.dispose();
            onPortSelected.accept(port);
        });

        frame.add(label);
        frame.add(field);
        frame.add(button);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        frame.setVisible(true);
    }
}