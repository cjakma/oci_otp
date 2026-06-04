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

public class AdminPortalApprovalActivity extends Activity {
    private EditText passwordInput;
    private Button approveButton;
    private TextView statusText;
    private String challengeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        challengeId = getIntent().getStringExtra("challenge_id");
        String adminId = getIntent().getStringExtra("admin_id");
        String expiresAt = getIntent().getStringExtra("expires_at");

        if (challengeId == null || challengeId.trim().isEmpty()) {
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
        description.setText("Enter the admin password to approve the pending terminal login.");
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

        passwordInput = new EditText(this);
        passwordInput.setHint("Admin password");
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.setMargins(0, dp(20), 0, 0);
        root.addView(passwordInput, inputParams);

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
        approveButton.setOnClickListener(view -> submitPassword());
        actions.addView(approveButton);

        setContentView(root);
        passwordInput.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void submitPassword() {
        String password = passwordInput.getText().toString();
        if (password.isEmpty()) {
            passwordInput.setError("Password is required.");
            return;
        }

        approveButton.setEnabled(false);
        passwordInput.setEnabled(false);
        statusText.setText("Verifying...");

        PortalApi.approveChallenge(challengeId, password, (success, message) ->
                runOnUiThread(() -> {
                    passwordInput.setText("");
                    if (success) {
                        Toast.makeText(this, "Admin terminal login approved.", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    approveButton.setEnabled(true);
                    passwordInput.setEnabled(true);
                    statusText.setText(message == null ? "Authentication failed." : message);
                    passwordInput.requestFocus();
                })
        );
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
