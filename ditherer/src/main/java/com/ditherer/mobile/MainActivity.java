package com.ditherer.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Host for the original MIT-licensed Ditherer web app. No remote server needed. */
public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int PICK_FILE = 2001;
    private static final int ASK_AUDIO = 2002;
    private WebView browser;
    private ValueCallback<Uri[]> fileResult;
    private PermissionRequest microphoneRequest;
    private final SavingBridge savingBridge = new SavingBridge();

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff222222);
        getWindow().setNavigationBarColor(0xff222222);
        browser = new WebView(this);
        browser.setBackgroundColor(0xff262626);
        setContentView(browser);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDefaultTextEncodingName("UTF-8");
        browser.addJavascriptInterface(savingBridge, "DithererAndroid");
        browser.setWebViewClient(new LocalAssetsClient());
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                                          FileChooserParams params) {
                if (fileResult != null) fileResult.onReceiveValue(null);
                fileResult = callback;
                Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("*/*");
                picker.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"image/*", "video/*", "audio/*", "application/json"});
                picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(picker, PICK_FILE);
                    return true;
                } catch (Exception ex) {
                    fileResult.onReceiveValue(null);
                    fileResult = null;
                    toast("Не удалось открыть выбор файла");
                    return false;
                }
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                Uri origin = request.getOrigin();
                if (!HOST.equals(origin.getHost()) || !"https".equals(origin.getScheme())) {
                    request.deny();
                    return;
                }
                boolean audioOnly = true;
                for (String resource : request.getResources()) {
                    if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) audioOnly = false;
                }
                if (!audioOnly) { request.deny(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    if (microphoneRequest != null) microphoneRequest.deny();
                    microphoneRequest = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_AUDIO);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (request == microphoneRequest) microphoneRequest = null;
            }
        });
        browser.loadUrl(START_URL);
    }

    private final class LocalAssetsClient extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
                return response(403, "Blocked", "text/plain", new byte[0]);
            }
            String path = uri.getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            if (path.contains("..") || path.contains("\\") || !path.startsWith("/")) {
                return response(403, "Bad asset path", "text/plain", new byte[0]);
            }
            String name = path.substring(1);
            try {
                InputStream data = getAssets().open("site/" + name);
                return new WebResourceResponse(mime(name), "UTF-8", 200, "OK",
                    Collections.singletonMap("Access-Control-Allow-Origin", "*"), data);
            } catch (IOException ex) {
                return response(404, "Not found", "text/plain", new byte[0]);
            }
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri uri = req.getUrl();
            if (HOST.equals(uri.getHost()) && "https".equals(uri.getScheme())) return false;
            if (req.isForMainFrame()) {
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ex) { toast("Не удалось открыть ссылку"); }
                }
            }
            return true;
        }
    }

    private static WebResourceResponse response(int status, String reason, String mime, byte[] data) {
        return new WebResourceResponse(mime, "UTF-8", status, reason,
            Collections.emptyMap(), new ByteArrayInputStream(data));
    }
    private static String mime(String file) {
        String f = file.toLowerCase(Locale.ROOT);
        if (f.endsWith(".html")) return "text/html";
        if (f.endsWith(".js") || f.endsWith(".mjs")) return "text/javascript";
        if (f.endsWith(".css")) return "text/css";
        if (f.endsWith(".json") || f.endsWith(".map")) return "application/json";
        if (f.endsWith(".wasm")) return "application/wasm";
        if (f.endsWith(".svg")) return "image/svg+xml";
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".gif")) return "image/gif";
        if (f.endsWith(".webp")) return "image/webp";
        if (f.endsWith(".avif")) return "image/avif";
        if (f.endsWith(".woff2")) return "font/woff2";
        if (f.endsWith(".woff")) return "font/woff";
        if (f.endsWith(".ttf")) return "font/ttf";
        if (f.endsWith(".mp4")) return "video/mp4";
        if (f.endsWith(".webm")) return "video/webm";
        if (f.endsWith(".mp3")) return "audio/mpeg";
        if (f.endsWith(".wav")) return "audio/wav";
        if (f.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILE || fileResult == null) return;
        Uri[] uris = null;
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                uris = new Uri[n];
                for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                uris = new Uri[]{data.getData()};
            }
        }
        fileResult.onReceiveValue(uris);
        fileResult = null;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != ASK_AUDIO || microphoneRequest == null) return;
        if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            microphoneRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else microphoneRequest.deny();
        microphoneRequest = null;
    }

    private void toast(String text) {
        runOnUiThread(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    /** Accepts bounded base64 chunks from generated blob download links. */
    private final class SavingBridge {
        private final Map<String, FileSave> sessions = new HashMap<>();
        private final class FileSave {
            final Uri uri;
            final OutputStream stream;
            FileSave(Uri uri, OutputStream stream) { this.uri = uri; this.stream = stream; }
        }

        @JavascriptInterface public synchronized String begin(String fileName, String mimeType) {
            if (sessions.size() >= 2) return "";
            if (mimeType == null || mimeType.length() > 120) return "";
            boolean allowed = mimeType.startsWith("image/") || mimeType.startsWith("video/") ||
                mimeType.equals("application/json") || mimeType.equals("application/zip") ||
                mimeType.equals("application/octet-stream") || mimeType.equals("text/plain");
            if (!allowed) return "";
            String safe = fileName == null ? "ditherer" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (safe.isEmpty()) safe = "ditherer";
            if (safe.length() > 96) safe = safe.substring(0, 96);
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, safe);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ditherer");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = null;
            try {
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return "";
                OutputStream stream = getContentResolver().openOutputStream(uri, "w");
                if (stream == null) { getContentResolver().delete(uri, null, null); return ""; }
                String id = UUID.randomUUID().toString();
                sessions.put(id, new FileSave(uri, stream));
                return id;
            } catch (Exception ex) {
                if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
                toast("Ошибка создания файла");
                return "";
            }
        }

        @JavascriptInterface public synchronized boolean chunk(String id, String encoded) {
            FileSave file = sessions.get(id);
            if (file == null || encoded == null || encoded.length() > 256 * 1024) return false;
            try {
                file.stream.write(Base64.decode(encoded, Base64.NO_WRAP));
                return true;
            } catch (Exception ex) {
                abort(id);
                toast("Ошибка при сохранении");
                return false;
            }
        }

        @JavascriptInterface public synchronized boolean finish(String id) {
            FileSave file = sessions.remove(id);
            if (file == null) return false;
            try {
                file.stream.close();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(file.uri, v, null, null);
                toast("Сохранено: Загрузки/Ditherer");
                return true;
            } catch (Exception ex) {
                try { getContentResolver().delete(file.uri, null, null); } catch (Exception ignored) { }
                toast("Не удалось завершить файл");
                return false;
            }
        }

        @JavascriptInterface public synchronized void abort(String id) {
            FileSave file = sessions.remove(id);
            if (file != null) {
                try { file.stream.close(); } catch (IOException ignored) { }
                try { getContentResolver().delete(file.uri, null, null); } catch (Exception ignored) { }
            }
        }
        synchronized void closeAll() {
            for (String key : sessions.keySet().toArray(new String[0])) abort(key);
        }
    }

    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileResult != null) { fileResult.onReceiveValue(null); fileResult = null; }
        if (microphoneRequest != null) { microphoneRequest.deny(); microphoneRequest = null; }
        savingBridge.closeAll();
        if (browser != null) {
            browser.removeJavascriptInterface("DithererAndroid");
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
