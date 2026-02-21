package net.vincent.communidirect.server;

public class ServerLauncher {
    public ServerSocket serverSocket;
    public ClientHandler clientHandler;

    public ServerLauncher(){
        serverSocket = new ServerSocket(this);
        clientHandler = new ClientHandler(this);
    }

    public void init(){
        serverSocket.init(Defaults.DEFAULT_IP, Defaults.DEFAULT_PORT);
        clientHandler.init(serverSocket.serverSocket);
    }

    public static void main(String[] args) {
        ServerLauncher serverLauncher = new ServerLauncher();
        serverLauncher.init();
    }
}
