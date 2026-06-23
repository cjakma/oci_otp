package org.pmoci.kskillauth;

import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

/**
 * Device-level authentication gate (fingerprint preferred, pattern/PIN fallback).
 *
 * Used to guard: first-launch enrollment, the Settings screen, and applying setting changes.
 * Nothing in the app proceeds to sensitive actions without passing this.
 */
final class DeviceAuth {
    interface Result {
        void onSuccess();

        void onFailure(String message);
    }

    private DeviceAuth() {
    }

    /** Authenticates the user with the configured method, then invokes the result on the UI thread. */
    static void authenticate(AppCompatActivity activity, String title, String subtitle, Result result) {
        final BiometricManager manager = BiometricManager.from(activity);
        final boolean preferBiometric = AppPrefs.AUTH_BIOMETRIC_FIRST.equals(AppPrefs.authMethod(activity));

        int allowed = resolveAllowedAuthenticators(manager, preferBiometric);

        BiometricPrompt prompt = new BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        result.onFailure(errString == null ? "인증이 취소되었습니다." : errString.toString());
                    }

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult authResult) {
                        result.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // Transient mismatch (e.g. wrong finger). The prompt stays open; no-op.
                    }
                });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle);

        boolean usesDeviceCredential =
                (allowed & BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0;

        builder.setAllowedAuthenticators(allowed);
        // A negative button is required UNLESS device credential is allowed (system provides its own).
        if (!usesDeviceCredential) {
            builder.setNegativeButtonText("취소");
        }

        try {
            prompt.authenticate(builder.build());
        } catch (Exception e) {
            result.onFailure(e.getMessage() == null ? "인증을 시작할 수 없습니다." : e.getMessage());
        }
    }

    /**
     * Picks the authenticator set:
     * - Android 11+ (API 30): combine biometric + device credential so the system shows
     *   fingerprint first and lets the user drop to pattern/PIN.
     * - Older (API 23–29): combining is not supported, so use biometric when enrolled,
     *   otherwise device credential.
     */
    private static int resolveAllowedAuthenticators(BiometricManager manager, boolean preferBiometric) {
        final int weak = BiometricManager.Authenticators.BIOMETRIC_WEAK;
        final int credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        boolean biometricReady;
        try {
            biometricReady = manager.canAuthenticate(weak) == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception e) {
            biometricReady = false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return preferBiometric ? (weak | credential) : credential;
        }

        // API 23–29: cannot OR biometric with device credential in one prompt.
        if (preferBiometric && biometricReady) {
            return weak;
        }
        return credential;
    }
}
