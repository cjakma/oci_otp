# pm-oci 인증앱 (ociotp) — 개발 내용 & Android Studio 검증 가이드

이 문서는 **Android Studio의 AI(예: Gemini/Assistant)에게 전달**하여, 이 앱이 의도대로 구현되었는지
확인·검증하기 위한 자료입니다. 서버(0852, Node.js)와의 암호 계약이 **정확히 일치**하는지가 핵심입니다.

- 패키지: `org.pmoci.kskillauth` · `minSdk 23` / `targetSdk 35` / `compileSdk 35`
- 버전: `versionCode 10` / `versionName 0.7.1`
- 대상기기: Galaxy S26, Galaxy S22 Ultra, LG V50

---

## 1. 앱의 역할 (OTP admin 인증)

0852 포트폴리오의 admin 로그인을 **ID/PW 대신 OTP**로 처리하는 2차기기 인증앱입니다.
- 사용자가 기억하는 **userKey**(비밀)와 폰의 **DeviceKey**(Keystore, 소유)를 폰 안에서 조합해 인증.
- 비밀번호/AES키/login-secret은 **폰을 떠나지 않음**. 서버는 검증값만 보관, 복호화 없음.
- 현재 메인 화면은 WebView가 아니라 네이티브 대기 화면이며, FCM 알림 또는 시스템 트레이 알림 탭을 통해 승인 화면으로 진입합니다.

## 2. 인증 흐름 & 암호 계약 (★서버와 일치해야 함)

### Enrollment (최초 1회 · `EnrollmentActivity`)
```
DeviceKey   = Keystore EC P-256 (StrongBox 우선)         # 비추출, 서명 전용
salt        = randomBytes(16)
DEK         = Argon2id(userKey, salt, m=64MiB, t=3, p=1, len=32)
loginSecret = randomBytes(32)
ciphertext  = AES-256-GCM(DEK, loginSecret)  ->  저장형식 = iv(12) || ct(+16 tag)
verifier    = SHA-256(loginSecret)  (소문자 hex 64자)
로컬 저장(SharedPreferences): salt(b64), ciphertext(b64), Android Keystore AES-GCM으로 감싼 loginSecret(b64)
서버 전송  : POST /api/admin/portal-enroll { device_pubkey=base64(DER SPKI), verifier=hex }  + 헤더 X-Admin-Device-Key
```

### Login (FCM 수신 후 · `AdminPortalApprovalActivity`)
```
입력: 없음. 지문/패턴 등 기기 인증 통과 후 자동 진행
loginSecret = Android Keystore AES-GCM으로 보호된 로컬 저장값 복호화
verifier    = SHA-256(loginSecret) (hex)
proof       = SHA-256( verifier + challengeId + nonce )  (UTF-8 문자열 연결, 결과 소문자 hex)
signMessage = UTF-8( challengeId + "." + nonce + "." + proof )
sig         = ECDSA(SHA256withECDSA, DeviceKey) over signMessage  ->  DER, base64
전송: (GATEWAY_BASE_URL 있으면) POST {GATEWAY}/api/submit { cid=challengeId, proof, sig }
      (없으면)                 POST {PORTAL}/api/admin/portal-callback { challenge_id, proof, sig } + X-Admin-Device-Key
```

### 서버측 검증(참고 — 일치 확인용)
```
expectedProof = SHA-256( verifier_stored + cid + nonce )           # 상수시간 비교
sig 검증      = crypto.verify('sha256', `${cid}.${nonce}.${proof}`, device_pubkey(DER SPKI), sig(DER))
```
> ⚠️ **검증 핵심**: 위 `proof`/`signMessage`의 **문자열 연결 순서·구분자(`.`)·hex 대소문자(소문자)**, 
> 공개키 형식(**DER SPKI base64**), 서명 형식(**DER**)이 서버와 정확히 동일해야 함.

## 3. 구현 파일

| 파일 | 역할 |
|---|---|
| `CryptoUtil.java` | Argon2id(`org.signal:argon2`), AES-256-GCM(iv∥ct), SHA-256 hex, base64/hex |
| `DeviceKeyStore.java` | Keystore EC P-256 생성(StrongBox→TEE fallback), 공개키 base64(DER SPKI), `SHA256withECDSA` 서명 |
| `LocalCredentialStore.java` | salt·ciphertext·Android Keystore AES-GCM 암호화 loginSecret을 SharedPreferences에 보관 |
| `EnrollmentActivity.java` | userKey(+확인) 입력 → 위 enrollment 수행, 실패 시 로컬 롤백 |
| `AdminPortalApprovalActivity.java` | FCM 수신 후 기기 인증 → 저장된 loginSecret으로 proof/sig 생성·제출, 미등록 시 enrollment 유도 |
| `PortalApi.java` | `registerFcmToken` / `enroll` / `submitProof`(Gateway 우선, fallback 서버 callback) |
| `MyFirebaseMessagingService.java` | FCM 토큰 갱신 등록, `admin_portal_login` data 푸시 수신 → 알림 생성, 알림 채널 생성 |
| `MainActivity.java` | 네이티브 메인 화면 + FCM 토큰 등록 + FCM 알림 탭 라우팅 + 미등록 시 EnrollmentActivity |
| `SettingsActivity.java` | 인증 방식, userKey 등록/삭제, 서버 주소 override, 메인 이미지 관리 |
| `AppPrefs.java` | 인증 방식, 서버 주소 override, 메인 이미지 경로 등 비밀이 아닌 앱 설정 저장 |
| `UiKit.java` | 공통 Material UI 컴포넌트/색상/레이아웃 헬퍼 |
| `AndroidManifest.xml` | 액티비티 등록, FCM 서비스, `POST_NOTIFICATIONS`, 기본 FCM 알림 채널, cleartext 차단 |
| `app/build.gradle` | `org.signal:argon2:13.1`, Firebase Messaging, Biometric, Material, Glide, buildConfig, Java 17 |

## 4. 빌드 선행조건 (검증 전 반드시)
- [ ] `app/google-services.json` 배치(현재 gitignore로 저장소에 없음 — Firebase `pm-oci-auth`에서 받기)
- [ ] Gradle 의존성 좌표 확인: `org.signal:argon2:13.1` 가 해석되는지. **안 되면 대체**:
      `com.lambdapioneer.argon2kt:argon2kt:1.5.0`(Kotlin) 또는 다른 Android용 Argon2 AAR로 교체하고
      `CryptoUtil.deriveKey`의 호출부를 해당 API로 맞출 것.
- [ ] JDK 17, Android SDK 35
- [ ] 빌드 파라미터 예시:
```
./gradlew assembleDebug \
  -PportalApiBaseUrl=https://portfolio.pm-oci.duckdns.org \
  -PgatewayBaseUrl=https://otp.pm-oci.duckdns.org \
  -PadminDeviceEnrollmentKey="<서버와 동일 키>"
```

## 5. ✅ Android Studio AI 검증 체크리스트

### A. 빌드/의존성
- [ ] `org.signal:argon2` API가 코드와 일치(`Argon2.Builder(Version.V13).type(Type.Argon2id).memoryCost(MemoryCost.MiB(64)).parallelism(1).iterations(3).hashLength(32).build().hash(pw, salt).getHash()`). 버전에 따라 시그니처 다르면 수정 제안.
- [ ] `buildConfig true` 활성, `BuildConfig.GATEWAY_BASE_URL`/`PORTAL_API_BASE_URL`/`ADMIN_DEVICE_ENROLLMENT_KEY` 생성 확인.

### B. Keystore / 서명 (`DeviceKeyStore`)
- [ ] EC P-256(`secp256r1`), `PURPOSE_SIGN|VERIFY`, `DIGEST_SHA256`. StrongBox 실패 시 TEE fallback 동작.
- [ ] `publicKey.getEncoded()` = X.509 **SPKI DER** 인지(서버가 `type:'spki'`로 파싱). base64 NO_WRAP.
- [ ] `Signature("SHA256withECDSA").sign()` 이 **DER** 서명 반환(서버 기본 dsaEncoding 'der'와 일치).
- [ ] 재설치/Keystore 삭제 시 키 분실 → 재enrollment 필요(의도된 동작).

### C. Argon2 / AES-GCM (`CryptoUtil`)
- [ ] `aesGcmEncrypt`가 `iv(12)∥ciphertext` 반환, `aesGcmDecrypt`가 동일 포맷 파싱. GCM tag 128bit.
- [ ] 등록/갱신 때 입력한 userKey로 생성한 loginSecret이 Android Keystore AES-GCM으로 저장되고, 승인 때 userKey 재입력 없이 복호화되는지.
- [ ] Keystore AES-GCM 암호화 시 앱이 IV를 직접 주입하지 않고 Provider가 생성한 IV를 저장하는지(`Caller-provided IV not permitted` 방지).
- [ ] `sha256Hex`/`sha256HexOfBytes` 결과가 **소문자 hex** 인지(`Character.forDigit` 사용 → 소문자).

### D. proof/sig 포맷 (★최우선)
- [ ] `proof = sha256Hex(verifierHex + challengeId + nonce)` — 연결 순서/대소문자 확인.
- [ ] `signMessage = (challengeId + "." + nonce + "." + proofHex)` UTF-8 — 구분자 `.` 확인.
- [ ] enrollment `verifier`와 login `verifier` 계산이 동일(`SHA-256(loginSecret)` hex)한지.

### E. FCM / 액티비티
- [ ] `MyFirebaseMessagingService`가 `type=="admin_portal_login"` && `nonce` 존재 시에만 알림.
- [ ] Intent extra(`challenge_id`,`nonce`,`admin_id`,`expires_at`)가 `AdminPortalApprovalActivity`로 정확히 전달.
- [ ] 백그라운드/종료 상태의 `notification`+`data` 메시지는 시스템 트레이 탭 → `MainActivity.routeAdminPortalRequest()` → `AdminPortalApprovalActivity`로 전달되는지.
- [ ] 포그라운드 data 메시지는 `MyFirebaseMessagingService`가 알림을 만들고 탭 시 `AdminPortalApprovalActivity`로 직접 이동하는지.
- [ ] Android 8+ 기본 FCM 알림 채널(`admin_portal_login`)이 manifest meta-data와 앱 시작 시 `ensureAdminPortalChannel()`로 생성되는지.
- [ ] FCM 토큰 발급/갱신 시 `PortalApi.registerFcmToken()`이 호출되고, 등록 성공/실패 로그로 멀티 디바이스 문제를 추적할 수 있는지.
- [ ] `FLAG_SECURE`(스크린샷 차단) 두 액티비티 적용.
- [ ] Android 13+ `POST_NOTIFICATIONS` 런타임 권한 요청.

### F. 네트워크/보안
- [ ] `network_security_config.xml`이 cleartext(HTTP)를 차단하고, 사용자 지정 서버 주소를 위해 시스템 신뢰 HTTPS 호스트를 허용하는지.
- [ ] Argon2/AES/Keystore 연산이 **백그라운드 스레드**에서 실행(UI 멈춤 없음) — 현재 `new Thread{}` 사용.
- [ ] enrollment 실패 시 `LocalCredentialStore.clear()`로 로컬 롤백되는지.
- [ ] `PortalApi`가 런타임 서버 주소 override(`AppPrefs.serverBaseUrl`)를 토큰 등록/enroll/callback에 반영하는지.

## 6. 서버 연동 계약 (검증 기준 엔드포인트)
| 호출 | 메서드/경로 | 바디 | 인증헤더 |
|---|---|---|---|
| 토큰등록 | `POST {PORTAL}/api/admin/portal-device/token` | `{fcm_token}` | `X-Admin-Device-Key` |
| 등록 | `POST {PORTAL}/api/admin/portal-enroll` | `{device_pubkey, verifier}` | `X-Admin-Device-Key` |
| 제출(GW) | `POST {GATEWAY}/api/submit` | `{cid, proof, sig}` | (없음) |
| 제출(직접) | `POST {PORTAL}/api/admin/portal-callback` | `{challenge_id, proof, sig}` | `X-Admin-Device-Key` |

## 7. 수동 테스트 시나리오
1. 설치 후 첫 실행 → 미등록이므로 EnrollmentActivity 표시 → userKey 설정 → 서버 등록 200 확인.
2. 웹에서 admin ID 입력(인증) → 폰에 FCM 알림 → 탭 → 지문/패턴 인증 → 자동 승인 → 웹이 `/admin` 진입.
3. 기기 인증 취소/실패 → 승인 proof가 제출되지 않고 요청 화면 종료.
4. Android 8+ 기기(LG V50 등)에서 알림 채널 `Admin terminal login`이 생성되고 켜져 있는지 확인.
5. FCM 토큰 등록 실패 시 Logcat(`MainActivity`, `PortalApi`, `FCMService`, `FirebaseMessaging`)에 원인이 남는지 확인.
6. challenge 만료(2분) 후 제출 → 서버 404/만료 처리.
7. 같은 proof 재전송(replay) → nonce 1회성으로 거부 확인.
8. 앱 데이터 삭제(키 분실) → 서버 reset 스크립트 후 재enrollment로 복구.

> 검증 결과(특히 §5-D proof/sig 불일치 가능성)와 빌드 오류가 있으면, 해당 파일·라인과 함께 수정 제안 바랍니다.
