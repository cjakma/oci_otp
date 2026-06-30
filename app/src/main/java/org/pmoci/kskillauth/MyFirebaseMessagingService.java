package org.pmoci.kskillauth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    static final String CHANNEL_ID = "admin_portal_login";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.i(TAG, "FCM token refreshed: " + tokenSuffix(token));
        PortalApi.registerFcmToken(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.i(TAG, "FCM message received. dataKeys=" + remoteMessage.getData().keySet());
        if (!"admin_portal_login".equals(remoteMessage.getData().get("type"))) {
            Log.d(TAG, "Ignored unsupported FCM message.");
            return;
        }

        String challengeId = remoteMessage.getData().get("challenge_id");
        String nonce = remoteMessage.getData().get("nonce");
        if (challengeId == null || challengeId.trim().isEmpty()
                || nonce == null || nonce.trim().isEmpty()) {
            return;
        }

        showAdminPortalNotification(
                challengeId,
                nonce,
                remoteMessage.getData().get("admin_id"),
                remoteMessage.getData().get("expires_at")
        );
    }

    private void showAdminPortalNotification(String challengeId, String nonce, String adminId, String expiresAt) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        ensureAdminPortalChannel(this);

        Intent intent = new Intent(this, AdminPortalApprovalActivity.class);
        intent.putExtra("challenge_id", challengeId);
        intent.putExtra("nonce", nonce);
        intent.putExtra("admin_id", adminId);
        intent.putExtra("expires_at", expiresAt);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                challengeId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Admin terminal login request")
                .setContentText("Tap to approve this login request.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_HIGH)
                .build();

        manager.notify(challengeId.hashCode(), notification);
    }

    static void ensureAdminPortalChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Admin terminal login",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Login requests that require approval on this device.");
        manager.createNotificationChannel(channel);
    }

    private static String tokenSuffix(String token) {
        if (token == null) {
            return "(null)";
        }
        int keep = Math.min(12, token.length());
        return "..." + token.substring(token.length() - keep);
    }
}
