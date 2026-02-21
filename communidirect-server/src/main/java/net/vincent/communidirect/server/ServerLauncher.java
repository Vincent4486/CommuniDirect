package net.vincent.communidirect.server;

public class ServerLauncher {
    public ServerSocket serverSocket;
    public ClientHandler clientHandler;
    public AccessLog accessLog;

    public ServerLauncher(){
        serverSocket = new ServerSocket(this);
        clientHandler = new ClientHandler(this);
        accessLog = new AccessLog(this);
    }

    public void init(){
        accessLog.init(Defaults.DEFAULT_LOG_DIR + Defaults.DEFAULT_ACCESSLOG_NAME);
        serverSocket.init(Defaults.DEFAULT_IP, Defaults.DEFAULT_PORT);
        clientHandler.init(serverSocket.serverSocket);
    }

    public static void main(String[] args) {
        ServerLauncher serverLauncher = new ServerLauncher();
        serverLauncher.init();
    }
}
