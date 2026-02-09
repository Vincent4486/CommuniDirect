package net.vincent.communidirect;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main application class for CommuniDirect — a simple Swing-based
 * client-server messaging tool.
 *
 * <p>Provides the ability to start a TCP server, receive messages from
 * remote clients, and send messages to other servers. Configuration
 * (e.g. port number) is persisted between sessions via
 * {@link PropertiesData}.
 *
 * @author Vincent
 * @version 1.1
 */
public class CommuniDirect {

    /** Default port used when no saved configuration exists. */
    static final int DEFAULT_PORT = 2556;

    /** Maximum number of log lines retained in the buffer. */
    private static final int MAX_LOG_LINES = 20;

    /** Buffer for recent log messages. */
    private static final List<String> logBuffer = new ArrayList<>();

    /** Current port the server listens on. */
    static int port = DEFAULT_PORT;

    /** Server socket for accepting incoming connections. */
    private ServerSocket serverSocket;

    /** Whether the server loop is active. */
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);

    /** Manages persistent configuration properties. */
    private final PropertiesData propertiesData;

    /** Main application window. */
    private final Window window;

    /**
     * Initialises the application: loads config, builds the UI, and
     * starts the server on the configured (or user-chosen) port.
     */
    public CommuniDirect() {
        propertiesData = new PropertiesData(this);
        window = new Window(this);

        propertiesData.load();

        if (port == DEFAULT_PORT) {
            window.showPortDialog(p -> {
                port = p;
                new Thread(() -> startServer(port)).start();
                propertiesData.save();
            });
        } else {
            new Thread(() -> startServer(port)).start();
        }
    }

    /**
     * Launches the application on the Swing event-dispatch thread.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new CommuniDirect();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Failed to initialise application: " + e.getMessage());
            }
        });
    }

    /**
     * Parses a string as a port number, returning {@code fallback}
     * if the string is not a valid integer.
     *
     * @param input    the string to parse
     * @param fallback the value to return on failure
     * @return the parsed port or the fallback value
     */
    int parsePort(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            logServer("Invalid port input — using " + fallback);
            port = DEFAULT_PORT;
            return fallback;
        }
    }

    /**
     * Starts the TCP server on the given port. Accepts connections in
     * a loop, reading lines from each client and echoing an
     * acknowledgement.
     *
     * <p>If the port is already in use, the user is prompted to
     * choose a different one.
     *
     * @param port the port to bind to
     */
    void startServer(int port) {
        serverRunning.set(true);
        try {
            serverSocket = new ServerSocket(port);
            logServer("Listening on port " + port);

            while (serverRunning.get()) {
                try (Socket client = serverSocket.accept();
                     BufferedReader in = new BufferedReader(
                             new InputStreamReader(client.getInputStream()))) {

                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                    String address = client.getInetAddress().getHostAddress();
                    String line;

                    while ((line = in.readLine()) != null) {
                        logServer("Received from " + address + ": " + line);
                        out.println("Server received message");
                    }
                } catch (SocketException e) {
                    if (serverRunning.get()) {
                        logServer("Client socket closed unexpectedly.");
                    }
                } catch (IOException e) {
                    logServer("Error handling client: " + e.getMessage());
                }
            }
        } catch (BindException e) {
            logServer("Port " + port + " is already in use.");
            SwingUtilities.invokeLater(() ->
                    window.showPortDialog(newPort -> {
                        CommuniDirect.port = newPort;
                        new Thread(() -> startServer(CommuniDirect.port)).start();
                    }));
        } catch (IOException e) {
            logServer("Failed to start server: " + e.getMessage());
        }
    }

    /**
     * Stops the server and closes the socket.
     */
    void stopServer() {
        serverRunning.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logServer("Server stopped.");
            } catch (IOException e) {
                logServer("Error closing server: " + e.getMessage());
            }
        }
    }

    /**
     * Sends a single-line message to a remote server.
     *
     * @param ip      target IP address
     * @param port    target port
     * @param message the message to send
     */
    void sendMessage(String ip, int port, String message) {
        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000);

                try (BufferedReader in = new BufferedReader(
                             new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                    out.println(message);
                    String response = in.readLine();
                    logClient("Server replied: " + response);
                }

                socket.close();
            } catch (SocketTimeoutException e) {
                logClient("Connection to " + ip + ":" + port + " timed out.");
            } catch (UnknownHostException e) {
                logClient("Unknown host: " + ip);
            } catch (ConnectException e) {
                logClient("Connection refused by " + ip + ":" + port);
            } catch (IOException e) {
                logClient("Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Logs a server-related message.
     *
     * @param message the text to log
     */
    void logServer(String message) {
        log("[Server] " + message);
    }

    /**
     * Logs a client-related message.
     *
     * @param message the text to log
     */
    void logClient(String message) {
        log("[Client] " + message);
    }

    /**
     * Appends a message to the log buffer and updates the UI.
     * Duplicate consecutive messages are suppressed.
     *
     * @param message the text to log
     */
    private synchronized void log(String message) {
        if (!logBuffer.isEmpty()
                && logBuffer.get(logBuffer.size() - 1).equals(message)) {
            return;
        }

        if (logBuffer.size() >= MAX_LOG_LINES) {
            logBuffer.remove(0);
        }
        logBuffer.add(message);

        SwingUtilities.invokeLater(() -> {
            window.appendLog(message);
        });
    }

    /** Returns the properties data manager (package-private). */
    PropertiesData getPropertiesData() {
        return propertiesData;
    }
}
