package org.pmoci.kskillauth;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.messaging.FirebaseMessaging;
import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Settings screen (entered from the main screen's top-right button).
 *
 * Sections:
 *  1) Device auth method (fingerprint-first vs device credential/pattern).
 *  2) userKey register / delete  (eye icon reveals the key only while registering).
 *  3) Server address register / delete (runtime override of the compiled-in server).
 *  4) Main-screen image register / delete (gallery, GIF supported).
 *
 * The main screen authenticates before starting this non-exported activity, so settings
 * remain behind the same device authentication gate without prompting twice.
 */
public class SettingsActivity extends AppCompatActivity {
    private static final int SALT_BYTES = 16;
    private static final int LOGIN_SECRET_BYTES = 32;

    private ActivityResultLauncher<String[]> imagePicker;
    private TextView userKeyStatus;
    private TextView serverStatus;
    private TextView imageStatus;
    private ImageView imagePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        UiKit.applyLightSystemBars(this);

        // Must be registered before the activity is started (do it unconditionally, up front).
        imagePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) {
                return;
            }
            // FIX: a SAF content:// URI loses its read grant after the app restarts, so the main
            // screen could not reload it later (the background image "disappeared"). Copy the
            // picked image into app-internal storage once and reference that stable file path.
            Toast.makeText(this, "이미지 저장 중...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String storedPath = copyImageToInternalStorage(uri);
                runOnUiThread(() -> {
                    if (storedPath == null) {
                        Toast.makeText(this, "이미지를 불러오지 못했습니다.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppPrefs.setMainImageUri(this, storedPath);
                    refreshImageState();
                    Toast.makeText(this, "메인 이미지를 저장했습니다.", Toast.LENGTH_SHORT).show();
                });
            }).start();
        });

        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UiKit.COLOR_BACKGROUND);
        LinearLayout root = UiKit.screenRoot(this);
        scroll.addView(root);

        root.addView(UiKit.title(this, "설정"), matchWrap());
        root.addView(UiKit.subtitle(this,
                "앱 버전 " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") · 인증 방식, 계정, userKey, 서버 주소, 메인 화면 이미지를 관리합니다."),
                topMargin(12));

        // ── 0) Account/device ───────────────────────────────────────────────
        root.addView(sectionHeader("계정 / 기기"), topMargin(24));
        TextView accountStatus = new TextView(this);
        accountStatus.setText(currentAccountSummary());
        root.addView(accountStatus, topMargin(4));

        TextInputLayout accountLayout = new TextInputLayout(this);
        accountLayout.setHint("계정 ID (예: AAAA 또는 BBBB)");
        UiKit.styleInput(accountLayout);
        final TextInputEditText accountInput = new TextInputEditText(accountLayout.getContext());
        accountInput.setSingleLine(true);
        accountInput.setInputType(InputType.TYPE_CLASS_TEXT);
        accountInput.setText(AppPrefs.accountId(this));
        accountLayout.addView(accountInput);
        root.addView(accountLayout, topMargin(8));

        TextInputLayout deviceLayout = new TextInputLayout(this);
        deviceLayout.setHint("기기 ID (예: A, B, C, D)");
        UiKit.styleInput(deviceLayout);
        final TextInputEditText deviceInput = new TextInputEditText(deviceLayout.getContext());
        deviceInput.setSingleLine(true);
        deviceInput.setInputType(InputType.TYPE_CLASS_TEXT);
        deviceInput.setText(AppPrefs.deviceId(this));
        deviceLayout.addView(deviceInput);
        root.addView(deviceLayout, topMargin(8));

        RadioGroup accountLevelGroup = new RadioGroup(this);
        accountLevelGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton accountAdmin = new RadioButton(this);
        accountAdmin.setText("Admin");
        accountAdmin.setId(View.generateViewId());
        RadioButton accountUser = new RadioButton(this);
        accountUser.setText("User");
        accountUser.setId(View.generateViewId());
        accountLevelGroup.addView(accountAdmin);
        accountLevelGroup.addView(accountUser);
        if (AppPrefs.ACCOUNT_LEVEL_USER.equals(AppPrefs.accountLevel(this))) accountUser.setChecked(true);
        else if (AppPrefs.ACCOUNT_LEVEL_ADMIN.equals(AppPrefs.accountLevel(this))) accountAdmin.setChecked(true);
        root.addView(accountLevelGroup, topMargin(8));

        LinearLayout accountButtons = new LinearLayout(this);
        accountButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton accountSave = primaryButton("현재 계정 저장");
        accountSave.setOnClickListener(v -> {
            String accountId = accountInput.getText() == null ? "" : accountInput.getText().toString().trim();
            String deviceId = deviceInput.getText() == null ? "" : deviceInput.getText().toString().trim();
            int checkedLevelId = accountLevelGroup.getCheckedRadioButtonId();
            if (checkedLevelId == View.NO_ID) {
                Toast.makeText(this, "계정 등급을 선택하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            String level = checkedLevelId == accountUser.getId()
                    ? AppPrefs.ACCOUNT_LEVEL_USER : AppPrefs.ACCOUNT_LEVEL_ADMIN;
            if (TextUtils.isEmpty(accountId) || TextUtils.isEmpty(deviceId)) {
                Toast.makeText(this, "계정 ID와 기기 ID를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            AppPrefs.setAccount(this, accountId, level, deviceId);
            accountStatus.setText(currentAccountSummary());
            refreshUserKeyState();
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> PortalApi.registerFcmToken(this, token));
            Toast.makeText(this, "현재 계정/기기를 저장했습니다.", Toast.LENGTH_SHORT).show();
        });
        MaterialButton accountCreate = primaryButton("계정 추가/갱신");
        accountCreate.setOnClickListener(v -> {
            if (!AppPrefs.isAdminAccount(this)) {
                Toast.makeText(this, "Admin 계정에서만 계정 추가가 가능합니다.", Toast.LENGTH_LONG).show();
                return;
            }
            String accountId = accountInput.getText() == null ? "" : accountInput.getText().toString().trim();
            int checkedLevelId = accountLevelGroup.getCheckedRadioButtonId();
            if (checkedLevelId == View.NO_ID) {
                Toast.makeText(this, "추가할 계정 등급을 선택하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            String level = checkedLevelId == accountUser.getId()
                    ? AppPrefs.ACCOUNT_LEVEL_USER : AppPrefs.ACCOUNT_LEVEL_ADMIN;
            if (AppPrefs.ACCOUNT_LEVEL_ADMIN.equals(level)) {
                Toast.makeText(this, "Admin 계정은 서버 도메인당 1개만 가능합니다. 추가 계정은 User로 등록하세요.", Toast.LENGTH_LONG).show();
                return;
            }
            if (TextUtils.isEmpty(accountId)) {
                Toast.makeText(this, "추가할 계정 ID를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            PortalApi.createAccount(AppPrefs.accountId(this), accountId, level, (success, message) -> runOnUiThread(() -> {
                Toast.makeText(this, success ? "User 계정을 저장했습니다." : "계정 저장 실패: " + message, Toast.LENGTH_LONG).show();
            }));
        });
        accountButtons.addView(accountSave, weight1());
        accountButtons.addView(spacer());
        accountButtons.addView(accountCreate, weight1());
        root.addView(accountButtons, topMargin(8));

        // ── 1) Device auth method ──────────────────────────────────────────
        root.addView(sectionHeader("인증 방식"), topMargin(24));
        RadioGroup authGroup = new RadioGroup(this);
        RadioButton bioFirst = new RadioButton(this);
        bioFirst.setText("지문 우선 (없으면 기기 잠금/패턴)");
        bioFirst.setMinHeight(dp(48));
        bioFirst.setId(View.generateViewId());
        RadioButton credentialOnly = new RadioButton(this);
        credentialOnly.setText("패턴 / 기기 잠금만");
        credentialOnly.setMinHeight(dp(48));
        credentialOnly.setId(View.generateViewId());
        authGroup.addView(bioFirst);
        authGroup.addView(credentialOnly);
        if (AppPrefs.AUTH_DEVICE_CREDENTIAL.equals(AppPrefs.authMethod(this))) {
            credentialOnly.setChecked(true);
        } else {
            bioFirst.setChecked(true);
        }
        authGroup.setOnCheckedChangeListener((group, checkedId) -> {
            AppPrefs.setAuthMethod(this,
                    checkedId == credentialOnly.getId() ? AppPrefs.AUTH_DEVICE_CREDENTIAL : AppPrefs.AUTH_BIOMETRIC_FIRST);
            Toast.makeText(this, "인증 방식을 변경했습니다.", Toast.LENGTH_SHORT).show();
        });
        root.addView(authGroup, topMargin(8));

        // ── 2) userKey register / delete ───────────────────────────────────
        root.addView(sectionHeader("userKey"), topMargin(28));
        userKeyStatus = new TextView(this);
        root.addView(userKeyStatus, topMargin(4));

        TextInputLayout keyLayout = new TextInputLayout(this);
        keyLayout.setHint("새 userKey 입력");
        keyLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE); // eye icon
        UiKit.styleInput(keyLayout);
        final TextInputEditText keyInput = new TextInputEditText(keyLayout.getContext());
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyLayout.addView(keyInput);
        root.addView(keyLayout, topMargin(8));

        LinearLayout keyButtons = new LinearLayout(this);
        keyButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton keyRegister = primaryButton("등록 / 갱신");
        keyRegister.setOnClickListener(v -> {
            String userKey = keyInput.getText() == null ? "" : keyInput.getText().toString();
            if (TextUtils.isEmpty(userKey)) {
                Toast.makeText(this, "userKey를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            registerUserKey(userKey, keyInput);
        });
        MaterialButton keyDelete = ghostButton("삭제");
        keyDelete.setOnClickListener(v -> {
            LocalCredentialStore.clear(this);
            refreshUserKeyState();
            Toast.makeText(this, "userKey 등록을 삭제했습니다.", Toast.LENGTH_SHORT).show();
        });
        keyButtons.addView(keyRegister, weight1());
        keyButtons.addView(spacer());
        keyButtons.addView(keyDelete, weight1());
        root.addView(keyButtons, topMargin(8));

        // ── 3) Server address register / delete ────────────────────────────
        root.addView(sectionHeader("서버 주소"), topMargin(28));
        serverStatus = new TextView(this);
        root.addView(serverStatus, topMargin(4));

        TextInputLayout serverLayout = new TextInputLayout(this);
        serverLayout.setHint("https://your-server.example.com");
        UiKit.styleInput(serverLayout);
        final TextInputEditText serverInput = new TextInputEditText(serverLayout.getContext());
        serverInput.setSingleLine(true);
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setText(AppPrefs.serverBaseUrl(this));
        serverLayout.addView(serverInput);
        root.addView(serverLayout, topMargin(8));

        LinearLayout serverButtons = new LinearLayout(this);
        serverButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton serverRegister = primaryButton("등록 / 갱신");
        serverRegister.setOnClickListener(v -> {
            String url = serverInput.getText() == null ? "" : serverInput.getText().toString().trim();
            if (!url.startsWith("https://")) {
                Toast.makeText(this, "https:// 로 시작하는 주소를 입력하세요.", Toast.LENGTH_LONG).show();
                return;
            }
            AppPrefs.setServerBaseUrl(this, url);
            PortalApi.setBaseUrlOverride(url);
            refreshServerState();
            Toast.makeText(this, "서버 주소를 저장했습니다.", Toast.LENGTH_SHORT).show();
        });
        MaterialButton serverDelete = ghostButton("삭제");
        serverDelete.setOnClickListener(v -> {
            AppPrefs.clearServerBaseUrl(this);
            PortalApi.setBaseUrlOverride(null);
            serverInput.setText("");
            refreshServerState();
            Toast.makeText(this, "서버 주소를 삭제했습니다.", Toast.LENGTH_SHORT).show();
        });
        serverButtons.addView(serverRegister, weight1());
        serverButtons.addView(spacer());
        serverButtons.addView(serverDelete, weight1());
        root.addView(serverButtons, topMargin(8));

        // ── 4) Main-screen image ───────────────────────────────────────────
        root.addView(sectionHeader("메인 화면 이미지 (GIF 지원)"), topMargin(28));
        imageStatus = new TextView(this);
        root.addView(imageStatus, topMargin(4));

        LinearLayout imageControls = new LinearLayout(this);
        imageControls.setOrientation(LinearLayout.HORIZONTAL);
        imageControls.setGravity(Gravity.CENTER_VERTICAL);
        imageControls.setBaselineAligned(false);

        imagePreview = new ImageView(this);
        imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imagePreview.setBackground(UiKit.rounded(UiKit.COLOR_SURFACE_VARIANT, dp(8), UiKit.COLOR_OUTLINE, dp(1)));
        imagePreview.setPadding(dp(8), dp(8), dp(8), dp(8));
        imageControls.addView(imagePreview, new LinearLayout.LayoutParams(0, dp(132), 1f));

        LinearLayout imageButtons = new LinearLayout(this);
        imageButtons.setOrientation(LinearLayout.VERTICAL);
        imageButtons.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton imagePick = primaryButton("갤러리에서 선택");
        imagePick.setOnClickListener(v -> imagePicker.launch(new String[]{"image/*"}));
        MaterialButton imageDelete = ghostButton("삭제");
        imageDelete.setOnClickListener(v -> {
            deleteInternalImage(AppPrefs.mainImageUri(this));
            AppPrefs.clearMainImageUri(this);
            refreshImageState();
            Toast.makeText(this, "메인 이미지를 삭제했습니다.", Toast.LENGTH_SHORT).show();
        });
        imageButtons.addView(imagePick, matchWrap());
        imageButtons.addView(verticalSpacer());
        imageButtons.addView(imageDelete, matchWrap());
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(dp(136), ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.setMargins(dp(12), 0, 0, 0);
        imageControls.addView(imageButtons, buttonsParams);
        root.addView(imageControls, topMargin(8));

        setContentView(scroll);

        refreshUserKeyState();
        refreshServerState();
        refreshImageState();
    }

    private void registerUserKey(String userKey, TextInputEditText keyInput) {
        Toast.makeText(this, "등록 중...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String devicePublicKeyBase64 = DeviceKeyStore.publicKeyBase64();
                byte[] salt = CryptoUtil.randomBytes(SALT_BYTES);
                byte[] dek = CryptoUtil.deriveKey(userKey, salt);
                byte[] loginSecret = CryptoUtil.randomBytes(LOGIN_SECRET_BYTES);
                byte[] ivAndCiphertext = CryptoUtil.aesGcmEncrypt(dek, loginSecret);
                String verifierHex = CryptoUtil.sha256HexOfBytes(loginSecret);

                LocalCredentialStore.save(this, salt, ivAndCiphertext, loginSecret);

                PortalApi.enroll(this, devicePublicKeyBase64, verifierHex, (success, message) -> runOnUiThread(() -> {
                    if (success) {
                        keyInput.setText("");
                        refreshUserKeyState();
                        Toast.makeText(this, "userKey 등록이 완료되었습니다.", Toast.LENGTH_LONG).show();
                    } else {
                        LocalCredentialStore.clear(this);
                        refreshUserKeyState();
                        Toast.makeText(this, "등록 실패: " + (message == null ? "" : message), Toast.LENGTH_LONG).show();
                    }
                }));
            } catch (Exception error) {
                LocalCredentialStore.clear(this);
                runOnUiThread(() -> {
                    refreshUserKeyState();
                    Toast.makeText(this, "등록 실패: " + (error.getMessage() == null ? "" : error.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static final String MAIN_IMAGE_PREFIX = "main_bg_";

    /**
     * Copies the picked image into app-internal storage and returns its absolute path, or null on
     * failure. A unique filename is used so a replaced image is never served from Glide's cache,
     * and the previous internal copy is deleted.
     */
    private String copyImageToInternalStorage(Uri source) {
        String previous = AppPrefs.mainImageUri(this);
        File target = new File(getFilesDir(), MAIN_IMAGE_PREFIX + System.currentTimeMillis() + ".img");
        try (InputStream in = getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (Exception e) {
            target.delete();
            return null;
        }
        deleteInternalImage(previous);
        return target.getAbsolutePath();
    }

    /** Deletes a previously stored internal image copy (ignores legacy content:// URIs). */
    private void deleteInternalImage(String path) {
        if (!TextUtils.isEmpty(path) && path.startsWith(getFilesDir().getAbsolutePath())) {
            new File(path).delete();
        }
    }

    private String currentAccountSummary() {
        String level = AppPrefs.accountLevel(this);
        String device = AppPrefs.deviceId(this);
        return "현재: " + (TextUtils.isEmpty(level) ? "" : level) + " / "
                + (TextUtils.isEmpty(device) ? "" : "기기 " + device);
    }

    private void refreshUserKeyState() {
        boolean enrolled = LocalCredentialStore.isEnrolled(this);
        userKeyStatus.setText(enrolled ? "상태: 등록됨" : "상태: 미등록");
        userKeyStatus.setTextColor(enrolled ? UiKit.COLOR_SUCCESS : UiKit.COLOR_ERROR);
    }

    private void refreshServerState() {
        String override = AppPrefs.serverBaseUrl(this);
        if (TextUtils.isEmpty(override)) {
            serverStatus.setText("현재: 미설정");
        } else {
            serverStatus.setText("현재: " + override);
        }
    }

    private void refreshImageState() {
        String uri = AppPrefs.mainImageUri(this);
        if (TextUtils.isEmpty(uri)) {
            imageStatus.setText("현재: 기본 화면");
            imagePreview.setImageDrawable(null);
        } else {
            imageStatus.setText("현재: 사용자 이미지");
            try {
                Object model = uri.startsWith("/") ? new File(uri) : Uri.parse(uri);
                Glide.with(this).load(model).into(imagePreview);
            } catch (Exception ignored) {
                imagePreview.setImageDrawable(null);
            }
        }
    }

    // ── small UI helpers (programmatic, matching the app's existing style) ──
    private TextView sectionHeader(String text) {
        return UiKit.sectionTitle(this, text);
    }

    private MaterialButton primaryButton(String text) {
        return UiKit.primaryButton(this, text);
    }

    private MaterialButton ghostButton(String text) {
        return UiKit.dangerButton(this, text);
    }

    private View spacer() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        return view;
    }

    private View verticalSpacer() {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(8)));
        return view;
    }

    private LinearLayout.LayoutParams weight1() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(dp), 0, 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
