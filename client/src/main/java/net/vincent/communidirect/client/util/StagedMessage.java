package net.vincent.communidirect.client.util;

/**
 * Immutable data bag for a message that has been composed but not yet sent.
 * Instances are produced by {@link MessageParser} from either a raw editor
 * temp-file or a persisted staged TOML file.
 */
public final class StagedMessage {

    /** Destination IPv4/IPv6 address or hostname for message delivery. */
    public final String targetIp;

    /** Destination TCP port for the remote server. */
    public final int    port;

    /** Alias of the recipient's public key in the local key store. */
    public final String keyName;

    /** Plaintext message body. */
    public final String body;

    /**
     * Constructs a {@code StagedMessage}, trimming whitespace from string fields
     * and guarding against {@code null} inputs.
     *
     * @param targetIp destination IPv4/IPv6 address or hostname
     * @param port     destination TCP port (1–65535)
     * @param keyName  alias of the recipient's public key in the local key store
     * @param body     plaintext message body
     */
    public StagedMessage(String targetIp, int port, String keyName, String body) {
        this.targetIp = (targetIp != null) ? targetIp.trim() : "";
        this.port     = port;
        this.keyName  = (keyName  != null) ? keyName.trim()  : "";
        this.body     = (body     != null) ? body             : "";
    }

    /**
     * Returns {@code true} when all required fields are filled in.
     *
     * @return {@code true} if targetIp, keyName, and body are all non-empty; {@code false} otherwise
     */
    public boolean isValid() {
        return !targetIp.isEmpty() && !keyName.isEmpty() && !body.isBlank();
    }

    /**
     * Returns a compact debug representation containing all fields.
     *
     * @return string in the form {@code StagedMessage{ip=…, port=…, key=…, bodyLen=…}}
     */
    @Override
    public String toString() {
        return "StagedMessage{ip=" + targetIp +
               ", port=" + port +
               ", key=" + keyName +
               ", bodyLen=" + body.length() + "}";
    }
}
