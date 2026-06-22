package org.pmoci.kskillauth;

import android.app.Activity;
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

/**
 * One-time device enrollment.
 *
 * Generates the Keystore device key, derives DEK = Argon2id(userKey, salt), wraps a random
 * login-secret with AES-256-GCM, stores {salt, ciphertext} locally, and registers
 * {device_pubkey, verifier=SHA-256(login-secret)} with the 0852 server. The userKey is never
 * stored or transmitted. Losing the userKey is unrecoverable by design (server reset only).
 */
public class EnrollmentActivity extends Activity {
    private static final int SALT_BYTES = 16;
    private static final int LOGIN_SECRET_BYTES = 32;

    private EditText userKeyInput;
    private EditText confirmInput;
    private Button enrollButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("인증앱 등록 (Enrollment)");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("기억할 userKey를 설정합니다. 이 값은 저장/전송되지 않으며, 분실 시 복구가 불가능하고 서버 초기화 후 재등록만 가능합니다.");
        description.setTextSize(14);
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.setMargins(0, dp(14), 0, 0);
        root.addView(description, descParams);

        userKeyInput = passwordField("userKey");
        LinearLayout.LayoutParams p1 = matchWrap();
        p1.setMargins(0, dp(20), 0, 0);
        root.addView(userKeyInput, p1);

        confirmInput = passwordField("userKey 확인");
        LinearLayout.LayoutParams p2 = matchWrap();
        p2.setMargins(0, dp(12), 0, 0);
        root.addView(confirmInput, p2);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        LinearLayout.LayoutParams sp = matchWrap();
        sp.setMargins(0, dp(12), 0, 0);
        root.addView(statusText, sp);

        enrollButton = new Button(this);
        enrollButton.setText("등록");
        enrollButton.setOnClickListener(view -> enroll());
        LinearLayout.LayoutParams bp = matchWrap();
        bp.setMargins(0, dp(20), 0, 0);
        root.addView(enrollButton, bp);

        setContentView(root);
    }

    private void enroll() {
        String userKey = userKeyInput.getText().toString();
        String confirm = confirmInput.getText().toString();

        if (userKey.isEmpty()) {
            userKeyInput.setError("userKey is required.");
            return;
        }
        if (!userKey.equals(confirm)) {
            confirmInput.setError("userKey가 일치하지 않습니다.");
            return;
        }

        enrollButton.setEnabled(false);
        userKeyInput.setEnabled(false);
        confirmInput.setEnabled(false);
        statusText.setText("등록 중...");

        new Thread(() -> {
            try {
                // Ensure the Keystore device key exists and grab its public key.
                String devicePublicKeyBase64 = DeviceKeyStore.publicKeyBase64();

                byte[] salt = CryptoUtil.randomBytes(SALT_BYTES);
                byte[] dek = CryptoUtil.deriveKey(userKey, salt);
                byte[] loginSecret = CryptoUtil.randomBytes(LOGIN_SECRET_BYTES);
                byte[] ivAndCiphertext = CryptoUtil.aesGcmEncrypt(dek, loginSecret);
                String verifierHex = CryptoUtil.sha256HexOfBytes(loginSecret);

                // Persist non-secret material locally BEFORE the network call so retries work.
                LocalCredentialStore.save(this, salt, ivAndCiphertext);

                PortalApi.enroll(devicePublicKeyBase64, verifierHex, (success, message) ->
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(this, "등록이 완료되었습니다.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            // Roll back local material if the server rejected enrollment.
                            LocalCredentialStore.clear(this);
                            reEnable(message == null ? "등록 실패" : message);
                        }));
            } catch (Exception error) {
                LocalCredentialStore.clear(this);
                runOnUiThread(() -> reEnable(error.getMessage() == null ? "등록 실패" : error.getMessage()));
            }
        }).start();
    }

    private void reEnable(String message) {
        enrollButton.setEnabled(true);
        userKeyInput.setEnabled(true);
        confirmInput.setEnabled(true);
        statusText.setText(message);
    }

    private EditText passwordField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
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
}
