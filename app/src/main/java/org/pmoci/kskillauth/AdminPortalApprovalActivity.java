package org.pmoci.kskillauth;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.nio.charset.StandardCharsets;

/**
 * Native approval screen. The user enters the memorized userKey; the proof is derived and
 * signed entirely on-device and submitted through the Hermes Gateway. No password is sent.
 */
public class AdminPortalApprovalActivity extends AppCompatActivity {
    private EditText userKeyInput;
    private MaterialButton approveButton;
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

        // Cannot approve before the device is enrolled (no salt/ciphertext/verifier yet).
        if (!LocalCredentialStore.isEnrolled(this)) {
            Toast.makeText(this, "먼저 인증앱 등록(enrollment)이 필요합니다.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, EnrollmentActivity.class));
            finish();
            return;
        }

        // Approving a login is gated by device authentication (fingerprint, else pattern/PIN).
        DeviceAuth.authenticate(this, "로그인 승인 인증", "기기 인증으로 잠금을 해제하세요.", new DeviceAuth.Result() {
            @Override
            public void onSuccess() {
                buildUi(adminId, expiresAt);
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
        root.addView(UiKit.subtitle(this,
                "대기 중인 관리자 터미널 로그인을 승인하려면 userKey를 입력하세요."),
                UiKit.topMargin(this, 12));

        MaterialCardView card = UiKit.card(this);
        LinearLayout content = UiKit.cardContent(this);
        card.addView(content);
        root.addView(card, UiKit.topMargin(this, 24));

        TextView requestTitle = UiKit.sectionTitle(this, "요청 정보");
        content.addView(requestTitle, UiKit.matchWrap());

        TextView requestInfo = UiKit.statusText(this);
        requestInfo.setText("관리자 ID: " + safe(adminId) + "\n만료 시각: " + safe(expiresAt));
        content.addView(requestInfo, UiKit.topMargin(this, 10));

        TextInputLayout userKeyLayout = new TextInputLayout(this);
        userKeyLayout.setHint("userKey");
        userKeyLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        UiKit.styleInput(userKeyLayout);
        TextInputEditText input = new TextInputEditText(userKeyLayout.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        userKeyLayout.addView(input);
        userKeyInput = input;
        content.addView(userKeyLayout, UiKit.topMargin(this, 20));

        statusText = UiKit.statusText(this);
        content.addView(statusText, UiKit.topMargin(this, 12));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        content.addView(actions, UiKit.topMargin(this, 20));

        MaterialButton cancelButton = UiKit.secondaryButton(this, "취소");
        cancelButton.setOnClickListener(view -> finish());
        actions.addView(cancelButton, UiKit.weight());

        TextView spacer = new TextView(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(12), 1));

        approveButton = UiKit.primaryButton(this, "승인");
        approveButton.setOnClickListener(view -> submitProof());
        actions.addView(approveButton, UiKit.weight());

        setContentView(scroll);
        userKeyInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void submitProof() {
        String userKey = userKeyInput.getText().toString();
        if (userKey.isEmpty()) {
            userKeyInput.setError("userKey를 입력하세요.");
            return;
        }

        UiKit.setButtonEnabled(approveButton, false);
        userKeyInput.setEnabled(false);
        statusText.setText("확인 중...");

        new Thread(() -> {
            try {
                byte[] salt = LocalCredentialStore.getSalt(this);
                byte[] ciphertext = LocalCredentialStore.getCiphertext(this);

                byte[] dek = CryptoUtil.deriveKey(userKey, salt);
                byte[] loginSecret;
                try {
                    loginSecret = CryptoUtil.aesGcmDecrypt(dek, ciphertext);
                } catch (Exception badKey) {
                    // AES-GCM tag mismatch -> wrong userKey.
                    finishWithError("Invalid userKey.");
                    return;
                }

                String verifierHex = CryptoUtil.sha256HexOfBytes(loginSecret);
                String proofHex = CryptoUtil.sha256Hex(verifierHex + challengeId + nonce);
                byte[] signMessage = (challengeId + "." + nonce + "." + proofHex).getBytes(StandardCharsets.UTF_8);
                String signatureBase64 = CryptoUtil.base64(DeviceKeyStore.sign(signMessage));

                PortalApi.submitProof(challengeId, proofHex, signatureBase64, (success, message) ->
                        runOnUiThread(() -> {
                            userKeyInput.setText("");
                            if (success) {
                                Toast.makeText(this, "관리자 터미널 로그인을 승인했습니다.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            reEnable(message == null ? "인증에 실패했습니다." : message);
                        }));
            } catch (Exception error) {
                finishWithError(error.getMessage() == null ? "인증에 실패했습니다." : error.getMessage());
            }
        }).start();
    }

    private void finishWithError(String message) {
        runOnUiThread(() -> reEnable(message));
    }

    private void reEnable(String message) {
        UiKit.setButtonEnabled(approveButton, true);
        userKeyInput.setEnabled(true);
        statusText.setText(message);
        userKeyInput.requestFocus();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }
}
