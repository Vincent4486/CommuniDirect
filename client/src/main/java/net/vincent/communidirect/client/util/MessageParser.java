package net.vincent.communidirect.client.util;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.vincent.communidirect.common.Defaults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts between three representations of a staged message:
 * <ol>
 *   <li>Raw editor temp-file text (headers + delimiter + body)</li>
 *   <li>{@link StagedMessage} in-memory object</li>
 *   <li>TOML file on disk under {@code ~/.communidirect/staged/}</li>
 * </ol>
 *
 * <h2>Temp-file format (written for the editor, read back after)</h2>
 * <pre>
 * TARGET_IP: 127.0.0.1
 * PORT: 9833
 * KEY_NAME: vincent
 * --- MESSAGE ---
 * Your multi-line
 * message goes here.
 * </pre>
 *
 * <h2>TOML format (persisted to disk)</h2>
 * <pre>
 * target_ip = "127.0.0.1"
 * port      = 9833
 * key_name  = "vincent"
 * body      = "Your multi-line\nmessage goes here."
 * </pre>
 */
public final class MessageParser {

    static final String DELIMITER = "--- MESSAGE ---";

    private MessageParser() { /* utility */ }

    // -------------------------------------------------------------------------
    // Temp-file generation (written before launching the external editor)
    // -------------------------------------------------------------------------

    /**
     * Produces the initial content of the temp file shown to the user in the
     * external editor.
     *
     * @param defaultIp   pre-filled default IP
     * @param defaultPort pre-filled default port
     * @return multi-line string ready to be written to a temporary file
     */
    public static String buildTempFileContent(String defaultIp, int defaultPort) {
        return "TARGET_IP: " + defaultIp + "\n"
             + "PORT: "      + defaultPort + "\n"
             + "KEY_NAME: \n"
             + DELIMITER + "\n"
             + "Type your message here...\n";
    }

    // -------------------------------------------------------------------------
    // Temp-file → StagedMessage (parsed after the editor exits)
    // -------------------------------------------------------------------------

    /**
     * Parses the raw contents of the editor temp file into a {@link StagedMessage}.
     *
     * @param content full file contents as returned by {@link Files#readString}
     * @return parsed staged message; fields may be empty if parsing fails
     */
    public static StagedMessage parseTempFile(String content) {
        String targetIp = "";
        int    port     = Defaults.DEFAULT_PORT;
        String keyName  = "";
        String body     = "";

        int delimIndex = content.indexOf(DELIMITER);
        String headerSection = (delimIndex >= 0)
            ? content.substring(0, delimIndex)
            : content;
        String bodySection = (delimIndex >= 0)
            ? content.substring(delimIndex + DELIMITER.length()).stripLeading()
            : "";

        for (String line : headerSection.split("\\r?\\n")) {
            if (line.startsWith("TARGET_IP:")) {
                targetIp = line.substring("TARGET_IP:".length()).trim();
            } else if (line.startsWith("PORT:")) {
                String v = line.substring("PORT:".length()).trim();
                try { port = Integer.parseInt(v); } catch (NumberFormatException ignored) {}
            } else if (line.startsWith("KEY_NAME:")) {
                keyName = line.substring("KEY_NAME:".length()).trim();
            }
        }

        // Strip the placeholder hint if the user didn't change it.
        if (bodySection.trim().equals("Type your message here...")) {
            body = "";
        } else {
            // Remove any trailing newline the editor may have appended.
            body = bodySection.stripTrailing();
        }

        return new StagedMessage(targetIp, port, keyName, body);
    }

    // -------------------------------------------------------------------------
    // StagedMessage → TOML file (saved to staged/)
    // -------------------------------------------------------------------------

    /**
     * Serialises a {@link StagedMessage} to disk as a TOML file.
     *
     * @param msg      message to persist
     * @param stagedDir path to the {@code staged/} directory
     * @param filename  target filename (e.g. {@code "20260221_190501_127.0.0.1.toml"})
     * @throws IOException on any write error
     */
    public static void writeStagedToml(StagedMessage msg, Path stagedDir, String filename)
            throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("target_ip", msg.targetIp);
        map.put("port",      msg.port);
        map.put("key_name",  msg.keyName);
        map.put("body",      msg.body);

        Path outPath = stagedDir.resolve(filename);
        new TomlWriter().write(map, outPath.toFile());
    }

    // -------------------------------------------------------------------------
    // TOML file → StagedMessage (loaded from staged/ or sent/)
    // -------------------------------------------------------------------------

    /**
     * Reads a staged TOML file from disk and deserialises it into a
     * {@link StagedMessage}.
     *
     * @param tomlFile path to the TOML file
     * @return parsed staged message
     * @throws IOException on any read error
     */
    public static StagedMessage readStagedToml(Path tomlFile) throws IOException {
        String content = Files.readString(tomlFile, StandardCharsets.UTF_8);
        Toml toml      = new Toml().read(content);

        String targetIp = toml.getString("target_ip", "");
        long   portL    = toml.getLong("port", (long) Defaults.DEFAULT_PORT);
        String keyName  = toml.getString("key_name", "");
        String body     = toml.getString("body", "");

        return new StagedMessage(targetIp, (int) portL, keyName, body);
    }
}
