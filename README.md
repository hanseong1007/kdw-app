---
title: "경덕농수산 안드로이드 앱 (통합 포털 · 업무일지)"
---

# 경덕농수산 안드로이드 앱

경덕농수산 웹앱을 휴대폰에서 앱처럼 쓰기 위한 안드로이드 WebView 앱입니다.
한 저장소에서 **두 가지 앱**을 함께 만듭니다.

| flavor | 앱 이름 | 패키지 | 시작 화면 | APK |
|---|---|---|---|---|
| `portal` | 경덕농수산 | `com.kdw.portal` | https://hanseong1007.github.io/ | `경덕농수산-포털.apk` |
| `worklog` | 경덕 업무일지 | `com.kdw.worklog` | https://hanseong1007.github.io/worklog-app/ | `경덕농수산-업무일지.apk` |

- 포털 앱은 통합 로그인 화면에서 발주화면 · 업무일지 · 근태 세 앱으로 들어갑니다.
- 업무일지 앱은 업무일지 화면(`/worklog-app/`)을 바로 엽니다.
- 패키지가 서로 달라 **한 휴대폰에 두 앱을 같이 설치**할 수 있습니다.

## APK 받기

빌드는 GitHub Actions 가 자동으로 합니다(`main` 에 올리면 실행, Actions 화면에서 수동 실행도 가능).

- 최신 APK (눌러서 바로 다운로드)
  - 포털: <https://github.com/hanseong1007/kdw-app/releases/latest/download/경덕농수산-포털.apk>
  - 업무일지: <https://github.com/hanseong1007/kdw-app/releases/latest/download/경덕농수산-업무일지.apk>
- 영문 이름 사본: `kdw-portal.apk`, `kdw-worklog.apk` (같은 파일)
- Actions 실행 기록의 Artifact `kdw-apk` 에서도 받을 수 있습니다.
- 설치할 때 "출처를 알 수 없는 앱 허용"(또는 "이 출처 허용")을 한 번 켜 주세요.

## 앱이 해 주는 일

웹에서 되던 기능이 앱에서도 그대로 되도록 WebView 에 다음을 붙였습니다.

- **카메라 (발주화면 바코드 스캔)** — `CAMERA` 권한, 첫 사용 때 런타임 권한 요청,
  `WebChromeClient.onPermissionRequest` 에서 `RESOURCE_VIDEO_CAPTURE` 자동 허용
- **파일 선택** — `<input type="file">` (CSV·엑셀 품목 업로드, JSON 백업 복원)
- **파일 저장** — `blob:`·`data:`·일반 다운로드를 모두 받아 `다운로드/경덕농수산/` 폴더에 저장
  (CSV 내보내기, 업무일지 JSON 백업)
- **인쇄 / PDF 저장** — 업무일지의 `window.print()` 를 안드로이드 `PrintManager` 로 연결,
  A4 용지 기준으로 인쇄하거나 "PDF로 저장" 선택
- **공유** — 발주서의 `navigator.share()` 를 안드로이드 공유 시트로 연결(카카오톡·문자 등)
- **뒤로가기** — 사이트 안에서는 이전 화면으로, 첫 화면에서는 "한 번 더 누르면 종료"
- **외부 링크** — `hanseong1007.github.io` 안쪽은 앱에서, 전화(`tel:`)·메일(`mailto:`)·다른 사이트는
  휴대폰의 해당 앱/브라우저로
- **로그인 유지** — `localStorage`·`sessionStorage`·쿠키를 켜 두어 다시 열어도 로그인이 유지됩니다
- **오프라인 안내** — 인터넷이 끊기면 "다시 시도" 버튼이 있는 안내 화면
- 화면이 바코드 스캔 중 꺼지지 않게 유지, 다크 테마에서도 흰 배경 유지, 앱 아이콘(초록 테마 `#1b5e20`)

## 구조

```
kdw-app/
├── settings.gradle · build.gradle · gradle.properties
├── app/
│   ├── build.gradle                     # flavor(portal / worklog) 정의
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # INTERNET · CAMERA · VIBRATE 등
│       │   ├── java/com/kdw/portal/MainActivity.java
│       │   ├── assets/offline.html      # 연결 실패 안내 화면
│       │   └── res/                     # 색·테마·문자열·아이콘(포털)
│       └── worklog/res/                 # 업무일지 전용 아이콘
├── tools/make_icons.py                  # 아이콘 다시 만들기
└── .github/workflows/build-apk.yml      # APK 자동 빌드 · Release 업로드
```

## 빌드 방법

### GitHub Actions (기본)

`main` 브랜치에 올리면 자동으로 두 APK 를 빌드해 Artifact 와 Release(`latest`)에 올립니다.
로컬에 Java·Gradle 이 없어도 되고, `gradlew`(wrapper)가 없어도
`gradle/actions/setup-gradle@v4` 가 Gradle 8.7 을 준비해 빌드합니다.

### 내 컴퓨터에서

Android Studio 로 이 폴더를 열고 `Run` 하거나,

```bash
gradle assembleDebug        # 또는 ./gradlew assembleDebug
# 결과
# app/build/outputs/apk/portal/debug/app-portal-debug.apk
# app/build/outputs/apk/worklog/debug/app-worklog-debug.apk
```

필요한 것: JDK 17, Android SDK(compileSdk 34). AGP 8.5.2 / Gradle 8.7.

### 아이콘 다시 만들기

```bash
python3 tools/make_icons.py   # Pillow 필요
```

## 자주 고치는 곳

- **시작 주소 / 앱 이름** — `app/build.gradle` 의 `productFlavors` 안 `resValue`
- **사이트 주소를 다른 곳으로** — 같은 곳 `start_url` + `MainActivity.ALLOWED_HOST`
  (앱 안에서 열 도메인)
- **버전** — `defaultConfig` 의 `versionCode` / `versionName`
- **권한 문구·색** — `app/src/main/res/values/`
- **정식 서명** — 지금은 설치만 되면 되도록 debug 키로 서명합니다. 배포용 키를 만들면
  `signingConfigs` 를 추가하고 `buildTypes.release.signingConfig` 를 바꿔 주세요.
  (키를 바꾸면 기존 앱을 지우고 다시 설치해야 합니다.)
