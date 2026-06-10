# Hermes Agent gateway 이용한 인증용 Android 앱

대상 기기: Samsung Galaxy S26, Samsung Galaxy S22 Ultra, LG V50 기준으로 구성한 Hermes/K-skill 인증 입력 앱입니다.

## 구현 방식

이 앱은 별도의 Android 네이티브 UI를 만들지 않고, 기존 Mobile Web을 안전하게 WebView로 감싸서 재사용합니다.

- 기본 인증서비스 주소: `https://otp.pm-oci.duckdns.org`
- Mobile Web의 `/api/status` 1초 polling, enabled/disabled 입력 전환, `/api/submit` 제출 로직을 그대로 사용합니다.
- 앱 이름: `pm-oci 인증용`
- 패키지/applicationId: `org.pmoci.kskillauth`
- `minSdk 23`, `targetSdk 35`, `compileSdk 35`

## 보안 설정

- `android:usesCleartextTraffic="false"`
- `network_security_config.xml`에서 `otp.pm-oci.duckdns.org` HTTPS만 허용
- WebView SSL 오류는 우회하지 않고 차단
- mixed content 차단
- `file://`/content 접근 차단
- `addJavascriptInterface` 미사용
- `FLAG_SECURE`로 스크린샷/최근 앱 화면 캡처 방지
- 앱 종료 시 WebView cache/history 정리

## Android Studio에서 빌드

1. Android Studio에서 이 폴더를 엽니다.

   ```text
   /home/ubuntu/agent-work/hermes/kskill-auth/android-auth-app
   ```

2. Android Studio가 Gradle sync를 수행하게 둡니다.
3. Debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

4. 산출물:

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. Galaxy S22 Ultra에 설치:

   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## VSCode/CLI에서 빌드

Android SDK와 JDK 17 이상이 설치되어 있고 `ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`가 잡혀 있어야 합니다.

```bash
cd /path/to/android-auth-app
# gradle wrapper가 없다면 Android Studio로 먼저 열거나, Gradle 설치 후 wrapper 생성
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

릴리즈 서명 APK가 필요하면 Android Studio의 **Build > Generate Signed Bundle / APK** 메뉴를 사용하거나, `signingConfigs`를 `app/build.gradle`에 추가하세요.
