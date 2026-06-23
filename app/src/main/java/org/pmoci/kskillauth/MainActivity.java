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

/**
 * "pm-oci 인증용" main screen.
 *
 * The actual login approval is fully native (FCM push → {@link AdminPortalApprovalActivity}),
 * so this screen no longer embeds the web auth page. It shows a configurable main image
 * (GIF supported) and a top-right Settings button gated by device authentication.
 */
public class MainActivity extends AppCompatActivity {
    private ImageView background;
    private TextView placeholder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Apply the runtime server-address override (Settings → 서버 주소) before any API call.
        PortalApi.setBaseUrlOverride(AppPrefs.serverBaseUrl(this));

        registerForAdminPortalRequests();

        // First launch: device must enroll a userKey before it can approve logins.
        if (!LocalCredentialStore.isEnrolled(this)) {
            startActivity(new Intent(this, EnrollmentActivity.class));
        }

        setContentView(buildUi());
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
            Glide.with(this).load(Uri.parse(uri)).into(background);
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
