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

    private PortalApi() {
    }

    static void registerFcmToken(String token) {
        if (token == null || token.trim().isEmpty() || BuildConfig.ADMIN_DEVICE_ENROLLMENT_KEY.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("fcm_token", token);
                post("/api/admin/portal-device/token", body);
            } catch (Exception ignored) {
                // Firebase will issue the token again, and MainActivity also retries at launch.
            }
        }).start();
    }

    static void approveChallenge(String challengeId, String password, Callback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("challenge_id", challengeId);
                body.put("admin_pw", password);
                String message = post("/api/admin/portal-login/approve", body);
                callback.onComplete(true, message);
            } catch (Exception error) {
                callback.onComplete(false, error.getMessage());
            }
        }).start();
    }

    private static String post(String path, JSONObject body) throws Exception {
        URL url = new URL(BuildConfig.PORTAL_API_BASE_URL + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Admin-Device-Key", BuildConfig.ADMIN_DEVICE_ENROLLMENT_KEY);

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
