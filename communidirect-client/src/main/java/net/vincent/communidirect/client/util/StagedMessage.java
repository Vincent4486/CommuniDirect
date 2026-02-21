package net.vincent.communidirect.client.util;

/**
 * Immutable data bag for a message that has been composed but not yet sent.
 * Instances are produced by {@link MessageParser} from either a raw editor
 * temp-file or a persisted staged TOML file.
 */
public final class StagedMessage {

    public final String targetIp;
    public final int    port;
    public final String keyName;
    public final String body;

    public StagedMessage(String targetIp, int port, String keyName, String body) {
        this.targetIp = (targetIp != null) ? targetIp.trim() : "";
        this.port     = port;
        this.keyName  = (keyName  != null) ? keyName.trim()  : "";
        this.body     = (body     != null) ? body             : "";
    }

    /** Returns {@code true} when all required fields are filled in. */
    public boolean isValid() {
        return !targetIp.isEmpty() && !keyName.isEmpty() && !body.isBlank();
    }

    @Override
    public String toString() {
        return "StagedMessage{ip=" + targetIp +
               ", port=" + port +
               ", key=" + keyName +
               ", bodyLen=" + body.length() + "}";
    }
}
