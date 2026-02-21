package net.vincent.communidirect.server;

import net.vincent.communidirect.common.Defaults;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Wraps {@link java.net.ServerSocket} and handles IP resolution and port binding
 * for the CommuniDirect server.
 *
 * <p>Construct an instance via {@link ServerSocket#ServerSocket(ServerLauncher)},
 * then call {@link #init(String,int)} to resolve the address and bind the socket.
 * The bound {@link java.net.ServerSocket} is exposed via {@link #serverSocket} so that
 * {@link ClientHandler} can call {@code accept()} on it.
 */
public class ServerSocket {
    /** Back-reference to the owning launcher. */
    private ServerLauncher serverLauncher;

    /** The underlying JDK server socket; populated by {@link #bindSocket()}. */
    public java.net.ServerSocket serverSocket;

    /** Resolved bind address set by {@link #init(String,int)}. */
    public InetAddress ip;

    /** Bind port set by {@link #init(String,int)}. */
    public int port;

    /**
     * Constructs a {@code ServerSocket} bound to the given launcher.
    * Call {@link #init(String,int)} to complete initialisation.
     *
     * @param serverLauncher the owning {@link ServerLauncher}
     */
    public ServerSocket(ServerLauncher serverLauncher){
        this.serverLauncher = serverLauncher;
    }

    /**
     * Creates and binds the underlying {@link java.net.ServerSocket} to
     * {@link #ip} and {@link #port}.
     *
     * <p>Any {@link IOException} is printed to stderr; the socket field will
     * remain {@code null} on failure.
     */
    private void bindSocket(){
        try {
            this.serverSocket = new java.net.ServerSocket(port, 0, ip);
        } catch (IOException e) {
            System.err.println("Failed to bind socket, trying to change the port.");
            e.printStackTrace();
        }
    }

    /**
     * Resolves {@code ip} to an {@link InetAddress}, stores {@code port}, then
     * calls {@link #bindSocket()}.
     *
     * <p>If {@code ip} cannot be resolved, falls back to
     * {@link net.vincent.communidirect.common.Defaults#DEFAULT_IP}.  A second
     * resolution failure throws a {@link RuntimeException}.
     *
     * @param ip   the bind address string (hostname or dotted-decimal IPv4)
     * @param port the TCP port to listen on
     */
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
