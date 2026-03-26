package net.vincent.communidirect.common.hook;

/**
 * Shared localhost webhook endpoint used for client-side message notifications.
 */
public final class LocalWebhookConfig {

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 9834;
    public static final String PATH = "/communidirect/message";

    private LocalWebhookConfig() {
    }
}