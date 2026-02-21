package net.vincent.communidirect.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class AccessLog {
    ServerLauncher serverLauncher;
    String outputFile;

    public AccessLog(ServerLauncher serverLauncher, String logFile){
        this.serverLauncher = serverLauncher;
        this.outputFile = logFile;
    }
}
