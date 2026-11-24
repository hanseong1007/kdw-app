// 경덕농수산 통합포털 — PC 실행파일(.exe) 런처
//
// 더블클릭하면 통합포털(https://hanseong1007.github.io/)을 연다.
//
//  1) Google Chrome 이 설치돼 있으면 크롬의 **일반 탭(새 창)** 으로 연다.
//     — 주소창 없는 --app 창이 아니라 일반 창이라, 포털 안의 쿠팡윙·롯데택배 같은
//     링크를 눌렀을 때 이미 로그인해 둔 크롬 프로필·쿠키가 그대로 살아 있다.
//  2) 크롬이 없으면 Windows 기본 브라우저로 연다(rundll32 url.dll,FileProtocolHandler).
//
// Go 표준 라이브러리만 쓴다(외부 런타임·.NET·WebView2 설치 불필요, 파일 하나로 동작).
// 빌드: GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -H=windowsgui" -o kdw-portal.exe .
package main

import (
	"os"
	"os/exec"
	"strings"
	"syscall"
	"unsafe"
)

const portalURL = "https://hanseong1007.github.io/"

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

	// 1) 크롬이 있으면 크롬 일반 탭으로. (프로필·쿠키·확장프로그램 그대로)
	if exe := findChrome(); exe != "" {
		if startDetached(exe, target) {
			return
		}
	}

	// 2) 크롬이 없을 때만 Windows 기본 브라우저로.
	if openWithDefaultBrowser(target) {
		return
	}
	msgBox("경덕농수산 통합포털",
		"브라우저를 찾지 못했습니다.\n\nGoogle Chrome 을 설치한 뒤 다시 실행해 주세요.\n(설치가 어려우면 Edge 등 기본 브라우저로 여세요.)")
}

// findChrome 은 chrome.exe 를 찾는다. 설치 경로 → PATH 순서.
func findChrome() string {
	for _, p := range chromePaths() {
		if fi, err := os.Stat(p); err == nil && !fi.IsDir() {
			return p
		}
	}
	if p, err := exec.LookPath("chrome.exe"); err == nil {
		return p
	}
	return ""
}

func chromePaths() []string {
	pf := os.Getenv("ProgramFiles")         // 보통 C:\Program Files
	pf86 := os.Getenv("ProgramFiles(x86)")  // 보통 C:\Program Files (x86) — 32비트 크롬
	w6432 := os.Getenv("ProgramW6432")      // 32비트 프로세스에서도 64비트 폴더
	local := os.Getenv("LOCALAPPDATA")      // 사용자 설치(%LocalAppData%)

	join := func(base string, parts ...string) string {
		if base == "" {
			return ""
		}
		return base + `\` + strings.Join(parts, `\`)
	}

	return []string{
		// 정식 크롬 — 64비트 설치 → 32비트 설치 → 사용자 설치
		join(pf, "Google", "Chrome", "Application", "chrome.exe"),
		join(pf86, "Google", "Chrome", "Application", "chrome.exe"),
		join(local, "Google", "Chrome", "Application", "chrome.exe"),
		join(w6432, "Google", "Chrome", "Application", "chrome.exe"),
		// 크롬 베타·카나리(정식이 없을 때만)
		join(pf, "Google", "Chrome Beta", "Application", "chrome.exe"),
		join(pf86, "Google", "Chrome Beta", "Application", "chrome.exe"),
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

// openWithDefaultBrowser 는 크롬을 못 찾았을 때의 마지막 방법.
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
