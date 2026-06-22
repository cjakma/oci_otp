package org.pmoci.kskillauth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * On-device storage of the non-secret enrollment material.
 *
 * SECURITY (design §8.3): the AES-GCM ciphertext and the Argon2 salt live ONLY on the phone
 * (here) and are never broadcast over FCM, so a rogue device that intercepts a push cannot
 * mount an offline brute-force against the userKey. The ciphertext is itself protected by the
 * userKey (Argon2 KDF), so it is not a plaintext secret.
 */
final class LocalCredentialStore {
    private static final String PREFS = "pmoci_otp_credential";
    private static final String KEY_SALT = "salt_b64";
    private static final String KEY_CIPHERTEXT = "ciphertext_b64"; // iv || ciphertext

    private LocalCredentialStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnrolled(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SALT) && p.contains(KEY_CIPHERTEXT);
    }

    static void save(Context context, byte[] salt, byte[] ivAndCiphertext) {
        prefs(context).edit()
                .putString(KEY_SALT, CryptoUtil.base64(salt))
                .putString(KEY_CIPHERTEXT, CryptoUtil.base64(ivAndCiphertext))
                .apply();
    }

    static byte[] getSalt(Context context) {
        String value = prefs(context).getString(KEY_SALT, null);
        return value == null ? null : CryptoUtil.fromBase64(value);
    }

    static byte[] getCiphertext(Context context) {
        String value = prefs(context).getString(KEY_CIPHERTEXT, null);
        return value == null ? null : CryptoUtil.fromBase64(value);
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
