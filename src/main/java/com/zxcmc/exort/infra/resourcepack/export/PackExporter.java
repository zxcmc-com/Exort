package com.zxcmc.exort.infra.resourcepack.export;

import com.zxcmc.exort.i18n.LocalizationFiles;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.plugin.java.JavaPlugin;

public final class PackExporter {
  private static final String RESOURCE_ROOT = "pack/";
  private static final String RAW_OUTPUT_NAME = "exort.raw.zip";
  private static final String OUTPUT_NAME = "exort.zip";
  private static final String PACK_META_NAME = "pack.mcmeta";
  private static final String BLOCKS_ATLAS_NAME = "assets/minecraft/atlases/blocks.json";
  private static final String DEFAULT_PACK_META =
      """
      {
        "pack": {
          "pack_format": 64,
          "supported_formats": [64, 199],
          "min_format": 64,
          "max_format": 199,
          "description": "Exort Resource Pack"
        }
      }
      """;

  private PackExporter() {}

  public static Result exportPack(JavaPlugin plugin) {
    File packDir = new File(plugin.getDataFolder(), "pack");
    Path source = getCodeSourcePath(plugin);
    if (source == null) {
      plugin
          .getLogger()
          .warning("Cannot resolve plugin code source; resource pack export skipped.");
      return Result.empty(new File(packDir, RAW_OUTPUT_NAME), new File(packDir, OUTPUT_NAME));
    }
    return exportPack(source, RESOURCE_ROOT, packDir, plugin.getLogger());
  }

  static Result exportPack(
      Path source, String resourceRoot, File packDir, java.util.logging.Logger logger) {
    if (!packDir.exists()) {
      packDir.mkdirs();
    }
    File rawFile = new File(packDir, RAW_OUTPUT_NAME);
    File outputFile = new File(packDir, OUTPUT_NAME);
    int entryCount = exportRawPack(logger, source, resourceRoot, rawFile);
    if (entryCount <= 0) {
      return Result.empty(rawFile, outputFile);
    }
    try {
      Files.copy(rawFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return new Result(
          rawFile, outputFile, sha1Hex(rawFile.toPath()), sha1Hex(outputFile.toPath()), entryCount);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to finalize " + OUTPUT_NAME, e);
      return Result.empty(rawFile, outputFile);
    }
  }

  private static int exportRawPack(
      java.util.logging.Logger logger, Path source, String resourceRoot, File outFile) {
    if (!resourceRoot.endsWith("/")) resourceRoot = resourceRoot + "/";
    File tmpFile = new File(outFile.getParentFile(), outFile.getName() + ".tmp");
    Map<String, byte[]> entries = new TreeMap<>();
    int count = 0;
    try (ZipOutputStream zos =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
      if (Files.isDirectory(source)) {
        Path root = source.resolve(resourceRoot);
        if (Files.exists(root)) {
          count += collectDirectory(root, entries);
        }
      } else {
        try (JarFile jar = new JarFile(source.toFile())) {
          Enumeration<JarEntry> jarEntries = jar.entries();
          while (jarEntries.hasMoreElements()) {
            JarEntry entry = jarEntries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(resourceRoot) || entry.isDirectory()) continue;
            String relative = name.substring(resourceRoot.length());
            if (relative.isEmpty()) continue;
            try (InputStream in = jar.getInputStream(entry)) {
              entries.put(relative, in.readAllBytes());
            }
            count++;
          }
        }
      }
      count += addGeneratedLanguageEntries(logger, source, entries);
      count += BreakingModelGenerator.addGeneratedEntries(logger, entries);
      entries.putIfAbsent(PACK_META_NAME, DEFAULT_PACK_META.getBytes(StandardCharsets.UTF_8));
      entries.putIfAbsent(BLOCKS_ATLAS_NAME, generatedBlocksAtlas(entries));
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        ZipEntry ze = new ZipEntry(entry.getKey());
        ze.setTime(0L);
        zos.putNextEntry(ze);
        zos.write(entry.getValue());
        zos.closeEntry();
      }
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to generate " + RAW_OUTPUT_NAME, e);
      return 0;
    }
    try {
      Files.move(tmpFile.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to finalize " + RAW_OUTPUT_NAME, e);
      return 0;
    }
    return count;
  }

  private static int addGeneratedLanguageEntries(
      java.util.logging.Logger logger, Path source, Map<String, byte[]> entries) {
    byte[] index = readSourceResource(source, LocalizationFiles.LANG_INDEX);
    if (index == null) {
      logger.warning("Bundled language index is missing; resource-pack translations skipped.");
      return 0;
    }
    int added = 0;
    try {
      for (String locale : LocalizationFiles.readLanguageIndex(new ByteArrayInputStream(index))) {
        byte[] language =
            readSourceResource(
                source, LocalizationFiles.LANG_DIR + locale + LocalizationFiles.LANG_EXT);
        if (language == null) {
          logger.warning("Bundled language file is missing: " + locale);
          continue;
        }
        Map<String, String> flat =
            LocalizationFiles.readFlatLanguage(new ByteArrayInputStream(language));
        String target = "assets/exort/lang/" + locale + ".json";
        if (!entries.containsKey(target)) {
          added++;
        }
        entries.put(target, LocalizationFiles.clientResourcePackJson(flat));
      }
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Failed to generate resource-pack translations.", e);
    }
    return added;
  }

  private static byte[] readSourceResource(Path source, String name) {
    try {
      if (Files.isDirectory(source)) {
        Path path = source.resolve(name);
        return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
      }
      try (JarFile jar = new JarFile(source.toFile())) {
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null || entry.isDirectory()) {
          return null;
        }
        try (InputStream in = jar.getInputStream(entry)) {
          return in.readAllBytes();
        }
      }
    } catch (IOException e) {
      return null;
    }
  }

  private static byte[] generatedBlocksAtlas(Map<String, byte[]> entries) {
    StringBuilder json = new StringBuilder();
    json.append("{\"sources\":[");
    boolean first = true;
    for (String entry : entries.keySet()) {
      TextureAsset texture = TextureAsset.fromEntry(entry);
      if (texture == null || "minecraft".equals(texture.namespace())) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"type\":\"single\",\"resource\":\"")
          .append(texture.namespace())
          .append(':')
          .append(texture.path())
          .append("\"}");
    }
    json.append("]}");
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static Path getCodeSourcePath(JavaPlugin plugin) {
    try {
      var url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
      if (url == null) return null;
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      return null;
    }
  }

  private static int collectDirectory(Path root, Map<String, byte[]> entries) throws IOException {
    if (!Files.exists(root)) return 0;
    int[] count = new int[] {0};
    try (var walk = Files.walk(root)) {
      walk.filter(Files::isRegularFile)
          .forEach(
              path -> {
                String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                if (relative.isEmpty()) return;
                try {
                  entries.put(relative, Files.readAllBytes(path));
                  count[0]++;
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof IOException io) {
        throw io;
      }
      throw e;
    }
    return count[0];
  }

  static String sha1Hex(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      try (InputStream in = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 digest is not available", e);
    }
  }

  public record Result(
      File rawFile, File outputFile, String rawSha1, String outputSha1, int entryCount) {
    static Result empty(File rawFile, File outputFile) {
      return new Result(rawFile, outputFile, "", "", 0);
    }

    public boolean available() {
      return entryCount > 0 && outputFile.isFile() && !outputSha1.isEmpty();
    }
  }

  private record TextureAsset(String namespace, String path) {
    static TextureAsset fromEntry(String entry) {
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
      return new TextureAsset(namespace, path);
    }
  }
}
