package com.bedrockmodstudio.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class AndroidBridge {
    private static final String EXPORT_FOLDER = "Bedrock Mod Studio";

    private final Activity activity;
    private final Object lock = new Object();

    private File pendingFile;
    private BufferedOutputStream pendingOutput;
    private String pendingName;
    private String pendingMime;
    private boolean pendingOpenAfter;

    AndroidBridge(Activity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getPlatform() {
        return "android";
    }

    @JavascriptInterface
    public void beginFile(String fileName, String mimeType, boolean openAfter) {
        synchronized (lock) {
            clearPendingLocked();
            try {
                String safeName = sanitizeFileName(fileName);
                File tempDir = new File(activity.getCacheDir(), "exports");
                if (!tempDir.exists() && !tempDir.mkdirs()) {
                    throw new IOException("No se pudo crear el directorio temporal");
                }
                pendingFile = File.createTempFile("bms_", ".part", tempDir);
                pendingOutput = new BufferedOutputStream(new FileOutputStream(pendingFile));
                pendingName = safeName;
                pendingMime = normalizeMime(mimeType, safeName);
                pendingOpenAfter = openAfter || isMinecraftPackage(safeName);
            } catch (Exception error) {
                clearPendingLocked();
                showToast("No pude preparar la exportación: " + error.getMessage());
            }
        }
    }

    @JavascriptInterface
    public void appendFileChunk(String base64Chunk) {
        synchronized (lock) {
            if (pendingOutput == null) {
                showToast("No hay una exportación activa.");
                return;
            }
            try {
                byte[] bytes = Base64.decode(base64Chunk, Base64.NO_WRAP);
                pendingOutput.write(bytes);
            } catch (Exception error) {
                showToast("Falló una parte de la exportación: " + error.getMessage());
                clearPendingLocked();
            }
        }
    }

    @JavascriptInterface
    public void finishFile() {
        final File source;
        final String fileName;
        final String mime;
        final boolean openAfter;

        synchronized (lock) {
            if (pendingOutput == null || pendingFile == null) {
                showToast("No hay una exportación que finalizar.");
                return;
            }
            try {
                pendingOutput.flush();
                pendingOutput.close();
            } catch (IOException error) {
                showToast("No pude cerrar el archivo: " + error.getMessage());
                clearPendingLocked();
                return;
            }
            source = pendingFile;
            fileName = pendingName;
            mime = pendingMime;
            openAfter = pendingOpenAfter;
            pendingOutput = null;
            pendingFile = null;
            pendingName = null;
            pendingMime = null;
            pendingOpenAfter = false;
        }

        try {
            Uri savedUri = persistExport(source, fileName, mime);
            if (!source.delete()) source.deleteOnExit();
            showToast("Guardado: " + fileName);
            if (openAfter) openExport(savedUri, fileName, mime);
        } catch (Exception error) {
            if (!source.delete()) source.deleteOnExit();
            showToast("No pude guardar " + fileName + ": " + error.getMessage());
        }
    }

    @JavascriptInterface
    public void cancelFile() {
        synchronized (lock) {
            clearPendingLocked();
        }
    }

    @JavascriptInterface
    public void openExternal(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                showToast("Enlace externo bloqueado.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            activity.runOnUiThread(() -> {
                try {
                    activity.startActivity(intent);
                } catch (ActivityNotFoundException error) {
                    Toast.makeText(activity, "No hay una app para abrir este enlace.", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception error) {
            showToast("No pude abrir el enlace.");
        }
    }

    @JavascriptInterface
    public void notifyError(String message) {
        showToast("Bedrock Mod Studio: " + message);
    }

    private Uri persistExport(File source, String fileName, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = activity.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_FOLDER);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore no devolvió una ubicación");

            try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                 OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("No se pudo abrir el destino");
                copy(input, output);
            } catch (Exception error) {
                resolver.delete(uri, null, null);
                throw error;
            }

            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);
            return uri;
        }

        File base = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = activity.getFilesDir();
        File folder = new File(base, EXPORT_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("No se pudo crear la carpeta de exportación");
        File destination = uniqueFile(folder, fileName);
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            copy(input, output);
        }
        return FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", destination);
    }

    private void openExport(Uri uri, String fileName, String mime) {
        activity.runOnUiThread(() -> {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, isMinecraftPackage(fileName) ? "application/octet-stream" : mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            view.setClipData(ClipData.newRawUri("Bedrock Mod Studio export", uri));

            if (isMinecraftPackage(fileName)) {
                Intent minecraft = new Intent(view);
                minecraft.setPackage("com.mojang.minecraftpe");
                try {
                    activity.startActivity(minecraft);
                    return;
                } catch (ActivityNotFoundException ignored) {
                    // Minecraft may be absent or may not expose the matching activity on this build.
                }
            }

            try {
                activity.startActivity(Intent.createChooser(view, "Abrir " + fileName));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(activity, "Archivo guardado, pero no encontré una app para abrirlo.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void clearPendingLocked() {
        if (pendingOutput != null) {
            try { pendingOutput.close(); } catch (IOException ignored) { }
        }
        if (pendingFile != null && pendingFile.exists() && !pendingFile.delete()) {
            pendingFile.deleteOnExit();
        }
        pendingOutput = null;
        pendingFile = null;
        pendingName = null;
        pendingMime = null;
        pendingOpenAfter = false;
    }

    private void showToast(String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_LONG).show());
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        output.flush();
    }

    private static File uniqueFile(File folder, String requestedName) {
        File direct = new File(folder, requestedName);
        if (!direct.exists()) return direct;

        int dot = requestedName.lastIndexOf('.');
        String stem = dot > 0 ? requestedName.substring(0, dot) : requestedName;
        String extension = dot > 0 ? requestedName.substring(dot) : "";
        for (int i = 2; i < 10000; i++) {
            File candidate = new File(folder, stem + " (" + i + ")" + extension);
            if (!candidate.exists()) return candidate;
        }
        return new File(folder, System.currentTimeMillis() + "_" + requestedName);
    }

    private static boolean isMinecraftPackage(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mcaddon") || lower.endsWith(".mcpack") || lower.endsWith(".mcworld");
    }

    private static String normalizeMime(String mime, String fileName) {
        if (mime != null && !mime.isBlank() && !mime.equals("application/octet-stream")) return mime;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".json")) return "application/json";
        if (isMinecraftPackage(fileName)) return "application/octet-stream";
        return "application/octet-stream";
    }

    private static String sanitizeFileName(String fileName) {
        String input = fileName == null || fileName.isBlank() ? "bedrock-mod-studio-export.bin" : fileName.trim();
        String safe = input.replaceAll("[\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (safe.length() > 180) safe = safe.substring(safe.length() - 180);
        return safe.isBlank() ? "bedrock-mod-studio-export.bin" : safe;
    }
}
