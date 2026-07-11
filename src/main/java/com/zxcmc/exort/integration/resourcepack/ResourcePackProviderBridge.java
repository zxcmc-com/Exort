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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
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
  private static final int ARCHIVE_BUFFER_SIZE = 8192;
  static final int MAX_PACK_ARCHIVE_ENTRIES = 10_000;
  static final long MAX_PACK_ARCHIVE_BYTES = 64L * 1024L * 1024L;
  static final long MAX_PACK_UNCOMPRESSED_BYTES = 128L * 1024L * 1024L;
  private static final AtomicMover ATOMIC_MOVER = ResourcePackProviderBridge::moveAtomically;

  private ResourcePackProviderBridge() {}

  public static boolean isProviderInstalled(JavaPlugin plugin, ResourcePackHosting hosting) {
    String pluginName = pluginName(hosting);
    if (pluginName == null) {
      return false;
    }
    Plugin provider = plugin.getServer().getPluginManager().getPlugin(pluginName);
    return provider != null && provider.isEnabled();
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
    return copyNexoPack(pluginsDir, source, ATOMIC_MOVER);
  }

  static HandoffResult copyNexoPack(File pluginsDir, File source, AtomicMover mover) {
    return copyProviderPack(
        source,
        new File(new File(new File(pluginsDir, NEXO_PLUGIN), "pack"), "external_packs"),
        NEXO_PACK_NAME,
        "Nexo",
        mover);
  }

  static HandoffResult prepareNexoApiHandoff(File pluginsDir, File source) {
    if (!isRegularFile(source)) {
      return HandoffResult.error(null, "Exort raw resource pack is missing");
    }
    File fallbackTarget = nexoHandoffTarget(pluginsDir);
    try {
      validateResourcePack(source.toPath());
      rejectSymlinkOrUnexpectedTarget(fallbackTarget.toPath(), false);
      if (Files.isRegularFile(fallbackTarget.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        Files.delete(fallbackTarget.toPath());
      }
    } catch (IOException e) {
      return HandoffResult.error(
          fallbackTarget,
          "Cannot prepare Exort Nexo API handoff without replacing the previous handoff: "
              + e.getMessage());
    }
    return HandoffResult.success(source, "Nexo post-generate API: " + source.getPath());
  }

  static HandoffResult copyOraxenPack(File pluginsDir, File source) {
    return copyProviderPack(
        source,
        new File(new File(new File(pluginsDir, ORAXEN_PLUGIN), "pack"), "uploads"),
        ORAXEN_PACK_NAME,
        "Oraxen",
        ATOMIC_MOVER);
  }

  private static HandoffResult copyProviderPack(
      File source, File targetDir, String packName, String providerName, AtomicMover mover) {
    if (!isRegularFile(source)) {
      return HandoffResult.error(null, "Exort raw resource pack is missing");
    }
    Path sourcePath = source.toPath();
    Path targetDirectory = targetDir.toPath();
    Path target = targetDirectory.resolve(packName);
    Path candidate = null;
    Path backup = null;
    boolean keepBackup = false;
    try {
      validateResourcePack(sourcePath);
      Files.createDirectories(targetDirectory);
      rejectSymlinkOrUnexpectedTarget(target, false);
      if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
          && Files.mismatch(sourcePath, target) == -1L) {
        return HandoffResult.success(target.toFile());
      }

      candidate = Files.createTempFile(targetDirectory, "." + packName + ".candidate-", ".tmp");
      Files.copy(sourcePath, candidate, StandardCopyOption.REPLACE_EXISTING);
      validateResourcePack(candidate);

      if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
        backup = Files.createTempFile(targetDirectory, "." + packName + ".backup-", ".tmp");
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        if (Files.mismatch(target, backup) != -1L) {
          throw new IOException("Cannot verify the previous " + providerName + " pack backup");
        }
      }

      mover.move(candidate, target);
      candidate = null;
      deleteTemporary(backup, providerName + " previous pack backup");
      backup = null;
      return HandoffResult.success(target.toFile());
    } catch (IOException e) {
      RecoveryResult recovery = restoreFileBackup(target, backup);
      backup = recovery.remainingBackup();
      keepBackup = backup != null;
      String error = recovery.error() == null ? e.getMessage() : e.getMessage() + recovery.error();
      return HandoffResult.error(target.toFile(), error);
    } finally {
      deleteTemporary(candidate, providerName + " candidate pack");
      if (!keepBackup) {
        deleteTemporary(backup, providerName + " previous pack backup");
      }
    }
  }

  static HandoffResult syncItemsAdderPack(File pluginsDir, File rawPack) {
    return syncItemsAdderPack(pluginsDir, rawPack, ATOMIC_MOVER);
  }

  static HandoffResult syncItemsAdderPack(File pluginsDir, File rawPack, AtomicMover mover) {
    if (!isRegularFile(rawPack)) {
      return HandoffResult.error(null, "Exort raw resource pack is missing");
    }
    Path resourcepackDir =
        new File(new File(pluginsDir, ITEMS_ADDER_PLUGIN), "contents/exort")
            .toPath()
            .resolve("resourcepack");
    Path parent = resourcepackDir.getParent();
    Path candidate = null;
    Path backup = null;
    boolean keepBackup = false;
    try {
      Files.createDirectories(parent);
      rejectSymlinkOrUnexpectedTarget(resourcepackDir, true);
      candidate = Files.createTempDirectory(parent, ".exort-resourcepack-candidate-");
      extractAssets(rawPack.toPath(), candidate);
      prepareItemsAdderItemTextures(candidate.toFile());

      if (Files.exists(resourcepackDir, LinkOption.NOFOLLOW_LINKS)) {
        backup = uniqueSibling(parent, ".exort-resourcepack-backup-");
        mover.move(resourcepackDir, backup);
      }

      try {
        mover.move(candidate, resourcepackDir);
        candidate = null;
      } catch (IOException commitFailure) {
        RecoveryResult recovery = restoreDirectoryBackup(resourcepackDir, backup, mover);
        backup = recovery.remainingBackup();
        keepBackup = backup != null;
        String error =
            recovery.error() == null
                ? commitFailure.getMessage()
                : commitFailure.getMessage() + recovery.error();
        return HandoffResult.error(resourcepackDir.toFile(), error);
      }

      deleteTemporaryTree(backup, "ItemsAdder previous pack backup");
      backup = null;
      return HandoffResult.success(resourcepackDir.toFile());
    } catch (IOException e) {
      RecoveryResult recovery = restoreDirectoryBackup(resourcepackDir, backup, mover);
      backup = recovery.remainingBackup();
      keepBackup = backup != null;
      String error = recovery.error() == null ? e.getMessage() : e.getMessage() + recovery.error();
      return HandoffResult.error(resourcepackDir.toFile(), error);
    } finally {
      deleteTemporaryTree(candidate, "ItemsAdder candidate pack");
      if (!keepBackup) {
        deleteTemporaryTree(backup, "ItemsAdder previous pack backup");
      }
    }
  }

  private static void extractAssets(Path rawPack, Path resourcepackDir) throws IOException {
    int copied = processArchive(rawPack, resourcepackDir.toAbsolutePath().normalize());
    if (copied <= 0) {
      throw new IOException("Exort raw resource pack has no assets");
    }
  }

  private static void validateResourcePack(Path rawPack) throws IOException {
    if (processArchive(rawPack, null) <= 0) {
      throw new IOException("Exort raw resource pack has no assets");
    }
  }

  private static int processArchive(Path rawPack, Path extractionRoot) throws IOException {
    validateArchiveFile(rawPack);
    int copied = 0;
    int entryCount = 0;
    long totalBytes = 0L;
    byte[] buffer = new byte[ARCHIVE_BUFFER_SIZE];
    Set<String> names = new HashSet<>();
    try (ZipFile zipDirectory = new ZipFile(rawPack.toFile())) {
      if (zipDirectory.size() > MAX_PACK_ARCHIVE_ENTRIES) {
        throw new IOException(
            "Resource-pack archive contains more than " + MAX_PACK_ARCHIVE_ENTRIES + " entries");
      }
    }
    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(rawPack))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        entryCount++;
        if (entryCount > MAX_PACK_ARCHIVE_ENTRIES) {
          throw new IOException(
              "Resource-pack archive contains more than " + MAX_PACK_ARCHIVE_ENTRIES + " entries");
        }
        String name = entry.getName();
        validateArchiveEntryName(name);
        if (!names.add(name)) {
          throw new IOException("Duplicate resource-pack entry: " + name);
        }
        long declaredSize = entry.getSize();
        if (declaredSize > MAX_PACK_UNCOMPRESSED_BYTES - totalBytes) {
          throw new IOException("Resource-pack archive exceeds the uncompressed size limit");
        }

        boolean assetFile = !entry.isDirectory() && name.startsWith("assets/");
        Path target = null;
        if (assetFile && extractionRoot != null) {
          try {
            target = extractionRoot.resolve(name).normalize();
          } catch (InvalidPathException invalidPath) {
            throw new IOException("Unsafe resource-pack entry: " + name, invalidPath);
          }
          if (!target.startsWith(extractionRoot)) {
            throw new IOException("Unsafe resource-pack entry: " + name);
          }
          Files.createDirectories(target.getParent());
        }

        try (OutputStream output =
            target == null ? OutputStream.nullOutputStream() : Files.newOutputStream(target)) {
          int read;
          while ((read = zip.read(buffer)) != -1) {
            totalBytes += read;
            if (totalBytes > MAX_PACK_UNCOMPRESSED_BYTES) {
              throw new IOException("Resource-pack archive exceeds the uncompressed size limit");
            }
            output.write(buffer, 0, read);
          }
        }
        zip.closeEntry();
        if (assetFile) {
          copied++;
        }
      }
    }
    return copied;
  }

  private static void validateArchiveFile(Path rawPack) throws IOException {
    if (rawPack == null || !Files.isRegularFile(rawPack, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Exort raw resource pack is missing");
    }
    long archiveBytes = Files.size(rawPack);
    if (archiveBytes <= 0L) {
      throw new IOException("Exort raw resource pack is empty");
    }
    if (archiveBytes > MAX_PACK_ARCHIVE_BYTES) {
      throw new IOException(
          "Resource-pack archive exceeds the " + MAX_PACK_ARCHIVE_BYTES + " byte file limit");
    }
  }

  private static void validateArchiveEntryName(String name) throws IOException {
    if (name == null
        || name.isBlank()
        || name.startsWith("/")
        || name.startsWith("\\")
        || name.indexOf('\\') >= 0
        || name.indexOf(':') >= 0
        || name.indexOf('\0') >= 0) {
      throw new IOException("Unsafe resource-pack entry: " + name);
    }
    String[] segments = name.split("/", -1);
    for (int i = 0; i < segments.length; i++) {
      String segment = segments[i];
      boolean trailingDirectorySeparator = i == segments.length - 1 && segment.isEmpty();
      if ((!trailingDirectorySeparator && segment.isEmpty())
          || ".".equals(segment)
          || "..".equals(segment)) {
        throw new IOException("Unsafe resource-pack entry: " + name);
      }
    }
  }

  private static boolean isRegularFile(File file) {
    return file != null && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS);
  }

  private static void rejectSymlinkOrUnexpectedTarget(Path target, boolean expectDirectory)
      throws IOException {
    if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    if (Files.isSymbolicLink(target)) {
      throw new IOException("Refusing to replace symbolic-link provider target: " + target);
    }
    boolean expectedType =
        expectDirectory
            ? Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
            : Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS);
    if (!expectedType) {
      throw new IOException("Provider target has an unexpected file type: " + target);
    }
  }

  private static RecoveryResult restoreFileBackup(Path target, Path backup) {
    if (backup == null) {
      return RecoveryResult.complete();
    }
    try {
      if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
          && Files.mismatch(backup, target) == -1L) {
        deleteTemporary(backup, "unchanged previous provider pack backup");
        return RecoveryResult.complete();
      }
      moveAtomically(backup, target);
      return RecoveryResult.complete();
    } catch (IOException rollbackFailure) {
      return RecoveryResult.failed(
          backup,
          "; restoring the previous provider pack failed; backup retained at "
              + backup
              + ": "
              + rollbackFailure.getMessage());
    }
  }

  private static RecoveryResult restoreDirectoryBackup(
      Path target, Path backup, AtomicMover mover) {
    if (backup == null || !Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
      return RecoveryResult.complete();
    }
    try {
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        deleteRecursively(target);
      }
      mover.move(backup, target);
      return RecoveryResult.complete();
    } catch (IOException rollbackFailure) {
      return RecoveryResult.failed(
          backup,
          "; restoring the previous ItemsAdder pack failed; backup retained at "
              + backup
              + ": "
              + rollbackFailure.getMessage());
    }
  }

  private static Path uniqueSibling(Path parent, String prefix) throws IOException {
    for (int attempt = 0; attempt < 10; attempt++) {
      Path candidate = parent.resolve(prefix + UUID.randomUUID());
      if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
        return candidate;
      }
    }
    throw new IOException("Cannot allocate a unique provider-pack backup path in " + parent);
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      throw new IOException(
          "Atomic provider-pack move is not supported between " + source + " and " + target, e);
    }
  }

  private static void deleteTemporary(Path path, String description) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException cleanupFailure) {
      ExortLog.warn(
          "Failed to remove " + description + " " + path + ": " + cleanupFailure.getMessage());
    }
  }

  private static void deleteTemporaryTree(Path path, String description) {
    if (path == null) {
      return;
    }
    try {
      deleteRecursively(path);
    } catch (IOException cleanupFailure) {
      ExortLog.warn(
          "Failed to remove " + description + " " + path + ": " + cleanupFailure.getMessage());
    }
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
    if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
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

  @FunctionalInterface
  interface AtomicMover {
    void move(Path source, Path target) throws IOException;
  }

  private record RecoveryResult(Path remainingBackup, String error) {
    static RecoveryResult complete() {
      return new RecoveryResult(null, null);
    }

    static RecoveryResult failed(Path backup, String error) {
      return new RecoveryResult(backup, error);
    }
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
