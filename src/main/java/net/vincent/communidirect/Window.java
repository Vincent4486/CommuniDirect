package net.vincent.communidirect;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Main application window for CommuniDirect.
 *
 * <p>The layout is split into two halves:
 * <ul>
 *   <li><b>Server panel</b> (left) — displays incoming messages
 *       and provides a {@link Command} input field</li>
 *   <li><b>Client panel</b> (right) — form for sending messages
 *       to a specified IP and port</li>
 * </ul>
 *
 * @author Vincent
 * @version 1.1
 */
public class Window extends JFrame {

    /** Reference to the main application instance. */
    private final CommuniDirect communiDirect;

    /** Text area that displays server log messages. */
    private final JTextArea serverArea;

    /**
     * Builds and shows the main window.
     *
     * @param communiDirect the main application instance
     */
    public Window(CommuniDirect communiDirect) {
        this.communiDirect = communiDirect;

        // --- Server panel ---
        serverArea = new JTextArea();
        serverArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(serverArea);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createLoweredBevelBorder());

        JPanel serverPanel = new JPanel(new BorderLayout());
        serverPanel.add(scrollPane, BorderLayout.CENTER);
        serverPanel.add(new Command(communiDirect), BorderLayout.SOUTH);

        // --- Client panel ---
        JPanel clientPanel = buildClientPanel();

        // --- Frame setup ---
        setTitle("CommuniDirect");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1, 2));
        add(serverPanel);
        add(clientPanel);
        setVisible(true);
    }

    /**
     * Appends a line to the server log area.
     *
     * @param message the text to append
     */
    void appendLog(String message) {
        serverArea.append(message + "\n");
        serverArea.setCaretPosition(serverArea.getDocument().getLength());
    }

    /**
     * Shows a small dialog asking the user to choose a port.
     * The selected port is passed to {@code onPortSelected}.
     *
     * @param onPortSelected callback invoked with the chosen port
     */
    void showPortDialog(Consumer<Integer> onPortSelected) {
        JFrame dialog = new JFrame("Choose Your Port");
        dialog.setLayout(new GridLayout(1, 3));

        JLabel label = new JLabel("Port to listen on:");
        JTextField field = new JTextField();
        JButton button = new JButton("Select");

        button.addActionListener(e -> {
            String text = field.getText().trim();
            int port = text.isEmpty()
                    ? CommuniDirect.DEFAULT_PORT
                    : communiDirect.parsePort(text, CommuniDirect.DEFAULT_PORT);
            dialog.dispose();
            onPortSelected.accept(port);
        });

        dialog.add(label);
        dialog.add(field);
        dialog.add(button);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------------
    //  Private helpers
    // ------------------------------------------------------------------

    /**
     * Builds the client-side form panel with message, IP, port fields
     * and a send button.
     */
    private JPanel buildClientPanel() {
        JLabel msgLabel  = new JLabel("Message");
        JLabel ipLabel   = new JLabel("IP");
        JLabel portLabel = new JLabel("Port");

        JTextField msgField  = new JTextField();
        JTextField ipField   = new JTextField();
        JTextField portField = new JTextField();

        msgField.setBorder(BorderFactory.createLoweredBevelBorder());
        ipField.setBorder(BorderFactory.createLoweredBevelBorder());
        portField.setBorder(BorderFactory.createLoweredBevelBorder());

        JButton sendButton = new JButton("Send");
        sendButton.setBorder(BorderFactory.createRaisedBevelBorder());

        sendButton.addActionListener(e -> {
            String message = msgField.getText().trim();
            String ip      = ipField.getText().trim();
            int port = communiDirect.parsePort(
                    portField.getText().trim(), CommuniDirect.DEFAULT_PORT);

            if (!message.isEmpty() && !ip.isEmpty()) {
                sendButton.setEnabled(false);
                new Thread(() -> {
                    communiDirect.sendMessage(ip, port, message);
                    SwingUtilities.invokeLater(() -> sendButton.setEnabled(true));
                }).start();
            } else {
                communiDirect.logClient("Please enter both an IP and a message.");
            }

            msgField.setText("");
            ipField.setText("");
            portField.setText("");
        });

        // Layout
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createLoweredBevelBorder());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(msgLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(msgField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(ipLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(portLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(sendButton, gbc);

        return panel;
    }
}
