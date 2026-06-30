# Hermes Agent gateway 이용한 인증용 Android 앱 (ociotp)

대상 기기: Samsung Galaxy S26, Samsung Galaxy S22 Ultra, LG V50 기준으로 구성한 인증용 앱입니다.
기존 WebView 방식에 더해, 보안이 강화된 **OTP redesign (FCM 푸시 기반 승인)** 기능이 추가되었습니다.

## 주요 기능 및 구현 방식

### 1. Legacy WebView 인증
- 기존 Mobile Web을 WebView로 캡출화하여 재사용합니다.
- 기본 인증서비스 주소: `https://otp.pm-oci.duckdns.org`
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
- **앱 이름**: `pm-oci 인증용`
- **패키지**: `org.pmoci.kskillauth`
- **버전**: `0.7.1` (versionCode 10)
- **SDK**: `minSdk 23`, `targetSdk 35`, `compileSdk 35`

## 빌드 및 설치

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
