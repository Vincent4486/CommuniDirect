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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private static final String ROOT_DIR = System.getProperty("user.home") + "/.communidirect";

    private PrivateKey              privateKey;
    private byte[]                  ownPublicKeyRaw;
    private final Map<String, PublicKey> peerKeys = new HashMap<>();
    private final Map<String, PrivateKey> privateKeys = new HashMap<>();
    private final Map<String, byte[]> ownPublicKeysRaw = new HashMap<>();
    private String activePrivateKeyAlias = "self";

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
        String legacyPrivPath = toml.getString("local.private_key_path", ROOT_DIR + "/private_keys/self.key");
        String privDir = toml.getString("local.private_keys_dir", derivePrivateDirFromLegacyPath(legacyPrivPath));
        String activeAlias = toml.getString("local.active_private_key", deriveAliasFromPath(legacyPrivPath));
        String pubDir = toml.getString("peers.public_keys_dir", ROOT_DIR + "/keys/");

        loadPrivateKeys(privDir, pubDir, activeAlias, legacyPrivPath);
        loadPeerKeys(pubDir);

        System.out.printf("[KeyStoreManager] Ready. Loaded %d private key(s) and %d peer key(s). Active=%s%n",
            privateKeys.size(),
            peerKeys.size(),
            activePrivateKeyAlias);
    }

    // -------------------------------------------------------------------------
    // Internal loaders
    // -------------------------------------------------------------------------

    /**
     * Reads and decodes the PKCS8-DER private key at {@code path}.
     *
     * @param path absolute path to the {@code identity.key} file
     * @throws Exception on any IO or JCA error
     */
    private void loadPrivateKeys(String privateDir, String publicDir, String preferredAlias, String legacyPrivPath) throws Exception {
        privateKeys.clear();
        ownPublicKeysRaw.clear();

        Path privDirPath = Paths.get(privateDir);
        if (!Files.isDirectory(privDirPath)) {
            System.err.println("[KeyStoreManager] Private keys directory missing: " + privateDir);
            return;
        }

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        List<String> loadedAliases = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(privDirPath, "*.key")) {
            for (Path keyFile : stream) {
                String filename = keyFile.getFileName().toString();
                String alias = filename.substring(0, filename.length() - 4);
                try {
                    byte[] encoded = Files.readAllBytes(keyFile);
                    PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));

                    Path pubPath = Paths.get(publicDir, alias + ".pub");
                    if (!Files.exists(pubPath) && "self".equals(alias)) {
                        pubPath = Paths.get(publicDir, "self.pub");
                    }
                    if (!Files.exists(pubPath)) {
                        System.err.println("[KeyStoreManager] Missing public key for private alias " + alias + " at " + pubPath);
                        continue;
                    }

                    byte[] pubBytes = Files.readAllBytes(pubPath);
                    byte[] raw = (pubBytes.length == 32) ? pubBytes : KeyGenerator.toRaw32(pubBytes);

                    privateKeys.put(alias, pk);
                    ownPublicKeysRaw.put(alias, raw);
                    loadedAliases.add(alias);
                    System.out.println("[KeyStoreManager] Loaded private key alias: " + alias);
                } catch (Exception e) {
                    System.err.println("[KeyStoreManager] Skipped invalid private key file " + filename + ": " + e.getMessage());
                }
            }
        }

        if (loadedAliases.isEmpty()) {
            Path legacyPath = Paths.get(legacyPrivPath);
            if (Files.exists(legacyPath)) {
                String legacyAlias = deriveAliasFromPath(legacyPrivPath);
                if (!legacyAlias.endsWith(".key") && "identity".equals(legacyAlias)) {
                    legacyAlias = "self";
                }
                try {
                    byte[] encoded = Files.readAllBytes(legacyPath);
                    PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
                    Path pubPath = Paths.get(publicDir, legacyAlias + ".pub");
                    if (!Files.exists(pubPath)) {
                        pubPath = Paths.get(publicDir, "self.pub");
                    }
                    if (Files.exists(pubPath)) {
                        byte[] pubBytes = Files.readAllBytes(pubPath);
                        byte[] raw = (pubBytes.length == 32) ? pubBytes : KeyGenerator.toRaw32(pubBytes);
                        privateKeys.put(legacyAlias, pk);
                        ownPublicKeysRaw.put(legacyAlias, raw);
                        loadedAliases.add(legacyAlias);
                        System.out.println("[KeyStoreManager] Loaded legacy private key path: " + legacyPath);
                    }
                } catch (Exception e) {
                    System.err.println("[KeyStoreManager] Failed to load legacy private key path " + legacyPath + ": " + e.getMessage());
                }
            }
        }

        loadedAliases.sort(String::compareTo);
        if (loadedAliases.isEmpty()) {
            throw new IllegalStateException("No usable private keys found in " + privateDir);
        }

        if (privateKeys.containsKey(preferredAlias)) {
            setActivePrivateKey(preferredAlias);
        } else {
            setActivePrivateKey(loadedAliases.getFirst());
        }
    }

    /**
     * Scans the given directory for {@code *.pub} files and loads each one as a
     * peer Ed25519 public key.  Both 32-byte raw and 44-byte SPKI-encoded files
     * are accepted.  The map key is derived from the filename without its
     * {@code .pub} extension.
     *
     * <p>Malformed key files are skipped with a warning; they do not abort the load.
     *
     * @param dir path to the directory containing {@code *.pub} peer key files
     * @throws Exception on catastrophic IO errors (individual bad files are tolerated)
     */
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

    /**
     * Returns the local Ed25519 private key.
     *
     * @return this system's Ed25519 private key for signing outbound messages
     */
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PrivateKey getPrivateKey(String alias) {
        return privateKeys.get(alias);
    }

    /**
     * Returns the server's own 32-byte raw Ed25519 public key, loaded from
     * {@code keys/self.pub}.  Required by {@link net.vincent.communidirect.common.proto.CdirMessage#decode}.
     *
     * @return the local 32-byte raw Ed25519 public key
     */
    public byte[] getOwnPublicKeyRaw() {
        return ownPublicKeyRaw;
    }

    public byte[] getOwnPublicKeyRaw(String alias) {
        return ownPublicKeysRaw.get(alias);
    }

    public String getActivePrivateKeyAlias() {
        return activePrivateKeyAlias;
    }

    public boolean setActivePrivateKey(String alias) {
        PrivateKey selected = privateKeys.get(alias);
        byte[] selectedPub = ownPublicKeysRaw.get(alias);
        if (selected == null || selectedPub == null) {
            return false;
        }
        activePrivateKeyAlias = alias;
        privateKey = selected;
        ownPublicKeyRaw = selectedPub;
        return true;
    }

    public Map<String, PrivateKey> getAllPrivateKeys() {
        return Collections.unmodifiableMap(privateKeys);
    }

    public List<String> getAllPrivateKeyAliases() {
        List<String> aliases = new ArrayList<>(privateKeys.keySet());
        aliases.sort(String::compareTo);
        return aliases;
    }

    /**
     * Returns the public key for a peer alias, or {@code null} if unknown.
     *
     * @param alias peer alias (filename without {@code .pub})
     * @return the {@link PublicKey} for the alias, or {@code null} if not found
     */
    public PublicKey getPeerKey(String alias) {
        return peerKeys.get(alias);
    }

    /**
     * Returns an unmodifiable view of all loaded peer public keys, keyed by
     * alias.
     *
     * @return an immutable map of peer aliases to their Ed25519 public keys
     */
    public Map<String, PublicKey> getAllPeerKeys() {
        return Collections.unmodifiableMap(peerKeys);
    }

    private static String derivePrivateDirFromLegacyPath(String legacyPath) {
        try {
            Path p = Paths.get(legacyPath);
            Path parent = p.getParent();
            if (parent != null) {
                return parent.toString();
            }
        } catch (Exception ignored) {
        }
        return ROOT_DIR + "/private_keys";
    }

    private static String deriveAliasFromPath(String path) {
        try {
            String name = Paths.get(path).getFileName().toString();
            if (name.endsWith(".key")) {
                return name.substring(0, name.length() - 4);
            }
            return name;
        } catch (Exception ignored) {
            return "self";
        }
    }
}
