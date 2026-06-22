package org.pmoci.kskillauth;

import android.util.Base64;

import org.signal.argon2.Argon2;
import org.signal.argon2.MemoryCost;
import org.signal.argon2.Type;
import org.signal.argon2.Version;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * OTP redesign crypto primitives (all on-device):
 *   DEK         = Argon2id(userKey, salt)          -- key derivation
 *   ciphertext  = AES-256-GCM(DEK, loginSecret)    -- protects the login-secret with userKey
 *   verifier    = SHA-256(loginSecret) (hex)       -- what the server stores
 *   proof       = SHA-256(verifier || cid || nonce) (hex)
 *
 * The userKey, the DEK and the login-secret never leave the device in plaintext.
 */
final class CryptoUtil {
    // Argon2id parameters (design §6). Tune for the target phones if needed.
    private static final int ARGON2_MEMORY_MIB = 64;
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_PARALLELISM = 1;
    private static final int ARGON2_HASH_LENGTH = 32; // 256-bit AES key

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {
    }

    static byte[] randomBytes(int length) {
        byte[] out = new byte[length];
        SECURE_RANDOM.nextBytes(out);
        return out;
    }

    /** DEK = Argon2id(userKey, salt). */
    static byte[] deriveKey(String userKey, byte[] salt) throws Exception {
        Argon2 argon2 = new Argon2.Builder(Version.V13)
                .type(Type.Argon2id)
                .memoryCost(MemoryCost.MiB(ARGON2_MEMORY_MIB))
                .parallelism(ARGON2_PARALLELISM)
                .iterations(ARGON2_ITERATIONS)
                .hashLength(ARGON2_HASH_LENGTH)
                .build();
        Argon2.Result result = argon2.hash(userKey.getBytes(StandardCharsets.UTF_8), salt);
        return result.getHash();
    }

    /** AES-256-GCM encrypt. Returns iv||ciphertext concatenated. */
    static byte[] aesGcmEncrypt(byte[] key, byte[] plaintext) throws Exception {
        byte[] iv = randomBytes(GCM_IV_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }

    /** AES-256-GCM decrypt of iv||ciphertext. Throws (bad tag) when the key/userKey is wrong. */
    static byte[] aesGcmDecrypt(byte[] key, byte[] ivAndCiphertext) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] ciphertext = new byte[ivAndCiphertext.length - GCM_IV_BYTES];
        System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_BYTES);
        System.arraycopy(ivAndCiphertext, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    static String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    static String sha256HexOfBytes(byte[] input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(input));
    }

    static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static String base64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    static byte[] fromBase64(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }
}
