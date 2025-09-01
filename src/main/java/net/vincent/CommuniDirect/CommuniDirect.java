package net.vincent.CommuniDirect;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommuniDirect {

    protected static final int defaultPort = 2556;
    protected static final List<String> logBuffer = new ArrayList<>();
    protected static final int maxLogLines = 20;
    protected static int port_ = defaultPort;
    private ServerSocket serverSocket;
    private AtomicBoolean isServerRunning = new AtomicBoolean(false);

    Window window;

    public CommuniDirect() {
        window = new Window(this);

        // Ask user for port and start server
        window.setPort(port -> {
            port_ = port;
            new Thread(() -> startServer(port_)).start();
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new CommuniDirect();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Failed to initialize application: " + e.getMessage());
            }
        });
    }

    protected int parsePort(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            logServer("⚠ Invalid port. Using " + fallback);
            port_ = 2556;
            return fallback;
        }
    }

    /** SERVER SIDE **/
    protected void startServer(int port) {
        isServerRunning.set(true);
        try {
            serverSocket = new ServerSocket(port);
            logServer("Listening on port " + port);

            while (isServerRunning.get()) {
                try (Socket clientSocket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    String message;
                    while ((message = in.readLine()) != null) {
                        logServer("Received from " + clientAddress + ": " + message);
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
                // Show a dialog to choose a different port
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

    public void stopServer() {
        isServerRunning.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logServer("Error closing server: " + e.getMessage());
            }
        }
    }

    /** CLIENT SIDE **/
    protected void sendMessage(String ip, int port, String message) {
        // Run in a separate thread to avoid blocking the UI
        new Thread(() -> {
            try {
                // Create socket with timeout
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000); // 5 second timeout

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

    /** LOGGING HELPERS **/
    protected void logServer(String message) {
        log("[Server] " + message);
    }

    protected void logClient(String message) {
        log("[Client] " + message);
    }

    /** CORE LOG METHOD **/
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