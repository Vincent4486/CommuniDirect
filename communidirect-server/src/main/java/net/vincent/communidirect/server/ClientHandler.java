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
}
