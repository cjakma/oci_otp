package org.pmoci.kskillauth;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * On-device storage of enrollment material.
 *
 * The userKey itself is not stored. Login approval uses the stored loginSecret after the
 * app-level device-auth gate has succeeded.
 */
final class LocalCredentialStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String LOGIN_SECRET_KEY_ALIAS = "pmoci_otp_login_secret_key";
    private static final String PREFS = "pmoci_otp_credential";
    private static final String KEY_SALT = "salt_b64";
    private static final String KEY_CIPHERTEXT = "ciphertext_b64"; // Legacy: userKey-wrapped loginSecret.
    private static final String KEY_LOGIN_SECRET_CIPHERTEXT = "login_secret_ciphertext_b64"; // iv || ciphertext

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private LocalCredentialStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String scoped(String key, String accountId, String deviceId) {
        String account = accountId == null || accountId.trim().isEmpty() ? "AAAA" : accountId.trim();
        String device = deviceId == null || deviceId.trim().isEmpty() ? "A" : deviceId.trim();
        return account + ":" + device + ":" + key;
    }

    static boolean isEnrolled(Context context) {
        return isEnrolled(context, AppPrefs.accountId(context), AppPrefs.deviceId(context));
    }

    private static boolean allowLegacyFallback(String accountId, String deviceId) {
        String account = accountId == null || accountId.trim().isEmpty() ? "AAAA" : accountId.trim();
        String device = deviceId == null || deviceId.trim().isEmpty() ? "A" : deviceId.trim();
        return "AAAA".equals(account) && "A".equals(device);
    }

    static boolean isEnrolled(Context context, String accountId, String deviceId) {
        return prefs(context).contains(scoped(KEY_LOGIN_SECRET_CIPHERTEXT, accountId, deviceId))
                || (allowLegacyFallback(accountId, deviceId) && prefs(context).contains(KEY_LOGIN_SECRET_CIPHERTEXT)); // legacy single-account install
    }

    static void save(Context context, byte[] salt, byte[] ivAndCiphertext, byte[] loginSecret) throws Exception {
        save(context, AppPrefs.accountId(context), AppPrefs.deviceId(context), salt, ivAndCiphertext, loginSecret);
    }

    static void save(Context context, String accountId, String deviceId, byte[] salt, byte[] ivAndCiphertext, byte[] loginSecret) throws Exception {
        prefs(context).edit()
                .putString(scoped(KEY_SALT, accountId, deviceId), CryptoUtil.base64(salt))
                .putString(scoped(KEY_CIPHERTEXT, accountId, deviceId), CryptoUtil.base64(ivAndCiphertext))
                .putString(scoped(KEY_LOGIN_SECRET_CIPHERTEXT, accountId, deviceId), CryptoUtil.base64(encryptLoginSecret(loginSecret)))
                .apply();
    }

    static byte[] getSalt(Context context) {
        return getSalt(context, AppPrefs.accountId(context), AppPrefs.deviceId(context));
    }

    static byte[] getSalt(Context context, String accountId, String deviceId) {
        String value = prefs(context).getString(scoped(KEY_SALT, accountId, deviceId),
                allowLegacyFallback(accountId, deviceId) ? prefs(context).getString(KEY_SALT, null) : null);
        return value == null ? null : CryptoUtil.fromBase64(value);
    }

    static byte[] getCiphertext(Context context) {
        return getCiphertext(context, AppPrefs.accountId(context), AppPrefs.deviceId(context));
    }

    static byte[] getCiphertext(Context context, String accountId, String deviceId) {
        String value = prefs(context).getString(scoped(KEY_CIPHERTEXT, accountId, deviceId),
                allowLegacyFallback(accountId, deviceId) ? prefs(context).getString(KEY_CIPHERTEXT, null) : null);
        return value == null ? null : CryptoUtil.fromBase64(value);
    }

    static byte[] getLoginSecret(Context context) throws Exception {
        return getLoginSecret(context, AppPrefs.accountId(context), AppPrefs.deviceId(context));
    }

    static byte[] getLoginSecret(Context context, String accountId, String deviceId) throws Exception {
        String value = prefs(context).getString(scoped(KEY_LOGIN_SECRET_CIPHERTEXT, accountId, deviceId),
                allowLegacyFallback(accountId, deviceId) ? prefs(context).getString(KEY_LOGIN_SECRET_CIPHERTEXT, null) : null);
        if (value == null) {
            return null;
        }
        return decryptLoginSecret(CryptoUtil.fromBase64(value));
    }

    static void clear(Context context) {
        clear(context, AppPrefs.accountId(context), AppPrefs.deviceId(context));
    }

    static void clear(Context context, String accountId, String deviceId) {
        prefs(context).edit()
                .remove(scoped(KEY_SALT, accountId, deviceId))
                .remove(scoped(KEY_CIPHERTEXT, accountId, deviceId))
                .remove(scoped(KEY_LOGIN_SECRET_CIPHERTEXT, accountId, deviceId))
                .apply();
    }

    private static byte[] encryptLoginSecret(byte[] loginSecret) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateLoginSecretKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(loginSecret);
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return out;
    }

    private static byte[] decryptLoginSecret(byte[] ivAndCiphertext) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        byte[] ciphertext = new byte[ivAndCiphertext.length - GCM_IV_BYTES];
        System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_BYTES);
        System.arraycopy(ivAndCiphertext, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateLoginSecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    private static SecretKey getOrCreateLoginSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(LOGIN_SECRET_KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(LOGIN_SECRET_KEY_ALIAS, null);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                LOGIN_SECRET_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}
