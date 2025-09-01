package net.vincent.CommuniDirect;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Array;

public class Command extends JTextField {

    CommuniDirect communiDirect;

    public Command(CommuniDirect communiDirect){

        this.communiDirect = communiDirect;

        setPreferredSize(new Dimension(250, 30));
        setToolTipText("Enter you command");
        setBorder(BorderFactory.createLoweredBevelBorder());
        addActionListener(new KeyHandler(communiDirect));

    }

    public void execCommand(String cmd) {
        String[] commandArray = tokenize(cmd);

        switch(commandArray[0]){
            case "port": portCMD(commandArray); break;
            case "quit": System.exit(0);break;
        }
    }

    public String[] tokenize(String cmd){

        String[] commandArray = cmd.split(" ");
        return commandArray;

    }

    public void portCMD(String[] cmd) {
        // Start from 1 if cmd[0] is your program name;
        // loop until the last index—i+1 checks below will guard against OOB
        for (int i = 1; i < cmd.length; i++) {
            String flag = cmd[i].toLowerCase();

            switch (flag) {
                case "--changedefault":
                    // ensure there’s a next token
                    if (i + 1 >= cmd.length) {
                        communiDirect.logClient("Missing port value after " + flag);
                        break;
                    }
                    try {
                        int port = Integer.parseInt(cmd[++i]);  // consume the value
                        CommuniDirect.port_ = port;
                        communiDirect.propertiesData.save();
                        communiDirect.logClient("Successfully changed default port to " + port);
                    } catch (NumberFormatException e) {
                        communiDirect.logClient("Invalid port for " + flag + ": " + e.getMessage());
                    }
                    break;

                case "--changecurrent":
                    if (i + 1 >= cmd.length) {
                        communiDirect.logClient("Missing port value after " + flag);
                        break;
                    }
                    try {
                        int port = Integer.parseInt(cmd[++i]);
                        communiDirect.stopServer();
                        CommuniDirect.port_ = port;
                        communiDirect.logClient("Changing current port to " + port);
                        // restart off the EDT/UI thread
                        new Thread(() -> communiDirect.startServer(port)).start();
                    } catch (NumberFormatException e) {
                        communiDirect.logClient("Invalid port for " + flag + ": " + e.getMessage());
                    }
                    break;

                // <-- add more flags here as new cases -->

                default:
                    // unrecognized flag: you could log or just ignore
                    break;
            }
        }
    }

}
