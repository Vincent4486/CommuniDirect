package net.vincent.communidirect.client.term;

import net.vincent.communidirect.client.core.ClientCore;
import net.vincent.communidirect.client.core.ClientWebhookListener;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyStoreManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TermClient {

    private final SettingsManager settings;
    private final KeyStoreManager keyStore;
    private final ClientCore core;

    public TermClient(SettingsManager settings, KeyStoreManager keyStore) {
        this.settings = settings;
        this.keyStore = keyStore;
        this.core = new ClientCore();
    }

    public void run(String[] args) {
        try {
            core.ensureDirectories();
        } catch (Exception e) {
            System.err.println("[TermClient] Failed to initialize client directories: " + e.getMessage());
            return;
        }

        List<String> filteredArgs = filterModeFlags(args);
        if (filteredArgs.isEmpty()) {
            runInteractiveShell();
            return;
        }

        if (filteredArgs.contains("--help") || filteredArgs.contains("-h")) {
            printUsage();
            return;
        }

        String sendTarget = null;
        String openTarget = null;
        boolean listStaged = false;
        boolean listReceived = false;
        boolean compose = false;
        boolean reload = false;
        boolean watch = false;

        for (int i = 0; i < filteredArgs.size(); i++) {
            String arg = filteredArgs.get(i);
            switch (arg) {
                case "--new", "-n" -> compose = true;
                case "--list-staged", "-S" -> listStaged = true;
                case "--list-received", "-l" -> listReceived = true;
                case "--reload", "-r" -> reload = true;
                case "--watch", "-w" -> watch = true;
                case "--send", "-s" -> {
                    if (i + 1 >= filteredArgs.size()) {
                        System.err.println("[TermClient] Missing value for " + arg + ". Use --send <index|filename|all>.");
                        return;
                    }
                    sendTarget = filteredArgs.get(++i);
                }
                case "--open", "-o" -> {
                    if (i + 1 >= filteredArgs.size()) {
                        System.err.println("[TermClient] Missing value for " + arg + ". Use --open <index|filename>.");
                        return;
                    }
                    openTarget = filteredArgs.get(++i);
                }
                default -> {
                    System.err.println("[TermClient] Unknown argument: " + arg);
                    printUsage();
                    return;
                }
            }
        }

        if (compose) {
            printResult(core.composeAndStage(settings));
        }

        if (reload) {
            printCounts();
        }

        if (listStaged) {
            printStaged();
        }

        if (listReceived) {
            printReceived();
        }

        if (sendTarget != null) {
            sendByTarget(sendTarget);
        }

        if (openTarget != null) {
            openByTarget(openTarget);
        }

        if (watch) {
            watchWebhook();
        }
    }

    private void runInteractiveShell() {
        String localId = ClientCore.deriveLocalId(keyStore.getOwnPublicKeyRaw());
        System.out.println("CommuniDirect terminal mode ready. ID: " + localId);
        System.out.println("Type 'help' for commands.");

        try (ClientWebhookListener listener = new ClientWebhookListener(
            payload -> {
                String summary = payload.isBlank() ? "new message received" : payload.trim();
                System.out.println("\n[webhook] " + summary);
                System.out.print("cdir> ");
            },
            error -> System.err.println("[webhook] " + error)
        )) {
            listener.start();
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("cdir> ");
                    if (!scanner.hasNextLine()) {
                        break;
                    }
                    String input = scanner.nextLine().trim();
                    if (input.isEmpty()) {
                        continue;
                    }

                    if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                        break;
                    }

                    if ("help".equalsIgnoreCase(input)) {
                        printInteractiveHelp();
                        continue;
                    }

                    if ("new".equalsIgnoreCase(input)) {
                        printResult(core.composeAndStage(settings));
                        continue;
                    }

                    if ("staged".equalsIgnoreCase(input)) {
                        printStaged();
                        continue;
                    }

                    if ("received".equalsIgnoreCase(input)) {
                        printReceived();
                        continue;
                    }

                    if ("reload".equalsIgnoreCase(input)) {
                        printCounts();
                        continue;
                    }

                    if ("send all".equalsIgnoreCase(input)) {
                        printResult(core.sendAllStaged(keyStore));
                        continue;
                    }

                    if (input.toLowerCase().startsWith("send ")) {
                        sendByTarget(input.substring(5).trim());
                        continue;
                    }

                    if (input.toLowerCase().startsWith("open ")) {
                        openByTarget(input.substring(5).trim());
                        continue;
                    }

                    System.out.println("Unknown command. Try 'help'.");
                }
            }
        }
    }

    private void watchWebhook() {
        System.out.println("Webhook watch mode started. Press Ctrl+C to exit.");
        try (ClientWebhookListener listener = new ClientWebhookListener(
            payload -> {
                String summary = payload.isBlank() ? "new message received" : payload.trim();
                System.out.println("[webhook] " + summary);
            },
            error -> System.err.println("[webhook] " + error)
        )) {
            if (!listener.start()) {
                return;
            }
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendByTarget(String target) {
        if ("all".equalsIgnoreCase(target)) {
            printResult(core.sendAllStaged(keyStore));
            return;
        }

        try {
            Path file = resolveByIndexOrName(core.listStagedFiles(), target);
            if (file == null) {
                System.err.println("[TermClient] No staged message matches: " + target);
                return;
            }
            printResult(core.sendStagedFile(file, keyStore));
        } catch (Exception e) {
            System.err.println("[TermClient] Send failed: " + e.getMessage());
        }
    }

    private void openByTarget(String target) {
        try {
            Path file = resolveByIndexOrName(core.listReceivedFiles(), target);
            if (file == null) {
                System.err.println("[TermClient] No received message matches: " + target);
                return;
            }
            String content = core.readReceivedFile(file);
            System.out.println("----- " + file.getFileName() + " -----");
            System.out.println(content);
            System.out.println("----- end -----");
        } catch (Exception e) {
            System.err.println("[TermClient] Failed to open message: " + e.getMessage());
        }
    }

    private void printCounts() {
        try {
            int staged = core.listStagedFiles().size();
            int received = core.listReceivedFiles().size();
            System.out.println("Reloaded. Staged=" + staged + ", Received=" + received + ".");
        } catch (Exception e) {
            System.err.println("[TermClient] Reload failed: " + e.getMessage());
        }
    }

    private void printStaged() {
        try {
            List<Path> files = core.listStagedFiles();
            if (files.isEmpty()) {
                System.out.println("No staged messages.");
                return;
            }
            System.out.println("Staged messages:");
            for (int i = 0; i < files.size(); i++) {
                System.out.println("  " + (i + 1) + ") " + files.get(i).getFileName());
            }
        } catch (Exception e) {
            System.err.println("[TermClient] Could not list staged messages: " + e.getMessage());
        }
    }

    private void printReceived() {
        try {
            List<Path> files = core.listReceivedFiles();
            if (files.isEmpty()) {
                System.out.println("No received messages.");
                return;
            }
            System.out.println("Received messages:");
            for (int i = 0; i < files.size(); i++) {
                System.out.println("  " + (i + 1) + ") " + files.get(i).getFileName());
            }
        } catch (Exception e) {
            System.err.println("[TermClient] Could not list received messages: " + e.getMessage());
        }
    }

    private static Path resolveByIndexOrName(List<Path> files, String token) {
        if (files.isEmpty()) {
            return null;
        }

        try {
            int index = Integer.parseInt(token);
            if (index < 1 || index > files.size()) {
                return null;
            }
            return files.get(index - 1);
        } catch (NumberFormatException ignored) {
        }

        for (Path file : files) {
            if (file.getFileName().toString().equals(token)) {
                return file;
            }
        }
        return null;
    }

    private static void printResult(ClientCore.Result result) {
        if (result.ok()) {
            System.out.println(result.message());
        } else {
            System.err.println(result.message());
        }
    }

    private static List<String> filterModeFlags(String[] args) {
        List<String> filtered = new ArrayList<>();
        for (String arg : args) {
            if ("--gui".equals(arg) || "-g".equals(arg) || "--tui".equals(arg) || "-t".equals(arg)) {
                continue;
            }
            filtered.add(arg);
        }
        return filtered;
    }

    private static void printUsage() {
        System.out.println("CommuniDirect terminal client options:");
        System.out.println("  --help, -h                Show help");
        System.out.println("  --new, -n                 Compose and stage a new message via $EDITOR");
        System.out.println("  --list-staged, -S         List staged messages");
        System.out.println("  --list-received, -l       List received messages");
        System.out.println("  --send, -s <i|name|all>   Send one staged message or all staged messages");
        System.out.println("  --open, -o <i|name>       Open and print one received message");
        System.out.println("  --reload, -r              Reload counts for staged and received");
        System.out.println("  --watch, -w               Start localhost webhook watch mode");
        System.out.println("Mode flags from launcher:");
        System.out.println("  --gui, -g                 GUI mode");
        System.out.println("  --tui, -t                 TUI mode");
        System.out.println();
        System.out.println("Without arguments, interactive terminal mode starts.");
    }

    private static void printInteractiveHelp() {
        System.out.println("Commands:");
        System.out.println("  help         Show this help");
        System.out.println("  new          Compose and stage a message");
        System.out.println("  staged       List staged messages");
        System.out.println("  send <i|n>   Send staged message by index or filename");
        System.out.println("  send all     Send all staged messages");
        System.out.println("  received     List received messages");
        System.out.println("  open <i|n>   Open received message by index or filename");
        System.out.println("  reload       Show staged/received counts");
        System.out.println("  quit         Exit");
    }
}
