package net.vincent.communidirect.server;

import net.vincent.communidirect.common.hook.LocalWebhookConfig;
import net.vincent.communidirect.common.proto.CdirMessage;
import net.vincent.communidirect.common.crypto.CryptoEngine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles incoming CDIR connections: accepts each socket, delegates to
 * {@link net.vincent.communidirect.common.proto.CdirMessage#decode} for protocol
 * parsing and signature verification, then persists the plaintext payload to
 * {@code ~/.communidirect/msg/YYYYMMDD_HHMMSS_IP.msg}.
 */
public class ClientHandler {
    /** The owning launcher, used to reach the shared {@link AccessLog} and {@link KeyStoreManager}. */
    ServerLauncher        serverLauncher;

    /** The bound server socket passed in from {@link ServerLauncher#init()}. */
    java.net.ServerSocket serverSocket;

    private static final String MSG_DIR =
        System.getProperty("user.home") + "/.communidirect/msg";

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Constructs a {@code ClientHandler} and ensures the message storage directory exists.
     *
     * @param serverLauncher the owning server launcher
     */
    public ClientHandler(ServerLauncher serverLauncher) {
        this.serverLauncher = serverLauncher;
        ensureMsgDirectory();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Stores the server socket reference, then enters the blocking accept loop.
     * This method does not return under normal operation.
     *
     * @param serverSocket the bound server socket to accept connections from
     */
    public void init(java.net.ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        listenLoop();
    }

    // -------------------------------------------------------------------------
    // Accept loop
    // -------------------------------------------------------------------------

    /**
     * Runs an infinite accept loop, logging and dispatching each inbound connection
     * to {@link #handleClient(Socket)}.
     *
     * @throws RuntimeException wrapping any {@link IOException} from {@code accept()}
     */
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

    /**
     * Processes a single accepted connection: decodes the CDIR frame from the socket
     * input stream and stores the verified plaintext message on disk.
     * Errors are printed to stderr and do not terminate the accept loop.
     *
     * @param clientSocket the accepted socket (closed implicitly via try-with-resources)
     */
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
     *   SENDER_PUBKEY_HASH:
     *   &lt;5x5 ASCII avatar&gt;
     *   ------------------------------
     *
     *   &lt;plaintext payload&gt;
     * </pre>
     */
    private void saveMessage(CdirMessage msg, String remoteIp) {
        String timestamp = TS_FMT.format(LocalDateTime.now());
        String filename  = timestamp + "_" + remoteIp + ".msg";
        Path   outPath   = Paths.get(MSG_DIR, filename);

        String content = buildSavedMessageContent(msg);

        try {
            Files.writeString(outPath, content);
            System.out.println("[ClientHandler] Message saved: " + outPath);
            notifyLocalWebhook(filename, remoteIp);
        } catch (IOException e) {
            System.err.println("[ClientHandler] Failed to save message: " + e.getMessage());
        }
    }

    private static String buildSavedMessageContent(CdirMessage msg) {
        String avatar = buildAvatarFromHash(msg.senderPubKeyHash);
        String messageBody = new String(msg.payload, StandardCharsets.UTF_8);
        return "SENDER_PUBKEY_HASH:\n" +
            avatar + "\n" +
            "------------------------------\n" +
            messageBody;
    }

    private static String buildAvatarFromHash(String hashHex) {
        try {
            byte[] hashBytes = hexToBytes(hashHex);
            if (hashBytes.length == 0) {
                return "Unavailable";
            }
            return CryptoEngine.getSymmetricAvatar(hashBytes);
        } catch (Exception ignored) {
            return "Unavailable";
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        String clean = hex.trim();
        if ((clean.length() % 2) != 0) {
            throw new IllegalArgumentException("Hex string must have even length.");
        }

        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < clean.length(); i += 2) {
            int hi = Character.digit(clean.charAt(i), 16);
            int lo = Character.digit(clean.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character.");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Creates {@value #MSG_DIR} (and all ancestor directories) if they do not yet exist.
     */
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

    private void notifyLocalWebhook(String filename, String remoteIp) {
        String payload = "new_message file=" + filename + " from=" + remoteIp;
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);

        try (Socket socket = new Socket(LocalWebhookConfig.HOST, LocalWebhookConfig.PORT)) {
            socket.setSoTimeout(1_000);
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            writer.write("POST " + LocalWebhookConfig.PATH + " HTTP/1.1\r\n");
            writer.write("Host: " + LocalWebhookConfig.HOST + ":" + LocalWebhookConfig.PORT + "\r\n");
            writer.write("Content-Type: text/plain; charset=utf-8\r\n");
            writer.write("Content-Length: " + body.length + "\r\n");
            writer.write("Connection: close\r\n\r\n");
            writer.write(payload);
            writer.flush();
        } catch (Exception ignored) {
            // Client webhook listener is optional; ignore if not running.
        }
    }
}
