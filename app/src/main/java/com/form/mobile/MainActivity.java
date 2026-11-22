package com.form.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/index.html";
    private static final int IMAGE_PICK_REQUEST = 1401;
    private ValueCallback<Uri[]> pendingImageResult;
    private WebView webView;
    private final DownloadBridge downloadBridge = new DownloadBridge();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF141414);
        getWindow().setNavigationBarColor(0xFF141414);
        getWindow().getDecorView().setSystemUiVisibility(0);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF141414);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDefaultTextEncodingName("UTF-8");

        webView.addJavascriptInterface(downloadBridge, "FormAndroid");
        webView.setWebViewClient(new OfflineWebClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams parameters) {
                if (pendingImageResult != null) pendingImageResult.onReceiveValue(null);
                pendingImageResult = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "image/png", "image/svg+xml", "image/webp", "image/jpeg"
                });
                try {
                    startActivityForResult(intent, IMAGE_PICK_REQUEST);
                    return true;
                } catch (Exception exception) {
                    pendingImageResult.onReceiveValue(null);
                    pendingImageResult = null;
                    showToast("No file picker available");
                    return false;
                }
            }
        });
        webView.loadUrl(START_URL);
    }

    private class OfflineWebClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !ASSET_HOST.equalsIgnoreCase(uri.getHost())) {
                // Form has no network dependencies at runtime. Do not load third-party resources.
                return emptyResponse();
            }
            String path = uri.getPath();
            if (path == null || "/".equals(path)) path = "/index.html";
            if (path.contains("..") || path.indexOf('\\') >= 0) return emptyResponse();
            String name = path.substring(1);
            try {
                InputStream stream = getAssets().open("site/" + name);
                return new WebResourceResponse(mimeType(name), "UTF-8", stream);
            } catch (IOException ex) {
                return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found",
                    new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if ("https".equalsIgnoreCase(uri.getScheme()) && ASSET_HOST.equalsIgnoreCase(uri.getHost())) {
                return false;
            }
            if (request.isForMainFrame() && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) { showToast("Cannot open link"); }
            }
            return true;
        }
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private static String mimeType(String name) {
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMAGE_PICK_REQUEST || pendingImageResult == null) return;
        Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;
        pendingImageResult.onReceiveValue(uri != null ? new Uri[]{uri} : null);
        pendingImageResult = null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private static class ActiveDownload {
        final Uri uri;
        final OutputStream stream;
        ActiveDownload(Uri uri, OutputStream stream) { this.uri = uri; this.stream = stream; }
    }

    private class DownloadBridge {
        private final Map<String, ActiveDownload> active = new HashMap<>();

        @JavascriptInterface
        public synchronized String begin(String inputName, String mime) {
            if (active.size() >= 2) return "";
            if (!"image/png".equals(mime) && !"video/webm".equals(mime)) return "";
            String extension = "image/png".equals(mime) ? ".png" : ".webm";
            String name = inputName == null ? "form" : inputName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (name.length() > 80) name = name.substring(0, 80);
            if (!name.toLowerCase().endsWith(extension)) name += extension;
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Form");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = null;
            try {
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return "";
                OutputStream stream = getContentResolver().openOutputStream(uri, "w");
                if (stream == null) {
                    getContentResolver().delete(uri, null, null);
                    return "";
                }
                String id = UUID.randomUUID().toString();
                active.put(id, new ActiveDownload(uri, stream));
                return id;
            } catch (Exception exception) {
                if (uri != null) getContentResolver().delete(uri, null, null);
                showToast("Could not create download");
                return "";
            }
        }

        @JavascriptInterface
        public synchronized boolean chunk(String id, String base64) {
            ActiveDownload item = active.get(id);
            if (item == null) return false;
            try {
                item.stream.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception exception) {
                remove(id);
                showToast("Download failed");
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean finish(String id) {
            ActiveDownload item = active.remove(id);
            if (item == null) return false;
            try {
                item.stream.close();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(item.uri, values, null, null);
                showToast("Saved to Downloads/Form");
                return true;
            } catch (Exception exception) {
                getContentResolver().delete(item.uri, null, null);
                showToast("Failed to finish download");
                return false;
            }
        }

        @JavascriptInterface
        public synchronized void cancel(String id) { remove(id); }

        private void remove(String id) {
            ActiveDownload item = active.remove(id);
            if (item == null) return;
            try { item.stream.close(); } catch (IOException ignored) { }
            try { getContentResolver().delete(item.uri, null, null); } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("FormAndroid");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
