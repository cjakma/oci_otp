package org.pmoci.kskillauth;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;

/**
 * "pm-oci 인증용" main screen.
 *
 * The actual login approval is fully native (FCM push → {@link AdminPortalApprovalActivity}),
 * so this screen no longer embeds the web auth page. It shows a configurable main image
 * (GIF supported) and a top-right Settings button gated by device authentication.
 */
public class MainActivity extends AppCompatActivity {
    private static final int MAX_LAUNCH_AUTH_FAILURES = 3;

    private ImageView background;
    private TextView placeholder;
    // Cumulative device-auth failures for the on-launch app lock (per launch session).
    private int launchAuthFailures = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Apply the runtime server-address override (Settings → 서버 주소) before any API call.
        PortalApi.setBaseUrlOverride(AppPrefs.serverBaseUrl(this));

        registerForAdminPortalRequests();

        setContentView(buildUi());

        // If the app was opened by tapping the FCM login notification, jump straight to the
        // native approval (which runs its own device-auth gate) and skip the launch lock below
        // so the user is not double-prompted.
        //
        // (Background: the server sends a `notification`+`data` push so the request is still
        // surfaced when the app is backgrounded/killed/battery-optimized — a data-only push is
        // silently dropped there, notably on Samsung. In that state onMessageReceived does NOT
        // fire; the system tray handles it and the tap launches THIS activity with the challenge
        // as intent extras.)
        if (routeAdminPortalRequest(getIntent())) {
            return;
        }

        if (!LocalCredentialStore.isEnrolled(this)) {
            // Req 1 / first run on this device: no stored userKey yet → go set one up.
            // EnrollmentActivity shows the userKey screen first, then commits with device auth.
            startActivity(new Intent(this, EnrollmentActivity.class));
        } else {
            // Req 2: a userKey already exists → lock the app behind device authentication on
            // launch (fingerprint, else pattern/PIN). Retries on failure; after 3 cumulative
            // failures it gives up and just shows the main screen (Settings / FCM approval stay
            // independently gated).
            promptLaunchAuth();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        routeAdminPortalRequest(intent);
    }

    /** @return true if the intent was an admin-portal login request and was routed to approval. */
    private boolean routeAdminPortalRequest(Intent intent) {
        if (intent == null || !"admin_portal_login".equals(intent.getStringExtra("type"))) {
            return false;
        }

        String challengeId = intent.getStringExtra("challenge_id");
        String nonce = intent.getStringExtra("nonce");
        if (TextUtils.isEmpty(challengeId) || TextUtils.isEmpty(nonce)) {
            return false;
        }

        Intent approval = new Intent(this, AdminPortalApprovalActivity.class);
        approval.putExtra("challenge_id", challengeId);
        approval.putExtra("nonce", nonce);
        approval.putExtra("admin_id", intent.getStringExtra("admin_id"));
        approval.putExtra("expires_at", intent.getStringExtra("expires_at"));
        startActivity(approval);

        // Consume the extras so a configuration change / relaunch does not re-open approval.
        intent.removeExtra("type");
        intent.removeExtra("challenge_id");
        intent.removeExtra("nonce");
        return true;
    }

    /**
     * On-launch app lock (Req 2): a stored userKey exists, so require device authentication
     * (fingerprint, else pattern/PIN) before the app is considered unlocked. Each failed attempt
     * re-prompts; after {@link #MAX_LAUNCH_AUTH_FAILURES} cumulative failures it stops prompting
     * and just shows the main screen — Settings and FCM approval remain independently gated.
     */
    private void promptLaunchAuth() {
        DeviceAuth.authenticate(this, "앱 잠금 해제", "지문 또는 기기 잠금 방식으로 인증하세요.",
                new DeviceAuth.Result() {
                    @Override
                    public void onSuccess() {
                        launchAuthFailures = 0;
                        // Unlocked; the main screen is already shown behind the prompt.
                    }

                    @Override
                    public void onFailure(String message) {
                        launchAuthFailures++;
                        if (launchAuthFailures >= MAX_LAUNCH_AUTH_FAILURES) {
                            launchAuthFailures = 0;
                            Toast.makeText(MainActivity.this,
                                    "인증 실패가 누적되어 메인 화면으로 이동합니다.", Toast.LENGTH_LONG).show();
                            // Stay on the main screen per design; no further prompting.
                            return;
                        }
                        Toast.makeText(MainActivity.this,
                                "인증 실패 (" + launchAuthFailures + "/" + MAX_LAUNCH_AUTH_FAILURES + "). 다시 시도하세요.",
                                Toast.LENGTH_SHORT).show();
                        // Re-prompt after the previous prompt fully dismisses.
                        background.postDelayed(MainActivity.this::promptLaunchAuth, 350);
                    }
                });
    }

    private FrameLayout buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#101827"));

        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        placeholder = new TextView(this);
        placeholder.setText("pm-oci 인증");
        placeholder.setTextColor(Color.parseColor("#94a3b8"));
        placeholder.setTextSize(20);
        FrameLayout.LayoutParams placeholderParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        placeholderParams.gravity = Gravity.CENTER;
        root.addView(placeholder, placeholderParams);

        Button settings = new Button(this);
        settings.setText("⚙ 설정");
        settings.setAllCaps(false);
        settings.setTextColor(Color.WHITE);
        settings.setBackgroundColor(Color.parseColor("#88000000"));
        settings.setOnClickListener(v -> openSettings());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        settingsParams.gravity = Gravity.TOP | Gravity.END;
        settingsParams.setMargins(0, dp(16), dp(16), 0);
        root.addView(settings, settingsParams);

        return root;
    }

    private void openSettings() {
        DeviceAuth.authenticate(this, "설정 잠금 해제", "지문 또는 기기 잠금 방식으로 인증하세요.", new DeviceAuth.Result() {
            @Override
            public void onSuccess() {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(MainActivity.this, "인증 실패: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyMainImage() {
        String uri = AppPrefs.mainImageUri(this);
        if (TextUtils.isEmpty(uri)) {
            background.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
            return;
        }
        placeholder.setVisibility(View.GONE);
        try {
            // Normally an absolute path to an app-internal copy of the image (see
            // SettingsActivity). Legacy installs may still hold a content:// URI string.
            Object model = uri.startsWith("/") ? new File(uri) : Uri.parse(uri);
            Glide.with(this).load(model).into(background);
        } catch (Exception e) {
            background.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    private void registerForAdminPortalRequests() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(PortalApi::registerFcmToken);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply in case the image (or server) was changed in Settings.
        PortalApi.setBaseUrlOverride(AppPrefs.serverBaseUrl(this));
        applyMainImage();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
