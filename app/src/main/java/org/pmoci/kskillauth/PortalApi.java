package org.pmoci.kskillauth;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class PortalApi {
    interface Callback {
        void onComplete(boolean success, String message);
    }

    // Runtime override of the server base URL (Settings → 서버 주소). When null/empty the
    // compiled-in BuildConfig.PORTAL_API_BASE_URL is used. Set at app launch from AppPrefs.
    private static volatile String baseUrlOverride = null;

    private PortalApi() {
    }

    static void setBaseUrlOverride(String url) {
        baseUrlOverride = (url == null || url.trim().isEmpty()) ? null : url.trim();
    }

    /** The effective server base URL: the Settings override if set, else the compiled default. */
    static String portalBaseUrl() {
        return baseUrlOverride != null ? baseUrlOverride : BuildConfig.PORTAL_API_BASE_URL;
    }

    /** Registers the FCM token with the server (device-enrollment-key protected). */
    static void registerFcmToken(String token) {
        if (token == null || token.trim().isEmpty() || BuildConfig.ADMIN_DEVICE_ENROLLMENT_KEY.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("fcm_token", token);
                post(portalBaseUrl(), "/api/admin/portal-device/token", body, true);
            } catch (Exception ignored) {
                // Firebase will issue the token again, and MainActivity also retries at launch.
            }
        }).start();
    }

    /** One-time enrollment: registers the Keystore public key + login-secret verifier. */
    static void enroll(String devicePublicKeyBase64, String verifierHex, Callback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_pubkey", devicePublicKeyBase64);
                body.put("verifier", verifierHex);
                String message = post(portalBaseUrl(), "/api/admin/portal-enroll", body, true);
                callback.onComplete(true, message);
            } catch (Exception error) {
                callback.onComplete(false, error.getMessage());
            }
        }).start();
    }

    /**
     * Submits the challenge-bound proof + ECDSA signature.
     * - When a Hermes Gateway is configured, posts to the gateway (public /api/submit); the
     *   gateway then calls the 0852 server back.
     * - Otherwise posts straight to the server callback using the device enrollment key.
     */
    static void submitProof(String challengeId, String proofHex, String signatureBase64, Callback callback) {
        new Thread(() -> {
            try {
                String gatewayBaseUrl = BuildConfig.GATEWAY_BASE_URL;
                JSONObject body = new JSONObject();
                String message;
                if (gatewayBaseUrl != null && !gatewayBaseUrl.trim().isEmpty()) {
                    body.put("cid", challengeId);
                    body.put("proof", proofHex);
                    body.put("sig", signatureBase64);
                    message = post(gatewayBaseUrl, "/api/submit", body, false);
                } else {
                    body.put("challenge_id", challengeId);
                    body.put("proof", proofHex);
                    body.put("sig", signatureBase64);
                    message = post(portalBaseUrl(), "/api/admin/portal-callback", body, true);
                }
                callback.onComplete(true, message);
            } catch (Exception error) {
                callback.onComplete(false, error.getMessage());
            }
        }).start();
    }

    private static String post(String baseUrl, String path, JSONObject body, boolean withDeviceKey) throws Exception {
        URL url = new URL(baseUrl + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (withDeviceKey) {
            connection.setRequestProperty("X-Admin-Device-Key", BuildConfig.ADMIN_DEVICE_ENROLLMENT_KEY);
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }

        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String response = readStream(stream);

        if (statusCode < 200 || statusCode >= 300) {
            String message = "Request failed (" + statusCode + ")";
            try {
                message = new JSONObject(response).optString("error", message);
            } catch (Exception ignored) {
                // Keep the status-based fallback.
            }
            throw new IllegalStateException(message);
        }

        return response;
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        return text.toString();
    }
}
