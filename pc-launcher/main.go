// 경덕농수산 통합포털 — PC 실행파일(.exe) 런처
//
// 더블클릭하면 주소창·탭 없는 전용 앱 창으로 포털(https://hanseong1007.github.io/)을 연다.
//
//  1) 설치된 Microsoft Edge 를 `--app=` 모드로 실행 (가장 흔함 · Windows 기본 브라우저)
//  2) 없으면 Google Chrome 을 `--app=` 모드로 실행
//  3) 둘 다 없으면 기본 브라우저로 연다
//
// Go 표준 라이브러리만 쓴다(외부 런타임·.NET·WebView2 설치 불필요, 파일 하나로 동작).
// 빌드: GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w -H=windowsgui" -o kdw-portal.exe .
package main

import (
	"os"
	"os/exec"
	"strings"
	"syscall"
	"unsafe"
)

const portalURL = "https://hanseong1007.github.io/"

// 앱 창 크기(픽셀). 화면이 작으면 Windows 가 알아서 줄인다.
const appWindow = "--window-size=1240,880"

const (
	createNewProcessGroup = 0x00000200 // CREATE_NEW_PROCESS_GROUP
	detachedProcess       = 0x00000008 // DETACHED_PROCESS
)

func main() {
	target := portalURL
	if len(os.Args) > 1 {
		a := strings.TrimSpace(os.Args[1])
		if strings.HasPrefix(a, "https://") || strings.HasPrefix(a, "http://") {
			target = a // 주소를 직접 넘기면 그 주소를 연다(시험용)
		}
	}

	appArg := "--app=" + target
	if exe := findChromium(); exe != "" {
		if startDetached(exe, appArg, appWindow, "--no-first-run", "--no-default-browser-check") {
			return
		}
	}
	if openWithDefaultBrowser(target) {
		return
	}
	msgBox("경덕농수산 통합포털",
		"브라우저를 찾지 못했습니다.\n\nMicrosoft Edge 또는 Google Chrome 을 설치한 뒤 다시 실행해 주세요.")
}

// findChromium 은 Edge → Chrome 순서로 실행 파일을 찾는다.
func findChromium() string {
	for _, p := range browserPaths() {
		if fi, err := os.Stat(p); err == nil && !fi.IsDir() {
			return p
		}
	}
	for _, name := range []string{"msedge.exe", "chrome.exe"} {
		if p, err := exec.LookPath(name); err == nil {
			return p
		}
	}
	return ""
}

func browserPaths() []string {
	pf := os.Getenv("ProgramFiles")
	pf86 := os.Getenv("ProgramFiles(x86)")
	local := os.Getenv("LOCALAPPDATA")

	join := func(base string, parts ...string) string {
		if base == "" {
			return ""
		}
		return base + `\` + strings.Join(parts, `\`)
	}

	return []string{
		// Microsoft Edge (Windows 10/11 기본 설치)
		join(pf86, "Microsoft", "Edge", "Application", "msedge.exe"),
		join(pf, "Microsoft", "Edge", "Application", "msedge.exe"),
		join(local, "Microsoft", "Edge", "Application", "msedge.exe"),
		// Google Chrome
		join(pf, "Google", "Chrome", "Application", "chrome.exe"),
		join(pf86, "Google", "Chrome", "Application", "chrome.exe"),
		join(local, "Google", "Chrome", "Application", "chrome.exe"),
		// Chrome 변형(설치돼 있으면 같이)
		join(pf, "Google", "Chrome Beta", "Application", "chrome.exe"),
		join(local, "Google", "Chrome SxS", "Application", "chrome.exe"),
	}
}

// startDetached 는 창을 띄우고 바로 빠진다(런처가 남지 않는다).
func startDetached(exe string, args ...string) bool {
	cmd := exec.Command(exe, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: createNewProcessGroup | detachedProcess,
	}
	if cmd.Start() != nil {
		return false
	}
	go func() { _ = cmd.Wait() }()
	return true
}

// openWithDefaultBrowser 는 Edge·Chrome 을 못 찾았을 때의 마지막 방법.
func openWithDefaultBrowser(url string) bool {
	cmd := exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	if cmd.Start() != nil {
		return false
	}
	go func() { _ = cmd.Wait() }()
	return true
}

// msgBox 는 브라우저가 하나도 없을 때 안내 창을 띄운다(user32 · 0x10 = MB_ICONERROR).
func msgBox(title, text string) {
	const mbIconError = 0x00000010
	t, err1 := syscall.UTF16PtrFromString(text)
	c, err2 := syscall.UTF16PtrFromString(title)
	if err1 != nil || err2 != nil {
		return
	}
	user32 := syscall.NewLazyDLL("user32.dll")
	proc := user32.NewProc("MessageBoxW")
	_, _, _ = proc.Call(0, uintptr(unsafe.Pointer(t)), uintptr(unsafe.Pointer(c)), mbIconError)
}
