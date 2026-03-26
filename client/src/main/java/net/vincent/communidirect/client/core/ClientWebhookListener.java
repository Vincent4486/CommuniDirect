package net.vincent.communidirect.client.core;

import net.vincent.communidirect.common.hook.LocalWebhookConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Lightweight localhost webhook server for client-side message notifications.
 */
public class ClientWebhookListener implements AutoCloseable {

    private final Consumer<String> onMessage;
    private final Consumer<String> onError;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private Thread thread;

    public ClientWebhookListener(Consumer<String> onMessage, Consumer<String> onError) {
        this.onMessage = onMessage;
        this.onError = onError;
    }

    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return true;
        }
        try {
            serverSocket = new ServerSocket(
                LocalWebhookConfig.PORT,
                10,
                InetAddress.getByName(LocalWebhookConfig.HOST)
            );
        } catch (IOException e) {
            running.set(false);
            onError.accept("Webhook listener not started: " + e.getMessage());
            return false;
        }

        thread = new Thread(this::acceptLoop, "cdir-webhook-listener");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void acceptLoop() {
        while (running.get()) {
            try (Socket socket = serverSocket.accept()) {
                handleConnection(socket);
            } catch (IOException e) {
                if (running.get()) {
                    onError.accept("Webhook accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setSoTimeout(2_000);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }

            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length())) {
                    String value = line.substring("Content-Length:".length()).trim();
                    contentLength = Integer.parseInt(value);
                }
            }

            String body = "";
            if (contentLength > 0) {
                char[] payload = new char[contentLength];
                int read = reader.read(payload);
                if (read > 0) {
                    body = new String(payload, 0, read);
                }
            }

            boolean accepted = requestLine.startsWith("POST " + LocalWebhookConfig.PATH + " ");
            if (accepted) {
                onMessage.accept(body);
                writer.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n");
            } else {
                writer.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
            }
            writer.flush();
        } catch (Exception e) {
            onError.accept("Webhook request error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}