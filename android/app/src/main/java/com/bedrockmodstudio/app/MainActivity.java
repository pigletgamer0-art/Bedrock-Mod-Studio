package com.bedrockmodstudio.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Collections;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    private static final String APP_URL = APP_ORIGIN + "/assets/www/index.html";

    private static final String NATIVE_DOWNLOAD_HOOK = """
        (() => {
          if (window.__BMS_ANDROID_HOOK__) return;
          window.__BMS_ANDROID_HOOK__ = true;
          window.BMS_NATIVE_ANDROID = true;
          try {
            if (navigator.serviceWorker && navigator.serviceWorker.register) {
              navigator.serviceWorker.register = () => Promise.reject(new Error('Service Worker desactivado dentro del APK nativo'));
            }
          } catch (_) {}

          const toBase64 = (bytes) => {
            let binary = '';
            for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
            return btoa(binary);
          };

          const sendBlob = async (href, filename) => {
            try {
              const response = await fetch(href);
              const blob = await response.blob();
              const bytes = new Uint8Array(await blob.arrayBuffer());
              const openAfter = /\\.(mcaddon|mcpack|mcworld)$/i.test(filename || '');
              AndroidBridge.beginFile(filename || 'bedrock-mod-studio-export.bin', blob.type || 'application/octet-stream', openAfter);
              const chunkBytes = 192 * 1024;
              for (let offset = 0; offset < bytes.length; offset += chunkBytes) {
                AndroidBridge.appendFileChunk(toBase64(bytes.subarray(offset, Math.min(bytes.length, offset + chunkBytes))));
              }
              AndroidBridge.finishFile();
            } catch (error) {
              try { AndroidBridge.cancelFile(); } catch (_) {}
              try { AndroidBridge.notifyError(error && error.message ? error.message : String(error)); } catch (_) {}
            }
          };

          const originalAnchorClick = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function() {
            if (this.download && typeof this.href === 'string' && this.href.startsWith('blob:') && window.AndroidBridge) {
              sendBlob(this.href, this.download);
              return;
            }
            return originalAnchorClick.apply(this, arguments);
          };

          const originalOpen = window.open;
          window.open = function(url) {
            if (url && window.AndroidBridge) {
              AndroidBridge.openExternal(String(url));
              return null;
            }
            return originalOpen ? originalOpen.apply(window, arguments) : null;
          };
        })();
        """;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(15, 17, 21));
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(webView);

        configureWebView();
        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    NATIVE_DOWNLOAD_HOOK,
                    Collections.singleton(APP_ORIGIN)
            );
        }

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (APP_ORIGIN.equals(uri.getScheme() + "://" + uri.getHost())) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(NATIVE_DOWNLOAD_HOOK, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallbackValue,
                    FileChooserParams fileChooserParams
            ) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = filePathCallbackValue;

                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception ignored) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException error) {
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                android.util.Log.d(
                        "BedrockModStudio",
                        consoleMessage.message() + " @ " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber()
                );
                return true;
            }
        });
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        filePathCallback.onReceiveValue(result);
        filePathCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
