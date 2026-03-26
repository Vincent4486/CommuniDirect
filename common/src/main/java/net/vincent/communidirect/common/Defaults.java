package net.vincent.communidirect.common;

/**
 * Application-wide default constants used when no explicit configuration is
 * present in {@code ~/.communidirect/config.toml}.
 *
 * <p>All submodules (server, client, common utilities) reference these values
 * through {@link net.vincent.communidirect.common.config.SettingsManager} so
 * that defaults are defined in exactly one place.
 *
 * <p>This class cannot be instantiated.
 */
public final class Defaults {
    /**
     * Private constructor – prevents instantiation of this utility class.
     */
    private Defaults() {
    }
    /** Default TCP port on which the server listens for incoming CDIR connections. */
    public static final int    DEFAULT_PORT            = 9833;

    /** Default bind address (loopback). Override in {@code config.toml} for LAN/WAN exposure. */
    public static final String DEFAULT_IP              = "127.0.0.1";

    /** Default directory (tilde-expanded at runtime) where log files are stored. */
    public static final String DEFAULT_LOG_DIR         = "~/.communidirect/logs";

    /** Default filename for the access log within {@link #DEFAULT_LOG_DIR}. */
    public static final String DEFAULT_ACCESSLOG_NAME  = "access.log";

    /** Default filename for the error log within {@link #DEFAULT_LOG_DIR}. */
    public static final String DEFAULT_ERRLOG_NAME     = "err.log";
}
