package net.vincent.communidirect.server;

import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;

public class ServerLauncher {
    public ServerSocket     serverSocket;
    public ClientHandler    clientHandler;
    public AccessLog        accessLog;
    public SettingsManager  settings;
    public KeyStoreManager  keyStore;

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

    public void init() {
        String logFile = settings.getLogDir() + "/" + settings.getAccessLogName();
        accessLog.init(logFile);
        serverSocket.init(settings.getIp(), settings.getPort());
        clientHandler.init(serverSocket.serverSocket);
    }

    public static void main(String[] args) {
        ServerLauncher serverLauncher = new ServerLauncher();
        serverLauncher.init();
    }
}
