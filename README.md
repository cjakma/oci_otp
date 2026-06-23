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
- **네이티브 승인 화면**: 사용자가 기억하는 `userKey`를 입력하여 기기 내에서 직접 승인 Proof를 생성하고 서명합니다.
- **암호화 계약**:
    - **KDF**: Argon2id (`org.signal:argon2`)를 사용하여 `userKey`로부터 암호화 키 추출.
    - **Key Storage**: Android Keystore (StrongBox/TEE)의 비추출 EC P-256 키 사용.
    - **Signature**: `SHA256withECDSA` 서명으로 소유 기반 인증 수행.

## 보안 설정

- **통신 보안**: `android:usesCleartextTraffic="false"`, 전용 `network_security_config.xml`로 HTTPS 도메인 제한.
- **화면 보호**: `FLAG_SECURE` 적용으로 스크린샷 및 최근 앱 화면 캡처 방지.
- **데이터 보호**: `userKey` 및 추출된 암호화 키는 메모리에만 존재하며 저장되거나 전송되지 않음.
- **WebView 보안**: Mixed content 차단, 파일 접근 차단, SSL 오류 우회 불가.

## 앱 정보
- **앱 이름**: `pm-oci 인증용`
- **패키지**: `org.pmoci.kskillauth`
- **버전**: `0.4.0` (versionCode 4)
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

## 최신 업데이트 (v0.4.0)
- **UI 개선**: 등록(Enrollment) 화면에 Material Design 적용.
- **편의성**: 비밀번호 가시성 토글(Eye Icon) 추가.
- **유효성 검사**: `userKey` 일치 여부에 따른 등록 버튼 활성화 및 색상 변경 로직 추가.
- **안정성**: `AppCompatActivity` 도입 및 테마 호환성 수정.
