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

import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.TimeUnit;

/**
 * One-time device enrollment, start to finish, from the three fields on this screen.
 *
 * It used to stop halfway: the screen registered the account's public key and verifier and
 * nothing else, while the FCM token — the thing the server actually needs to reach the phone —
 * was left to MainActivity.onCreate(), which had already run and skipped it because no account
 * was configured yet. The device then sat in the registry with no token, and every login for
 * that account failed with `no_active_device` until the app happened to be cold-started.
 *
 * So the button now runs all four steps in order and reports which one failed:
 *
 *   1. 서버 접속 확인  — GET /healthz, before anything is written locally
 *   2. 계정 등록       — POST /api/admin/portal-account (admin level only for ADMIN_ACCOUNT_ID)
 *   3. userKey 등록    — derive the login secret, store it, POST the verifier + public key
 *   4. FCM 키 등록     — resolve the token and POST it, so the account can be pushed to
 *
 * Steps 1–3 abort cleanly on failure (nothing half-written). Step 4 does not roll the
 * enrollment back — it is valid — and instead leaves a retry button on the screen.
 */
public class EnrollmentActivity extends AppCompatActivity {
    private static final int SALT_BYTES = 16;
    private static final int LOGIN_SECRET_BYTES = 32;

    private EditText userKeyInput;
    private EditText confirmInput;
    private EditText serverUrlInput;
    private EditText accountIdInput;
    private MaterialButton enrollButton;
    private TextView statusText;
    /** True once step 4 failed: the button then retries only the FCM registration. */
    private boolean fcmRetryMode = false;

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
                "서버 주소, account_id, userKey 세 가지만 입력하면 서버 접속 확인 → 계정 등록 → userKey 등록 → FCM 키 등록까지 한 번에 끝냅니다. "
                        + "device_id는 앱이 자동으로 만들고, 계정 등급도 자동입니다(admin은 "
                        + BuildConfig.ADMIN_ACCOUNT_ID + " 단독, 그 외는 user). "
                        + "userKey는 저장되거나 전송되지 않으며, 분실하면 서버 초기화 후 다시 등록해야 합니다.");
        root.addView(description, UiKit.topMargin(this, 12));

        MaterialCardView card = UiKit.card(this);
        LinearLayout content = UiKit.cardContent(this);
        card.addView(content);
        root.addView(card, UiKit.topMargin(this, 24));

        TextInputLayout serverLayout = textLayout("서버 주소 (https://...)");
        serverUrlInput = serverLayout.getEditText();
        serverUrlInput.setText(AppPrefs.serverBaseUrl(this));
        content.addView(serverLayout, UiKit.matchWrap());

        TextInputLayout accountLayout = textLayout("account_id");
        accountIdInput = accountLayout.getEditText();
        accountIdInput.setText(AppPrefs.accountId(this));
        content.addView(accountLayout, UiKit.topMargin(this, 12));

        TextInputLayout userKeyLayout = passwordLayout("userKey");
        userKeyInput = userKeyLayout.getEditText();
        content.addView(userKeyLayout, UiKit.topMargin(this, 12));

        TextInputLayout confirmLayout = passwordLayout("userKey 다시 입력");
        confirmInput = confirmLayout.getEditText();
        content.addView(confirmLayout, UiKit.topMargin(this, 12));

        statusText = UiKit.statusText(this);
        content.addView(statusText, UiKit.topMargin(this, 12));

        enrollButton = UiKit.primaryButton(this, "등록");
        UiKit.setButtonEnabled(enrollButton, false);
        enrollButton.setOnClickListener(view -> {
            if (fcmRetryMode) {
                retryFcmRegistration();
            } else {
                authenticateThenEnroll();
            }
        });
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
        serverUrlInput.addTextChangedListener(watcher);
        accountIdInput.addTextChangedListener(watcher);
        userKeyInput.addTextChangedListener(watcher);
        confirmInput.addTextChangedListener(watcher);
    }

    private void updateButtonState() {
        if (fcmRetryMode) {
            // The inputs are done with; the button belongs to the FCM retry now.
            UiKit.setButtonEnabled(enrollButton, true);
            return;
        }
        String serverUrl = serverUrlInput.getText().toString().trim();
        String accountId = accountIdInput.getText().toString().trim();
        String p1 = userKeyInput.getText().toString();
        String p2 = confirmInput.getText().toString();
        boolean serverReady = serverUrl.startsWith("https://");
        boolean accountReady = !accountId.isEmpty();
        boolean match = serverReady && accountReady && !p1.isEmpty() && p1.equals(p2);

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
        final String serverUrl = serverUrlInput.getText().toString().trim();
        final String accountId = accountIdInput.getText().toString().trim();
        final String userKey = userKeyInput.getText().toString();

        if (!serverUrl.startsWith("https://")) {
            reEnable("https:// 로 시작하는 서버 주소를 입력하세요.");
            return;
        }
        if (accountId.isEmpty()) {
            reEnable("account_id를 입력하세요.");
            return;
        }

        lockInputs();
        new Thread(() -> runEnrollment(serverUrl, accountId, userKey)).start();
    }

    /** The four steps. Runs on a background thread; every UI touch is posted back. */
    private void runEnrollment(String serverUrl, String accountId, String userKey) {
        say("1/4 서버 접속 확인 중…");
        try {
            PortalApi.ping(serverUrl);
        } catch (Exception error) {
            failStep("1/4 서버 접속 실패", error);
            return;
        }

        // The address answered, so it is worth keeping — and steps 2-4 post to it.
        String accountLevel = accountLevelFor(accountId);
        AppPrefs.setServerBaseUrl(this, serverUrl);
        PortalApi.setBaseUrlOverride(serverUrl);
        AppPrefs.setAccount(this, accountId, accountLevel, AppPrefs.generatedDeviceId(this));

        say("2/4 계정 등록 중… (" + accountId + " · " + accountLevel + ")");
        try {
            PortalApi.createAccountSync(accountId, accountLevel);
        } catch (Exception error) {
            failStep("2/4 계정 등록 실패", error);
            return;
        }

        say("3/4 userKey 등록 중…");
        try {
            String devicePublicKeyBase64 = DeviceKeyStore.publicKeyBase64();
            byte[] salt = CryptoUtil.randomBytes(SALT_BYTES);
            byte[] dek = CryptoUtil.deriveKey(userKey, salt);
            byte[] loginSecret = CryptoUtil.randomBytes(LOGIN_SECRET_BYTES);
            byte[] ivAndCiphertext = CryptoUtil.aesGcmEncrypt(dek, loginSecret);
            String verifierHex = CryptoUtil.sha256HexOfBytes(loginSecret);

            LocalCredentialStore.save(this, salt, ivAndCiphertext, loginSecret);
            PortalApi.enrollSync(this, devicePublicKeyBase64, verifierHex);
        } catch (Exception error) {
            LocalCredentialStore.clear(this);
            failStep("3/4 userKey 등록 실패", error);
            return;
        }

        say("4/4 FCM 키 등록 중…");
        try {
            registerFcmTokenBlocking();
        } catch (Exception error) {
            // The enrollment above is real and stays. Only the push channel is missing, and
            // that is exactly what the retry button re-runs.
            offerFcmRetry(error);
            return;
        }

        runOnUiThread(() -> {
            Toast.makeText(this, "등록이 완료되었습니다. (계정 · userKey · FCM 키)", Toast.LENGTH_LONG).show();
            finish();
        });
    }

    /**
     * Admin is a single account by policy (BuildConfig.ADMIN_ACCOUNT_ID); everyone else
     * enrolls as a user. Previously this was hardcoded to admin for whoever typed first.
     */
    private String accountLevelFor(String accountId) {
        String adminAccountId = BuildConfig.ADMIN_ACCOUNT_ID == null ? "" : BuildConfig.ADMIN_ACCOUNT_ID.trim();
        return !adminAccountId.isEmpty() && adminAccountId.equalsIgnoreCase(accountId)
                ? AppPrefs.ACCOUNT_LEVEL_ADMIN
                : AppPrefs.ACCOUNT_LEVEL_USER;
    }

    /** Resolves the FCM token (Firebase may have to fetch one) and registers it. */
    private void registerFcmTokenBlocking() throws Exception {
        String token = Tasks.await(FirebaseMessaging.getInstance().getToken(), 30, TimeUnit.SECONDS);
        PortalApi.registerFcmTokenSync(this, token);
    }

    private void retryFcmRegistration() {
        UiKit.setButtonEnabled(enrollButton, false);
        say("4/4 FCM 키 등록 재시도 중…");
        new Thread(() -> {
            try {
                registerFcmTokenBlocking();
                runOnUiThread(() -> {
                    Toast.makeText(this, "FCM 키 등록이 완료되었습니다.", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception error) {
                offerFcmRetry(error);
            }
        }).start();
    }

    private void offerFcmRetry(Exception error) {
        fcmRetryMode = true;
        runOnUiThread(() -> {
            statusText.setText("4/4 FCM 키 등록 실패 — " + describe(error)
                    + "\n계정과 userKey는 등록됐습니다. 인터넷 연결을 확인한 뒤 재시도하세요.");
            statusText.setTextColor(UiKit.COLOR_ERROR);
            enrollButton.setText("FCM 키 재등록");
            UiKit.setButtonEnabled(enrollButton, true);
        });
    }

    private void failStep(String step, Exception error) {
        runOnUiThread(() -> {
            unlockInputs();
            statusText.setText(step + " — " + describe(error));
            statusText.setTextColor(UiKit.COLOR_ERROR);
        });
    }

    private void say(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            statusText.setTextColor(UiKit.COLOR_MUTED);
        });
    }

    private static String describe(Exception error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? String.valueOf(error) : message;
    }

    private void lockInputs() {
        UiKit.setButtonEnabled(enrollButton, false);
        serverUrlInput.setEnabled(false);
        accountIdInput.setEnabled(false);
        userKeyInput.setEnabled(false);
        confirmInput.setEnabled(false);
    }

    private void unlockInputs() {
        serverUrlInput.setEnabled(true);
        accountIdInput.setEnabled(true);
        userKeyInput.setEnabled(true);
        confirmInput.setEnabled(true);
        updateButtonState();
    }

    private void reEnable(String message) {
        unlockInputs();
        statusText.setText(message);
        statusText.setTextColor(UiKit.COLOR_ERROR);
    }

    private TextInputLayout textLayout(String hint) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        UiKit.styleInput(layout);

        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        layout.addView(input);
        return layout;
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
