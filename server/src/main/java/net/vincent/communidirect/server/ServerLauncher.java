package net.vincent.communidirect.server;

import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;

/**
 * Application entry point and dependency-injection root for the CommuniDirect server.
 *
 * <p>The constructor wires all components together:
 * <ol>
 *   <li>{@link SettingsManager} loads (or creates) {@code ~/.communidirect/config.toml}.</li>
 *   <li>{@link KeyStoreManager} loads the local Ed25519 identity and trusted peer keys.</li>
 *   <li>{@link ServerSocket}, {@link ClientHandler} and {@link AccessLog} are instantiated
 *       and held as public fields so that sibling components can cross-reference them.</li>
 * </ol>
 * Call {@link #init()} to complete binding and start accepting connections.
 */
public class ServerLauncher {
    /** TCP server socket abstraction used by {@link ClientHandler} to accept connections. */
    public ServerSocket     serverSocket;

    /** Handles per-connection decoding and message persistence. */
    public ClientHandler    clientHandler;

    /** Records inbound connection events to the configured access log file. */
    public AccessLog        accessLog;

    /** Provides runtime configuration (port, IP, log paths). */
    public SettingsManager  settings;

    /** Holds the local Ed25519 identity and all trusted peer public keys. */
    public KeyStoreManager  keyStore;

    /**
     * Constructs and wires all server components.  Configuration and key
     * loading happen synchronously during construction; any key-store failure
     * is printed to stderr but does not abort startup.
     */
    public ServerLauncher() {
        settings = new SettingsManager();
        settings.load();

        keyStore = new KeyStoreManager();
        try {
            keyStore.load();
        } catch (Exception e) {
            System.err.println("[ServerLauncher] Failed to load key store: " + e.getMessage());
            e.printStackTrace();
        }

        serverSocket  = new ServerSocket(this);
        clientHandler = new ClientHandler(this);
        accessLog     = new AccessLog(this);
    }

    /**
     * Initialises all components and blocks on the accept loop.
     *
     * <p>Execution order:
     * <ol>
     *   <li>Configures the access log file path from settings.</li>
     *   <li>Binds the server socket to the configured IP and port.</li>
     *   <li>Starts the blocking {@link ClientHandler} accept loop.</li>
     * </ol>
     * This method does not return under normal operation.
     */
    public void init() {
        String logFile = settings.getLogDir() + "/" + settings.getAccessLogName();
        accessLog.init(logFile);
        serverSocket.init(settings.getIp(), settings.getPort());
        clientHandler.init(serverSocket.serverSocket);
    }

    /**
     * Application entry point.  Constructs a {@code ServerLauncher} and calls
     * {@link #init()} which blocks indefinitely while accepting connections.
     *
     * @param args command-line arguments (currently unused)
     */
    public static void main(String[] args) {
        ServerLauncher serverLauncher = new ServerLauncher();
        serverLauncher.init();
    }
}
