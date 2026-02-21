package net.vincent.communidirect.server;

import java.io.IOException;
import java.net.Socket;

public class ClientHandler {
    ServerLauncher serverLauncher;
    java.net.ServerSocket serverSocket;

    public ClientHandler(ServerLauncher serverLauncher) {
        this.serverLauncher = serverLauncher;
    }

    private void ListenLoop() {
        Socket clientSocket;
        while (true) {
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void init(java.net.ServerSocket serverSocket) {
        this.serverSocket = serverSocket;

    }
}
