package net.vincent.communidirect.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;

public class AccessLog {
    ServerLauncher serverLauncher;
    String outputFile;

    public AccessLog(ServerLauncher serverLauncher){
        this.serverLauncher = serverLauncher;
    }

    public void init(String logFile) {
        this.outputFile = logFile;
    }

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
