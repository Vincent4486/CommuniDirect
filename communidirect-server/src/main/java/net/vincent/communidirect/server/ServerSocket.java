package net.vincent.communidirect.server;

import net.vincent.communidirect.common.Defaults;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerSocket {
    private ServerLauncher serverLauncher;

    public java.net.ServerSocket serverSocket;

    public InetAddress ip;
    public int port;

    public ServerSocket(ServerLauncher serverLauncher){
        this.serverLauncher = serverLauncher;
    }

    private void bindSocket(){
        try {
            this.serverSocket = new java.net.ServerSocket(port, 0, ip);
        } catch (IOException e) {
            System.err.println("Failed to bind socket, trying to change the port.");
            e.printStackTrace();
        }
    }

    public void init(String ip, int port){
        try {
            this.ip = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            System.err.println("Invalid IP address, defaulting to " + Defaults.DEFAULT_IP);
            try {
                this.ip = InetAddress.getByName(Defaults.DEFAULT_IP);
            } catch (UnknownHostException e2) {
                System.err.println("Failed to bind IP address");
                throw new RuntimeException();
            }
        }
        this.port = port;

        bindSocket();
    }
}
