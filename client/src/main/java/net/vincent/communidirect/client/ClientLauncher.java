package net.vincent.communidirect.client;

import net.vincent.communidirect.client.tui.TuiClient;
import net.vincent.communidirect.client.term.TermClient;
import net.vincent.communidirect.client.gui.GuiClient;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

/**
 * Entry point for the CommuniDirect client application.
 *
 * Initializes settings and key material, optionally attempts to auto-start the
 * server, and then launches one of the supported client modes:
 * terminal (default), GUI, or TUI.
 */
public class ClientLauncher {

    /** Utility class. */
    private ClientLauncher() {
    }

    /**
     * Starts the client by loading settings and keys, selecting the requested
     * mode from command-line flags, and launching the mode runtime.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SettingsManager settings = new SettingsManager();
        settings.load();

        KeyStoreManager keyStore = new KeyStoreManager();
        try {
            keyStore.load();
        } catch (Exception e) {
            System.err.println("[ClientLauncher] Fatal: could not load key store - " + e.getMessage());
            System.exit(1);
        }

        checkAndStartServer(settings);

        boolean useGui = false;
        boolean useTui = false;
        
        for (String arg : args) {
            if ("--gui".equals(arg) || "-g".equals(arg)) {
                useGui = true;
            } else if ("--tui".equals(arg) || "-t".equals(arg)) {
                useTui = true;
            }
        }

        if (useGui && useTui) {
            System.err.println("[ClientLauncher] Choose only one mode: --gui or --tui. Falling back to terminal mode.");
            useGui = false;
            useTui = false;
        }

        try {
            if (useGui) {
                new GuiClient().run(settings, keyStore);
            } else if (useTui) {
                System.out.println("[ClientLauncher] Starting TUI...");
                new TuiClient(settings, keyStore).run();
            } else {
                new TermClient(settings, keyStore).run(args);
            }
        } catch (Exception e) {
            System.err.println("[ClientLauncher] Client crashed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Verifies whether the server is reachable and, when unavailable, attempts
     * to start it using the bundled launcher scripts.
     *
     * @param settings active settings containing server host and port
     */
    private static void checkAndStartServer(SettingsManager settings) {
        try {
            if (isServerReachable(settings)) {
                return;
            }

            System.out.println("[ClientLauncher] Server not responding. Attempting to start 'cd-server'...");

            String os = System.getProperty("os.name").toLowerCase();
            boolean isWin = os.contains("win");
            String exeName = isWin ? "cd-server.exe" : "cd-server";

            ProcessBuilder pb = new ProcessBuilder(exeName);
            File scriptsCdServer = new File("scripts/cd-server" + (isWin ? ".bat" : ""));
            File scriptsServer = new File("scripts/server" + (isWin ? ".bat" : ""));

            if (scriptsCdServer.exists() && scriptsCdServer.canExecute()) {
                pb.command(scriptsCdServer.getAbsolutePath());
            } else if (scriptsServer.exists() && scriptsServer.canExecute()) {
                pb.command(scriptsServer.getAbsolutePath());
            }

            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb.start();

            Thread.sleep(1000);
            if (isServerReachable(settings)) {
                System.out.println("[ClientLauncher] Server started successfully.");
            } else {
                System.err.println("[ClientLauncher] Server start command ran but the server is still unreachable.");
            }
        } catch (Exception e) {
            System.err.println("[ClientLauncher] Warning: Could not auto-start server (" + e.getMessage() + ")");
        }
    }

    /**
     * Performs a TCP reachability probe to the configured server endpoint.
     *
     * @param settings active settings containing server host and port
     * @return true when a connection can be established, otherwise false
     */
    private static boolean isServerReachable(SettingsManager settings) {
        try (Socket socket = new Socket(settings.getIp(), settings.getPort())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
