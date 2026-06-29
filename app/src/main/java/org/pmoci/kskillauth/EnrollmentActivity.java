package org.pmoci.kskillauth;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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

/**
 * One-time device enrollment.
 */
public class EnrollmentActivity extends AppCompatActivity {
    private static final int SALT_BYTES = 16;
    private static final int LOGIN_SECRET_BYTES = 32;

    private EditText userKeyInput;
    private EditText confirmInput;
    private MaterialButton enrollButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        UiKit.applyLightSystemBars(this);

        // Req 1: show the userKey setup screen first; device authentication is required to
        // COMMIT the enrollment (on the 등록 button), so the order is 입력 → 기기 인증 → 저장.
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiKit.COLOR_BACKGROUND);

        LinearLayout root = UiKit.screenRoot(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(UiKit.title(this, "인증앱 등록"), UiKit.matchWrap());
        TextView description = UiKit.subtitle(this,
                "기억할 userKey를 설정합니다. userKey는 저장되거나 전송되지 않으며, 분실하면 서버 초기화 후 다시 등록해야 합니다.");
        root.addView(description, UiKit.topMargin(this, 12));

        MaterialCardView card = UiKit.card(this);
        LinearLayout content = UiKit.cardContent(this);
        card.addView(content);
        root.addView(card, UiKit.topMargin(this, 24));

        TextInputLayout userKeyLayout = passwordLayout("userKey");
        userKeyInput = userKeyLayout.getEditText();
        content.addView(userKeyLayout, UiKit.matchWrap());

        TextInputLayout confirmLayout = passwordLayout("userKey 다시 입력");
        confirmInput = confirmLayout.getEditText();
        content.addView(confirmLayout, UiKit.topMargin(this, 12));

        statusText = UiKit.statusText(this);
        content.addView(statusText, UiKit.topMargin(this, 12));

        enrollButton = UiKit.primaryButton(this, "등록");
        UiKit.setButtonEnabled(enrollButton, false);
        enrollButton.setOnClickListener(view -> authenticateThenEnroll());
        content.addView(enrollButton, UiKit.topMargin(this, 20));

        setContentView(scroll);

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

        UiKit.setButtonEnabled(enrollButton, match);
    }

    private void authenticateThenEnroll() {
        // Commit step is gated by device authentication (fingerprint, else pattern/PIN).
        DeviceAuth.authenticate(this, "userKey 등록 인증", "기기 인증으로 userKey 등록을 완료하세요.",
                new DeviceAuth.Result() {
                    @Override
                    public void onSuccess() {
                        enroll();
                    }

                    @Override
                    public void onFailure(String message) {
                        Toast.makeText(EnrollmentActivity.this, "인증 실패: " + message, Toast.LENGTH_LONG).show();
                    }
                });
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
        UiKit.styleInput(layout);
        
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
