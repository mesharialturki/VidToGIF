package com.gifstudio.app;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

/**
 * GIF Studio: hosts the editor (app/src/main/assets/index.html) in a WebView.
 * Everything runs on the phone; the app needs no internet connection.
 */
public class MainActivity extends ComponentActivity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private ActivityResultLauncher<Intent> pickLauncher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(0xFF050B1E);
        setContentView(web);

        // Serve the bundled files from a secure https origin so every web feature works.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        // Video / image picker for <input type="file">
        pickLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (fileCallback == null) return;
                    fileCallback.onReceiveValue(
                            WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData()));
                    fileCallback = null;
                });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                String[] types = params.getAcceptTypes();
                String type = (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty())
                        ? types[0] : "*/*";
                Intent intent;
                if (Build.VERSION.SDK_INT >= 33 && (type.startsWith("video/") || type.startsWith("image/"))) {
                    // Android 13+: the system photo & video picker
                    intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                    intent.setType(type);
                } else {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(type);
                }
                try {
                    pickLauncher.launch(intent);
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        // Back button closes open sheets first, then leaves the app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("(window.__onBack && window.__onBack()) ? 'y' : 'n'", value -> {
                    if (!"\"y\"".equals(value)) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                });
            }
        });

        web.loadUrl(START_URL);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /** Lets the page save finished GIFs / videos into the phone's Gallery. */
    private class Bridge {
        private OutputStream out;
        private Uri pending;
        private Uri last;
        private String lastMime;

        @JavascriptInterface
        public synchronized boolean begin(String name, String mime) {
            abort();
            try {
                if (mime == null || mime.isEmpty()) {
                    mime = name.endsWith(".gif") ? "image/gif" : name.endsWith(".webm") ? "video/webm" : "video/mp4";
                }
                boolean isImage = mime.startsWith("image/");
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        (isImage ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES) + "/GIF Studio");
                cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri collection = isImage
                        ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                pending = getContentResolver().insert(collection, cv);
                if (pending == null) return false;
                out = getContentResolver().openOutputStream(pending);
                lastMime = mime;
                return out != null;
            } catch (Exception e) {
                abort();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean chunk(String base64) {
            if (out == null) return false;
            try {
                out.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                abort();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean end() {
            if (out == null || pending == null) return false;
            try {
                out.close();
                out = null;
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(pending, cv, null, null);
                last = pending;
                pending = null;
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Saved to Gallery › GIF Studio", Toast.LENGTH_SHORT).show());
                return true;
            } catch (Exception e) {
                abort();
                return false;
            }
        }

        @JavascriptInterface
        public void shareLast() {
            final Uri uri = last;
            if (uri == null) return;
            final Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(lastMime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, "Share")));
        }

        private void abort() {
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            out = null;
            if (pending != null) {
                try { getContentResolver().delete(pending, null, null); } catch (Exception ignored) { }
                pending = null;
            }
        }
    }
}
