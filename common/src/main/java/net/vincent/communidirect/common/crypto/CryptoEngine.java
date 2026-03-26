package net.vincent.communidirect.common.crypto;

import java.security.SecureRandom;

/**
 * Core cryptographic primitives for CommuniDirect.
 *
 * <ul>
 *   <li>XOR cipher – fast, symmetric, key-stream based</li>
 *   <li>Secure key generation via {@link SecureRandom}</li>
 *   <li>Symmetric ASCII avatar – visual terminal identity derived from a key</li>
 * </ul>
 *
 * All methods are stateless and thread-safe.
 */
public final class CryptoEngine {

    /** Characters used when rendering the identity avatar. */
    private static final char[] AVATAR_CHARS = { '#', '@', '*', '+', '=', 'X', 'O' };

    private CryptoEngine() { /* utility class */ }

    // -------------------------------------------------------------------------
    // XOR cipher
    // -------------------------------------------------------------------------

    /**
     * Applies a repeating XOR key-stream to {@code data}.  The same call encrypts
     * and decrypts (XOR is its own inverse).
     *
     * @param data non-null byte array to transform
     * @param key  non-null, non-empty key; shorter keys are repeated cyclically
     * @return a new byte array of the same length as {@code data}
     * @throws IllegalArgumentException if {@code data} or {@code key} is null / empty
     */
    public static byte[] xorTransform(byte[] data, byte[] key) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null.");
        }
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("key must not be null or empty.");
        }

        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Key generation
    // -------------------------------------------------------------------------

    /**
     * Generates a cryptographically-strong random key of the given size.
     *
     * @param size key length in bytes (must be > 0)
     * @return freshly-generated key bytes
     * @throws IllegalArgumentException if {@code size} is not positive
     */
    public static byte[] generateSecureKey(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Key size must be positive.");
        }
        byte[] key = new byte[size];
        new SecureRandom().nextBytes(key);
        return key;
    }

    // -------------------------------------------------------------------------
    // Symmetric ASCII avatar
    // -------------------------------------------------------------------------

    /**
     * Derives a 5×5 symmetric ASCII art string from a key, suitable for
     * terminal-based peer identification.  Left and right halves mirror each
     * other; the centre column is shared.
     *
     * <pre>
     * Example output (5 rows × 5 cols, columns separated by spaces):
     *
     *   # @ * @ #
     *   X O + O X
     *   = # @ # =
     *   X O + O X
     *   # @ * @ #
     * </pre>
     *
     * @param key non-null, non-empty byte array used to seed the avatar pattern
     * @return multi-line string representation of the avatar
     * @throws IllegalArgumentException if {@code key} is null or empty
     */
    public static String getSymmetricAvatar(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("key must not be null or empty.");
        }

        // Build the left-half columns (indices 0–2) of a 5×5 grid, then mirror.
        int[][] grid = new int[5][5];
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= 2; col++) {
                int byteIndex = (row * 3 + col) % key.length;
                int charIndex = Math.abs(key[byteIndex]) % AVATAR_CHARS.length;
                grid[row][col]     = charIndex;
                grid[row][4 - col] = charIndex;   // horizontal mirror
            }
        }

        StringBuilder sb = new StringBuilder(5 * (5 * 2) /* chars + spaces */);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                sb.append(AVATAR_CHARS[grid[row][col]]);
                if (col < 4) sb.append(' ');
            }
            if (row < 4) sb.append('\n');
        }
        return sb.toString();
    }
}
