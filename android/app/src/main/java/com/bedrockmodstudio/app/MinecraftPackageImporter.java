package com.bedrockmodstudio.app;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class MinecraftPackageImporter {
    private static final int MAX_ENTRIES = 2048;
    private static final int MAX_SINGLE_FILE = 16 * 1024 * 1024;
    private static final long MAX_TOTAL_UNCOMPRESSED = 64L * 1024L * 1024L;
    private static final int MAX_NESTED_PACK = 32 * 1024 * 1024;

    private MinecraftPackageImporter() { }

    static byte[] convert(Context context, Uri uri, String displayName) throws Exception {
        ProjectBuilder builder = new ProjectBuilder(displayName);
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Android no pudo abrir el paquete");
            collectContainer(input, builder, true);
        }
        if (builder.files.isEmpty()) throw new IOException("El paquete no contiene archivos Bedrock utilizables");
        return builder.toProjectJson().toString(2).getBytes(StandardCharsets.UTF_8);
    }

    private static void collectContainer(InputStream input, ProjectBuilder builder, boolean nestedAllowed) throws Exception {
        Map<String, byte[]> loose = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            int entries = 0;
            long total = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++entries > MAX_ENTRIES) throw new IOException("El paquete tiene demasiados archivos");

                String name = normalizePath(entry.getName());
                if (name == null) continue;
                byte[] bytes = readEntry(zip, MAX_SINGLE_FILE);
                total += bytes.length;
                if (total > MAX_TOTAL_UNCOMPRESSED) throw new IOException("El paquete descomprimido supera 64 MB");

                if (nestedAllowed && name.toLowerCase(Locale.ROOT).endsWith(".mcpack")) {
                    if (bytes.length > MAX_NESTED_PACK) throw new IOException("Un .mcpack interno supera 32 MB");
                    collectContainer(new ByteArrayInputStream(bytes), builder, false);
                } else {
                    loose.put(name, bytes);
                }
            }
        }

        if (!loose.isEmpty()) ingestPack(loose, builder);
    }

    private static void ingestPack(Map<String, byte[]> rawFiles, ProjectBuilder builder) {
        Map<String, byte[]> files = stripCommonFolder(rawFiles);
        String prefix = classifyPack(files);
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String path = entry.getKey();
            if (path.equalsIgnoreCase("manifest.json")) continue;
            if (path.startsWith("BP/") || path.startsWith("RP/")) {
                builder.addFile(path, entry.getValue());
            } else {
                builder.addFile(prefix + "/" + path, entry.getValue());
            }
        }
    }

    private static String classifyPack(Map<String, byte[]> files) {
        byte[] manifestBytes = files.get("manifest.json");
        if (manifestBytes != null) {
            try {
                JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
                JSONArray modules = manifest.optJSONArray("modules");
                boolean resources = false;
                boolean behavior = false;
                if (modules != null) {
                    for (int i = 0; i < modules.length(); i++) {
                        JSONObject module = modules.optJSONObject(i);
                        String type = module == null ? "" : module.optString("type", "");
                        if ("resources".equalsIgnoreCase(type)) resources = true;
                        if ("data".equalsIgnoreCase(type) || "script".equalsIgnoreCase(type)) behavior = true;
                    }
                }
                if (behavior) return "BP";
                if (resources) return "RP";
            } catch (Exception ignored) { }
        }

        for (String path : files.keySet()) {
            String p = path.toLowerCase(Locale.ROOT);
            if (p.startsWith("items/") || p.startsWith("blocks/") || p.startsWith("entities/")
                    || p.startsWith("recipes/") || p.startsWith("loot_tables/")
                    || p.startsWith("spawn_rules/") || p.startsWith("scripts/")
                    || p.startsWith("functions/")) return "BP";
        }
        return "RP";
    }

    private static Map<String, byte[]> stripCommonFolder(Map<String, byte[]> input) {
        String common = null;
        for (String path : input.keySet()) {
            int slash = path.indexOf('/');
            if (slash <= 0) return input;
            String first = path.substring(0, slash);
            if (common == null) common = first;
            else if (!common.equals(first)) return input;
        }
        if (common == null) return input;

        Map<String, byte[]> stripped = new LinkedHashMap<>();
        String prefix = common + "/";
        for (Map.Entry<String, byte[]> entry : input.entrySet()) {
            stripped.put(entry.getKey().substring(prefix.length()), entry.getValue());
        }
        return stripped;
    }

    private static byte[] readEntry(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[32 * 1024];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Un archivo interno supera 16 MB");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String normalizePath(String raw) {
        if (raw == null) return null;
        String path = raw.replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty() || path.contains("../") || path.equals("..") || path.contains("/./")) return null;
        return path;
    }

    private static boolean isTextPath(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".json") || p.endsWith(".js") || p.endsWith(".ts")
                || p.endsWith(".mcfunction") || p.endsWith(".lang")
                || p.endsWith(".txt") || p.endsWith(".material");
    }

    private static String baseName(String displayName) {
        String name = displayName == null ? "Add-On importado" : displayName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : Arrays.asList(".mcaddon", ".mcpack", ".zip")) {
            if (lower.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        }
        return name.trim().isEmpty() ? "Add-On importado" : name.trim();
    }

    private static final class ProjectBuilder {
        final String projectName;
        final Map<String, byte[]> files = new LinkedHashMap<>();

        ProjectBuilder(String displayName) {
            projectName = baseName(displayName);
        }

        void addFile(String path, byte[] data) {
            String normalized = normalizePath(path);
            if (normalized != null && data != null) files.put(normalized, data);
        }

        JSONObject toProjectJson() {
            JSONObject root = new JSONObject();
            root.put("format", "bedrock-mod-studio-project");
            root.put("version", 6);

            boolean hasBp = false;
            boolean hasRp = false;
            boolean hasScript = false;
            for (String path : files.keySet()) {
                if (path.startsWith("BP/")) hasBp = true;
                if (path.startsWith("RP/")) hasRp = true;
                if (path.startsWith("BP/scripts/")) hasScript = true;
            }

            JSONObject meta = new JSONObject();
            meta.put("projectName", projectName);
            meta.put("projectType", hasBp ? "addon" : (hasRp ? "texture_pack" : "addon"));
            meta.put("namespace", "imported");
            meta.put("minEngineVersion", "1.26.0");
            root.put("meta", meta);

            JSONObject uuids = new JSONObject();
            uuids.put("bpUuid", uuid());
            uuids.put("bpModuleUuid", uuid());
            uuids.put("scriptModuleUuid", uuid());
            uuids.put("rpUuid", uuid());
            uuids.put("rpModuleUuid", uuid());
            root.put("uuids", uuids);

            root.put("content", inferContent());
            root.put("models", new JSONObject());
            root.put("selectedModelName", "modelo_prueba");
            root.put("animations", new JSONObject());
            root.put("selectedAnimationName", "idle");
            root.put("hasScript", hasScript);

            JSONArray encodedFiles = new JSONArray();
            List<Map.Entry<String, byte[]>> sorted = new ArrayList<>(files.entrySet());
            sorted.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, byte[]> entry : sorted) {
                JSONObject file = new JSONObject();
                file.put("path", entry.getKey());
                if (isTextPath(entry.getKey())) {
                    file.put("type", "text");
                    file.put("text", new String(entry.getValue(), StandardCharsets.UTF_8));
                } else {
                    file.put("type", "binary");
                    file.put("base64", Base64.encodeToString(entry.getValue(), Base64.NO_WRAP));
                }
                encodedFiles.put(file);
            }
            root.put("files", encodedFiles);
            return root;
        }

        private JSONArray inferContent() {
            JSONArray out = new JSONArray();
            List<String> seen = new ArrayList<>();
            for (String path : files.keySet()) {
                String type = null;
                if (path.startsWith("BP/items/") && path.endsWith(".json")) type = "item";
                else if (path.startsWith("BP/blocks/") && path.endsWith(".json")) type = "block";
                else if (path.startsWith("BP/entities/") && path.endsWith(".json")) type = "entity";
                else if (path.startsWith("BP/recipes/") && path.endsWith(".json")) type = "recipe";
                else if (path.startsWith("BP/loot_tables/") && path.endsWith(".json")) type = "loot";
                else if (path.startsWith("BP/spawn_rules/") && path.endsWith(".json")) type = "spawn";
                else if (path.equals("BP/scripts/main.js")) type = "script";
                else if (path.startsWith("RP/models/entity/") && path.endsWith(".geo.json")) type = "entity-model";
                if (type == null) continue;

                String name = path.substring(path.lastIndexOf('/') + 1)
                        .replace(".geo.json", "")
                        .replace(".json", "")
                        .replace(".js", "");
                String key = type + ":" + name;
                if (seen.contains(key)) continue;
                seen.add(key);

                JSONObject item = new JSONObject();
                item.put("type", type);
                item.put("name", name);
                item.put("id", "imported:" + name);
                JSONArray paths = new JSONArray();
                paths.put(path);
                item.put("paths", paths);
                out.put(item);
            }
            return out;
        }

        private static String uuid() {
            return UUID.randomUUID().toString();
        }
    }
}
