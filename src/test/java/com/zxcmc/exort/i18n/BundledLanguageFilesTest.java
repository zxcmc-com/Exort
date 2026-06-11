package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BundledLanguageFilesTest {
  private static final Path RUNTIME_LANG_DIR = Path.of("src/main/resources/lang");
  private static final Path PACK_LANG_DIR = Path.of("src/main/resources/pack/assets/exort/lang");
  private static final Path LOCALES_FIXTURE =
      Path.of("src/test/resources/minecraft-lang-locales.txt");
  private static final Pattern JAVA_PLACEHOLDER = Pattern.compile("\\{\\d+}");
  private static final Set<String> CRITICAL_PLAYER_FACING_KEYS =
      Set.of(
          "item.storage_core",
          "item.terminal",
          "item.crafting_terminal",
          "item.wire",
          "item.monitor",
          "item.import_bus",
          "item.export_bus",
          "item.wireless_terminal",
          "gui.give.title",
          "gui.bus.import_title",
          "gui.bus.export_title",
          "gui.bus.info.exort_storage",
          "message.storage_load_failed",
          "message.storage_loading",
          "message.operation_failed");

  @Test
  void everyPinnedMinecraftLocaleHasBundledRuntimeLanguageFile() throws IOException {
    for (String locale : checkedLocales()) {
      assertTrue(
          Files.isRegularFile(RUNTIME_LANG_DIR.resolve(locale + ".yml")),
          "missing bundled runtime language file for " + locale);
    }
  }

  @Test
  void bundledLanguageIndexMatchesPinnedMinecraftLocales() throws IOException {
    YamlConfiguration cfg =
        YamlConfiguration.loadConfiguration(RUNTIME_LANG_DIR.resolve("index.yml").toFile());

    assertEquals(checkedLocales(), new LinkedHashSet<>(cfg.getStringList("languages")));
  }

  @Test
  void bundledLanguageIndexMatchesRuntimeLanguageFiles() throws IOException {
    Set<String> files = new LinkedHashSet<>();
    try (var paths = Files.list(RUNTIME_LANG_DIR)) {
      paths
          .filter(path -> path.getFileName().toString().endsWith(".yml"))
          .map(path -> path.getFileName().toString())
          .filter(name -> !name.equals("index.yml"))
          .map(name -> name.substring(0, name.length() - ".yml".length()))
          .sorted()
          .forEach(files::add);
    }

    Set<String> indexed = new LinkedHashSet<>(checkedLocales());
    assertEquals(indexed.stream().sorted().toList(), files.stream().sorted().toList());
  }

  @Test
  void allBundledRuntimeLanguageFilesMatchEnglishKeysAndPlaceholders() throws IOException {
    Map<String, String> english = readRuntimeLang("en_us");
    Set<String> expectedKeys = english.keySet();

    for (String locale : checkedLocales()) {
      Map<String, String> localized = readRuntimeLang(locale);
      assertEquals(expectedKeys, localized.keySet(), locale + " has a mismatched key set");
      for (String key : expectedKeys) {
        assertEquals(
            placeholders(english.get(key)),
            placeholders(localized.get(key)),
            locale + " has mismatched placeholders for " + key);
      }
    }
  }

  @Test
  void russianCriticalPlayerFacingKeysDoNotUseEnglishFallbacks() {
    Map<String, String> english = readRuntimeLang("en_us");
    Map<String, String> russian = readRuntimeLang("ru_ru");

    for (String key : CRITICAL_PLAYER_FACING_KEYS) {
      assertTrue(russian.containsKey(key), "ru_ru missing critical key " + key);
      assertEquals(
          placeholders(english.get(key)),
          placeholders(russian.get(key)),
          "ru_ru critical key placeholders changed for " + key);
      assertTrue(
          !english.get(key).equals(russian.get(key)),
          "ru_ru critical key still equals English fallback: " + key);
    }
  }

  @Test
  void resourcePackLanguageFilesAreGeneratedAtExportTime() throws IOException {
    if (!Files.exists(PACK_LANG_DIR)) {
      return;
    }
    try (var files = Files.walk(PACK_LANG_DIR)) {
      assertEquals(
          0,
          files.filter(path -> path.getFileName().toString().endsWith(".json")).count(),
          "resource-pack language JSON must not be committed");
    }
  }

  private static Set<String> checkedLocales() throws IOException {
    Set<String> locales = new LinkedHashSet<>();
    locales.add("en_us");
    Files.readAllLines(LOCALES_FIXTURE, StandardCharsets.UTF_8).stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .forEach(locales::add);
    return locales;
  }

  private static Map<String, String> readRuntimeLang(String locale) {
    YamlConfiguration cfg =
        YamlConfiguration.loadConfiguration(RUNTIME_LANG_DIR.resolve(locale + ".yml").toFile());
    Map<String, String> entries = new TreeMap<>();
    for (String key : cfg.getKeys(true)) {
      if (cfg.isConfigurationSection(key)) {
        continue;
      }
      String value = cfg.getString(key);
      if (value != null) {
        entries.put(key, value);
      }
    }
    return entries;
  }

  private static List<String> placeholders(String value) {
    List<String> result = new ArrayList<>();
    var matcher = JAVA_PLACEHOLDER.matcher(value);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    Collections.sort(result);
    return result;
  }
}
