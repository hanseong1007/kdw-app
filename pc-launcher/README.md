# 경덕농수산 통합포털 — PC 실행파일(.exe)

Windows 10/11(64비트)에서 **더블클릭 한 번**으로 통합 포털을 주소창 없는 앱 창으로 엽니다.
설치 과정이 없고, 지우려면 파일만 삭제하면 됩니다.

## 받기

- 최신 실행파일: <https://github.com/hanseong1007/kdw-app/releases/latest/download/kdw-portal.exe>
  (한글 이름 사본: `경덕농수산-통합포털.exe` — 같은 파일)

처음 실행할 때 **"Windows의 PC 보호"** 창이 뜰 수 있습니다(서명이 없는 파일이라 그렇습니다).
`추가 정보` → `실행` 을 한 번 누르면 다음부터는 바로 열립니다.

## 어떻게 열리나

1. 설치된 **Microsoft Edge** 를 `--app=https://hanseong1007.github.io/` 로 실행 → 탭·주소창·즐겨찾기 줄이 없는 앱 창
2. Edge 가 없으면 **Google Chrome** 을 같은 방식으로 실행
3. 둘 다 없으면 **기본 브라우저**로 연다
4. 브라우저가 하나도 없으면 안내 창을 띄운다

- 브라우저의 기존 프로필을 그대로 쓰므로 **브라우저에서 로그인해 둔 상태와 같게** 열립니다(별도 로그인 불필요).
- 그래서 포털에서 "PC 앱 설치(PWA)"로 설치한 것과 사실상 같은 화면이 뜹니다. 둘 중 편한 것을 쓰시면 됩니다.
- 실행 후 런처는 바로 종료됩니다(작업 관리자에 남지 않음).

## 빌드

### 자동 (GitHub Actions)

`pc-launcher/` 를 고쳐 `main` 에 올리면 `.github/workflows/build-pc-exe.yml` 이
`windows-latest` 에서 빌드해 Release **`pc-latest`** 에 올립니다. Actions 화면에서 수동 실행도 됩니다.

### 내 컴퓨터에서 (크로스 컴파일도 가능)

```bash
go install github.com/josephspurrier/goversioninfo/cmd/goversioninfo@latest
cd pc-launcher
goversioninfo -icon=assets/kdw-portal.ico -o=resource_windows_amd64.syso versioninfo.json
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 \
  go build -trimpath -buildvcs=false -ldflags "-s -w -H=windowsgui" -o kdw-portal.exe .
```

- `-H=windowsgui` — 검은 콘솔 창을 띄우지 않는다.
- `resource_windows_amd64.syso` — 아이콘과 속성(회사·제품 이름·버전)을 exe 안에 넣는다(없으면 기본 아이콘).
- 아이콘 원본: `assets/kdw-portal.ico` (포털 `documents/portal/icons/kdw-portal.ico` 와 같은 그림,
  `python3 scripts/make_portal_icons.py` 로 다시 만든다).

## 구조

```
pc-launcher/
├── main.go                     # 런처 본체 (Go 표준 라이브러리만 사용)
├── go.mod
├── versioninfo.json            # exe 속성: (주)경덕농수산 · 경덕농수산 통합포털 · 1.0.0.0
└── assets/kdw-portal.ico       # exe 아이콘 (16~256px)
```

## 참고

- 외부 런타임이 필요 없습니다(.NET·WebView2·Python 설치 불필요). 파일 하나(약 1.9MB)로 동작합니다.
- 포털 주소를 바꾸려면 `main.go` 의 `portalURL` 상수를 고쳐 다시 빌드합니다.
- 레지스트리·시작 프로그램·설치 폴더를 건드리지 않습니다.
