package net.vincent.communidirect.client.gui;

import net.vincent.communidirect.client.core.ClientCore;
import net.vincent.communidirect.client.core.ClientWebhookListener;
import net.vincent.communidirect.client.util.StagedMessage;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GuiClient {

    private final ClientCore core = new ClientCore();
    private final List<Path> stagedFiles = new ArrayList<>();
    private final List<Path> receivedFiles = new ArrayList<>();

    private DefaultListModel<String> stagedModel;
    private DefaultListModel<String> receivedModel;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JFrame frame;
    private JList<String> stagedList;
    private JList<String> receivedList;
    private SettingsManager settings;
    private KeyStoreManager keyStore;
    private ClientWebhookListener webhookListener;

    public void run(SettingsManager settings, KeyStoreManager keyStore) {
        this.settings = settings;
        this.keyStore = keyStore;

        SwingUtilities.invokeLater(() -> {
            this.frame = new JFrame("CommuniDirect GUI");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(980, 620);
            frame.setLayout(new BorderLayout(10, 10));

            JPanel headerPanel = new JPanel(new BorderLayout());
            JLabel title = new JLabel("CommuniDirect Client", SwingConstants.LEFT);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
            headerPanel.add(title, BorderLayout.WEST);

            String localId = ClientCore.deriveLocalId(keyStore.getOwnPublicKeyRaw());
            JLabel idLabel = new JLabel("ID: " + localId + "   Server: " + settings.getIp() + ":" + settings.getPort());
            headerPanel.add(idLabel, BorderLayout.EAST);

            frame.add(headerPanel, BorderLayout.NORTH);
            frame.add(buildMainPanel(), BorderLayout.CENTER);
            frame.add(buildFooterPanel(), BorderLayout.SOUTH);

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (webhookListener != null) {
                        webhookListener.close();
                    }
                }
            });

            initializeAndRefresh();
            startWebhookListener();
        });
    }

    private JComponent buildMainPanel() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        stagedModel = new DefaultListModel<>();
        stagedList = new JList<>(stagedModel);
        stagedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        receivedModel = new DefaultListModel<>();
        receivedList = new JList<>(receivedModel);
        receivedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel stagedPanel = new JPanel(new BorderLayout(8, 8));
        stagedPanel.setBorder(BorderFactory.createTitledBorder("Staged"));
        stagedPanel.add(new JScrollPane(stagedList), BorderLayout.CENTER);
        stagedPanel.add(buildStagedButtons(), BorderLayout.SOUTH);

        JPanel receivedPanel = new JPanel(new BorderLayout(8, 8));
        receivedPanel.setBorder(BorderFactory.createTitledBorder("Received"));
        receivedPanel.add(new JScrollPane(receivedList), BorderLayout.CENTER);
        receivedPanel.add(buildReceivedButtons(), BorderLayout.SOUTH);

        splitPane.setLeftComponent(stagedPanel);
        splitPane.setRightComponent(receivedPanel);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.add(splitPane, BorderLayout.CENTER);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setRows(8);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        mainPanel.add(logScroll, BorderLayout.SOUTH);

        return mainPanel;
    }

    private JComponent buildStagedButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton newBtn = new JButton("New Message");
        JButton sendBtn = new JButton("Send Selected");
        JButton sendAllBtn = new JButton("Send All");
        JButton refreshBtn = new JButton("Reload");

        newBtn.addActionListener(e -> openComposeEditorFrame());

        sendBtn.addActionListener(e -> sendSelectedStaged());
        sendAllBtn.addActionListener(e -> {
            ClientCore.Result result = core.sendAllStaged(keyStore);
            log(result.message());
            refreshLists();
        });
        refreshBtn.addActionListener(e -> refreshLists());

        panel.add(newBtn);
        panel.add(sendBtn);
        panel.add(sendAllBtn);
        panel.add(refreshBtn);
        return panel;
    }

    private void openComposeEditorFrame() {
        JFrame editor = new JFrame("Compose Message");
        editor.setSize(780, 560);
        editor.setLocationRelativeTo(frame);
        editor.setLayout(new BorderLayout(10, 10));

        JTextField targetIpField = new JTextField(settings.getIp(), 24);
        JTextField portField = new JTextField(String.valueOf(settings.getPort()), 8);
        JTextField keyNameField = new JTextField(24);
        JTextArea bodyArea = new JTextArea(18, 60);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);

        List<String> peerAliases = new ArrayList<>(keyStore.getAllPeerKeys().keySet());
        peerAliases.sort(String::compareTo);
        JComboBox<String> peerSelect = new JComboBox<>(peerAliases.toArray(new String[0]));
        if (!peerAliases.isEmpty()) {
            keyNameField.setText(peerAliases.getFirst());
        }
        peerSelect.addActionListener(e -> {
            Object selected = peerSelect.getSelectedItem();
            if (selected != null) {
                keyNameField.setText(selected.toString());
            }
        });

        List<String> senderAliases = keyStore.getAllPrivateKeyAliases();
        JComboBox<String> senderSelect = new JComboBox<>(senderAliases.toArray(new String[0]));
        senderSelect.setSelectedItem(keyStore.getActivePrivateKeyAlias());

        JPanel headerForm = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        headerForm.add(new JLabel("Target IP:"), gbc);
        gbc.gridx = 1;
        headerForm.add(targetIpField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        headerForm.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        headerForm.add(portField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        headerForm.add(new JLabel("Peer alias:"), gbc);
        gbc.gridx = 1;
        headerForm.add(keyNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        headerForm.add(new JLabel("Known peers:"), gbc);
        gbc.gridx = 1;
        headerForm.add(peerSelect, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        headerForm.add(new JLabel("Sender key:"), gbc);
        gbc.gridx = 1;
        headerForm.add(senderSelect, gbc);

        JPanel top = new JPanel(new BorderLayout());
        top.add(headerForm, BorderLayout.CENTER);
        editor.add(top, BorderLayout.NORTH);

        JPanel bodyPanel = new JPanel(new BorderLayout(6, 6));
        bodyPanel.add(new JLabel("Message body:"), BorderLayout.NORTH);
        bodyPanel.add(new JScrollPane(bodyArea), BorderLayout.CENTER);
        editor.add(bodyPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton stageButton = new JButton("Stage Message");
        JButton cancelButton = new JButton("Cancel");
        actions.add(cancelButton);
        actions.add(stageButton);
        editor.add(actions, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> editor.dispose());
        stageButton.addActionListener(e -> {
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(editor, "Invalid port value.", "Invalid Input", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Object sender = senderSelect.getSelectedItem();
            if (sender != null && !keyStore.setActivePrivateKey(sender.toString())) {
                JOptionPane.showMessageDialog(editor, "Could not activate sender key.", "Key Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            StagedMessage staged = new StagedMessage(
                targetIpField.getText().trim(),
                port,
                keyNameField.getText().trim(),
                bodyArea.getText()
            );
            ClientCore.Result result = core.stageMessage(staged);
            log(result.message());
            refreshLists();
            if (result.ok()) {
                editor.dispose();
            }
        });

        editor.setVisible(true);
    }

    private JComponent buildReceivedButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton openBtn = new JButton("Open Selected");
        JButton refreshBtn = new JButton("Reload");

        openBtn.addActionListener(e -> openSelectedReceived());
        refreshBtn.addActionListener(e -> refreshLists());

        panel.add(openBtn);
        panel.add(refreshBtn);
        return panel;
    }

    private JComponent buildFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready.");
        footer.add(statusLabel, BorderLayout.WEST);
        return footer;
    }

    private void initializeAndRefresh() {
        try {
            core.ensureDirectories();
            refreshLists();
            log("GUI ready.");
        } catch (Exception e) {
            log("Initialization failed: " + e.getMessage());
        }
    }

    private void refreshLists() {
        try {
            int selectedStaged = stagedList.getSelectedIndex();
            int selectedReceived = receivedList.getSelectedIndex();

            stagedFiles.clear();
            stagedFiles.addAll(core.listStagedFiles());
            receivedFiles.clear();
            receivedFiles.addAll(core.listReceivedFiles());

            stagedModel.clear();
            for (Path p : stagedFiles) {
                stagedModel.addElement(p.getFileName().toString());
            }

            receivedModel.clear();
            for (Path p : receivedFiles) {
                receivedModel.addElement(p.getFileName().toString());
            }

            if (!stagedFiles.isEmpty()) {
                stagedList.setSelectedIndex(Math.max(0, Math.min(selectedStaged, stagedFiles.size() - 1)));
            }
            if (!receivedFiles.isEmpty()) {
                receivedList.setSelectedIndex(Math.max(0, Math.min(selectedReceived, receivedFiles.size() - 1)));
            }

            statusLabel.setText("Staged=" + stagedFiles.size() + " | Received=" + receivedFiles.size());
        } catch (Exception e) {
            log("Refresh failed: " + e.getMessage());
        }
    }

    private void sendSelectedStaged() {
        int index = stagedList.getSelectedIndex();
        if (index < 0 || index >= stagedFiles.size()) {
            log("Select a staged message first.");
            return;
        }

        Path file = stagedFiles.get(index);
        ClientCore.Result result = core.sendStagedFile(file, keyStore);
        log(result.message());
        refreshLists();
    }

    private void openSelectedReceived() {
        int index = receivedList.getSelectedIndex();
        if (index < 0 || index >= receivedFiles.size()) {
            log("Select a received message first.");
            return;
        }

        Path file = receivedFiles.get(index);
        try {
            String content = core.readReceivedFile(file);
            JTextArea area = new JTextArea(content);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JScrollPane pane = new JScrollPane(area);
            pane.setPreferredSize(new Dimension(760, 480));
            JOptionPane.showMessageDialog(
                frame,
                pane,
                file.getFileName().toString(),
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception e) {
            log("Failed to open message: " + e.getMessage());
        }
    }

    private void startWebhookListener() {
        webhookListener = new ClientWebhookListener(
            payload -> SwingUtilities.invokeLater(() -> {
                String summary = payload.isBlank() ? "New message notification received." : payload.trim();
                log("Webhook: " + summary);
                refreshLists();
            }),
            error -> SwingUtilities.invokeLater(() -> log("Webhook error: " + error))
        );
        boolean started = webhookListener.start();
        if (started) {
            log("Webhook listener started on localhost.");
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }
}
