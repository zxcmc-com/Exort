package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackExporterTest {
  @TempDir Path tempDir;

  @Test
  void bundledRuntimeLanguageFilesAreConvertedDuringExport() throws IOException {
    Path source = tempDir.resolve("source");
    Path langDir = source.resolve("lang");
    Files.createDirectories(langDir);
    Files.writeString(langDir.resolve("index.yml"), "languages:\n- en_us\n- rpr\n");
    Files.writeString(
        langDir.resolve("en_us.yml"),
        "message:\n  no_permission: No permission.\nitem:\n  terminal: Storage Terminal\n",
        StandardCharsets.UTF_8);
    Files.writeString(
        langDir.resolve("rpr.yml"),
        "message:\n  no_permission: Нѣтъ правъ.\nitem:\n  terminal: Терминалъ хранилища\n",
        StandardCharsets.UTF_8);

    PackExporter.Result result =
        PackExporter.exportPack(
            source, "pack/", tempDir.resolve("out").toFile(), Logger.getLogger("test"));

    assertTrue(result.available());
    assertEquals(
        "Storage Terminal",
        langValue(
            result.outputFile().toPath(), "assets/exort/lang/en_us.json", "exort.item.terminal"));
    assertEquals(
        "Терминалъ хранилища",
        langValue(
            result.outputFile().toPath(), "assets/exort/lang/rpr.json", "exort.item.terminal"));
    assertFalse(
        hasKey(
            result.outputFile().toPath(),
            "assets/exort/lang/en_us.json",
            "exort.message.no_permission"));
  }

  @Test
  void projectPackExportIncludesEveryPinnedLanguageFile() throws IOException {
    PackExporter.Result result =
        PackExporter.exportPack(
            Path.of("src/main/resources"),
            "pack/",
            tempDir.resolve("project-out").toFile(),
            Logger.getLogger("test"));

    assertTrue(result.available());
    try (ZipFile zip = new ZipFile(result.outputFile())) {
      for (String locale : pinnedLocales()) {
        assertTrue(
            zip.getEntry("assets/exort/lang/" + locale + ".json") != null,
            "missing exported Exort language file for " + locale);
      }
    }
  }

  private static String langValue(Path pack, String entryName, String key) throws IOException {
    try (ZipFile zip = new ZipFile(pack.toFile())) {
      var entry = zip.getEntry(entryName);
      String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
      return JsonParser.parseString(json).getAsJsonObject().get(key).getAsString();
    }
  }

  private static boolean hasKey(Path pack, String entryName, String key) throws IOException {
    try (ZipFile zip = new ZipFile(pack.toFile())) {
      var entry = zip.getEntry(entryName);
      String json = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
      return JsonParser.parseString(json).getAsJsonObject().has(key);
    }
  }

  private static Set<String> pinnedLocales() throws IOException {
    Set<String> locales = new LinkedHashSet<>();
    locales.add("en_us");
    Files.readAllLines(
            Path.of("src/test/resources/minecraft-lang-locales.txt"), StandardCharsets.UTF_8)
        .stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .forEach(locales::add);
    return locales;
  }
}
