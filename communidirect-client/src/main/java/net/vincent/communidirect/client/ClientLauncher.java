package net.vincent.communidirect.client;

import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.CryptoEngine;
import net.vincent.communidirect.common.crypto.KeyGenerator;
import net.vincent.communidirect.common.crypto.KeyStoreManager;
import net.vincent.communidirect.common.proto.CdirMessage;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Scanner;

public class ClientLauncher {

    // ANSI colour helpers
    private static final String ANSI_RED   = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN  = "\u001B[36m";
    private static final String ANSI_BOLD  = "\u001B[1m";
    private static final String ANSI_RESET = "\u001B[0m";

    private static final int SOCKET_TIMEOUT_MS = 5_000;

    public static void main(String[] args) {

        // ---- Bootstrap common services ------------------------------------
        SettingsManager settings = new SettingsManager();
        settings.load();

        KeyStoreManager keyStore = new KeyStoreManager();
        try {
            keyStore.load();
        } catch (Exception e) {
            System.err.println(ANSI_RED + "[ClientLauncher] Fatal: could not load key store – " +
                               e.getMessage() + ANSI_RESET);
            return;
        }

        Scanner scanner = new Scanner(System.in);

        printBanner();

        // ---- Interactive loop ---------------------------------------------
        while (true) {
            System.out.println();

            // 1. Target IP
            System.out.print(ANSI_BOLD + "Target IP" + ANSI_RESET +
                             " [" + settings.getIp() + "]: ");
            String ipInput = scanner.nextLine().trim();
            String targetIp = ipInput.isEmpty() ? settings.getIp() : ipInput;

            // 2. Target Port
            System.out.print(ANSI_BOLD + "Target Port" + ANSI_RESET +
                             " [" + settings.getPort() + "]: ");
            String portInput = scanner.nextLine().trim();
            int targetPort = settings.getPort();
            if (!portInput.isEmpty()) {
                try {
                    targetPort = Integer.parseInt(portInput);
                } catch (NumberFormatException e) {
                    System.out.println(ANSI_RED + "Invalid port – using default " +
                                       settings.getPort() + ANSI_RESET);
                }
            }

            // 3. Key Name (peer alias)
            System.out.print(ANSI_BOLD + "Key Name" + ANSI_RESET +
                             " (recipient alias in ~/.communidirect/keys/): ");
            String keyName = scanner.nextLine().trim();

            PublicKey recipientPub = keyStore.getPeerKey(keyName);
            if (recipientPub == null) {
                System.out.println(ANSI_RED + "Identity unknown locally." + ANSI_RESET);
                continue;
            }

            // Show avatar so the user can visually confirm the recipient.
            byte[] recipientRaw = KeyGenerator.toRaw32(recipientPub.getEncoded());
            String avatar       = CryptoEngine.getSymmetricAvatar(recipientRaw);
            System.out.println();
            System.out.println(ANSI_CYAN + ANSI_BOLD +
                               "--- Recipient identity: " + keyName + " ---" + ANSI_RESET);
            System.out.println(ANSI_CYAN + avatar + ANSI_RESET);
            System.out.println(ANSI_CYAN + "-----------------------------------" + ANSI_RESET);
            System.out.println();

            // 4. Message
            System.out.print(ANSI_BOLD + "Message" + ANSI_RESET + ": ");
            String messageText = scanner.nextLine();
            if (messageText.isEmpty()) {
                System.out.println(ANSI_RED + "Empty message – aborted." + ANSI_RESET);
                continue;
            }

            // ---- Dispatch -------------------------------------------------
            dispatch(messageText, targetIp, targetPort, keyStore, recipientRaw);
        }
    }

    // -------------------------------------------------------------------------
    // Packet construction & transmission
    // -------------------------------------------------------------------------

    private static void dispatch(String message,
                                 String targetIp,
                                 int    targetPort,
                                 KeyStoreManager keyStore,
                                 byte[] recipientPubRaw) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            socket.connect(
                new java.net.InetSocketAddress(targetIp, targetPort),
                SOCKET_TIMEOUT_MS
            );

            OutputStream out = socket.getOutputStream();

            CdirMessage.encode(
                payload,
                keyStore.getPrivateKey(),
                keyStore.getOwnPublicKeyRaw(),
                recipientPubRaw,
                out
            );

            System.out.println(ANSI_GREEN + ANSI_BOLD +
                               "Message securely dispatched to " + targetIp + "." +
                               ANSI_RESET);

        } catch (Exception e) {
            System.out.println(ANSI_RED + "Dispatch failed: " + e.getMessage() + ANSI_RESET);
        }
    }

    // -------------------------------------------------------------------------
    // Cosmetics
    // -------------------------------------------------------------------------

    private static void printBanner() {
        System.out.println(ANSI_BOLD + ANSI_CYAN);
        System.out.println("  ██████╗██████╗ ██╗██████╗ ");
        System.out.println(" ██╔════╝██╔══██╗██║██╔══██╗");
        System.out.println(" ██║     ██║  ██║██║██████╔╝");
        System.out.println(" ██║     ██║  ██║██║██╔══██╗");
        System.out.println(" ╚██████╗██████╔╝██║██║  ██║");
        System.out.println("  ╚═════╝╚═════╝ ╚═╝╚═╝  ╚═╝");
        System.out.println(" CommuniDirect Client  v1.1.0" + ANSI_RESET);
        System.out.println(" Type Ctrl+C to exit.");
    }
}

