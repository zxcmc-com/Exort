package com.zxcmc.exort.core.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class PackObfuscator {
  private static final String CACHE_VERSION = "simple-v1";
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private PackObfuscator() {}

  static void obfuscate(File rawFile, File outputFile) throws IOException {
    String rawSha1 = PackExporter.sha1Hex(rawFile.toPath());
    File cacheDir = new File(new File(outputFile.getParentFile(), ".cache"), CACHE_VERSION);
    File cacheFile = new File(cacheDir, rawSha1 + ".zip");
    if (cacheFile.isFile()) {
      Files.copy(cacheFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return;
    }
    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
    }
    Map<String, byte[]> entries = readZip(rawFile);
    var mappings = Mappings.from(entries, rawSha1);
    Map<String, byte[]> obfuscated = new TreeMap<>();
    for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
      String sourceName = entry.getKey();
      String targetName = mappings.targetEntryName(sourceName);
      byte[] data = entry.getValue();
      if (sourceName.endsWith(".json")) {
        data = rewriteJson(sourceName, data, mappings);
      }
      obfuscated.put(targetName, data);
    }
    File tmpCache = new File(cacheDir, cacheFile.getName() + ".tmp");
    writeZip(obfuscated, tmpCache);
    Files.move(tmpCache.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    Files.copy(cacheFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  private static Map<String, byte[]> readZip(File zipFile) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipFile zip = new ZipFile(zipFile)) {
      var zipEntries = zip.entries();
      while (zipEntries.hasMoreElements()) {
        ZipEntry entry = zipEntries.nextElement();
        if (entry.isDirectory()) continue;
        try (var in = zip.getInputStream(entry)) {
          entries.put(entry.getName(), in.readAllBytes());
        }
      }
    }
    return entries;
  }

  private static void writeZip(Map<String, byte[]> entries, File outputFile) throws IOException {
    File parent = outputFile.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    try (ZipOutputStream zos =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        ZipEntry ze = new ZipEntry(entry.getKey());
        ze.setTime(0L);
        zos.putNextEntry(ze);
        zos.write(entry.getValue());
        zos.closeEntry();
      }
    }
  }

  private static byte[] rewriteJson(String name, byte[] data, Mappings mappings) {
    try {
      JsonElement json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
      boolean modelFile = isModelJson(name);
      boolean fontFile = isFontJson(name);
      boolean itemFile = isItemJson(name);
      boolean blockStateFile = isBlockStateJson(name);
      boolean atlasFile = isAtlasJson(name);
      if (!modelFile && !fontFile && !itemFile && !blockStateFile && !atlasFile) {
        return data;
      }
      JsonElement rewritten =
          rewriteElement(json, mappings, modelFile, fontFile, atlasFile, false, null, false);
      return GSON.toJson(rewritten).getBytes(StandardCharsets.UTF_8);
    } catch (RuntimeException ignored) {
      return data;
    }
  }

  private static JsonElement rewriteElement(
      JsonElement element,
      Mappings mappings,
      boolean modelFile,
      boolean fontFile,
      boolean atlasFile,
      boolean inTextures,
      String fieldName,
      boolean copyObject) {
    if (element == null || element.isJsonNull()) {
      return element;
    }
    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
      String value = element.getAsString();
      String rewritten = value;
      if (inTextures) {
        rewritten = mappings.rewriteTexture(value);
      } else if ("model".equals(fieldName) || "parent".equals(fieldName)) {
        rewritten = mappings.rewriteModel(value);
      } else if (atlasFile && "resource".equals(fieldName)) {
        rewritten = mappings.rewriteTexture(value);
      } else if (fontFile && "file".equals(fieldName)) {
        rewritten = mappings.rewriteTexture(value);
      }
      return rewritten.equals(value) ? element : new JsonPrimitive(rewritten);
    }
    if (element.isJsonArray()) {
      var source = element.getAsJsonArray();
      var target = new com.google.gson.JsonArray();
      for (JsonElement child : source) {
        target.add(
            rewriteElement(
                child, mappings, modelFile, fontFile, atlasFile, inTextures, null, true));
      }
      return target;
    }
    if (!element.isJsonObject()) {
      return element;
    }
    JsonObject source = element.getAsJsonObject();
    JsonObject target = copyObject ? new JsonObject() : source.deepCopy();
    if (!copyObject) {
      target = new JsonObject();
    }
    for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
      String childName = entry.getKey();
      boolean childInTextures = inTextures || (modelFile && "textures".equals(childName));
      target.add(
          childName,
          rewriteElement(
              entry.getValue(),
              mappings,
              modelFile,
              fontFile,
              atlasFile,
              childInTextures,
              childName,
              true));
    }
    return target;
  }

  private static boolean isModelJson(String name) {
    return name.startsWith("assets/") && name.contains("/models/") && name.endsWith(".json");
  }

  private static boolean isItemJson(String name) {
    return name.startsWith("assets/") && name.contains("/items/") && name.endsWith(".json");
  }

  private static boolean isFontJson(String name) {
    return name.startsWith("assets/") && name.contains("/font/") && name.endsWith(".json");
  }

  private static boolean isBlockStateJson(String name) {
    return name.startsWith("assets/") && name.contains("/blockstates/") && name.endsWith(".json");
  }

  private static boolean isAtlasJson(String name) {
    return name.startsWith("assets/minecraft/atlases/") && name.endsWith(".json");
  }

  private record Mappings(
      Map<String, String> entryTargets, Map<String, String> models, Map<String, String> textures) {
    static Mappings from(Map<String, byte[]> entries, String rawSha1) {
      Map<String, String> entryTargets = new LinkedHashMap<>();
      Map<String, String> models = new LinkedHashMap<>();
      Map<String, String> textures = new LinkedHashMap<>();
      for (String entry : entries.keySet()) {
        AssetPath model = AssetPath.model(entry);
        if (model != null && !"minecraft".equals(model.namespace())) {
          String obfuscated = uuid(rawSha1, "model", model.key());
          models.put(model.key(), model.namespace() + ":" + obfuscated);
          entryTargets.put(
              entry, "assets/" + model.namespace() + "/models/" + obfuscated + ".json");
          continue;
        }
        AssetPath texture = AssetPath.texture(entry);
        if (texture != null && !"minecraft".equals(texture.namespace())) {
          String obfuscated = uuid(rawSha1, "texture", texture.key());
          textures.put(texture.key(), texture.namespace() + ":" + obfuscated);
          entryTargets.put(
              entry, "assets/" + texture.namespace() + "/textures/" + obfuscated + ".png");
          continue;
        }
        AssetPath textureMeta = AssetPath.textureMeta(entry);
        if (textureMeta != null && !"minecraft".equals(textureMeta.namespace())) {
          String obfuscated = uuid(rawSha1, "texture", textureMeta.key());
          entryTargets.put(
              entry,
              "assets/" + textureMeta.namespace() + "/textures/" + obfuscated + ".png.mcmeta");
        }
      }
      return new Mappings(entryTargets, models, textures);
    }

    String targetEntryName(String sourceName) {
      return entryTargets.getOrDefault(sourceName, sourceName);
    }

    String rewriteModel(String value) {
      if (value == null || value.startsWith("#")) return value;
      String normalized = normalize(value, "minecraft");
      return models.getOrDefault(normalized, value);
    }

    String rewriteTexture(String value) {
      if (value == null || value.startsWith("#")) return value;
      boolean hasPng = value.endsWith(".png");
      String base = hasPng ? value.substring(0, value.length() - 4) : value;
      String normalized = normalize(base, "minecraft");
      String replacement = textures.get(normalized);
      if (replacement == null) {
        return value;
      }
      return hasPng ? replacement + ".png" : replacement;
    }

    private static String normalize(String value, String defaultNamespace) {
      return value.indexOf(':') >= 0 ? value : defaultNamespace + ":" + value;
    }

    private static String uuid(String rawSha1, String kind, String key) {
      return UUID.nameUUIDFromBytes(
              (CACHE_VERSION + '\0' + rawSha1 + '\0' + kind + '\0' + key)
                  .getBytes(StandardCharsets.UTF_8))
          .toString();
    }
  }

  private record AssetPath(String namespace, String path) {
    String key() {
      return namespace + ":" + path;
    }

    static AssetPath model(String entry) {
      return parse(entry, "/models/", ".json");
    }

    static AssetPath texture(String entry) {
      return parse(entry, "/textures/", ".png");
    }

    static AssetPath textureMeta(String entry) {
      return parse(entry, "/textures/", ".png.mcmeta");
    }

    private static AssetPath parse(String entry, String marker, String extension) {
      if (!entry.startsWith("assets/") || !entry.endsWith(extension)) return null;
      int markerStart = entry.indexOf(marker);
      if (markerStart < 0) return null;
      String namespace = entry.substring("assets/".length(), markerStart);
      String path =
          entry.substring(markerStart + marker.length(), entry.length() - extension.length());
      if (namespace.isEmpty() || path.isEmpty()) return null;
      return new AssetPath(namespace, path);
    }
  }
}
