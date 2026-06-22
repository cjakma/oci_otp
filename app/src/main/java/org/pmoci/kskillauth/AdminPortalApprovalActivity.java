package org.pmoci.kskillauth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

/**
 * Native approval screen. The user enters the memorized userKey; the proof is derived and
 * signed entirely on-device and submitted through the Hermes Gateway. No password is sent.
 */
public class AdminPortalApprovalActivity extends Activity {
    private EditText userKeyInput;
    private Button approveButton;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("Admin Terminal Authentication");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("Enter your userKey to approve the pending terminal login.");
        description.setTextSize(16);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.setMargins(0, dp(14), 0, 0);
        root.addView(description, descriptionParams);

        TextView requestInfo = new TextView(this);
        requestInfo.setText("Admin ID: " + safe(adminId) + "\nExpires: " + safe(expiresAt));
        requestInfo.setTextSize(14);
        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.setMargins(0, dp(20), 0, 0);
        root.addView(requestInfo, infoParams);

        userKeyInput = new EditText(this);
        userKeyInput.setHint("userKey");
        userKeyInput.setSingleLine(true);
        userKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.setMargins(0, dp(20), 0, 0);
        root.addView(userKeyInput, inputParams);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, dp(12), 0, 0);
        root.addView(statusText, statusParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.setMargins(0, dp(20), 0, 0);
        root.addView(actions, actionsParams);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        cancelButton.setOnClickListener(view -> finish());
        actions.addView(cancelButton);

        approveButton = new Button(this);
        approveButton.setText("Approve");
        approveButton.setOnClickListener(view -> submitProof());
        actions.addView(approveButton);

        setContentView(root);
        userKeyInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void submitProof() {
        String userKey = userKeyInput.getText().toString();
        if (userKey.isEmpty()) {
            userKeyInput.setError("userKey is required.");
            return;
        }

        approveButton.setEnabled(false);
        userKeyInput.setEnabled(false);
        statusText.setText("Verifying...");

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
                                Toast.makeText(this, "Admin terminal login approved.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            reEnable(message == null ? "Authentication failed." : message);
                        }));
            } catch (Exception error) {
                finishWithError(error.getMessage() == null ? "Authentication failed." : error.getMessage());
            }
        }).start();
    }

    private void finishWithError(String message) {
        runOnUiThread(() -> reEnable(message));
    }

    private void reEnable(String message) {
        approveButton.setEnabled(true);
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
