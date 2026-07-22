# Cloud Server Authentication Android App

> This README is arranged with the English version first and the Korean version below.
> 이 README는 영문을 위쪽에, 한글을 아래쪽에 배치했습니다.

This Android authentication app is configured for Samsung Galaxy S26, Samsung Galaxy S26 Ultra, Samsung Galaxy S22 Ultra, Galaxy A52s 5G, and LG V50 devices.
In addition to the legacy WebView flow, it includes a hardened **OTP redesign using FCM push-based approval**.

## Features and Implementation

### 1. Legacy WebView Authentication
- Reuses the existing mobile web flow by encapsulating it in a WebView.
- Default authentication service URL: `https://{entered directly inside the app}`
- Reuses the existing `/api/status` polling and `/api/submit` logic.

### 2. OTP Redesign (FCM-Based Approval)
- **FCM push notification**: A push notification is sent to the phone when an admin portal login is requested.
- **Native approval screen**: After tapping the FCM notification, if fingerprint/pattern authentication succeeds, the app generates and signs the approval proof from the stored enrollment material.
- **Cryptographic contract**:
    - **KDF**: Derives an encryption key from `userKey` with Argon2id (`org.signal:argon2`).
    - **Key Storage**: Uses a non-exportable Android Keystore EC P-256 key, backed by StrongBox/TEE when available.
    - **Signature**: Uses `SHA256withECDSA` for possession-based authentication.

## Security Settings

- **Transport security**: `android:usesCleartextTraffic="false"` and a dedicated `network_security_config.xml` for HTTPS handling.
- **Screen protection**: Applies `FLAG_SECURE` to block screenshots and recent-apps snapshots.
- **Data protection**: `userKey` and derived encryption keys exist only in memory and are never stored or transmitted. The `loginSecret` needed for approval is encrypted in app-internal storage with Android Keystore AES-GCM.
- **WebView security**: Blocks mixed content, blocks file access, and does not allow bypassing SSL errors.

## App Information
- **App name**: `인증용 App`
- **Package**: `org.pmoci.kskillauth`
- **Version**: `0.7.8` (versionCode 15)
- **SDK**: `minSdk 23`, `targetSdk 35`, `compileSdk 35`

## Build and Install

### Verification Reference
- When verifying app implementation, cryptographic contracts, or the FCM approval flow, use [`ANDROID_DEV_AND_VERIFICATION_KOR.md`](ANDROID_DEV_AND_VERIFICATION_KOR.md) as the source-of-truth document.
- Other AI agents performing code review or Android Studio verification should also prioritize the checklist and manual test scenarios in that document.

### Prerequisites
- `app/google-services.json` is required.
- `gradle.properties` must contain `adminDeviceEnrollmentKey` for server enrollment.

### Android Studio / CLI Build
1. Open the project and run Gradle Sync.
2. Build:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Device Allowance / Compatibility Notes

- Primary allowed/verified device set: **Galaxy S26**, **Galaxy S26 Ultra**, **Galaxy S22 Ultra**, **Galaxy A52s 5G**, **LG V50**.
- The repository does not contain a Java/Manifest runtime model whitelist. Android installability is governed by `minSdk 23`, required permissions/features, APK signing, and any external distribution/device-catalog policy.
- **Galaxy A52s 5G public-spec review**: Samsung's public page lists Android OS, Samsung Knox, on-screen fingerprint sensor, Google apps, 5G, and NFC. GSMArena lists Android 11 upgradeable to Android 14/One UI 6, Snapdragon 778G 5G, 4–8GB RAM, Wi-Fi, Bluetooth, NFC, and model family `SM-A528*`. Based on those specs, it satisfies this app's practical requirements: Android API above 23, Google/Firebase-capable Android environment, device credential/fingerprint authentication, Android Keystore, and network/FCM support, so it is included in the primary allowed-device set. Real-device APK install and FCM/enrollment/approval verification is still recommended.

## Latest Update (v0.7.8) — First Enrollment Account ID and Automatic Device ID
The first userKey enrollment screen now asks for `account_id` while the app generates `device_id` automatically (versionCode 15, versionName 0.7.8).

- **First-run account setup**: `EnrollmentActivity` includes a required `account_id` input alongside the server address and userKey fields.
- **Automatic device_id**: The first enrollment no longer asks the user for `device_id`; the app generates a stable Android device identifier from the device model and Android ID suffix, then stores it in `AppPrefs` before enrollment.
- **Initial account role**: The first account registered from the enrollment screen is stored locally as `admin`; additional user accounts are still managed later from the Admin-only account screen.

## Latest Update (v0.7.7) — First Enrollment Server Address Input
The first userKey enrollment screen now asks for the server address before enrollment is submitted (versionCode 14, versionName 0.7.7).

- **First-run server address setup**: `EnrollmentActivity` includes a required `https://...` server address field so first launch can register the userKey even when the compiled default server URL is intentionally empty.
- **Persistence before enrollment**: The entered server address is saved to `AppPrefs` and applied to `PortalApi` immediately before the enrollment request.
- **Validation**: The Register button is enabled only when the server address starts with `https://` and the two userKey fields match.

## Latest Update (v0.7.1) — Bug Fixes
Improved the FCM approval flow and device-specific notification receiving behavior (versionCode 10, versionName 0.7.1).

- **FCM approval flow fix**: Registered devices now submit the approval proof immediately after fingerprint/pattern authentication, without asking for `userKey` again.
- **Keystore AES-GCM fix**: Uses the IV generated by the Android Keystore Provider when encrypting `loginSecret`, preventing the `Caller-provided IV not permitted` error.
- **FCM notification channel hardening**: Defines the default FCM notification channel in the manifest and creates it at app startup so background notification messages are displayed more reliably on Android 8+ devices such as LG V50.
- **FCM diagnostic logs**: Adds logs for token acquisition, token registration success/failure, and message receipt to help diagnose multi-device delivery issues.

## Latest Update (v0.7.0) — Android Design Guide and UI Improvements
Refined the overall UI/UX and organized the codebase (versionCode 9, versionName 0.7.0).

- **Design guide alignment**: Adjusted buttons, input fields, and layout spacing according to Material Design guidance.
- **UiKit introduction**: Consolidated shared UI component creation into `UiKit.java` to reduce duplication and keep styling consistent.
- **Logic optimization**: Improved activity data passing and initialization logic for better app stability.
- **Icon and style updates**: Added the settings button icon (`ic_settings_24`) and refined app-wide themes and colors (`colors.xml`).

## Latest Update (v0.6.3) — Approval Submission "Unexpected end of stream" Fix
Fixed an issue where approval proof submission could fail with `Unexpected end of stream on com.android.okhttp...` (versionCode 8, versionName 0.6.3).

- **Cause**: `HttpURLConnection` (`com.android.okhttp`) does not automatically retry POST requests, so dropped or stale reused connections surfaced as transport exceptions. Gateway/server callback logic itself was normal.
- **App fix (`PortalApi`)**: All POST requests now retry once on transport (`IOException`) failure with a fresh connection (`post` -> `postOnce`). Real HTTP error responses (401/404/409/5xx) are not retried. The app also sends `Connection: close` to avoid reusing proxy-closed connections.
- **Server/gateway companion fix**: The gateway now forwards the actual callback status/error message to the phone instead of flattening all callback failures into `callback_failed` (502). Proof mismatch or expiration errors now surface as messages such as `Invalid authentication proof.` or `... not found or has expired.`
- **Note**: Login challenges are in-memory and have a **2-minute TTL**. If notification receipt -> fingerprint/pattern authentication -> proof submission takes longer than 2 minutes, the challenge can expire. If that happens, retry with `Send Request` from the web side.

## Latest Update (v0.6.2) — Launch Lock Authentication and Settings Background Image Fix
Fixed three behaviors (versionCode 7, versionName 0.6.2).

- **1. No saved userKey / first launch**: The app shows the `userKey setup screen` first, then completes registration with device authentication when the user taps "Register". The order changed from authentication first -> screen to input -> device authentication -> save (`EnrollmentActivity`).
- **2. Saved enrollment exists / second launch and later**: Applies an app launch lock using device authentication (`MainActivity.promptLaunchAuth`).
  - Authentication success -> enter the main screen.
  - Authentication failure -> automatic retry. After **3 cumulative failures**, the app moves to the main screen and stops prompting. Settings and FCM approval remain separately gated.
  - If the app is opened by tapping an **FCM notification**, the launch lock is skipped because `AdminPortalApprovalActivity` performs its own authentication gate, avoiding double prompts.
- **3. Settings background image behavior fix**: SAF `content://` URI read grants can disappear after app restart, causing the main screen image to fail to reload.
  - Selected images are copied into app-internal storage (`getFilesDir()/main_bg_<timestamp>.img`) and loaded from the stable path, including GIFs. Unique filenames avoid Glide cache confusion when replacing images, and internal files are deleted when the image is removed.

## Latest Update (v0.6.1) — FCM Receiving Logic Fix
- The server sends `notification` + `data` FCM messages. When the app is backgrounded, killed, or battery-optimized, `onMessageReceived` may not be called because the system tray handles the notification. This previously prevented the approval screen from opening after tapping the notification.
- `MainActivity` now detects notification-tap intent extras (`challenge_id/nonce/admin_id/expires_at`) for `admin_portal_login` and forwards them to `AdminPortalApprovalActivity` (`routeAdminPortalRequest`, `onCreate`, and `onNewIntent`).
- `MainActivity` now uses `launchMode="singleTop"` in `AndroidManifest` to prevent duplicate instances when tapping notifications while the app is already running.

## Latest Update (v0.6.0) — Fingerprint Authentication Test
- Test release for the v0.5.0 features: **fingerprint/pattern authentication, settings screen, server address settings, and main image/GIF support** (versionCode 6, versionName 0.6.0).
- Tested with server-side multi-device FCM support (web-portfolio v0.8.5.4), including scenarios where multiple devices such as S22 Ultra and V50 receive authentication pushes at the same time.
- Version bump and test release only; see v0.5.0 below for feature details.
- Checkpoint: behavior on API 28-29 devices such as V50 when fingerprint is unavailable and pattern/PIN-only authentication is used.

## Latest Update (v0.5.0) — Fingerprint Authentication Added
- **Device authentication integration**: Added `DeviceAuth` based on `androidx.biometric`. It prefers fingerprint and falls back to device lock pattern/PIN.
  - Enrollment, approval screen, and settings entry are all protected by device authentication from the first install.
- **Settings screen (`SettingsActivity`)**: Opened from the top-right settings button on the main screen and protected by device authentication.
  - **Authentication method selection**: Fingerprint first / pattern or device lock only.
  - **userKey register/delete**: Input can be revealed with the eye icon during registration.
  - **Server address register/delete**: Runtime HTTPS server address override for the compiled default.
  - **Main screen image register/delete**: Select from gallery, with **GIF playback support** via Glide.
- **Native main screen**: Replaced the gateway web page/WebView with a native screen containing a configurable image and settings button.
- **Server integration**: Sends FCM token registration, enrollment, and proof submission to the configured server address (`PortalApi` runtime base URL override).
- **Network security**: Cleartext HTTP remains blocked; HTTPS is allowed through system trust anchors to support custom server addresses.

### Build Notes
- Added dependencies: `androidx.biometric:biometric:1.1.0`, `com.github.bumptech.glide:glide:4.16.0`.
- On API 30+, fingerprint and device credential can be offered in one prompt. On API 28-29 devices such as V50, pattern/PIN-only operation without fingerprint may require additional handling via `KeyguardManager`, so real-device verification is recommended.

---

# Cloud Server 인증용 Android 앱

대상 기기: Samsung Galaxy S26, Samsung Galaxy S26 Ultra, Samsung Galaxy S22 Ultra, Galaxy A52s 5G, LG V50 기준으로 구성한 인증용 앱입니다.
기존 WebView 방식에 더해, 보안이 강화된 **OTP redesign (FCM 푸시 기반 승인)** 기능이 추가되었습니다.

## 주요 기능 및 구현 방식

### 1. Legacy WebView 인증
- 기존 Mobile Web을 WebView로 캡출화하여 재사용합니다.
- 기본 인증서비스 주소: `https://{App내에서 직접 입력}`
- `/api/status` 폴링 및 `/api/submit` 로직을 그대로 사용합니다.

### 2. OTP Redesign (FCM 기반 승인)
- **FCM 푸시 알림**: 관리자 포털 로그인 시도 시 폰으로 푸시 알림이 발송됩니다.
- **네이티브 승인 화면**: FCM 알림 탭 후 지문/패턴 인증이 통과하면, 저장된 등록 정보로 승인 Proof를 생성하고 서명합니다.
- **암호화 계약**:
    - **KDF**: Argon2id (`org.signal:argon2`)를 사용하여 `userKey`로부터 암호화 키 추출.
    - **Key Storage**: Android Keystore (StrongBox/TEE)의 비추출 EC P-256 키 사용.
    - **Signature**: `SHA256withECDSA` 서명으로 소유 기반 인증 수행.

## 보안 설정

- **통신 보안**: `android:usesCleartextTraffic="false"`, 전용 `network_security_config.xml`로 HTTPS 도메인 제한.
- **화면 보호**: `FLAG_SECURE` 적용으로 스크린샷 및 최근 앱 화면 캡처 방지.
- **데이터 보호**: `userKey` 및 추출된 암호화 키는 메모리에만 존재하며 저장되거나 전송되지 않음. 승인에 필요한 `loginSecret`은 Android Keystore AES-GCM으로 앱 내부에 암호화 저장.
- **WebView 보안**: Mixed content 차단, 파일 접근 차단, SSL 오류 우회 불가.

## 앱 정보
- **앱 이름**: `인증용 App`
- **패키지**: `org.pmoci.kskillauth`
- **버전**: `0.7.8` (versionCode 15)
- **SDK**: `minSdk 23`, `targetSdk 35`, `compileSdk 35`

## 빌드 및 설치

### 검증 기준 문서
- 앱 구현·암호 계약·FCM 승인 흐름을 검증할 때는 [`ANDROID_DEV_AND_VERIFICATION_KOR.md`](ANDROID_DEV_AND_VERIFICATION_KOR.md)를 기준 문서로 사용하세요.
- 다른 AI Agent가 코드 리뷰나 Android Studio 검증을 수행할 때도 위 문서의 체크리스트와 수동 테스트 시나리오를 우선 기준으로 삼아야 합니다.

### 선행 조건
- `app/google-services.json` 파일이 필요합니다.
- `gradle.properties`에 `adminDeviceEnrollmentKey`가 설정되어 있어야 서버 등록이 가능합니다.

### Android Studio / CLI 빌드
1. 프로젝트를 열고 Gradle Sync를 수행합니다.
2. 빌드 실행:
   ```bash
   ./gradlew assembleDebug
   ```
3. 설치:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## 기기 허용 / 호환성 메모

- 1차 허용/검증 기기: **Galaxy S26**, **Galaxy S26 Ultra**, **Galaxy S22 Ultra**, **Galaxy A52s 5G**, **LG V50**.
- 현재 저장소에는 Java/Manifest 수준의 런타임 모델 whitelist가 없습니다. Android 설치 가능 여부는 `minSdk 23`, 요구 권한/기능, APK 서명, 그리고 외부 배포 채널의 device catalog 정책에 의해 결정됩니다.
- **Galaxy A52s 5G 공개 스펙 검토**: Samsung 공식 페이지는 Android OS, Samsung Knox, 온스크린 지문 센서, Google 앱, 5G, NFC를 명시합니다. GSMArena는 Android 11 출시 후 Android 14/One UI 6 업그레이드, Snapdragon 778G 5G, 4–8GB RAM, Wi-Fi/Bluetooth/NFC, `SM-A528*` 모델군을 명시합니다. 따라서 이 앱의 실질 요구사항인 Android API 23 이상, Google/Firebase 사용 가능 환경, 기기 잠금/지문 인증, Android Keystore, 네트워크/FCM 지원을 공개 스펙상 충족하므로 1차 허용 기기 목록에 포함했습니다. 실기기에서 설치 → FCM token 등록 → userKey enrollment → 로그인 승인까지 확인하는 것은 계속 권장합니다.

## 최신 업데이트 (v0.7.8) — 최초 등록 account_id 입력 및 device_id 자동 처리
첫 userKey 등록 화면에서 `account_id`를 입력받고, `device_id`는 앱이 자동 생성해 등록하도록 수정했습니다(versionCode 15, versionName 0.7.8).

- **첫 기동 account_id 설정**: `EnrollmentActivity`에 서버 주소/userKey 입력과 함께 필수 `account_id` 입력란을 추가했습니다.
- **device_id 자동 생성**: 최초 등록 화면에서는 `device_id`를 묻지 않고, 앱이 기기 모델과 Android ID suffix 기반의 안정적인 기기 식별자를 생성해 `AppPrefs`에 저장한 뒤 등록 요청에 사용합니다.
- **최초 계정 등급**: 최초 등록 화면에서 등록되는 계정은 로컬에 `admin`으로 저장됩니다. 추가 user 계정은 이후 Admin 전용 계정 화면에서 관리합니다.

## 최신 업데이트 (v0.7.7) — 최초 등록 서버 주소 입력 추가
첫 userKey 등록 화면에서 서버 주소를 함께 입력한 뒤 등록 요청을 보내도록 수정했습니다(versionCode 14, versionName 0.7.7).

- **첫 기동 서버 주소 설정**: 기본 서버 주소가 비어 있어도 최초 등록이 가능하도록 `EnrollmentActivity`에 필수 `https://...` 서버 주소 입력란을 추가했습니다.
- **등록 전 저장/적용**: 등록 요청 직전에 입력한 서버 주소를 `AppPrefs`에 저장하고 `PortalApi` override에 적용합니다.
- **입력 검증**: 서버 주소가 `https://`로 시작하고 userKey/확인 값이 일치할 때만 등록 버튼이 활성화됩니다.

## 최신 업데이트 (v0.7.6) — 설정 화면 표시/기본 서버주소 정리
설정 화면에서 현재 반영 버전을 바로 확인할 수 있도록 앱 버전을 표시하고, 계정/기기/userKey/서버주소 표시 문구를 정리했습니다(versionCode 13, versionName 0.7.6).

- **버전 표시**: 메인 화면과 설정 화면에 `BuildConfig.VERSION_NAME`/`VERSION_CODE`를 표시합니다.
- **현재 계정 표시**: `현재: admin 또는 user / 기기명` 형태로 계정 등급과 기기명만 표시합니다. 등록값이 없으면 빈칸으로 둡니다.
- **userKey 표시**: userKey 상태에서 `계정 AAAA / 기기 A` 문구를 제거하고 등록/미등록 상태만 표시합니다.
- **서버 주소 기본값**: `PORTAL_API_BASE_URL` 컴파일 기본값을 빈값으로 변경했습니다. 서버 주소가 없으면 `현재: 미설정`으로 표시하고, FCM/enroll 요청은 명확히 실패/스킵합니다.
- **Admin 단일성**: 계정 추가 UI에서 Admin 추가를 차단하고 User 추가만 허용합니다. 서버도 같은 도메인에 다른 Admin 계정을 추가하려 하면 `409`로 거부해야 합니다.

## 최신 업데이트 (v0.7.5) — 단일관리자+다중사용자 계정별 OCIOTP 인증
서버의 `accounts[]` 구조에 맞춰 앱이 현재 계정/권한/기기 ID를 저장하고, 계정별 userKey·credential·인증 이력을 분리하도록 보강했습니다(versionCode 12, versionName 0.7.5).

- **계정/기기 설정(`SettingsActivity`, `AppPrefs`)**: 설정 화면에 계정 ID, 계정 권한(`admin`/`user`), 기기 ID 입력을 추가했습니다. Admin 계정에서만 `계정 추가/갱신` API를 호출할 수 있습니다.
- **계정별 userKey 저장(`LocalCredentialStore`)**: 기존 단일 저장소를 `account_id:device_id` 스코프로 분리해 AAAA/A, AAAA/B, BBBB/D가 서로 다른 userKey와 credential을 사용할 수 있습니다. 기존 AAAA/A 단일 계정 설치는 legacy fallback으로 읽습니다.
- **계정별 FCM/enroll/proof 계약(`PortalApi`)**: FCM token 등록, device public key enrollment, proof submit에 `account_id`, `account_level`, `device_id`를 포함합니다.
- **계정별 인증 이력(`AuthRequestHistoryStore`)**: pending/approved 이력을 `account_id`와 함께 저장·표시하여 AAAA와 BBBB 요청/완료 이력을 구분합니다.

## 최신 업데이트 (v0.7.4) — 멀티 디바이스 인증 완료 이력 유지
여러 디바이스(A/B/C)에 동시에 인증 요청이 도착한 뒤 B 디바이스에서 승인하더라도, A/C 디바이스가 기존 요청을 삭제하지 않고 **요청됨 → 인증됨** 상태를 확인할 수 있도록 보강했습니다(versionCode 11, versionName 0.7.4).

- **앱 수신 로직(`MyFirebaseMessagingService`)**: 기존 `admin_portal_login` 요청(`status=pending`)은 로컬 이력에 `요청됨`으로 저장하고, 서버가 보내는 완료 FCM(`status=approved`/`authenticated`)은 같은 `challenge_id`의 알림을 `인증 완료`로 업데이트합니다. 알림 ID를 `challenge_id.hashCode()`로 유지해 pending 알림을 조용히 삭제하지 않고 상태만 바꿉니다.
- **앱 메인 화면(`MainActivity`)**: 최근 인증 요청/완료 이력 5건을 하단 패널에 표시합니다. 앱이 백그라운드/종료 상태라 `onMessageReceived`가 호출되지 않고 시스템 알림 탭으로 들어오는 경우에도 pending/approved extras를 이력에 반영합니다.
- **로컬 감사 이력(`AuthRequestHistoryStore`)**: 최근 30건을 앱 SharedPreferences에 보관합니다. 인증 자체와 분리된 best-effort 저장소라 이력 저장 실패가 승인 흐름을 막지 않습니다.
- **서버 계약**: ociotp를 인증도구로 쓰는 서버는 승인 성공 후 모든 등록 FCM 토큰에 `type=admin_portal_login`, `challenge_id`, `status=approved`, `approved_at` 데이터 푸시를 best-effort로 보내야 합니다. 완료 FCM 실패는 이미 성공한 브라우저 인증을 롤백하지 않습니다.

## 최신 업데이트 (v0.7.1) — 오류수정
FCM 승인 흐름과 기기별 알림 수신 문제를 보완했습니다(versionCode 10, versionName 0.7.1).

- **FCM 승인 흐름 수정**: 등록된 기기에서는 FCM 인증 요청 시 지문/패턴 인증 후 userKey 재입력 없이 바로 승인 proof를 제출합니다.
- **Keystore AES-GCM 오류 수정**: `Caller-provided IV not permitted` 오류가 나지 않도록 Android Keystore Provider가 생성한 IV를 사용해 `loginSecret`을 암호화 저장합니다.
- **FCM 알림 채널 보강**: Android 8+ 기기(LG V50 등)에서 백그라운드 notification 메시지가 안정적으로 표시되도록 기본 FCM 알림 채널을 manifest에 지정하고 앱 시작 시 미리 생성합니다.
- **FCM 진단 로그 추가**: 토큰 발급, 토큰 서버 등록 성공/실패, 메시지 수신 로그를 남겨 멀티 디바이스 수신 문제를 추적할 수 있게 했습니다.

## 최신 업데이트 (v0.7.0) — 안드로이드 디자인 가이드 적용 및 UI 개선
전반적인 UI/UX를 다듬고 코드를 체계화했습니다(versionCode 9, versionName 0.7.0).

- **디자인 가이드 적용**: Material Design 가이드를 준수하여 버튼, 입력 필드, 레이아웃 간격 등을 조정했습니다.
- **UiKit 도입**: 공통적으로 사용되는 UI 컴포넌트 생성을 `UiKit.java`로 통합하여 코드 중복을 제거하고 일관된 디자인을 적용했습니다.
- **로직 최적화**: 액티비티 간의 데이터 전달 및 초기화 로직을 개선하여 앱 안정성을 높였습니다.
- **아이콘 및 스타일 수정**: 설정 버튼 아이콘(`ic_settings_24`)을 추가하고, 앱 전반의 테마와 색상(`colors.xml`)을 더욱 세련되게 다듬었습니다.

## 최신 업데이트 (v0.6.3) — 승인 제출 "Unexpected end of stream" 수정
Approve(proof 제출) 시 `Unexpected end of stream on com.android.okhttp...` 오류가 나던 문제를 수정했습니다(versionCode 8, versionName 0.6.3).

- **원인**: `HttpURLConnection`(com.android.okhttp)은 **POST를 자동 재시도하지 않아**, 끊기거나 재사용된(stale) 연결이 그대로 전송 예외로 표면화됩니다. 게이트웨이·서버 콜백 로직 자체는 정상.
- **앱 수정(`PortalApi`)**: 모든 POST에 대해 전송(IOException) 실패 시 **새 연결로 1회 재시도**(`post` → `postOnce` 분리). 실제 HTTP 오류 응답(401/404/409/5xx)은 재시도하지 않습니다. 또한 요청에 `Connection: close`를 지정해 프록시가 이미 닫은 연결을 재사용하지 않도록 합니다.
- **서버(게이트웨이) 동반 수정**: 게이트웨이가 콜백 실패 시 무조건 `callback_failed`(502)로 뭉개던 것을, **콜백의 실제 status/에러 메시지를 그대로 폰에 전달**하도록 변경. 이제 proof 불일치/만료 등은 `Invalid authentication proof.` / `... not found or has expired.` 처럼 실제 사유가 표시됩니다.
- **참고**: 로그인 challenge는 in-memory + **TTL 2분**입니다. 알림 수신 → 지문/패턴 인증 → proof 제출이 2분을 넘기면 만료될 수 있으니, 위 메시지가 보이면 웹에서 다시 `Send Request` 후 진행하세요.

## 최신 업데이트 (v0.6.2) — 실행 잠금 인증 + 설정 배경이미지 수정
세 가지 동작을 수정했습니다(versionCode 7, versionName 0.6.2).

- **① userKey 미저장(최초 실행)**: `userKey 설정화면`을 **먼저** 표시하고, "등록" 버튼을 누를 때 기기 인증(지문/패턴)으로 등록을 완료합니다. (기존: 인증 먼저 → 화면 → 순서를 `입력 → 기기 인증 → 저장`으로 변경, `EnrollmentActivity`.)
- **② 등록 정보 저장됨(2회차 이상)**: 앱 실행 시 **기기 인증 앱 잠금**(`MainActivity.promptLaunchAuth`)을 적용합니다.
  - 인증 성공 → 메인 화면 진입.
  - 인증 실패 → 자동 재시도. **누적 3회 실패 시 메인 화면으로 전환**(이후 프롬프트 중단). 설정·FCM 승인은 각각 별도 기기 인증이 그대로 유지됩니다.
  - **FCM 알림 탭**으로 진입한 경우는 승인 화면(`AdminPortalApprovalActivity`)이 자체 인증하므로 잠금을 생략(이중 인증 방지).
- **③ 설정 배경이미지 동작 수정**: SAF `content://` URI는 앱 재시작 후 읽기 권한이 사라져 메인 화면에서 이미지를 다시 불러오지 못하던 문제를 수정했습니다.
  - 선택한 이미지를 **앱 내부 저장소로 복사**(`getFilesDir()/main_bg_<timestamp>.img`)한 뒤 고정 경로로 로드(GIF 포함). 교체 시 고유 파일명으로 Glide 캐시 꼬임을 방지하고, 삭제 시 내부 파일도 함께 정리합니다.

## 최신 업데이트 (v0.6.1) — FCM 수신 로직 에러수정
- 서버가 `notification`+`data` FCM 메시지를 보내면서, 앱이 **백그라운드/종료/절전** 상태일 때 `onMessageReceived`가 호출되지 않아(시스템 트레이가 알림 처리) 알림 탭 시 인증 화면이 뜨지 않던 문제를 수정했습니다.
- `MainActivity`가 알림 탭으로 전달된 `admin_portal_login` 인텐트 extras(`challenge_id/nonce/admin_id/expires_at`)를 감지해 `AdminPortalApprovalActivity`로 전달합니다(`routeAdminPortalRequest`, `onCreate` + `onNewIntent`).
- `AndroidManifest`의 `MainActivity`에 `launchMode="singleTop"`을 추가해 실행 중 알림 탭 시 중복 인스턴스를 방지합니다.

## 최신 업데이트 (v0.6.0) — 지문인식 테스트
- v0.5.0의 **지문/패턴 인증 + 설정 화면 + 서버 주소 설정 + 메인 이미지(GIF)** 기능을 실기기 테스트하기 위한 빌드(versionCode 6, versionName 0.6.0).
- 서버 측 멀티 디바이스 FCM 적용(web-portfolio v0.8.5.4)으로, S22 Ultra·V50 등 **여러 기기가 동시에 인증 푸시를 수신**하는 시나리오를 함께 점검합니다.
- 코드 변경 없는 버전업 + 테스트 릴리스(기능 상세는 아래 v0.5.0 항목 참조).
- 확인 포인트: API 28–29(예: V50)에서 지문 미사용·패턴/PIN 단독 인증 동작.

## 최신 업데이트 (v0.5.0) — 지문인식 추가
- **디바이스 인증(지문/패턴) 연동**: `androidx.biometric` 기반 `DeviceAuth` 추가. 지문 우선, 없으면 기기 잠금(패턴/PIN)으로 폴백.
  - 최초 설치 시 Enrollment, 승인 화면, 설정 진입이 모두 디바이스 인증으로 보호됩니다.
- **설정 화면(`SettingsActivity`)**: 메인 화면 우측 상단 ⚙ 설정 버튼에서 진입(디바이스 인증 필요).
  - **인증 방식 선택**: 지문 우선 / 패턴·기기잠금만.
  - **userKey 등록·삭제**: eye 아이콘으로 입력값 표시 가능(등록 시).
  - **서버 주소 등록·삭제**: 컴파일된 기본값을 런타임에 덮어쓰는 서버 주소(HTTPS) 설정.
  - **메인 화면 이미지 등록·삭제**: 갤러리에서 선택, **GIF 재생 지원**(Glide).
- **메인 화면 네이티브화**: 게이트웨이 웹 페이지(WebView) 대신 설정 가능한 이미지 + 설정 버튼의 네이티브 화면으로 전환.
- **서버 연동**: 설정한 서버 주소로 FCM 토큰 등록/enroll/proof 전송(`PortalApi` 런타임 base URL 오버라이드).
- **네트워크 보안**: cleartext(HTTP)는 계속 차단, HTTPS는 시스템 신뢰 기관 기반으로 임의 호스트 허용(사용자 지정 서버 대응).

### 빌드 참고
- 추가 의존성: `androidx.biometric:biometric:1.1.0`, `com.github.bumptech.glide:glide:4.16.0`.
- API 30+ 에서는 지문+기기잠금을 한 프롬프트로 제공합니다. API 28–29(예: V50)에서 **지문 없이 패턴/PIN만** 사용하는 경우 별도 처리(KeyguardManager)가 필요할 수 있어 실기기 확인을 권장합니다.
