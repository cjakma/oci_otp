package org.pmoci.kskillauth;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.nio.charset.StandardCharsets;

/**
 * Native approval screen for FCM login requests.
 *
 * If enrollment exists, the FCM flow is: notification tap -> device auth -> proof submit.
 * The userKey is only needed when registering or renewing enrollment.
 */
public class AdminPortalApprovalActivity extends AppCompatActivity {
    private MaterialButton retryButton;
    private TextView statusText;
    private String challengeId;
    private String nonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        UiKit.applyLightSystemBars(this);

        challengeId = getIntent().getStringExtra("challenge_id");
        nonce = getIntent().getStringExtra("nonce");
        String adminId = getIntent().getStringExtra("admin_id");
        String expiresAt = getIntent().getStringExtra("expires_at");

        if (challengeId == null || challengeId.trim().isEmpty()
                || nonce == null || nonce.trim().isEmpty()) {
            finish();
            return;
        }

        if (!LocalCredentialStore.isEnrolled(this)) {
            Toast.makeText(this, "먼저 userKey 등록이 필요합니다.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, EnrollmentActivity.class));
            finish();
            return;
        }

        DeviceAuth.authenticate(this, "로그인 승인 인증", "기기 인증으로 요청을 승인합니다.", new DeviceAuth.Result() {
            @Override
            public void onSuccess() {
                buildUi(adminId, expiresAt);
                submitProof();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(AdminPortalApprovalActivity.this, "인증 실패: " + message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void buildUi(String adminId, String expiresAt) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiKit.COLOR_BACKGROUND);

        LinearLayout root = UiKit.screenRoot(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(UiKit.title(this, "로그인 승인"), UiKit.matchWrap());
        root.addView(UiKit.subtitle(this, "기기 인증이 완료되면 저장된 인증 정보로 승인 요청을 처리합니다."),
                UiKit.topMargin(this, 12));

        MaterialCardView card = UiKit.card(this);
        LinearLayout content = UiKit.cardContent(this);
        card.addView(content);
        root.addView(card, UiKit.topMargin(this, 24));

        content.addView(UiKit.sectionTitle(this, "요청 정보"), UiKit.matchWrap());

        TextView requestInfo = UiKit.statusText(this);
        requestInfo.setText("관리자 ID: " + safe(adminId) + "\n만료 시각: " + safe(expiresAt));
        content.addView(requestInfo, UiKit.topMargin(this, 10));

        statusText = UiKit.statusText(this);
        statusText.setText("승인 대기 중...");
        content.addView(statusText, UiKit.topMargin(this, 16));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        content.addView(actions, UiKit.topMargin(this, 20));

        MaterialButton cancelButton = UiKit.secondaryButton(this, "닫기");
        cancelButton.setOnClickListener(view -> finish());
        actions.addView(cancelButton, UiKit.weight());

        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(12), 1));

        retryButton = UiKit.primaryButton(this, "다시 시도");
        retryButton.setOnClickListener(view -> submitProof());
        UiKit.setButtonEnabled(retryButton, false);
        actions.addView(retryButton, UiKit.weight());

        setContentView(scroll);
    }

    private void submitProof() {
        UiKit.setButtonEnabled(retryButton, false);
        statusText.setText("승인 처리 중...");

        new Thread(() -> {
            try {
                byte[] loginSecret = LocalCredentialStore.getLoginSecret(this);
                if (loginSecret == null) {
                    showFailure("저장된 인증 정보가 없습니다. userKey를 다시 등록하세요.");
                    return;
                }

                String verifierHex = CryptoUtil.sha256HexOfBytes(loginSecret);
                String proofHex = CryptoUtil.sha256Hex(verifierHex + challengeId + nonce);
                byte[] signMessage = (challengeId + "." + nonce + "." + proofHex).getBytes(StandardCharsets.UTF_8);
                String signatureBase64 = CryptoUtil.base64(DeviceKeyStore.sign(signMessage));

                PortalApi.submitProof(challengeId, proofHex, signatureBase64, (success, message) ->
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(this, "관리자 로그인을 승인했습니다.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            showFailure(message == null ? "인증에 실패했습니다." : message);
                        }));
            } catch (Exception error) {
                showFailure(error.getMessage() == null ? "인증에 실패했습니다." : error.getMessage());
            }
        }).start();
    }

    private void showFailure(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            UiKit.setButtonEnabled(retryButton, true);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
