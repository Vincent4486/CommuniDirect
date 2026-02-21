package net.vincent.communidirect.client;

import net.vincent.communidirect.client.tui.TuiClient;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;

public class ClientLauncher {

    public static void main(String[] args) {
        // Bootstrap shared services.
        SettingsManager settings = new SettingsManager();
        settings.load();

        KeyStoreManager keyStore = new KeyStoreManager();
        try {
            keyStore.load();
        } catch (Exception e) {
            System.err.println("[ClientLauncher] Fatal: could not load key store – " +
                               e.getMessage());
            System.exit(1);
        }

        // Hand off to the Lanterna TUI. The TUI owns the terminal from here.
        try {
            new TuiClient(settings, keyStore).run();
        } catch (Exception e) {
            System.err.println("[ClientLauncher] TUI crashed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
