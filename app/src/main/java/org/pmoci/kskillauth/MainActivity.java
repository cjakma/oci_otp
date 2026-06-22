package org.pmoci.kskillauth;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;

/**
 * "pm-oci 인증용" Android app.
 *
 * This intentionally reuses the existing mobile web auth UI instead of
 * duplicating the enabled/disabled/status logic in Android code.
 */
public class MainActivity extends Activity {
    private static final String AUTH_URL = BuildConfig.AUTH_BASE_URL;
    private static final String ALLOWED_HOST = Uri.parse(AUTH_URL).getHost();

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Passphrase 입력 화면이 최근 앱/스크린샷에 남지 않게 방지합니다.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        registerForAdminPortalRequests();

        // OTP redesign: prompt one-time enrollment before the device can approve logins.
        if (!LocalCredentialStore.isEnrolled(this)) {
            startActivity(new android.content.Intent(this, EnrollmentActivity.class));
        }

        webView = new WebView(this);
        configureWebView(webView);
        setContentView(webView);

        if (savedInstanceState == null) {
            webView.loadUrl(AUTH_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void registerForAdminPortalRequests() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(PortalApi::registerFcmToken);
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);          // 기존 모바일 웹의 fetch/polling 로직 재사용
        settings.setDomStorageEnabled(false);         // 현재 서비스에는 local/sessionStorage 불필요
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSaveFormData(false);

        view.clearCache(true);
        view.clearHistory();
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowed(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowed(Uri.parse(url));
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 인증용 입력 앱이므로 SSL 오류는 절대 우회하지 않습니다.
                handler.cancel();
                Toast.makeText(MainActivity.this, getString(R.string.ssl_error_message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isAllowed(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && ALLOWED_HOST != null
                && ALLOWED_HOST.equalsIgnoreCase(uri.getHost());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
