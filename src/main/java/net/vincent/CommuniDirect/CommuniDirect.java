package net.vincent.CommuniDirect;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CommuniDirect is a simple Swing-based client-server communication tool.
 * It allows users to start a server, receive messages from clients, and send messages to a server.
 * Configuration is persisted via PropertiesData, and the UI is handled through the Window class.
 *
 * @author Vincent
 * @version 1.0
 */
public class CommuniDirect {

    /** Default port used if none is specified. */
    protected static final int defaultPort = 2556;

    /** Buffer for storing recent log messages. */
    protected static final List<String> logBuffer = new ArrayList<>();

    /** Maximum number of log lines to retain in the buffer. */
    protected static final int maxLogLines = 20;

    /** Current port used by the server. */
    protected static int port_ = defaultPort;

    /** Server socket instance for accepting client connections. */
    private ServerSocket serverSocket;

    /** Flag indicating whether the server is currently running. */
    private AtomicBoolean isServerRunning = new AtomicBoolean(false);

    /** Handles keyboard input events. */
    KeyHandler keyHandler;

    /** Manages persistent configuration properties. */
    PropertiesData propertiesData;

    /** Handles command parsing and execution. */
    Command command;

    /** Main application window. */
    Window window;
    
    /** The history manager */
    History history;

    /**
     * Initializes the CommuniDirect application.
     * Loads configuration, sets up UI, and starts the server based on user input or default settings.
     */
    @EntryPoint(EntryType.NETWORK)
    public CommuniDirect() {
        propertiesData = new PropertiesData(this);
        window = new Window(this);
        keyHandler = new KeyHandler(this);
        command = new Command(this);
        history = new History(this);

        propertiesData.load();

        if (port_ == defaultPort) {
            window.setPort(p -> {
                port_ = p;
                new Thread(() -> startServer(port_)).start();
                propertiesData.save();
            });
        } else {
            new Thread(() -> startServer(port_)).start();
        }
    }

    /**
     * Launches the CommuniDirect application on the Swing event dispatch thread.
     *
     * @param args Command-line arguments (unused).
     */
    @EntryPoint(EntryType.UI)
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new CommuniDirect();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Failed to initialize application: " + e.getMessage());
            }
        });
    }

    /**
     * Parses a string input into a valid port number.
     * Falls back to a default value if parsing fails.
     *
     * @param input   The string input to parse.
     * @param fallback The fallback port number.
     * @return Parsed port number or fallback.
     */
    protected int parsePort(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            logServer("⚠ Invalid port. Using " + fallback);
            port_ = 2556;
            return fallback;
        }
    }

    /**
     * Starts the server on the specified port.
     * Accepts client connections and logs incoming messages.
     *
     * @param port The port to bind the server to.
     */
    @EntryPoint(EntryType.NETWORK)
    protected void startServer(int port) {
        isServerRunning.set(true);
        try {
            serverSocket = new ServerSocket(port);
            logServer("Listening on port " + port);
            logServer("type ? to see how to change it");

            while (isServerRunning.get()) {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    String message;

                    while ((message = in.readLine()) != null) {
                        logServer("Received from " + clientAddress + ": " + message);
                        out.println("Server received message");
                    }

                } catch (SocketException e) {
                    if (isServerRunning.get()) {
                        logServer("Server socket closed.");
                    }
                } catch (IOException e) {
                    logServer("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (e instanceof BindException) {
                logServer("Port " + port + " is already in use. Please choose a different port.");
                SwingUtilities.invokeLater(() -> {
                    window.setPort(newPort -> {
                        port_ = newPort;
                        new Thread(() -> startServer(port_)).start();
                    });
                });
            } else {
                logServer("Failed to start server: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the server and closes the socket.
     */
    @EntryPoint(EntryType.NETWORK)
    public void stopServer() {
        isServerRunning.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                logServer("Successfully closed server");
            } catch (IOException e) {
                logServer("Error closing server: " + e.getMessage());
            }
        }
    }

    /**
     * Sends a message to a server at the specified IP and port.
     * Handles connection errors and logs responses.
     *
     * @param ip      Target server IP address.
     * @param port    Target server port.
     * @param message Message to send.
     */
    @EntryPoint(EntryType.NETWORK)
    protected void sendMessage(String ip, int port, String message) {
        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000);

                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
                logClient("Error connecting to " + ip + ":" + port + " - " + e.getMessage());
            }
        }).start();
    }

    /**
     * Logs a server-related message.
     *
     * @param message Message to log.
     */
    protected void logServer(String message) {
        log("[Server] " + message);
    }

    /**
     * Logs a client-related message.
     *
     * @param message Message to log.
     */
    protected void logClient(String message) {
        log("[Client] " + message);
    }

    /**
     * Core logging method that updates the log buffer and UI.
     *
     * @param message Message to log.
     */
    @EntryPoint(EntryType.UTILITY)
    protected synchronized void log(String message) {
        if (!logBuffer.isEmpty() && logBuffer.get(logBuffer.size() - 1).equals(message)) {
            return;
        }

        if (logBuffer.size() >= maxLogLines) {
            logBuffer.remove(0);
        }
        logBuffer.add(message);

        SwingUtilities.invokeLater(() -> {
            window.serverArea.append(message + "\n");
            window.serverArea.setCaretPosition(window.serverArea.getDocument().getLength());
        });
    }
}