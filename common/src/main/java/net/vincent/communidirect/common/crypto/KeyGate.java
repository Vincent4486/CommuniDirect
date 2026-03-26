package net.vincent.communidirect.common.crypto;

/**
 * Stateful wrapper around {@link CryptoEngine} that holds a symmetric key and
 * exposes high-level encrypt / decrypt / avatar operations.
 *
 * <p>Both the server and client modules depend on {@code communidirect-common}, so
 * this single class provides a unified cryptographic entry-point for both sides
 * of a connection without duplicating logic.
 *
 * <p>Usage example:
 * <pre>
 *   // Server – generate a fresh session key
 *   KeyGate gate = KeyGate.withNewKey(32);
 *   byte[] cipher = gate.encrypt("Hello, peer!".getBytes());
 *
 *   // Client – reconstruct gate from the exchanged key bytes
 *   KeyGate gate = new KeyGate(receivedKeyBytes);
 *   String plain = new String(gate.decrypt(cipher));
 *
 *   // Show visual identity in terminal
 *   System.out.println(gate.getAvatar());
 * </pre>
 */
public final class KeyGate {

    private final byte[] key;

    /**
     * Creates a {@code KeyGate} from an existing key.
     *
     * @param key non-null, non-empty key bytes; a defensive copy is stored internally
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public KeyGate(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key must not be null or empty.");
        }
        this.key = key.clone();
    }

    /**
     * Factory method – generates a new cryptographically-strong random key.
     *
     * @param size key length in bytes (must be > 0)
     * @return a ready-to-use {@code KeyGate}
     */
    public static KeyGate withNewKey(int size) {
        return new KeyGate(CryptoEngine.generateSecureKey(size));
    }

    // -------------------------------------------------------------------------
    // Operations
    // -------------------------------------------------------------------------

    /**
     * Encrypts {@code data} using the stored key (XOR cipher).
     *
     * @param data plaintext bytes
     * @return ciphertext bytes
     */
    public byte[] encrypt(byte[] data) {
        return CryptoEngine.xorTransform(data, key);
    }

    /**
     * Decrypts {@code data} using the stored key.  Because XOR is its own
     * inverse, this is identical to {@link #encrypt(byte[])}.
     *
     * @param data ciphertext bytes
     * @return plaintext bytes
     */
    public byte[] decrypt(byte[] data) {
        return CryptoEngine.xorTransform(data, key);
    }

    /**
     * Returns a 5×5 symmetric ASCII art string that visually identifies this
     * key in a terminal.  Peers sharing the same key will see the same avatar.
     *
     * @return multi-line avatar string
     */
    public String getAvatar() {
        return CryptoEngine.getSymmetricAvatar(key);
    }

    /**
     * Returns a defensive copy of the raw key bytes, suitable for transmission
     * to a peer during key exchange.
     *
     * @return copy of the key bytes
     */
    public byte[] getKey() {
        return key.clone();
    }
}
