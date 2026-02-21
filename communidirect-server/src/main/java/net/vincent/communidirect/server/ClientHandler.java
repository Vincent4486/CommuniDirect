package net.vincent.communidirect.server;

import net.vincent.communidirect.common.proto.CdirMessage;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientHandler {
    ServerLauncher        serverLauncher;
    java.net.ServerSocket serverSocket;

    private static final String MSG_DIR =
        System.getProperty("user.home") + "/.communidirect/msg";

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public ClientHandler(ServerLauncher serverLauncher) {
        this.serverLauncher = serverLauncher;
        ensureMsgDirectory();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void init(java.net.ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        listenLoop();
    }

    // -------------------------------------------------------------------------
    // Accept loop
    // -------------------------------------------------------------------------

    private void listenLoop() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                serverLauncher.accessLog.logAccess(clientSocket);
                handleClient(clientSocket);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Per-connection handling
    // -------------------------------------------------------------------------

    private void handleClient(Socket clientSocket) {
        String remoteIp = sanitiseIp(clientSocket.getInetAddress());
        try (InputStream in = clientSocket.getInputStream()) {

            CdirMessage msg = CdirMessage.decode(
                in,
                serverLauncher.keyStore.getPrivateKey(),
                serverLauncher.keyStore.getOwnPublicKeyRaw()
            );

            saveMessage(msg, remoteIp);

        } catch (Exception e) {
            System.err.println("[ClientHandler] Failed to process message from " +
                               remoteIp + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Message persistence
    // -------------------------------------------------------------------------

    /**
     * Writes a decoded message to
     * {@code ~/.communidirect/msg/YYYYMMDD_HHMMSS_IP.msg}.
     *
     * File format:
     * <pre>
     *   SENDER_PUBKEY_HASH: &lt;sha256-hex&gt;
     *
     *   &lt;plaintext payload&gt;
     * </pre>
     */
    private void saveMessage(CdirMessage msg, String remoteIp) {
        String timestamp = TS_FMT.format(LocalDateTime.now());
        String filename  = timestamp + "_" + remoteIp + ".msg";
        Path   outPath   = Paths.get(MSG_DIR, filename);

        String content =
            "SENDER_PUBKEY_HASH: " + msg.senderPubKeyHash + "\n\n" +
            new String(msg.payload, StandardCharsets.UTF_8);

        try {
            Files.writeString(outPath, content);
            System.out.println("[ClientHandler] Message saved: " + outPath);
        } catch (IOException e) {
            System.err.println("[ClientHandler] Failed to save message: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void ensureMsgDirectory() {
        try {
            Files.createDirectories(Paths.get(MSG_DIR));
        } catch (IOException e) {
            System.err.println("[ClientHandler] Failed to create msg dir: " + e.getMessage());
        }
    }

    /** Returns the IP string with the leading {@code /} stripped. */
    private static String sanitiseIp(InetAddress addr) {
        return addr.toString().replaceAll("^/", "").replace(':', '_');
    }
}
