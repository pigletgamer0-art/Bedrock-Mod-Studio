package com.bedrockmodstudio.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 4107;
    private static final int PACKAGE_PICKER_REQUEST = 4108;
    private static final int MAX_NATIVE_IMPORT_BYTES = 8 * 1024 * 1024;
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    private static final String APP_URL = APP_ORIGIN + "/assets/www/index.html";

    private static final String NATIVE_DOWNLOAD_HOOK = """
        (() => {
          if (window.__BMS_ANDROID_HOOK__) return;
          window.__BMS_ANDROID_HOOK__ = true;
          window.BMS_NATIVE_ANDROID = true;
          document.documentElement.classList.add('android-native');
          document.documentElement.dataset.bmsOrientation = 'portrait';

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

          const fromBase64 = (value) => {
            const binary = atob(value);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return bytes;
          };

          const sendBlob = async (href, filename) => {
            try {
              const response = await fetch(href);
              const blob = await response.blob();
              const bytes = new Uint8Array(await blob.arrayBuffer());
              const chunkBytes = 192 * 1024;

              if (window.__BMS_AUTOSAVE_CAPTURE__ && /\\.bmsproject\\.json$/i.test(filename || '')) {
                AndroidBridge.beginAutosave();
                for (let offset = 0; offset < bytes.length; offset += chunkBytes) {
                  AndroidBridge.appendAutosaveChunk(toBase64(bytes.subarray(offset, Math.min(bytes.length, offset + chunkBytes))));
                }
                AndroidBridge.finishAutosave();
                window.__BMS_AUTOSAVE_CAPTURE__ = false;
                return;
              }

              if (window.__BMS_LIBRARY_CAPTURE__ && /\\.bmsproject\\.json$/i.test(filename || '')) {
                let internalName = String(window.__BMS_LIBRARY_PROJECT_NAME__ || filename || 'Proyecto').trim();
                if (!internalName.toLowerCase().endsWith('.bmsproject.json')) internalName += '.bmsproject.json';
                AndroidBridge.beginProjectSnapshot(internalName);
                for (let offset = 0; offset < bytes.length; offset += chunkBytes) {
                  AndroidBridge.appendProjectSnapshotChunk(toBase64(bytes.subarray(offset, Math.min(bytes.length, offset + chunkBytes))));
                }
                AndroidBridge.finishProjectSnapshot();
                window.__BMS_LIBRARY_CAPTURE__ = false;
                window.dispatchEvent(new Event('bms-project-library-changed'));
                return;
              }

              const openAfter = /\\.(mcaddon|mcpack|mcworld|mctemplate)$/i.test(filename || '');
              const projectSnapshot = /\\.bmsproject\\.json$/i.test(filename || '');
              AndroidBridge.beginFile(filename || 'bedrock-mod-studio-export.bin', blob.type || 'application/octet-stream', openAfter);
              if (projectSnapshot) AndroidBridge.beginProjectSnapshot(filename);
              for (let offset = 0; offset < bytes.length; offset += chunkBytes) {
                const encoded = toBase64(bytes.subarray(offset, Math.min(bytes.length, offset + chunkBytes)));
                AndroidBridge.appendFileChunk(encoded);
                if (projectSnapshot) AndroidBridge.appendProjectSnapshotChunk(encoded);
              }
              AndroidBridge.finishFile();
              if (projectSnapshot) {
                AndroidBridge.finishProjectSnapshot();
                window.dispatchEvent(new Event('bms-project-library-changed'));
              }
            } catch (error) {
              try { AndroidBridge.cancelFile(); } catch (_) {}
              try { AndroidBridge.cancelProjectSnapshot(); } catch (_) {}
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

          window.__BMS_NATIVE_RECEIVE__ = function(name, mime, base64) {
            try {
              const lower = String(name || '').toLowerCase();
              let input = null;
              if (lower.endsWith('.png')) input = document.getElementById('importTextureInput');
              else if (lower.endsWith('.geo.json')) input = document.getElementById('importGeoInput');
              else if (lower.endsWith('.bmsproject.json')) input = document.getElementById('loadProjectInput');

              if (!input) {
                AndroidBridge.notifyError('Ese archivo no se puede importar directamente todavía: ' + name);
                return false;
              }

              const bytes = fromBase64(base64);
              const file = new File([bytes], name, { type: mime || 'application/octet-stream' });
              const transfer = new DataTransfer();
              transfer.items.add(file);
              input.files = transfer.files;
              input.dispatchEvent(new Event('change', { bubbles: true }));
              return true;
            } catch (error) {
              AndroidBridge.notifyError(error && error.message ? error.message : String(error));
              return false;
            }
          };

          window.__BMS_ANDROID_BACK__ = function() {
            const createSheet = document.getElementById('androidCreateSheet');
            if (createSheet && !createSheet.hidden) {
              createSheet.hidden = true;
              document.documentElement.classList.remove('android-create-sheet-open');
              return 'handled';
            }
            if (!document.documentElement.classList.contains('android-sidebar-collapsed')) {
              document.documentElement.classList.add('android-sidebar-collapsed');
              return 'handled';
            }

            const launcher = document.getElementById('projectLauncher');
            if (launcher && !launcher.hidden) {
              const close = document.getElementById('closeLauncherBtn');
              if (close) close.click();
              else launcher.hidden = true;
              return 'handled';
            }

            const active = document.querySelector('.tab.active');
            if (active && active.dataset.tab && active.dataset.tab !== 'designer') {
              const designer = document.querySelector('.tab[data-tab="designer"]');
              if (designer) designer.click();
              return 'handled';
            }
            return 'exit';
          };

          const captureAutosave = () => {
            try {
              const save = document.getElementById('saveProjectBtn');
              if (!save || window.__BMS_AUTOSAVE_CAPTURE__ || window.__BMS_LIBRARY_CAPTURE__) return;
              window.__BMS_AUTOSAVE_CAPTURE__ = true;
              save.click();
              setTimeout(() => { window.__BMS_AUTOSAVE_CAPTURE__ = false; }, 1800);
            } catch (_) {
              window.__BMS_AUTOSAVE_CAPTURE__ = false;
            }
          };

          const restoreAutosave = () => {
            try {
              if (!AndroidBridge.hasAutosave()) return;
              const base64 = AndroidBridge.getAutosaveBase64();
              if (!base64) return;
              const input = document.getElementById('loadProjectInput');
              if (!input) return;
              const bytes = fromBase64(base64);
              const file = new File([bytes], 'recuperado.bmsproject.json', { type: 'application/json' });
              const transfer = new DataTransfer();
              transfer.items.add(file);
              input.files = transfer.files;
              input.dispatchEvent(new Event('change', { bubbles: true }));
              setTimeout(() => AndroidBridge.notifyError('Borrador recuperado automáticamente'), 250);
            } catch (_) {}
          };

          const setupCreationHome = () => {
            const designer = document.getElementById('designer');
            const contentList = document.getElementById('contentList');
            if (!designer || !contentList || document.getElementById('androidCreationHome')) return;

            const legacyCreatorCard = contentList.closest('.card');
            const home = document.createElement('section');
            home.id = 'androidCreationHome';
            home.className = 'android-creation-home';
            home.innerHTML = [
              '<div class="android-home-head">',
                '<div><span class="eyebrow">TU PROYECTO</span><h2>Lo que has creado</h2><small>Todo tu contenido en un solo lugar.</small></div>',
                '<button id="androidCreatePlus" class="android-create-plus" type="button" aria-label="Crear">+</button>',
              '</div>',
              '<div class="android-home-tools"><label class="android-home-search"><span>⌕</span><input id="androidCreationSearch" type="search" placeholder="Buscar en lo que has creado..." autocomplete="off"></label><span id="androidCreationCount" class="android-creation-count">0</span></div>',
              '<div id="androidProjectLibrary" class="android-project-library"></div>',
              '<div id="androidHomeContent" class="android-home-content"></div>',
              '<div id="androidNoCreationResults" class="android-no-results" hidden>No encontré coincidencias.</div>',
              '<div id="androidSavedTextures" class="android-saved-textures" hidden></div>'
            ].join('');

            const hero = designer.querySelector('.hero');
            if (hero && hero.nextSibling) designer.insertBefore(home, hero.nextSibling);
            else designer.prepend(home);

            document.getElementById('androidHomeContent').appendChild(contentList);
            if (legacyCreatorCard) legacyCreatorCard.classList.add('android-legacy-create-card');

            const sheet = document.createElement('div');
            sheet.id = 'androidCreateSheet';
            sheet.className = 'android-create-sheet-backdrop';
            sheet.hidden = true;
            sheet.innerHTML = [
              '<section class="android-create-sheet" role="dialog" aria-modal="true" aria-label="Crear contenido">',
                '<div class="android-sheet-grabber"></div>',
                '<div class="android-sheet-head"><div><span class="eyebrow">CREAR</span><h2>¿Qué quieres crear?</h2></div><button id="androidCloseCreateSheet" type="button">×</button></div>',
                '<div class="android-create-section"><h3>Contenido del juego</h3><div class="android-create-grid">',
                  '<button data-proxy-create="item"><span>🗡️</span><b>Objeto</b><small>Ítems y herramientas</small></button>',
                  '<button data-proxy-create="block"><span>🧱</span><b>Bloque</b><small>Bloques Bedrock</small></button>',
                  '<button data-proxy-create="entity"><span>🐾</span><b>Entidad</b><small>Mob + modelo base</small></button>',
                '</div></div>',
                '<div class="android-create-section"><h3>Gameplay y lógica</h3><div class="android-create-grid">',
                  '<button data-proxy-create="script"><span>⚙️</span><b>Script</b><small>Script API</small></button>',
                  '<button data-proxy-create="recipe"><span>🧪</span><b>Receta</b><small>Crafting</small></button>',
                  '<button data-proxy-create="loot"><span>🎁</span><b>Loot</b><small>Tablas de botín</small></button>',
                  '<button data-proxy-create="spawn"><span>🌱</span><b>Spawn</b><small>Reglas de aparición</small></button>',
                '</div></div>',
                '<div class="android-create-section"><h3>Visual</h3><div class="android-create-grid">',
                  '<button data-proxy-tool="pixel"><span>🎨</span><b>Textura</b><small>Pixel Studio</small></button>',
                  '<button data-proxy-tool="model"><span>🧊</span><b>Modelo 3D</b><small>Geometría + UV</small></button>',
                  '<button data-proxy-tool="animation"><span>🎞️</span><b>Animación</b><small>Timeline por huesos</small></button>',
                '</div></div>',
                '<div class="android-create-section"><h3>Proyecto</h3>',
                  '<button class="android-project-create" data-proxy-new-project="true"><span>＋</span><div><b>Nuevo proyecto</b><small>Add-On completo, una sola cosa, pack de texturas, textura o modelo.</small></div><strong>›</strong></button>',
                  '<button class="android-project-create" data-proxy-import-package="true"><span>⇩</span><div><b>Importar Add-On</b><small>Abrir .mcpack o .mcaddon como proyecto editable.</small></div><strong>›</strong></button>',
                '</div>',
              '</section>'
            ].join('');
            document.body.appendChild(sheet);

            const openSheet = () => {
              sheet.hidden = false;
              document.documentElement.classList.add('android-create-sheet-open');
            };
            const closeSheet = () => {
              sheet.hidden = true;
              document.documentElement.classList.remove('android-create-sheet-open');
            };

            document.getElementById('androidCreatePlus').addEventListener('click', openSheet);
            document.getElementById('androidCloseCreateSheet').addEventListener('click', closeSheet);
            sheet.addEventListener('pointerdown', (event) => { if (event.target === sheet) closeSheet(); });

            sheet.querySelectorAll('[data-proxy-create]').forEach((button) => {
              button.addEventListener('click', () => {
                const original = document.querySelector('.create-card[data-create="' + button.dataset.proxyCreate + '"]');
                closeSheet();
                original?.click();
              });
            });

            sheet.querySelectorAll('[data-proxy-tool]').forEach((button) => {
              button.addEventListener('click', () => {
                const tool = button.dataset.proxyTool;
                closeSheet();
                if (tool === 'animation') {
                  document.querySelector('.tab[data-tab="designer"]')?.click();
                  document.querySelector('.animation-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  return;
                }
                document.querySelector('[data-open-tool="' + tool + '"]')?.click();
              });
            });

            sheet.querySelector('[data-proxy-new-project]')?.addEventListener('click', () => {
              closeSheet();
              document.getElementById('newProjectBtn')?.click();
            });
            sheet.querySelector('[data-proxy-import-package]')?.addEventListener('click', () => {
              closeSheet();
              AndroidBridge.pickMinecraftPackage();
            });

            const searchInput = document.getElementById('androidCreationSearch');
            const creationCount = document.getElementById('androidCreationCount');
            const noResults = document.getElementById('androidNoCreationResults');
            const refreshCreationList = () => {
              const rows = [...contentList.querySelectorAll('.content-row')];
              const query = (searchInput?.value || '').trim().toLocaleLowerCase();
              let visible = 0;
              rows.forEach((row) => {
                const show = !query || row.textContent.toLocaleLowerCase().includes(query);
                row.hidden = !show;
                if (show) visible++;
              });
              if (creationCount) creationCount.textContent = query ? visible + '/' + rows.length : String(rows.length);
              if (noResults) noResults.hidden = !query || visible > 0;
            };
            searchInput?.addEventListener('input', refreshCreationList);
            new MutationObserver(refreshCreationList).observe(contentList, { childList: true, subtree: true, characterData: true });
            refreshCreationList();

            const projectLibrary = document.getElementById('androidProjectLibrary');
            const refreshProjectLibrary = () => {
              if (!projectLibrary) return;
              let projects = [];
              try { projects = JSON.parse(AndroidBridge.listProjectSnapshotsJson() || '[]'); } catch (_) {}
              projectLibrary.replaceChildren();

              const title = document.createElement('div');
              title.className = 'android-project-library-title';
              title.innerHTML = '<div><b>📁 Mis proyectos</b><small>Guardados dentro de la app</small></div><div class="android-project-library-actions"><span>' + projects.length + '</span><button type="button">Guardar actual</button></div>';
              const saveCurrent = title.querySelector('button');
              saveCurrent?.addEventListener('click', () => {
                if (window.__BMS_LIBRARY_CAPTURE__ || window.__BMS_AUTOSAVE_CAPTURE__) return;
                window.__BMS_LIBRARY_CAPTURE__ = true;
                window.__BMS_LIBRARY_PROJECT_NAME__ = document.getElementById('projectName')?.value || 'Proyecto';
                document.getElementById('saveProjectBtn')?.click();
                setTimeout(() => { window.__BMS_LIBRARY_CAPTURE__ = false; }, 15000);
              });
              projectLibrary.appendChild(title);

              if (!projects.length) {
                const empty = document.createElement('div');
                empty.className = 'android-project-library-empty';
                empty.textContent = 'Guarda un proyecto y aparecerá aquí.';
                projectLibrary.appendChild(empty);
                return;
              }

              projects.forEach((project) => {
                const row = document.createElement('div');
                row.className = 'android-project-library-row';

                const open = document.createElement('button');
                open.type = 'button';
                open.className = 'android-project-open';
                const name = document.createElement('b');
                name.textContent = project.name.replace(/\\.bmsproject\\.json$/i, '');
                const meta = document.createElement('small');
                const kb = Math.max(1, Math.round(Number(project.size || 0) / 1024));
                const date = new Date(Number(project.modified || 0));
                meta.textContent = kb + ' KB · ' + (Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString());
                open.append(name, meta);
                open.addEventListener('click', () => {
                  const base64 = AndroidBridge.getProjectSnapshotBase64(project.name);
                  if (!base64) return AndroidBridge.notifyError('No pude abrir la copia interna');
                  const bytes = fromBase64(base64);
                  const file = new File([bytes], project.name, { type: 'application/json' });
                  const transfer = new DataTransfer();
                  transfer.items.add(file);
                  const input = document.getElementById('loadProjectInput');
                  if (!input) return;
                  input.files = transfer.files;
                  input.dispatchEvent(new Event('change', { bubbles: true }));
                });

                const remove = document.createElement('button');
                remove.type = 'button';
                remove.className = 'android-project-remove';
                remove.textContent = '×';
                remove.setAttribute('aria-label', 'Eliminar copia');
                remove.addEventListener('click', () => {
                  if (AndroidBridge.deleteProjectSnapshot(project.name)) refreshProjectLibrary();
                });

                row.append(open, remove);
                projectLibrary.appendChild(row);
              });
            };
            window.addEventListener('bms-project-library-changed', refreshProjectLibrary);
            refreshProjectLibrary();

            const textureList = document.getElementById('androidSavedTextures');
            const fileTree = document.getElementById('fileTree');
            const refreshTextureList = () => {
              if (!textureList || !fileTree) return;
              const pngs = [...fileTree.querySelectorAll('[data-path]')]
                .map((row) => row.dataset.path)
                .filter((path) => /\\.png$/i.test(path || ''));
              if (!pngs.length) {
                textureList.hidden = true;
                textureList.innerHTML = '';
                return;
              }
              textureList.hidden = false;
              textureList.innerHTML = '<div class="android-texture-title"><b>🎨 Texturas guardadas</b><small>' + pngs.length + '</small></div>' +
                pngs.map((path) => '<button type="button" data-texture-path="' + path.replace(/"/g, '&quot;') + '"><span>▦</span><div><b>' + path.split('/').pop() + '</b><small>' + path + '</small></div></button>').join('');
              textureList.querySelectorAll('[data-texture-path]').forEach((button) => {
                button.addEventListener('click', () => document.querySelector('.tab[data-tab="pixel"]')?.click());
              });
            };
            if (fileTree) new MutationObserver(refreshTextureList).observe(fileTree, { childList: true, subtree: true });
            refreshTextureList();
          };

          const setupAndroidUi = () => {
            if (!document.body || document.getElementById('androidDock')) return;

            document.documentElement.classList.add('android-sidebar-collapsed');

            const projectToggle = document.createElement('button');
            projectToggle.id = 'androidProjectToggle';
            projectToggle.className = 'android-project-toggle';
            projectToggle.type = 'button';
            projectToggle.innerHTML = '<span>☰</span><b>Proyecto</b>';
            projectToggle.addEventListener('click', () => {
              document.documentElement.classList.toggle('android-sidebar-collapsed');
            });
            document.body.appendChild(projectToggle);

            const dock = document.createElement('nav');
            dock.id = 'androidDock';
            dock.className = 'android-dock';
            dock.setAttribute('aria-label', 'Accesos rápidos Android');
            dock.innerHTML = [
              '<button type="button" data-native-action="new"><span>＋</span><small>Nuevo</small></button>',
              '<button type="button" data-native-tab="designer"><span>◆</span><small>Diseño</small></button>',
              '<button type="button" data-native-tab="pixel"><span>▦</span><small>Pixel</small></button>',
              '<button type="button" data-native-tab="code"><span>⌘</span><small>Código</small></button>',
              '<button type="button" class="native-export" data-native-action="export"><span>↑</span><small>Exportar</small></button>'
            ].join('');
            document.body.appendChild(dock);

            const syncDock = () => {
              const activeTab = document.querySelector('.tab.active')?.dataset?.tab || '';
              dock.querySelectorAll('[data-native-tab]').forEach((button) => {
                button.classList.toggle('active', button.dataset.nativeTab === activeTab);
              });
            };

            dock.addEventListener('click', (event) => {
              const button = event.target.closest('button');
              if (!button) return;
              if (button.dataset.nativeTab) {
                document.querySelector('.tab[data-tab="' + button.dataset.nativeTab + '"]')?.click();
                syncDock();
                return;
              }
              if (button.dataset.nativeAction === 'new') document.getElementById('newProjectBtn')?.click();
              if (button.dataset.nativeAction === 'export') document.getElementById('exportBtn')?.click();
            });

            document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', syncDock));
            syncDock();

            setupCreationHome();
            setTimeout(restoreAutosave, 500);
            setInterval(captureAutosave, 45000);
            document.addEventListener('visibilitychange', () => {
              if (document.visibilityState === 'hidden') captureAutosave();
            });
            document.getElementById('loadProjectInput')?.addEventListener('change', () => {
              setTimeout(captureAutosave, 2500);
            });

            const viewport = window.visualViewport;
            if (viewport) {
              let maxHeight = viewport.height;
              const updateKeyboard = () => {
                maxHeight = Math.max(maxHeight, viewport.height);
                document.documentElement.classList.toggle('android-keyboard-open', viewport.height < maxHeight * 0.72);
              };
              viewport.addEventListener('resize', updateKeyboard);
              viewport.addEventListener('scroll', updateKeyboard);
              updateKeyboard();
            }
          };

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setupAndroidUi, { once: true });
          } else {
            setupAndroidUi();
          }
        })();
        """;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private volatile boolean pageReady;
    private Uri pendingIncomingUri;
    private String pendingIncomingName;
    private String pendingIncomingMime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(15, 17, 21));
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(webView);
        ViewCompat.requestApplyInsets(webView);

        configureWebView();
        handleIncomingIntent(getIntent());

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
                Uri uri = request.getUrl();
                String origin = uri.getScheme() + "://" + uri.getHost();
                if (APP_ORIGIN.equals(origin)) {
                    return assetLoader.shouldInterceptRequest(uri);
                }
                if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                    return new WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            new ByteArrayInputStream(new byte[0])
                    );
                }
                return null;
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
                view.evaluateJavascript(NATIVE_DOWNLOAD_HOOK, ignored -> {
                    pageReady = true;
                    deliverPendingIncomingFile();
                });
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                @SuppressWarnings("deprecation")
                Uri legacyUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                uri = legacyUri;
            }
        }

        if (uri == null) return;

        pendingIncomingUri = uri;
        pendingIncomingName = queryDisplayName(uri);
        pendingIncomingMime = intent.getType();
        if (pendingIncomingMime == null || pendingIncomingMime.trim().isEmpty()) {
            pendingIncomingMime = getContentResolver().getType(uri);
        }
        if (pendingIncomingMime == null) pendingIncomingMime = "application/octet-stream";

        intent.setData(null);
        intent.removeExtra(Intent.EXTRA_STREAM);

        if (pageReady) deliverPendingIncomingFile();
    }

    private void deliverPendingIncomingFile() {
        final Uri uri = pendingIncomingUri;
        final String fileName = pendingIncomingName;
        final String mime = pendingIncomingMime;

        if (!pageReady || uri == null || fileName == null) return;
        pendingIncomingUri = null;
        pendingIncomingName = null;
        pendingIncomingMime = null;

        String lower = fileName.toLowerCase(Locale.ROOT);
        boolean minecraftPackage = lower.endsWith(".mcaddon") || lower.endsWith(".mcpack");
        if (!(lower.endsWith(".png") || lower.endsWith(".geo.json") || lower.endsWith(".bmsproject.json") || minecraftPackage)) {
            Toast.makeText(this, "Ese archivo todavía no se puede importar directamente: " + fileName, Toast.LENGTH_LONG).show();
            return;
        }

        new Thread(() -> {
            try {
                byte[] bytes = minecraftPackage
                        ? MinecraftPackageImporter.convert(this, uri, fileName)
                        : readIncomingFile(uri);
                String deliveredName = minecraftPackage
                        ? fileName.replaceAll("(?i)\\\\.(mcaddon|mcpack)$", "") + ".bmsproject.json"
                        : fileName;
                String deliveredMime = minecraftPackage ? "application/json" : mime;
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                String js = "window.__BMS_NATIVE_RECEIVE__ && window.__BMS_NATIVE_RECEIVE__("
                        + JSONObject.quote(deliveredName) + ","
                        + JSONObject.quote(deliveredMime) + ","
                        + JSONObject.quote(base64) + ");";
                runOnUiThread(() -> {
                    if (webView != null) webView.evaluateJavascript(js, null);
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "No pude importar " + fileName + ": " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        }, "bms-native-import").start();
    }

    private byte[] readIncomingFile(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Android no pudo abrir el archivo");
            byte[] buffer = new byte[32 * 1024];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_NATIVE_IMPORT_BYTES) {
                    throw new IOException("El archivo supera el límite de importación directa de 8 MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String queryDisplayName(Uri uri) {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        String value = cursor.getString(index);
                        if (value != null && !value.trim().isEmpty()) return value;
                    }
                }
            } catch (Exception ignored) { }
        }

        String segment = uri.getLastPathSegment();
        return segment == null || segment.trim().isEmpty() ? "archivo_importado" : segment;
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) { }
    }

    void openMinecraftPackagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        try {
            startActivityForResult(intent, PACKAGE_PICKER_REQUEST);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No encontré un selector de archivos.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PACKAGE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                pendingIncomingUri = uri;
                pendingIncomingName = queryDisplayName(uri);
                pendingIncomingMime = getContentResolver().getType(uri);
                if (pendingIncomingMime == null || pendingIncomingMime.trim().isEmpty()) {
                    pendingIncomingMime = "application/octet-stream";
                }
                deliverPendingIncomingFile();
            }
            return;
        }

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
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(
                "window.__BMS_ANDROID_BACK__ ? window.__BMS_ANDROID_BACK__() : 'exit'",
                value -> {
                    if ("\"handled\"".equals(value)) return;
                    if (webView != null && webView.canGoBack()) webView.goBack();
                    else finish();
                }
        );
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
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
