package net.vincent.communidirect.common.crypto;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the local identity keys and all trusted peer public keys as described
 * in {@code ~/.communidirect/keys.toml}.
 *
 * <p>On first run, if {@code keys.toml} is absent, delegates to
 * {@link KeyGenerator#generate()} to create a fresh identity before loading.
 *
 * <p>Peer public-key discovery uses a {@code *.pub} glob over the configured
 * {@code peers.public_keys_dir}.  Each file must contain either 32 raw Ed25519
 * bytes or the full 44-byte SubjectPublicKeyInfo DER encoding; both forms are
 * accepted transparently.  The map key is the filename stripped of its
 * {@code .pub} extension (e.g. {@code "vincent"} for {@code vincent.pub}).
 */
public class KeyStoreManager {

    private static final String KEYS_TOML =
        System.getProperty("user.home") + "/.communidirect/keys.toml";

    private PrivateKey              privateKey;
    private byte[]                  ownPublicKeyRaw;
    private final Map<String, PublicKey> peerKeys = new HashMap<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Loads keys from disk.  Generates a fresh identity if no {@code keys.toml}
     * is found.
     *
     * @throws Exception on any IO or JCA error
     */
    public void load() throws Exception {
        Path tomlPath = Paths.get(KEYS_TOML);
        if (!Files.exists(tomlPath)) {
            System.out.println("[KeyStoreManager] keys.toml not found – generating new identity.");
            KeyGenerator.generate();
        }

        Toml toml   = new Toml().read(tomlPath.toFile());
        String priv = toml.getString("local.private_key_path");
        String pub  = toml.getString("peers.public_keys_dir");

        loadPrivateKey(priv);
        loadOwnPublicKey(pub);
        loadPeerKeys(pub);

        System.out.printf("[KeyStoreManager] Ready. Loaded private key and %d peer key(s).%n",
                          peerKeys.size());
    }

    // -------------------------------------------------------------------------
    // Internal loaders
    // -------------------------------------------------------------------------

    private void loadPrivateKey(String path) throws Exception {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        KeyFactory kf  = KeyFactory.getInstance("Ed25519");
        privateKey     = kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    /** Loads the 32-byte raw public key from {@code <pubsDir>/self.pub}. */
    private void loadOwnPublicKey(String dir) {
        Path selfPub = Paths.get(dir, "self.pub");
        if (!Files.exists(selfPub)) {
            System.err.println("[KeyStoreManager] self.pub not found at: " + selfPub);
            return;
        }
        try {
            byte[] raw = Files.readAllBytes(selfPub);
            ownPublicKeyRaw = (raw.length == 32) ? raw
                : KeyGenerator.toRaw32(raw);
        } catch (IOException e) {
            System.err.println("[KeyStoreManager] Failed to read self.pub: " + e.getMessage());
        }
    }

    private void loadPeerKeys(String dir) throws Exception {
        Path dirPath = Paths.get(dir);
        if (!Files.isDirectory(dirPath)) {
            System.err.println("[KeyStoreManager] Peer keys directory missing: " + dir);
            return;
        }

        KeyFactory kf = KeyFactory.getInstance("Ed25519");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath, "*.pub")) {
            for (Path pubFile : stream) {
                String filename = pubFile.getFileName().toString();
                String alias    = filename.endsWith(".pub")
                    ? filename.substring(0, filename.length() - 4)
                    : filename;

                try {
                    byte[] fileBytes = Files.readAllBytes(pubFile);
                    // Accept both raw-32 and full-44-byte SPKI encodings.
                    byte[] spki = (fileBytes.length == 32)
                        ? KeyGenerator.toSpki(fileBytes)
                        : fileBytes;

                    PublicKey key = kf.generatePublic(new X509EncodedKeySpec(spki));
                    peerKeys.put(alias, key);
                    System.out.println("[KeyStoreManager] Loaded peer key: " + alias);
                } catch (Exception e) {
                    System.err.println("[KeyStoreManager] Skipped invalid key file " +
                                       filename + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[KeyStoreManager] Failed to scan peer keys dir: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the local Ed25519 private key. */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    /**
     * Returns the server's own 32-byte raw Ed25519 public key, loaded from
     * {@code keys/self.pub}.  Required by {@link net.vincent.communidirect.common.proto.CdirMessage#decode}.
     */
    public byte[] getOwnPublicKeyRaw() {
        return ownPublicKeyRaw;
    }

    /**
     * Returns the public key for a peer alias, or {@code null} if unknown.
     *
     * @param alias peer alias (filename without {@code .pub})
     */
    public PublicKey getPeerKey(String alias) {
        return peerKeys.get(alias);
    }

    /**
     * Returns an unmodifiable view of all loaded peer public keys, keyed by
     * alias.
     */
    public Map<String, PublicKey> getAllPeerKeys() {
        return Collections.unmodifiableMap(peerKeys);
    }
}
