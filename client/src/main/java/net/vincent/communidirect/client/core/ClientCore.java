package net.vincent.communidirect.client.core;

import net.vincent.communidirect.client.util.MessageParser;
import net.vincent.communidirect.client.util.StagedMessage;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyGenerator;
import net.vincent.communidirect.common.crypto.KeyStoreManager;
import net.vincent.communidirect.common.proto.CdirMessage;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared client workflows used by both GUI and terminal client modes.
 */
public class ClientCore {

    public static final Path ROOT_DIR = Paths.get(System.getProperty("user.home"), ".communidirect");
    public static final Path STAGED_DIR = ROOT_DIR.resolve("staged");
    public static final Path SENT_DIR = ROOT_DIR.resolve("sent");
    public static final Path MSG_DIR = ROOT_DIR.resolve("msg");

    private static final int SOCKET_TIMEOUT_MS = 5_000;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public record Result(boolean ok, String message) {
    }

    public void ensureDirectories() throws java.io.IOException {
        Files.createDirectories(ROOT_DIR);
        Files.createDirectories(STAGED_DIR);
        Files.createDirectories(SENT_DIR);
        Files.createDirectories(MSG_DIR);
    }

    public List<Path> listStagedFiles() throws java.io.IOException {
        return listByGlob(STAGED_DIR, "*.toml", Comparator.comparing(Path::getFileName));
    }

    public List<Path> listReceivedFiles() throws java.io.IOException {
        return listByGlob(MSG_DIR, "*.msg", (a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (java.io.IOException e) {
                return b.getFileName().compareTo(a.getFileName());
            }
        });
    }

    public Result composeAndStage(SettingsManager settings) {
        Path tmpFile = null;
        try {
            ensureDirectories();
            tmpFile = Files.createTempFile("cdir-", ".tmp");
            String template = MessageParser.buildTempFileContent(settings.getIp(), settings.getPort());
            Files.writeString(tmpFile, template, StandardCharsets.UTF_8);

            String editor = System.getenv("EDITOR");
            if (editor == null || editor.isBlank()) {
                editor = "vi";
            }

            int exitCode = new ProcessBuilder(editor, tmpFile.toString())
                .inheritIO()
                .start()
                .waitFor();

            if (exitCode != 0) {
                return new Result(false, "Editor exited with code " + exitCode + ".");
            }

            String raw = Files.readString(tmpFile, StandardCharsets.UTF_8);
            StagedMessage msg = MessageParser.parseTempFile(raw);
            if (!msg.isValid()) {
                return new Result(false, "Incomplete message. Fill TARGET_IP, KEY_NAME, and body.");
            }

            String filename = TS_FMT.format(LocalDateTime.now()) + "_" + msg.targetIp.replace(':', '_') + ".toml";
            MessageParser.writeStagedToml(msg, STAGED_DIR, filename);
            return new Result(true, "Staged " + filename + " for " + msg.targetIp + " [" + msg.keyName + "].");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Editor interrupted.");
        } catch (Exception e) {
            return new Result(false, "Failed to stage message: " + e.getMessage());
        } finally {
            if (tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (java.io.IOException ignored) {
                }
            }
        }
    }

    public Result stageMessage(StagedMessage msg) {
        try {
            ensureDirectories();
            if (!msg.isValid()) {
                return new Result(false, "Incomplete message. Fill TARGET_IP, KEY_NAME, and body.");
            }
            String filename = TS_FMT.format(LocalDateTime.now()) + "_" + msg.targetIp.replace(':', '_') + ".toml";
            MessageParser.writeStagedToml(msg, STAGED_DIR, filename);
            return new Result(true, "Staged " + filename + " for " + msg.targetIp + " [" + msg.keyName + "].");
        } catch (Exception e) {
            return new Result(false, "Failed to stage message: " + e.getMessage());
        }
    }

    public Result sendStagedFile(Path stagedFile, KeyStoreManager keyStore) {
        try {
            StagedMessage msg = MessageParser.readStagedToml(stagedFile);
            if (!msg.isValid()) {
                return new Result(false, "Staged message is incomplete: " + stagedFile.getFileName());
            }

            var recipientPub = keyStore.getPeerKey(msg.keyName);
            if (recipientPub == null) {
                return new Result(false, "Unknown identity: " + msg.keyName);
            }

            byte[] recipientRaw = KeyGenerator.toRaw32(recipientPub.getEncoded());
            byte[] payload = msg.body.getBytes(StandardCharsets.UTF_8);

            try (Socket socket = new Socket()) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                socket.connect(new InetSocketAddress(msg.targetIp, msg.port), SOCKET_TIMEOUT_MS);
                OutputStream out = socket.getOutputStream();
                CdirMessage.encode(payload,
                    keyStore.getPrivateKey(),
                    keyStore.getOwnPublicKeyRaw(),
                    recipientRaw,
                    out);
            }

            Path target = SENT_DIR.resolve(stagedFile.getFileName());
            try {
                Files.move(stagedFile, target);
            } catch (FileAlreadyExistsException e) {
                Files.move(stagedFile, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return new Result(true, "Sent " + stagedFile.getFileName() + " to " + msg.targetIp + ".");
        } catch (Exception e) {
            return new Result(false, "Send failed for " + stagedFile.getFileName() + ": " + e.getMessage());
        }
    }

    public Result sendAllStaged(KeyStoreManager keyStore) {
        try {
            List<Path> staged = listStagedFiles();
            if (staged.isEmpty()) {
                return new Result(true, "No staged messages.");
            }

            int sent = 0;
            List<String> failures = new ArrayList<>();
            for (Path path : staged) {
                Result result = sendStagedFile(path, keyStore);
                if (result.ok()) {
                    sent++;
                } else {
                    failures.add(result.message());
                }
            }

            if (failures.isEmpty()) {
                return new Result(true, "Sent " + sent + " message(s).");
            }
            return new Result(false, "Sent " + sent + " message(s), " + failures.size() + " failed. First error: " + failures.getFirst());
        } catch (Exception e) {
            return new Result(false, "Batch send failed: " + e.getMessage());
        }
    }

    public String readReceivedFile(Path file) throws java.io.IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static String deriveLocalId(byte[] pubKeyRaw) {
        if (pubKeyRaw == null) {
            return "unknown";
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(pubKeyRaw);
            return String.format("%02x%02x%02x%02x",
                hash[0] & 0xff,
                hash[1] & 0xff,
                hash[2] & 0xff,
                hash[3] & 0xff);
        } catch (Exception e) {
            return "n/a";
        }
    }

    private static List<Path> listByGlob(Path dir, String glob, Comparator<Path> sort) throws java.io.IOException {
        List<Path> paths = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return paths;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path p : stream) {
                paths.add(p);
            }
        }
        paths.sort(sort);
        return paths;
    }
}