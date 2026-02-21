package net.vincent.communidirect.client;

import net.vincent.communidirect.client.tui.TuiClient;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;

/**
 * Bootstrap entry point for the CommuniDirect interactive client.
 *
 * <p>Responsibility chain:
 * <ol>
 *   <li>Loads (or creates) settings via {@link net.vincent.communidirect.common.config.SettingsManager}.</li>
 *   <li>Loads (or generates) the local Ed25519 identity via
 *       {@link net.vincent.communidirect.common.crypto.KeyStoreManager}.</li>
 *   <li>Delegates all subsequent UI and network interactions to
 *       {@link net.vincent.communidirect.client.tui.TuiClient}.</li>
 * </ol>
 */
public class ClientLauncher {

    /**
     * Application entry point.
     *
     * @param args command-line arguments (currently unused)
     */
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
