package com.zxcmc.exort.integration.resourcepack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackHosting;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourcePackProviderBridge {
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
  private static final String NEXO_PLUGIN = "Nexo";
  private static final String ITEMS_ADDER_PLUGIN = "ItemsAdder";
  private static final String ORAXEN_PLUGIN = "Oraxen";
  private static final String NEXO_PACK_NAME = "zxcmc_exort.zip";
  private static final String ORAXEN_PACK_NAME = "zxcmc_exort.zip";
  private static final String EXORT_NAMESPACE = "exort";
  private static final String ITEMS_ADDER_ITEM_TEXTURE_PREFIX = "item/ia_";

  private ResourcePackProviderBridge() {}

  public static boolean isProviderInstalled(JavaPlugin plugin, ResourcePackHosting hosting) {
    String pluginName = pluginName(hosting);
    if (pluginName == null) {
      return false;
    }
    Plugin provider = plugin.getServer().getPluginManager().getPlugin(pluginName);
    if (provider != null) {
      return true;
    }
    return isPluginJarInstalled(pluginsDir(plugin), pluginName);
  }

  public static HandoffResult handoff(
      JavaPlugin plugin, ResourcePackHosting hosting, File rawPack) {
    String pluginName = pluginName(hosting);
    if (pluginName == null) {
      return HandoffResult.error(null, "Unsupported resource-pack provider: " + hosting);
    }
    if (!isProviderInstalled(plugin, hosting)) {
      return HandoffResult.error(null, pluginName + " is not installed");
    }
    File pluginsDir = pluginsDir(plugin);
    if (hosting == ResourcePackHosting.NEXO) {
      return copyNexoPack(pluginsDir, rawPack);
    }
    if (hosting == ResourcePackHosting.ITEMSADDER) {
      return syncItemsAdderPack(pluginsDir, rawPack);
    }
    if (hosting == ResourcePackHosting.ORAXEN) {
      return copyOraxenPack(pluginsDir, rawPack);
    }
    return HandoffResult.error(null, "Unsupported resource-pack provider: " + hosting);
  }

  public static HandoffResult prepareNexoApiHandoff(JavaPlugin plugin, File rawPack) {
    return prepareNexoApiHandoff(pluginsDir(plugin), rawPack);
  }

  public static void removeOtherProviderHandoffs(JavaPlugin plugin, ResourcePackHosting active) {
    if (active != ResourcePackHosting.NEXO) {
      removeNexoHandoff(pluginsDir(plugin));
    }
    if (active != ResourcePackHosting.ITEMSADDER) {
      removeItemsAdderHandoff(pluginsDir(plugin));
    }
    if (active != ResourcePackHosting.ORAXEN) {
      removeOraxenHandoff(pluginsDir(plugin));
    }
  }

  public static void removeAll(JavaPlugin plugin) {
    removeAll(pluginsDir(plugin));
  }

  static void removeAll(File pluginsDir) {
    removeNexoHandoff(pluginsDir);
    removeItemsAdderHandoff(pluginsDir);
    removeOraxenHandoff(pluginsDir);
  }

  static boolean isPluginJarInstalled(File pluginsDir, String pluginName) {
    if (pluginsDir == null || pluginName == null || pluginName.isBlank()) {
      return false;
    }
    File[] jars =
        pluginsDir.listFiles(
            file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (jars == null) {
      return false;
    }
    for (File jar : jars) {
      if (pluginName.equals(readPluginName(jar))) {
        return true;
      }
    }
    return false;
  }

  static HandoffResult copyNexoPack(File pluginsDir, File source) {
    return copyProviderPack(
        source,
        new File(new File(new File(pluginsDir, NEXO_PLUGIN), "pack"), "external_packs"),
        NEXO_PACK_NAME,
        "Nexo external_packs",
        "Nexo");
  }

  static HandoffResult prepareNexoApiHandoff(File pluginsDir, File source) {
    if (source == null || !source.isFile()) {
      return HandoffResult.error(null, "Exort raw resource pack is missing");
    }
    File fallbackTarget = nexoHandoffTarget(pluginsDir);
    if (fallbackTarget.isFile()) {
      try {
        Files.delete(fallbackTarget.toPath());
      } catch (IOException e) {
        return HandoffResult.error(
            fallbackTarget,
            "Cannot remove existing Exort Nexo external_packs handoff before API handoff: "
                + e.getMessage());
      }
    }
    return HandoffResult.success(source, "Nexo post-generate API: " + source.getPath());
  }

  static HandoffResult copyOraxenPack(File pluginsDir, File source) {
    return copyProviderPack(
        source,
        new File(new File(new File(pluginsDir, ORAXEN_PLUGIN), "pack"), "uploads"),
        ORAXEN_PACK_NAME,
        "Oraxen uploads",
        "Oraxen");
  }

  private static HandoffResult copyProviderPack(
      File source, File targetDir, String packName, String directoryName, String providerName) {
    if (source == null || !source.isFile()) {
      return HandoffResult.error(null, "Exort raw resource pack is missing");
    }
    if (!targetDir.exists() && !targetDir.mkdirs()) {
      return HandoffResult.error(targetDir, "Cannot create " + directoryName + " directory");
    }
    File target = new File(targetDir, packName);
    try {
      if (target.isFile() && Files.mismatch(source.toPath(), target.toPath()) == -1L) {
        return HandoffResult.success(target);
      }
      Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return HandoffResult.success(target);
    } catch (IOException e) {
      ExortLog.warn(
          "Failed to copy Exort resource pack to " + providerName + ": " + e.getMessage());
      return HandoffResult.error(target, e.getMessage());
    }
  }

  static HandoffResult syncItemsAdderPack(File pluginsDir, File rawPack) {
    if (rawPack == null || !rawPack.isFile()) {
      return HandoffResult.error(null, "Exort raw resource pack is missing");
    }
    File resourcepackDir =
        new File(
            new File(new File(pluginsDir, ITEMS_ADDER_PLUGIN), "contents/exort"), "resourcepack");
    try {
      deleteRecursively(resourcepackDir.toPath());
      Files.createDirectories(resourcepackDir.toPath());
      int copied = extractAssets(rawPack, resourcepackDir);
      if (copied <= 0) {
        return HandoffResult.error(resourcepackDir, "Exort raw resource pack has no assets");
      }
      prepareItemsAdderItemTextures(resourcepackDir);
      return HandoffResult.success(resourcepackDir);
    } catch (IOException e) {
      ExortLog.warn("Failed to sync Exort resource pack to ItemsAdder: " + e.getMessage());
      return HandoffResult.error(resourcepackDir, e.getMessage());
    }
  }

  private static int extractAssets(File rawPack, File resourcepackDir) throws IOException {
    Path root = resourcepackDir.toPath().toAbsolutePath().normalize();
    int copied = 0;
    try (ZipFile zip = new ZipFile(rawPack)) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (entry.isDirectory() || !name.startsWith("assets/")) {
          continue;
        }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
          throw new IOException("Unsafe resource-pack entry: " + name);
        }
        Files.createDirectories(target.getParent());
        try (var in = zip.getInputStream(entry)) {
          Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        copied++;
      }
    }
    return copied;
  }

  private static void prepareItemsAdderItemTextures(File resourcepackDir) throws IOException {
    Path root = resourcepackDir.toPath().toAbsolutePath().normalize();
    Path modelsRoot = root.resolve("assets/exort/models");
    if (!Files.isDirectory(modelsRoot)) {
      return;
    }
    Map<String, String> aliases = new LinkedHashMap<>();
    try (var walk = Files.walk(modelsRoot)) {
      for (Path model :
          walk.filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .sorted()
              .toList()) {
        rewriteItemsAdderModelTextures(root, model, aliases);
      }
    }
    if (aliases.isEmpty()) {
      return;
    }
    for (Map.Entry<String, String> alias : aliases.entrySet()) {
      copyItemsAdderTextureAlias(root, alias.getKey(), alias.getValue());
    }
    writeBlocksAtlas(root);
  }

  private static void rewriteItemsAdderModelTextures(
      Path root, Path model, Map<String, String> aliases) throws IOException {
    String source = Files.readString(model, StandardCharsets.UTF_8);
    JsonElement json;
    try {
      json = JsonParser.parseString(source);
    } catch (RuntimeException ignored) {
      return;
    }
    JsonElement rewritten = rewriteTextureValues(root, json, false, aliases);
    String target = GSON.toJson(rewritten);
    if (!target.equals(source)) {
      Files.writeString(model, target, StandardCharsets.UTF_8);
    }
  }

  private static JsonElement rewriteTextureValues(
      Path root, JsonElement element, boolean inTextures, Map<String, String> aliases) {
    if (element == null || element.isJsonNull()) {
      return element;
    }
    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
      String value = element.getAsString();
      String alias = inTextures ? itemsAdderTextureAlias(root, value, aliases) : value;
      return alias.equals(value) ? element : new JsonPrimitive(alias);
    }
    if (element.isJsonArray()) {
      JsonArray target = new JsonArray();
      for (JsonElement child : element.getAsJsonArray()) {
        target.add(rewriteTextureValues(root, child, inTextures, aliases));
      }
      return target;
    }
    if (!element.isJsonObject()) {
      return element;
    }
    JsonObject target = new JsonObject();
    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
      boolean childInTextures = inTextures || "textures".equals(entry.getKey());
      target.add(
          entry.getKey(), rewriteTextureValues(root, entry.getValue(), childInTextures, aliases));
    }
    return target;
  }

  private static String itemsAdderTextureAlias(
      Path root, String texture, Map<String, String> aliases) {
    if (texture == null || texture.startsWith("#")) {
      return texture;
    }
    int namespaceEnd = texture.indexOf(':');
    if (namespaceEnd <= 0 || !EXORT_NAMESPACE.equals(texture.substring(0, namespaceEnd))) {
      return texture;
    }
    String path = texture.substring(namespaceEnd + 1);
    if (path.startsWith(ITEMS_ADDER_ITEM_TEXTURE_PREFIX)) {
      return texture;
    }
    Path source = texturePath(root, EXORT_NAMESPACE, path);
    if (!source.startsWith(root) || !Files.isRegularFile(source)) {
      return texture;
    }
    String aliasPath = ITEMS_ADDER_ITEM_TEXTURE_PREFIX + sanitizeTextureAlias(path);
    String alias = EXORT_NAMESPACE + ":" + aliasPath;
    aliases.put(texture, alias);
    return alias;
  }

  private static void copyItemsAdderTextureAlias(Path root, String sourceKey, String aliasKey)
      throws IOException {
    TextureKey source = TextureKey.parse(sourceKey);
    TextureKey alias = TextureKey.parse(aliasKey);
    if (source == null || alias == null) {
      return;
    }
    Path sourceFile = texturePath(root, source.namespace(), source.path());
    Path aliasFile = texturePath(root, alias.namespace(), alias.path());
    if (!aliasFile.startsWith(root)) {
      throw new IOException("Unsafe ItemsAdder texture alias: " + aliasKey);
    }
    Files.createDirectories(aliasFile.getParent());
    Files.copy(sourceFile, aliasFile, StandardCopyOption.REPLACE_EXISTING);
  }

  private static void writeBlocksAtlas(Path root) throws IOException {
    Path assetsRoot = root.resolve("assets");
    if (!Files.isDirectory(assetsRoot)) {
      return;
    }
    List<String> textures = new ArrayList<>();
    try (var walk = Files.walk(assetsRoot)) {
      for (Path file :
          walk.filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".png"))
              .sorted()
              .toList()) {
        TextureKey texture = textureKey(root, file);
        if (texture == null || "minecraft".equals(texture.namespace())) {
          continue;
        }
        textures.add(texture.key());
      }
    }
    JsonObject atlas = new JsonObject();
    JsonArray sources = new JsonArray();
    for (String texture : textures) {
      JsonObject source = new JsonObject();
      source.addProperty("type", "single");
      source.addProperty("resource", texture);
      sources.add(source);
    }
    atlas.add("sources", sources);
    Path atlasFile = root.resolve("assets/minecraft/atlases/blocks.json");
    Files.createDirectories(atlasFile.getParent());
    Files.writeString(atlasFile, GSON.toJson(atlas), StandardCharsets.UTF_8);
  }

  private static Path texturePath(Path root, String namespace, String path) {
    return root.resolve("assets/" + namespace + "/textures/" + path + ".png").normalize();
  }

  private static TextureKey textureKey(Path root, Path file) {
    Path relative = root.relativize(file).normalize();
    String entry = relative.toString().replace(File.separatorChar, '/');
    if (!entry.startsWith("assets/") || !entry.endsWith(".png")) {
      return null;
    }
    int texturesStart = entry.indexOf("/textures/");
    if (texturesStart < 0) {
      return null;
    }
    String namespace = entry.substring("assets/".length(), texturesStart);
    String path = entry.substring(texturesStart + "/textures/".length(), entry.length() - 4);
    if (namespace.isEmpty() || path.isEmpty()) {
      return null;
    }
    return new TextureKey(namespace, path);
  }

  private static String sanitizeTextureAlias(String path) {
    StringBuilder out = new StringBuilder(path.length());
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
        out.append(c);
      } else {
        out.append('_');
      }
    }
    return out.toString();
  }

  private static void removeNexoHandoff(File pluginsDir) {
    File target = nexoHandoffTarget(pluginsDir);
    if (!target.isFile()) {
      return;
    }
    try {
      Files.delete(target.toPath());
    } catch (IOException e) {
      ExortLog.warn("Failed to remove Exort resource pack from Nexo: " + e.getMessage());
    }
  }

  private static File nexoHandoffTarget(File pluginsDir) {
    return new File(
        new File(new File(pluginsDir, NEXO_PLUGIN), "pack/external_packs"), NEXO_PACK_NAME);
  }

  private static void removeItemsAdderHandoff(File pluginsDir) {
    File target =
        new File(
            new File(new File(pluginsDir, ITEMS_ADDER_PLUGIN), "contents/exort"), "resourcepack");
    try {
      deleteRecursively(target.toPath());
    } catch (IOException e) {
      ExortLog.warn("Failed to remove Exort resource pack from ItemsAdder: " + e.getMessage());
    }
  }

  private static void removeOraxenHandoff(File pluginsDir) {
    File target =
        new File(new File(new File(pluginsDir, ORAXEN_PLUGIN), "pack/uploads"), ORAXEN_PACK_NAME);
    if (!target.isFile()) {
      return;
    }
    try {
      Files.delete(target.toPath());
    } catch (IOException e) {
      ExortLog.warn("Failed to remove Exort resource pack from Oraxen: " + e.getMessage());
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (var walk = Files.walk(path)) {
      for (Path child : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
        Files.deleteIfExists(child);
      }
    }
  }

  private static String readPluginName(File jar) {
    try (JarFile jarFile = new JarFile(jar)) {
      String name = readPluginName(jarFile, "paper-plugin.yml");
      if (name != null) {
        return name;
      }
      return readPluginName(jarFile, "plugin.yml");
    } catch (IOException ignored) {
      return null;
    }
  }

  private static String readPluginName(JarFile jarFile, String metadataPath) throws IOException {
    ZipEntry entry = jarFile.getEntry(metadataPath);
    if (entry == null) {
      return null;
    }
    try (var in = jarFile.getInputStream(entry)) {
      String metadata = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      for (String line : metadata.split("\\R")) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("name:")) {
          continue;
        }
        String value = trimmed.substring("name:".length()).trim();
        int comment = value.indexOf(" #");
        if (comment >= 0) {
          value = value.substring(0, comment).trim();
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
          value = value.substring(1, value.length() - 1);
        }
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }

  private static String pluginName(ResourcePackHosting hosting) {
    return switch (hosting) {
      case NEXO -> NEXO_PLUGIN;
      case ITEMSADDER -> ITEMS_ADDER_PLUGIN;
      case ORAXEN -> ORAXEN_PLUGIN;
      default -> null;
    };
  }

  private static File pluginsDir(JavaPlugin plugin) {
    File dataFolder = plugin.getDataFolder();
    File parent = dataFolder == null ? null : dataFolder.getParentFile();
    return parent == null ? new File("plugins") : parent;
  }

  private record TextureKey(String namespace, String path) {
    String key() {
      return namespace + ":" + path;
    }

    static TextureKey parse(String key) {
      if (key == null) {
        return null;
      }
      int namespaceEnd = key.indexOf(':');
      if (namespaceEnd <= 0 || namespaceEnd == key.length() - 1) {
        return null;
      }
      return new TextureKey(key.substring(0, namespaceEnd), key.substring(namespaceEnd + 1));
    }
  }

  public record HandoffResult(boolean success, File target, String displayTarget, String error) {
    public HandoffResult(boolean success, File target, String error) {
      this(success, target, null, error);
    }

    public String targetPath() {
      if (displayTarget != null && !displayTarget.isBlank()) {
        return displayTarget;
      }
      return target == null ? null : target.getPath();
    }

    static HandoffResult success(File target) {
      return new HandoffResult(true, target, null, null);
    }

    static HandoffResult success(File target, String displayTarget) {
      return new HandoffResult(true, target, displayTarget, null);
    }

    static HandoffResult error(File target, String error) {
      return new HandoffResult(false, target, null, error);
    }
  }
}
