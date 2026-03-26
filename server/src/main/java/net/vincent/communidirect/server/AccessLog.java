package net.vincent.communidirect.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;

/**
 * Records inbound connection events to a flat-text access log file.
 *
 * <p>Each call to {@link #logAccess(Socket)} appends a single line containing
 * a timestamp and the remote IP address.
 *
 * <p><b>Note:</b> The underlying {@link FileWriter} is opened without the
 * append flag, so each invocation overwrites the previous content.  This is
 * intentional for the current lightweight logging design.
 */
public class AccessLog {
    /** Back-reference to the owning launcher (kept for future cross-component logging). */
    ServerLauncher serverLauncher;

    /** Absolute path to the access log file, set via {@link #init(String)}. */
    String outputFile;

    /**
     * Constructs an {@code AccessLog}.  Call {@link #init(String)} before
     * logging any events.
     *
     * @param serverLauncher the owning server launcher instance
     */
    public AccessLog(ServerLauncher serverLauncher){
        this.serverLauncher = serverLauncher;
    }

    /**
     * Configures the log file destination.  Must be called once before any
     * calls to {@link #logAccess(Socket)}.
     *
     * @param logFile absolute path to the log file to write
     */
    public void init(String logFile) {
        this.outputFile = logFile;
    }

    /**
     * Appends a timestamped access entry for the given socket's remote address.
     *
     * @param clientSocket the accepted client socket whose IP is recorded
     */
    public void logAccess(Socket clientSocket) {
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile));

            StringBuilder log = new StringBuilder();

            LocalDateTime localDateTime = LocalDateTime.now();
            String accessTime = localDateTime.toString();
            log.append(accessTime + " ");

            InetAddress inetAddress = clientSocket.getInetAddress();
            String accessIp = inetAddress.toString();
            log.append(accessIp + " ");

            bufferedWriter.newLine();
            bufferedWriter.write(log.toString());

            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
