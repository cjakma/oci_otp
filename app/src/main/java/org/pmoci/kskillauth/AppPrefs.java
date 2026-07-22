package org.pmoci.kskillauth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * Non-secret app settings (device-auth method, server address override, main-screen image).
 *
 * userKey material is NOT stored here — that stays in {@link LocalCredentialStore} as the
 * Argon2-derived ciphertext. This holds only user preferences.
 */
final class AppPrefs {
    private static final String PREFS = "pmoci_app_settings";

    // "biometric_first" = fingerprint preferred, falls back to device credential (pattern/PIN).
    // "device_credential" = device credential (pattern/PIN) only.
    static final String AUTH_BIOMETRIC_FIRST = "biometric_first";
    static final String AUTH_DEVICE_CREDENTIAL = "device_credential";

    private static final String KEY_AUTH_METHOD = "auth_method";
    private static final String KEY_SERVER_BASE_URL = "server_base_url";
    private static final String KEY_MAIN_IMAGE_URI = "main_image_uri";
    private static final String KEY_ACCOUNT_ID = "account_id";
    private static final String KEY_ACCOUNT_LEVEL = "account_level";
    private static final String KEY_DEVICE_ID = "device_id";

    static final String ACCOUNT_LEVEL_ADMIN = "admin";
    static final String ACCOUNT_LEVEL_USER = "user";

    private AppPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String authMethod(Context context) {
        return prefs(context).getString(KEY_AUTH_METHOD, AUTH_BIOMETRIC_FIRST);
    }

    static void setAuthMethod(Context context, String method) {
        String value = AUTH_DEVICE_CREDENTIAL.equals(method) ? AUTH_DEVICE_CREDENTIAL : AUTH_BIOMETRIC_FIRST;
        prefs(context).edit().putString(KEY_AUTH_METHOD, value).apply();
    }

    /** Runtime server base URL override; empty string means "use the compiled-in default". */
    static String serverBaseUrl(Context context) {
        return prefs(context).getString(KEY_SERVER_BASE_URL, "");
    }

    static void setServerBaseUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_SERVER_BASE_URL, url == null ? "" : url.trim()).apply();
    }

    static void clearServerBaseUrl(Context context) {
        prefs(context).edit().remove(KEY_SERVER_BASE_URL).apply();
    }

    static boolean hasServerBaseUrl(Context context) {
        return !TextUtils.isEmpty(serverBaseUrl(context));
    }

    static String mainImageUri(Context context) {
        return prefs(context).getString(KEY_MAIN_IMAGE_URI, "");
    }

    static void setMainImageUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_MAIN_IMAGE_URI, uri == null ? "" : uri).apply();
    }

    static void clearMainImageUri(Context context) {
        prefs(context).edit().remove(KEY_MAIN_IMAGE_URI).apply();
    }

    static String accountId(Context context) {
        return prefs(context).getString(KEY_ACCOUNT_ID, "");
    }

    static String accountLevel(Context context) {
        return prefs(context).getString(KEY_ACCOUNT_LEVEL, "");
    }

    static boolean isAdminAccount(Context context) {
        return ACCOUNT_LEVEL_ADMIN.equals(accountLevel(context));
    }

    static String deviceId(Context context) {
        return prefs(context).getString(KEY_DEVICE_ID, "");
    }

    static String generatedDeviceId(Context context) {
        String androidId = Settings.Secure.getString(
                context.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        String suffix = TextUtils.isEmpty(androidId) ? Build.DEVICE : androidId;
        if (suffix == null) {
            suffix = "unknown";
        }
        int keep = Math.min(8, suffix.length());
        String model = TextUtils.isEmpty(Build.MODEL) ? "android" : Build.MODEL;
        return sanitizeDeviceId("device-" + model + "-" + suffix.substring(suffix.length() - keep));
    }

    static void setAccount(Context context, String accountId, String accountLevel, String deviceId) {
        String level = ACCOUNT_LEVEL_USER.equals(accountLevel) ? ACCOUNT_LEVEL_USER : ACCOUNT_LEVEL_ADMIN;
        prefs(context).edit()
                .putString(KEY_ACCOUNT_ID, accountId == null ? "" : accountId.trim())
                .putString(KEY_ACCOUNT_LEVEL, level)
                .putString(KEY_DEVICE_ID, deviceId == null ? "" : deviceId.trim())
                .apply();
    }

    private static String sanitizeDeviceId(String value) {
        return value == null ? "device-unknown" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
