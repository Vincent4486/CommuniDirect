package net.vincent.communidirect.common.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import net.vincent.communidirect.common.Defaults;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and persists CommuniDirect settings from {@code ~/.communidirect/config.toml}.
 * If the file (or its parent directory) does not exist it is created with built-in
 * defaults.  The log directory is also created if it is absent.
 */
public class SettingsManager {

    private static final String ROOT_DIR    = System.getProperty("user.home") + "/.communidirect";
    private static final String CONFIG_FILE = ROOT_DIR + "/config.toml";

    private int    port;
    private String ip;
    private String logDir;
    private String accessLogName;
    private String errLogName;

    /**
     * Initialises all fields with their compiled-in defaults.
     * Call {@link #load()} to overlay values from
     * {@code ~/.communidirect/config.toml}.
     */
    public SettingsManager() {
        this.port          = Defaults.DEFAULT_PORT;
        this.ip            = Defaults.DEFAULT_IP;
        this.logDir        = Defaults.DEFAULT_LOG_DIR;
        this.accessLogName = Defaults.DEFAULT_ACCESSLOG_NAME;
        this.errLogName    = Defaults.DEFAULT_ERRLOG_NAME;
    }

    /**
     * Loads configuration from disk.  Creates the config file (and all required
     * directories) with default values if it does not already exist.
     */
    public void load() {
        ensureRootDirectory();

        File configFile = new File(CONFIG_FILE);
        if (!configFile.exists()) {
            writeDefaults(configFile);
        } else {
            readConfig(configFile);
        }

        ensureLogDirectory();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Creates {@code ~/.communidirect/} and any missing ancestor directories.
     * Silently succeeds if the directory already exists.
     */
    private void ensureRootDirectory() {
        Path rootPath = Paths.get(ROOT_DIR);
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
                System.out.println("[SettingsManager] Created config directory: " + ROOT_DIR);
            } catch (IOException e) {
                System.err.println("[SettingsManager] Failed to create config directory: " + e.getMessage());
            }
        }
    }

    /**
     * Creates the configured log directory (with tilde expansion) if it does not
     * yet exist.  Called unconditionally after each {@link #load()} cycle.
     */
    private void ensureLogDirectory() {
        Path logPath = Paths.get(resolveHome(logDir));
        if (!Files.exists(logPath)) {
            try {
                Files.createDirectories(logPath);
                System.out.println("[SettingsManager] Created log directory: " + logPath);
            } catch (IOException e) {
                System.err.println("[SettingsManager] Failed to create log directory: " + e.getMessage());
            }
        }
    }

    /**
     * Serialises the compiled-in defaults to {@code configFile} as TOML.
     * Called the first time the application runs and no config file exists.
     *
     * @param configFile the destination file; parent directory must already exist
     */
    private void writeDefaults(File configFile) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("port",            Defaults.DEFAULT_PORT);
        config.put("ip",              Defaults.DEFAULT_IP);
        config.put("log_dir",         Defaults.DEFAULT_LOG_DIR);
        config.put("access_log_name", Defaults.DEFAULT_ACCESSLOG_NAME);
        config.put("err_log_name",    Defaults.DEFAULT_ERRLOG_NAME);

        try {
            new TomlWriter().write(config, configFile);
            System.out.println("[SettingsManager] Wrote default config to: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("[SettingsManager] Failed to write default config: " + e.getMessage());
        }
    }

    /**
     * Parses {@code configFile} and updates all settings fields.  Any missing
     * key falls back to its compiled-in default rather than throwing.
     *
     * @param configFile an existing, readable TOML config file
     */
    private void readConfig(File configFile) {
        try {
            Toml toml = new Toml().read(configFile);
            this.port          = toml.getLong("port",            (long) Defaults.DEFAULT_PORT).intValue();
            this.ip            = toml.getString("ip",              Defaults.DEFAULT_IP);
            this.logDir        = toml.getString("log_dir",         Defaults.DEFAULT_LOG_DIR);
            this.accessLogName = toml.getString("access_log_name", Defaults.DEFAULT_ACCESSLOG_NAME);
            this.errLogName    = toml.getString("err_log_name",    Defaults.DEFAULT_ERRLOG_NAME);
            System.out.println("[SettingsManager] Loaded config from: " + CONFIG_FILE);
        } catch (Exception e) {
            System.err.println("[SettingsManager] Failed to read config – using defaults: " + e.getMessage());
        }
    }

    /**
     * Expands a leading {@code ~} to the current user's home directory.
     *
     * @param path the path string, possibly starting with {@code ~}
     * @return the same path with {@code ~} replaced by the user's home directory,
     *         or the original path if it does not start with {@code ~}
     */
    public static String resolveHome(String path) {
        if (path != null && path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Listening port (default {@value Defaults#DEFAULT_PORT}). */
    public int getPort() {
        return port;
    }

    /** Bind IP address (default {@value Defaults#DEFAULT_IP}). */
    public String getIp() {
        return ip;
    }

    /**
     * Absolute path to the log directory, with {@code ~} expanded to the home
     * directory.
     */
    public String getLogDir() {
        return resolveHome(logDir);
    }

    /** Filename (not path) for the access log (default {@value Defaults#DEFAULT_ACCESSLOG_NAME}). */
    public String getAccessLogName() {
        return accessLogName;
    }

    /** Filename (not path) for the error log (default {@value Defaults#DEFAULT_ERRLOG_NAME}). */
    public String getErrLogName() {
        return errLogName;
    }
}
