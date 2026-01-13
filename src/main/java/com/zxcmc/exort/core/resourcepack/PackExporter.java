package com.zxcmc.exort.core.resourcepack;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.plugin.java.JavaPlugin;

public final class PackExporter {
  private static final String RESOURCE_ROOT = "pack/";
  private static final String OUTPUT_NAME = "exort.zip";

  private PackExporter() {}

  public static void exportPack(JavaPlugin plugin) {
    File packDir = new File(plugin.getDataFolder(), "pack");
    if (!packDir.exists()) {
      packDir.mkdirs();
    }
    File outFile = new File(packDir, OUTPUT_NAME);
    exportPack(plugin, RESOURCE_ROOT, outFile);
  }

  private static void exportPack(JavaPlugin plugin, String resourceRoot, File outFile) {
    if (!resourceRoot.endsWith("/")) resourceRoot = resourceRoot + "/";
    File dataDir = plugin.getDataFolder();
    if (!dataDir.exists()) {
      dataDir.mkdirs();
    }
    File tmpFile = new File(outFile.getParentFile(), outFile.getName() + ".tmp");
    int[] count = new int[] {0};
    try (ZipOutputStream zos =
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
      Path source = getCodeSourcePath(plugin);
      if (source == null) {
        plugin
            .getLogger()
            .warning("Cannot resolve plugin code source; resource pack export skipped.");
        return;
      }
      if (Files.isDirectory(source)) {
        Path root = source.resolve(resourceRoot);
        if (Files.exists(root)) {
          zipDirectory(root, zos, count);
        }
      } else {
        try (JarFile jar = new JarFile(source.toFile())) {
          Enumeration<JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(resourceRoot) || entry.isDirectory()) continue;
            String relative = name.substring(resourceRoot.length());
            if (relative.isEmpty()) continue;
            ZipEntry ze = new ZipEntry(relative);
            ze.setTime(entry.getTime());
            zos.putNextEntry(ze);
            try (InputStream in = jar.getInputStream(entry)) {
              in.transferTo(zos);
            }
            zos.closeEntry();
            count[0]++;
          }
        }
      }
    } catch (IOException e) {
      plugin.getLogger().log(Level.WARNING, "Failed to generate " + OUTPUT_NAME, e);
      return;
    }
    try {
      Files.move(tmpFile.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      plugin.getLogger().log(Level.WARNING, "Failed to finalize " + OUTPUT_NAME, e);
      return;
    }
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

  private static void zipDirectory(Path root, ZipOutputStream zos, int[] count) throws IOException {
    if (!Files.exists(root)) return;
    try (var walk = Files.walk(root)) {
      walk.filter(Files::isRegularFile)
          .forEach(
              path -> {
                String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                if (relative.isEmpty()) return;
                try (InputStream in = Files.newInputStream(path)) {
                  ZipEntry ze = new ZipEntry(relative);
                  zos.putNextEntry(ze);
                  in.transferTo(zos);
                  zos.closeEntry();
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
  }
}
