package net.vincent.communidirect.common.crypto;

import com.moandjiezana.toml.TomlWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Generates an Ed25519 identity key pair and persists it under
 * {@code ~/.communidirect/}.
 *
 * <pre>
 *   ~/.communidirect/identity.key  – PKCS8 DER private key  (chmod 600)
 *   ~/.communidirect/keys/self.pub – 32-byte raw Ed25519 public key
 *   ~/.communidirect/keys.toml     – path manifest consumed by KeyStoreManager
 * </pre>
 *
 * The class is also the single source of truth for the encode/decode helpers
 * that convert between Java's 44-byte SubjectPublicKeyInfo DER form and the
 * 32-byte raw wire form required by the CDIR protocol.
 */
public final class KeyGenerator {

    private static final String ROOT      = System.getProperty("user.home") + "/.communidirect";
    private static final String PRIV_FILE = ROOT + "/identity.key";
    private static final String KEYS_DIR  = ROOT + "/keys";
    private static final String PUB_FILE  = KEYS_DIR + "/self.pub";
    private static final String KEYS_TOML = ROOT + "/keys.toml";

    /**
     * Fixed 12-byte DER prefix that Java prepends when encoding an Ed25519
     * public key as SubjectPublicKeyInfo (44 bytes total = 12 header + 32 raw).
     */
    static final byte[] ED25519_SPKI_HEADER = {
        (byte) 0x30, (byte) 0x2a,
        (byte) 0x30, (byte) 0x05,
        (byte) 0x06, (byte) 0x03,
        (byte) 0x2b, (byte) 0x65, (byte) 0x70,
        (byte) 0x03, (byte) 0x21, (byte) 0x00
    };

    private KeyGenerator() { /* utility class */ }

    // -------------------------------------------------------------------------
    // Key generation
    // -------------------------------------------------------------------------

    /**
     * Generates a fresh Ed25519 key pair, stores it on disk, writes
     * {@code keys.toml} and returns the in-memory {@link KeyPair}.
     *
     * @return the newly generated key pair
     * @throws Exception on any IO or JCA error
     */
    public static KeyPair generate() throws Exception {
        Files.createDirectories(Paths.get(KEYS_DIR));

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();

        // ---- private key (PKCS8 DER, chmod 600) ----------------------------
        Path privPath = Paths.get(PRIV_FILE);
        Files.write(privPath, keyPair.getPrivate().getEncoded());
        setPosixPermissions600(privPath);

        // ---- public key (raw 32 bytes) --------------------------------------
        byte[] rawPub = toRaw32(keyPair.getPublic().getEncoded());
        Files.write(Paths.get(PUB_FILE), rawPub);

        // ---- keys.toml ------------------------------------------------------
        writeKeysToml();

        System.out.println("[KeyGenerator] Ed25519 key pair generated.");
        System.out.println("[KeyGenerator]   Private: " + PRIV_FILE);
        System.out.println("[KeyGenerator]   Public:  " + PUB_FILE);

        return keyPair;
    }

    // -------------------------------------------------------------------------
    // SPKI ↔ raw-32 conversion helpers (used by KeyStoreManager & CdirMessage)
    // -------------------------------------------------------------------------

    /**
     * Strips the 12-byte Ed25519 SPKI DER header from {@code spkiEncoded}
     * (44 bytes) and returns the 32 raw key bytes suitable for wire transfer
     * or direct file storage.
     *
     * @param spkiEncoded 44-byte SubjectPublicKeyInfo-encoded public key
     * @return 32 raw Ed25519 public key bytes
     */
    public static byte[] toRaw32(byte[] spkiEncoded) {
        if (spkiEncoded.length == 32) return spkiEncoded;
        if (spkiEncoded.length < 44) {
            throw new IllegalArgumentException(
                "Unexpected Ed25519 SPKI length: " + spkiEncoded.length);
        }
        byte[] raw = new byte[32];
        System.arraycopy(spkiEncoded, spkiEncoded.length - 32, raw, 0, 32);
        return raw;
    }

    /**
     * Prepends the fixed 12-byte Ed25519 SPKI DER header to 32 raw key bytes
     * so that JCA can reconstruct a {@link java.security.PublicKey} via
     * {@link java.security.spec.X509EncodedKeySpec}.
     *
     * @param raw32 32-byte raw Ed25519 public key
     * @return 44-byte SubjectPublicKeyInfo-encoded public key
     */
    public static byte[] toSpki(byte[] raw32) {
        if (raw32.length != 32) {
            throw new IllegalArgumentException("Expected 32-byte raw key, got " + raw32.length);
        }
        byte[] spki = new byte[44];
        System.arraycopy(ED25519_SPKI_HEADER, 0, spki, 0, 12);
        System.arraycopy(raw32, 0, spki, 12, 32);
        return spki;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static void writeKeysToml() throws IOException {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("private_key_path", PRIV_FILE);

        Map<String, Object> peers = new LinkedHashMap<>();
        peers.put("public_keys_dir", KEYS_DIR + "/");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("local", local);
        root.put("peers", peers);

        new TomlWriter().write(root, Paths.get(KEYS_TOML).toFile());
        System.out.println("[KeyGenerator] keys.toml written to: " + KEYS_TOML);
    }

    private static void setPosixPermissions600(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            System.err.println("[KeyGenerator] POSIX permissions not supported on this platform.");
        } catch (IOException e) {
            System.err.println("[KeyGenerator] Failed to set 600 on " + path + ": " + e.getMessage());
        }
    }
}
