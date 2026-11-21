package com.kdw.portal;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 경덕농수산 안드로이드 앱의 공통 WebView 화면.
 *
 * flavor(portal / worklog)마다 resValue 로 들어오는
 * R.string.app_name, R.string.start_url 만 다르고 동작은 같습니다.
 *
 * 발주화면(바코드 카메라), 업무일지(A4 인쇄·백업·CSV), 근태(출퇴근 기록) 세 웹앱이
 * 앱 안에서 그대로 동작하도록 다음을 처리합니다.
 *  - 카메라 권한 + getUserMedia(onPermissionRequest)
 *  - 파일 선택(<input type="file">) 과 CSV/JSON 다운로드(blob 포함)
 *  - window.print() → 안드로이드 인쇄/PDF 저장
 *  - navigator.share() → 안드로이드 공유 시트
 *  - 뒤로가기, 외부 링크(전화·메일·다른 사이트)는 외부 앱으로
 */
public class MainActivity extends Activity {

    private static final String ALLOWED_HOST = "hanseong1007.github.io";
    private static final String USER_AGENT_SUFFIX = " KdwPortal/1.0.0";
    private static final String SAVE_FOLDER = "경덕농수산";

    private static final int REQ_FILE = 1001;
    private static final int REQ_CAMERA_PERMISSION = 1002;
    private static final int REQ_STORAGE_PERMISSION = 1003;

    private WebView webView;
    private View loadingView;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;
    private long lastBackPressedAt = 0L;

    /** 안드로이드 9 이하에서 저장소 권한을 받은 뒤 이어서 저장할 파일 */
    private String pendingSaveName;
    private String pendingSaveMime;
    private byte[] pendingSaveData;

    // ------------------------------------------------------------------
    // 생명주기
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#1B5E20"));
        getWindow().setNavigationBarColor(Color.parseColor("#0F3D14"));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // 바코드를 오래 비추는 동안 화면이 꺼지지 않도록
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F1F4F2"));

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webView);

        FrameLayout loading = new FrameLayout(this);
        loading.setBackgroundColor(Color.parseColor("#1B5E20"));
        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name));
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        loading.addView(title, titleParams);
        root.addView(loading, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadingView = loading;

        setContentView(root);

        setupWebView();
        webView.loadUrl(getString(R.string.start_url));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.removeJavascriptInterface("KdwApp");
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt < 2000L) {
            super.onBackPressed();
            return;
        }
        lastBackPressedAt = now;
        toast("한 번 더 누르면 앱이 종료됩니다");
    }

    // ------------------------------------------------------------------
    // WebView 설정
    // ------------------------------------------------------------------

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);
        // 페이지가 앱에서 열렸는지 알 수 있게 표시만 덧붙입니다(동작에는 영향 없음).
        settings.setUserAgentString(settings.getUserAgentString() + USER_AGENT_SUFFIX);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.parseColor("#F1F4F2"));
        webView.addJavascriptInterface(new Bridge(this), "KdwApp");
        webView.setWebViewClient(new Client());
        webView.setWebChromeClient(new ChromeClient());
        webView.setDownloadListener(new Downloader());
    }

    private void hideLoading() {
        if (loadingView != null && loadingView.getVisibility() == View.VISIBLE) {
            loadingView.setVisibility(View.GONE);
        }
    }

    /** 페이지마다 blob 다운로드 · 공유 · 인쇄를 안드로이드 기능에 연결합니다. */
    private void injectHooks(WebView view) {
        view.evaluateJavascript(JS_HOOKS, null);
    }

    private static final String JS_HOOKS =
            "(function(){try{"
            + "if(window.__kdwHooks) return; window.__kdwHooks = 1;"
            + "var app = window.KdwApp; if(!app) return;"
            + "function nameFromHref(h){var s=String(h||'');var i=s.lastIndexOf('/');var n=i>=0?s.slice(i+1):s;try{n=decodeURIComponent(n);}catch(e){}return n||'download';}"
            + "var proto = HTMLAnchorElement.prototype; var origClick = proto.click;"
            + "proto.click = function(){"
            + "  try{ var href = this.href || '';"
            + "    if(href.indexOf('blob:') === 0){"
            + "      var nm = this.download || nameFromHref(href);"
            + "      fetch(href).then(function(r){ return r.blob(); }).then(function(b){"
            + "        var fr = new FileReader();"
            + "        fr.onload = function(){ var d = String(fr.result||''); var k = d.indexOf(',');"
            + "          if(k > 0){ app.saveBase64(nm, b.type || '', d.slice(k+1)); } };"
            + "        fr.readAsDataURL(b);"
            + "      }).catch(function(){});"
            + "      return;"
            + "    }"
            + "  }catch(e){}"
            + "  return origClick.apply(this, arguments);"
            + "};"
            + "try{ if(!navigator.share){ navigator.share = function(d){"
            + "  var t=''; try{ t = (d && (d.text || d.title || d.url)) || ''; }catch(e){}"
            + "  if(t){ app.share(String(t)); } return Promise.resolve(); }; } }catch(e){}"
            + "try{ window.print = function(){ app.printPage(); }; }catch(e){}"
            + "}catch(e){}})();";

    // ------------------------------------------------------------------
    // 페이지 이동 · 외부 링크
    // ------------------------------------------------------------------

    private boolean isInternalHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals(ALLOWED_HOST) || h.equals("www." + ALLOWED_HOST);
    }

    /** true를 돌려주면 WebView가 그 URL을 열지 않습니다. */
    private boolean handleUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

        if (scheme.equals("http") || scheme.equals("https")) {
            if (isInternalHost(uri.getHost())) {
                return false; // 사이트 안쪽 이동은 앱 안에서
            }
            openExternally(uri, "이 링크를 열 브라우저가 없습니다");
            return true;
        }

        if (scheme.equals("tel") || scheme.equals("mailto") || scheme.equals("sms")
                || scheme.equals("smsto") || scheme.equals("geo")) {
            openExternally(uri, "이 링크를 열 앱이 없습니다");
            return true;
        }

        if (scheme.equals("intent")) {
            try {
                startActivity(Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME));
            } catch (Exception e) {
                toast("이 링크를 열 앱이 없습니다");
            }
            return true;
        }

        // blob:, data:, about: 등은 WebView가 처리합니다.
        return false;
    }

    private void openExternally(Uri uri, String failMessage) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast(failMessage);
        } catch (Exception e) {
            toast(failMessage);
        }
    }

    private class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request == null) return false;
            String method = request.getMethod();
            if (method != null && method.equalsIgnoreCase("POST")) {
                return false; // 폼 전송은 WebView가 그대로
            }
            return handleUri(request.getUrl());
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            hideLoading();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            hideLoading();
            injectHooks(view);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request == null || !request.isForMainFrame()) return;
            hideLoading();
            if (request.getUrl() != null && isInternalHost(request.getUrl().getHost())) {
                webView.loadUrl("file:///android_asset/offline.html");
            }
        }
    }

    // ------------------------------------------------------------------
    // 카메라 권한 · 파일 선택
    // ------------------------------------------------------------------

    private class ChromeClient extends WebChromeClient {

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    grantCameraRequest(request);
                }
            });
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            pendingPermissionRequest = null;
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                        FileChooserParams params) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;

            Intent intent = buildFilePickerIntent(params);
            try {
                startActivityForResult(intent, REQ_FILE);
            } catch (ActivityNotFoundException e) {
                filePathCallback = null;
                callback.onReceiveValue(null);
                toast("파일을 고를 수 있는 앱이 없습니다");
                return false;
            }
            return true;
        }
    }

    /** 카메라(getUserMedia) 요청만 허용하고, 마이크 등은 쓰지 않습니다. */
    private void grantCameraRequest(PermissionRequest request) {
        if (request == null) return;
        boolean wantsCamera = false;
        List<String> grant = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                wantsCamera = true;
                grant.add(resource);
            }
        }
        if (!wantsCamera) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            request.grant(grant.toArray(new String[0]));
            return;
        }
        pendingPermissionRequest = request;
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
    }

    private Intent buildFilePickerIntent(WebChromeClient.FileChooserParams params) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        List<String> mimeTypes = new ArrayList<>();
        String[] acceptTypes = params != null ? params.getAcceptTypes() : null;
        if (acceptTypes != null) {
            for (String accept : acceptTypes) {
                if (accept == null) continue;
                for (String part : accept.split(",")) {
                    String type = part.trim();
                    if (type.isEmpty() || type.startsWith(".") || type.equals("*/*")) continue;
                    if (type.endsWith("/*") || type.contains("/")) {
                        mimeTypes.add(type);
                    }
                }
            }
        }

        if (mimeTypes.isEmpty()) {
            intent.setType("*/*");
        } else if (mimeTypes.size() == 1) {
            intent.setType(mimeTypes.get(0));
        } else {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toArray(new String[0]));
        }

        boolean multiple = params != null
                && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        if (multiple) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    List<Uri> uris = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        if (uri != null) uris.add(uri);
                    }
                    if (!uris.isEmpty()) results = uris.toArray(new Uri[0]);
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
                if (results != null) {
                    for (Uri uri : results) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) {
                            // 권한 유지가 안 되는 문서 제공자도 있습니다. 읽기 자체는 됩니다.
                        }
                    }
                }
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CAMERA_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            PermissionRequest request = pendingPermissionRequest;
            pendingPermissionRequest = null;
            if (request == null) return;
            if (granted) {
                grantCameraRequest(request);
            } else {
                request.deny();
                toast("카메라 권한이 필요합니다. 설정 > 앱 > " + getString(R.string.app_name)
                        + " > 권한에서 허용해 주세요");
            }
            return;
        }

        if (requestCode == REQ_STORAGE_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingSaveData != null) {
                writeToDownloadsLegacy(pendingSaveName, pendingSaveMime, pendingSaveData);
            } else if (!granted) {
                toast("파일을 저장하려면 저장소 권한이 필요합니다");
            }
            pendingSaveName = null;
            pendingSaveMime = null;
            pendingSaveData = null;
        }
    }

    // ------------------------------------------------------------------
    // 다운로드 (CSV · JSON 백업 · data:/blob:)
    // ------------------------------------------------------------------

    private class Downloader implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                    String mimetype, long contentLength) {
            if (url == null) return;

            if (url.startsWith("data:")) {
                saveDataUrl(url, contentDisposition, mimetype);
                return;
            }
            if (url.startsWith("blob:")) {
                // 페이지의 a.click() 후크가 처리하지만, 못 잡은 경우를 대비한 안내
                toast("파일을 준비하는 중입니다. 잠시 후 다시 눌러 주세요");
                return;
            }
            if (!url.startsWith("http")) {
                return;
            }

            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager == null) {
                    openExternally(Uri.parse(url), "다운로드를 시작할 수 없습니다");
                    return;
                }
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                if (mimetype != null && !mimetype.isEmpty()) {
                    request.setMimeType(mimetype);
                }
                if (userAgent != null) {
                    request.addRequestHeader("User-Agent", userAgent);
                }
                request.setTitle(fileName);
                request.setDescription(getString(R.string.app_name));
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, SAVE_FOLDER + "/" + safeName(fileName));
                manager.enqueue(request);
                toast("다운로드를 시작했습니다\n" + fileName);
            } catch (Exception e) {
                toast("다운로드를 시작할 수 없습니다: " + e.getMessage());
            }
        }
    }

    private void saveDataUrl(String url, String contentDisposition, String mimetype) {
        try {
            int comma = url.indexOf(',');
            if (comma < 0) return;
            String meta = url.substring(5, comma);
            String payload = url.substring(comma + 1);
            String lower = meta.toLowerCase(Locale.ROOT);
            boolean base64 = lower.contains(";base64");

            String mime = mimetype;
            int semicolon = meta.indexOf(';');
            if (semicolon > 0) {
                mime = meta.substring(0, semicolon);
            }
            if (mime == null || mime.isEmpty()) {
                mime = "application/octet-stream";
            }

            byte[] data = base64
                    ? Base64.decode(payload, Base64.DEFAULT)
                    : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
            String name = URLUtil.guessFileName("file", contentDisposition, mime);
            saveBytes(name, mime, data);
        } catch (Exception e) {
            toast("파일을 저장하지 못했습니다");
        }
    }

    // ------------------------------------------------------------------
    // 파일 저장 (다운로드/경덕농수산 폴더)
    // ------------------------------------------------------------------

    private void saveBytes(String name, String mime, byte[] data) {
        if (data == null) return;
        String fileName = safeName(name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                writeToDownloadsMediaStore(fileName, mime, data);
            } catch (Exception e) {
                toast("파일을 저장하지 못했습니다: " + e.getMessage());
            }
            return;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSaveName = fileName;
            pendingSaveMime = mime;
            pendingSaveData = data;
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_STORAGE_PERMISSION);
            return;
        }
        writeToDownloadsLegacy(fileName, mime, data);
    }

    private void writeToDownloadsMediaStore(String name, String mime, byte[] data) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,
                (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SAVE_FOLDER);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("저장 위치를 만들 수 없습니다");
        }

        OutputStream stream = null;
        try {
            stream = getContentResolver().openOutputStream(uri);
            if (stream == null) throw new IOException("파일을 열 수 없습니다");
            stream.write(data);
            stream.flush();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getContentResolver().update(uri, done, null, null);

        toast("다운로드 폴더(" + SAVE_FOLDER + ")에 저장했습니다\n" + name);
    }

    @SuppressWarnings("deprecation")
    private void writeToDownloadsLegacy(String name, String mime, byte[] data) {
        File folder = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                SAVE_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            toast("저장 폴더를 만들 수 없습니다");
            return;
        }
        File file = new File(folder, name);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(data);
            stream.flush();
        } catch (IOException e) {
            toast("파일을 저장하지 못했습니다: " + e.getMessage());
            return;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
        try {
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
        } catch (Exception ignored) {
        }
        toast("다운로드 폴더(" + SAVE_FOLDER + ")에 저장했습니다\n" + name);
    }

    private String safeName(String name) {
        String value = (name == null || name.trim().isEmpty())
                ? ("download-" + System.currentTimeMillis())
                : name.trim();
        value = value.replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_')
                .replace('\n', '_')
                .replace('\r', '_')
                .replace('\t', '_');
        if (value.length() > 120) {
            String extension = "";
            int dot = value.lastIndexOf('.');
            if (dot > 0 && value.length() - dot <= 10) {
                extension = value.substring(dot);
            }
            value = value.substring(0, 120 - extension.length()) + extension;
        }
        return value;
    }

    // ------------------------------------------------------------------
    // 인쇄 (업무일지 A4) · 공유
    // ------------------------------------------------------------------

    private void printPage() {
        if (webView == null) return;
        try {
            PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
            if (manager == null) {
                toast("인쇄 기능을 쓸 수 없습니다");
                return;
            }
            String jobName = getString(R.string.app_name) + " 문서";
            PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);
            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();
            manager.print(jobName, adapter, attributes);
        } catch (Exception e) {
            toast("인쇄 화면을 열 수 없습니다: " + e.getMessage());
        }
    }

    private void shareText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(intent, "공유"));
        } catch (Exception e) {
            toast("공유할 앱이 없습니다");
        }
    }

    // ------------------------------------------------------------------
    // 웹페이지에서 부르는 다리(JavaScript interface)
    // ------------------------------------------------------------------

    /** public 이어야 웹페이지의 자바스크립트에서 호출할 수 있습니다. */
    public static class Bridge {
        private final MainActivity activity;

        Bridge(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public void saveBase64(final String name, final String mime, final String base64) {
            if (base64 == null) return;
            final byte[] data;
            try {
                data = Base64.decode(base64, Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                return;
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.saveBytes(name, mime, data);
                }
            });
        }

        @JavascriptInterface
        public void saveText(final String name, final String text, final String mime) {
            final byte[] data = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.saveBytes(name, mime, data);
                }
            });
        }

        @JavascriptInterface
        public void share(final String text) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.shareText(text);
                }
            });
        }

        @JavascriptInterface
        public void printPage() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.printPage();
                }
            });
        }

        @JavascriptInterface
        public void reload() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (activity.webView != null) {
                        activity.webView.loadUrl(activity.getString(R.string.start_url));
                    }
                }
            });
        }

        @JavascriptInterface
        public String appName() {
            return activity.getString(R.string.app_name);
        }

        @JavascriptInterface
        public String startUrl() {
            return activity.getString(R.string.start_url);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
