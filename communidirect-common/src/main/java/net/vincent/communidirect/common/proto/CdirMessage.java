package net.vincent.communidirect.common.proto;

import net.vincent.communidirect.common.crypto.CryptoEngine;
import net.vincent.communidirect.common.crypto.KeyGenerator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;

/**
 * Codec for the <b>CDIR v1</b> message protocol.
 *
 * <h2>Wire format (all integers big-endian)</h2>
 * <pre>
 *  Offset  Size  Field
 *  ------  ----  -----------------------------------------------
 *       0     4  Magic            0x43 0x44 0x49 0x52  ("CDIR")
 *       4     1  Version          0x01
 *       5    64  Signature        Ed25519 sig over payload plaintext
 *      69    32  Sender PubKey    Raw 32-byte Ed25519 public key
 *     101   256  Sealed Sess. Key XOR-wrapped 32-byte session key, zero-padded
 *     357     4  Payload Length   Unsigned 32-bit big-endian int
 *     361   var  XORed Payload    Payload XOR'd with repeating session key
 * </pre>
 *
 * <h2>Session key wrapping</h2>
 * Because Ed25519 is a signing algorithm (not a KEM), the session key is
 * wrapped using a key-derivation function over the recipient's public key:
 * <pre>
 *   wrapKey  = SHA-256(recipientRawPubKey32)          // 32 bytes
 *   sealed   = XOR(sessionKey32, wrapKey)             // 32 bytes
 *   wire[0..31] = sealed; wire[32..255] = 0x00        // 256 bytes total
 * </pre>
 * The recipient unseals by computing {@code SHA-256(ownRawPubKey32)} and
 * XOR-ing it against the first 32 bytes of the field.
 */
public final class CdirMessage {

    // -------------------------------------------------------------------------
    // Protocol constants
    // -------------------------------------------------------------------------

    /** Four ASCII bytes "CDIR" interpreted as a big-endian int. */
    public static final int  MAGIC       = 0x43444952;
    public static final byte VERSION     = 0x01;

    public static final int HEADER_SIZE  = 4 + 1 + 64 + 32 + 256 + 4; // 361 bytes

    private static final int OFF_MAGIC      =   0;
    private static final int OFF_VERSION    =   4;
    private static final int OFF_SIG        =   5;
    private static final int OFF_SENDER_PUB =  69;
    private static final int OFF_SESSION    = 101;
    private static final int OFF_PAY_LEN   = 357;

    // -------------------------------------------------------------------------
    // Data model
    // -------------------------------------------------------------------------

    /** 32-byte raw Ed25519 public key of the sender. */
    public final byte[] senderPubKeyRaw;

    /** Decrypted (plaintext) payload bytes. */
    public final byte[] payload;

    /**
     * Hex-encoded SHA-256 digest of the sender's raw public key – used as a
     * human-readable identity label in stored message headers.
     */
    public final String senderPubKeyHash;

    /**
     * Private constructor – instances are created only by {@link #decode}.
     *
     * @param senderPubKeyRaw 32-byte raw Ed25519 public key of the sender
     * @param payload         decrypted plaintext payload bytes
     */
    private CdirMessage(byte[] senderPubKeyRaw, byte[] payload) {
        this.senderPubKeyRaw  = senderPubKeyRaw;
        this.payload          = payload;
        this.senderPubKeyHash = sha256Hex(senderPubKeyRaw);
    }

    // -------------------------------------------------------------------------
    // Encoding (client → wire)
    // -------------------------------------------------------------------------

    /**
     * Encodes a plaintext {@code payload} into a CDIR frame and writes it to
     * {@code out}.
     *
     * @param payload           plaintext bytes to send
     * @param senderPrivKey     sender's Ed25519 private key (for signing)
     * @param senderPubKeyRaw   sender's 32-byte raw Ed25519 public key
     * @param recipientPubRaw   recipient's 32-byte raw Ed25519 public key
     *                          (used to wrap the session key)
     * @param out               destination stream
     * @throws Exception on any crypto or IO failure
     */
    public static void encode(byte[]     payload,
                              PrivateKey senderPrivKey,
                              byte[]     senderPubKeyRaw,
                              byte[]     recipientPubRaw,
                              OutputStream out) throws Exception {

        // 1. Generate a random session key and XOR-encrypt the payload.
        byte[] sessionKey     = CryptoEngine.generateSecureKey(32);
        byte[] encryptedPayload = CryptoEngine.xorTransform(payload, sessionKey);

        // 2. Sign the plaintext payload with the sender's private key.
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(senderPrivKey);
        signer.update(payload);
        byte[] signature = signer.sign(); // always 64 bytes for Ed25519

        // 3. Seal the session key: XOR with SHA-256(recipientPubKey).
        byte[] wrapKey        = sha256(recipientPubRaw);
        byte[] sealedRaw      = CryptoEngine.xorTransform(sessionKey, wrapKey);
        byte[] sealedPadded   = new byte[256];
        System.arraycopy(sealedRaw, 0, sealedPadded, 0, 32);

        // 4. Write the frame.
        ByteBuffer hdr = ByteBuffer.allocate(HEADER_SIZE);
        hdr.putInt(MAGIC);
        hdr.put(VERSION);
        hdr.put(signature);          // 64 bytes
        hdr.put(senderPubKeyRaw);    // 32 bytes
        hdr.put(sealedPadded);       // 256 bytes
        hdr.putInt(encryptedPayload.length);
        out.write(hdr.array());
        out.write(encryptedPayload);
        out.flush();
    }

    // -------------------------------------------------------------------------
    // Decoding (wire → server)
    // -------------------------------------------------------------------------

    /**
     * Reads one CDIR frame from {@code in}, verifies its signature, decrypts the
     * payload and returns a populated {@link CdirMessage}.
     *
     * @param in                source stream (must be positioned at the first
     *                          magic byte)
     * @param recipientPrivKey  server's Ed25519 private key (unused for Ed25519
     *                          KEM – kept for future asymmetric upgrade)
     * @param recipientPubRaw   server's own 32-byte raw Ed25519 public key
     *                          (used to unseal the session key)
     * @return decoded, signature-verified message
     * @throws IOException        on read errors
     * @throws SignatureException  if the Ed25519 signature does not verify
     * @throws Exception           on any other crypto or protocol error
     */
    public static CdirMessage decode(InputStream in,
                                     PrivateKey  recipientPrivKey,
                                     byte[]      recipientPubRaw) throws Exception {

        DataInputStream dis = (in instanceof DataInputStream)
            ? (DataInputStream) in
            : new DataInputStream(in);

        // ---- magic + version ------------------------------------------------
        int  magic   = dis.readInt();
        byte version = dis.readByte();
        if (magic != MAGIC) {
            throw new IOException(String.format("Bad CDIR magic: 0x%08X", magic));
        }
        if (version != VERSION) {
            throw new IOException(String.format("Unsupported CDIR version: 0x%02X", version));
        }

        // ---- signature (64 bytes) -------------------------------------------
        byte[] signature = new byte[64];
        dis.readFully(signature);

        // ---- sender public key (32 bytes) -----------------------------------
        byte[] senderPubRaw = new byte[32];
        dis.readFully(senderPubRaw);

        // ---- sealed session key (256 bytes) ---------------------------------
        byte[] sealedPadded = new byte[256];
        dis.readFully(sealedPadded);

        // ---- XORed payload --------------------------------------------------
        int    payloadLen       = dis.readInt();
        byte[] encryptedPayload = new byte[payloadLen];
        dis.readFully(encryptedPayload);

        // ---- Unseal session key using own public key ------------------------
        byte[] wrapKey    = sha256(recipientPubRaw);
        byte[] sealedRaw  = new byte[32];
        System.arraycopy(sealedPadded, 0, sealedRaw, 0, 32);
        byte[] sessionKey = CryptoEngine.xorTransform(sealedRaw, wrapKey);

        // ---- Decrypt payload ------------------------------------------------
        byte[] plaintext = CryptoEngine.xorTransform(encryptedPayload, sessionKey);

        // ---- Verify Ed25519 signature over plaintext ------------------------
        PublicKey senderPub = reconstructPublicKey(senderPubRaw);
        Signature verifier  = Signature.getInstance("Ed25519");
        verifier.initVerify(senderPub);
        verifier.update(plaintext);
        if (!verifier.verify(signature)) {
            throw new SignatureException("CDIR signature verification failed – message rejected.");
        }

        return new CdirMessage(senderPubRaw, plaintext);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Reconstructs a JCA {@link PublicKey} from a 32-byte raw Ed25519 public key
     * by prepending the fixed SPKI DER header via {@link KeyGenerator#toSpki}.
     *
     * @param raw32 32-byte raw Ed25519 public key
     * @return the corresponding {@link PublicKey}
     * @throws Exception on any JCA error
     */
    private static PublicKey reconstructPublicKey(byte[] raw32) throws Exception {
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("Ed25519");
        return kf.generatePublic(
            new java.security.spec.X509EncodedKeySpec(KeyGenerator.toSpki(raw32)));
    }

    /**
     * Computes the SHA-256 digest of {@code input}.
     *
     * @param input bytes to hash
     * @return 32-byte SHA-256 digest
     * @throws RuntimeException if SHA-256 is not available in the JCA provider
     */
    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of {@code input}.
     *
     * @param input bytes to hash
     * @return 64-character lowercase hex string
     */
    private static String sha256Hex(byte[] input) {
        byte[] hash = sha256(input);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
