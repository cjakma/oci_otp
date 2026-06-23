package org.pmoci.kskillauth;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * One-time device enrollment.
 */
public class EnrollmentActivity extends AppCompatActivity {
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

        // First-launch enrollment is gated by device authentication (fingerprint, else pattern/PIN).
        DeviceAuth.authenticate(this, "인증앱 등록", "기기 인증으로 잠금을 해제하세요.", new DeviceAuth.Result() {
            @Override
            public void onSuccess() {
                buildUi();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(EnrollmentActivity.this, "인증 실패: " + message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void buildUi() {
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

        TextInputLayout userKeyLayout = passwordLayout("userKey");
        userKeyInput = userKeyLayout.getEditText();
        LinearLayout.LayoutParams p1 = matchWrap();
        p1.setMargins(0, dp(20), 0, 0);
        root.addView(userKeyLayout, p1);

        TextInputLayout confirmLayout = passwordLayout("userKey 확인");
        confirmInput = confirmLayout.getEditText();
        LinearLayout.LayoutParams p2 = matchWrap();
        p2.setMargins(0, dp(12), 0, 0);
        root.addView(confirmLayout, p2);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        LinearLayout.LayoutParams sp = matchWrap();
        sp.setMargins(0, dp(12), 0, 0);
        root.addView(statusText, sp);

        enrollButton = new Button(this);
        enrollButton.setText("등록");
        enrollButton.setEnabled(false);
        enrollButton.setBackgroundColor(Color.LTGRAY);
        enrollButton.setTextColor(Color.DKGRAY);
        enrollButton.setOnClickListener(view -> enroll());
        LinearLayout.LayoutParams bp = matchWrap();
        bp.setMargins(0, dp(20), 0, 0);
        root.addView(enrollButton, bp);

        setContentView(root);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateButtonState();
            }
        };
        userKeyInput.addTextChangedListener(watcher);
        confirmInput.addTextChangedListener(watcher);
    }

    private void updateButtonState() {
        String p1 = userKeyInput.getText().toString();
        String p2 = confirmInput.getText().toString();
        boolean match = !p1.isEmpty() && p1.equals(p2);
        
        enrollButton.setEnabled(match);
        if (match) {
            enrollButton.setBackgroundColor(Color.parseColor("#101827"));
            enrollButton.setTextColor(Color.WHITE);
        } else {
            enrollButton.setBackgroundColor(Color.LTGRAY);
            enrollButton.setTextColor(Color.DKGRAY);
        }
    }

    private void enroll() {
        String userKey = userKeyInput.getText().toString();

        enrollButton.setEnabled(false);
        userKeyInput.setEnabled(false);
        confirmInput.setEnabled(false);
        statusText.setText("등록 중...");

        new Thread(() -> {
            try {
                String devicePublicKeyBase64 = DeviceKeyStore.publicKeyBase64();
                byte[] salt = CryptoUtil.randomBytes(SALT_BYTES);
                byte[] dek = CryptoUtil.deriveKey(userKey, salt);
                byte[] loginSecret = CryptoUtil.randomBytes(LOGIN_SECRET_BYTES);
                byte[] ivAndCiphertext = CryptoUtil.aesGcmEncrypt(dek, loginSecret);
                String verifierHex = CryptoUtil.sha256HexOfBytes(loginSecret);

                LocalCredentialStore.save(this, salt, ivAndCiphertext);

                PortalApi.enroll(devicePublicKeyBase64, verifierHex, (success, message) ->
                        runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(this, "등록이 완료되었습니다.", Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
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
        updateButtonState();
        userKeyInput.setEnabled(true);
        confirmInput.setEnabled(true);
        statusText.setText(message);
    }

    private TextInputLayout passwordLayout(String hint) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        layout.addView(input);
        return layout;
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
