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
