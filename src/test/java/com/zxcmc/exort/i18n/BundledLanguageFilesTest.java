package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
  private static final Path CREATIVE_CATEGORY_REFS =
      Path.of("src/test/resources/minecraft-creative-category-refs.json");
  private static final Pattern JAVA_PLACEHOLDER = Pattern.compile("\\{\\d+}");

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
  void resourcePackLanguageKeysMatchRuntimeLanguageKeys() throws IOException {
    Set<String> runtimeKeys = readRuntimeLang("en_us").keySet();

    for (String locale : checkedLocales()) {
      JsonObject pack = readPackLang(locale);
      Set<String> packKeys = new LinkedHashSet<>();
      for (String key : pack.keySet()) {
        if (key.startsWith("exort.")) {
          packKeys.add(key.substring("exort.".length()));
        }
      }
      assertEquals(runtimeKeys, packKeys, locale + " pack/runtime key mismatch");
    }
  }

  @Test
  void creativeCategoryNamesMatchMinecraftLanguageFiles() throws IOException {
    JsonObject refs =
        JsonParser.parseString(Files.readString(CREATIVE_CATEGORY_REFS, StandardCharsets.UTF_8))
            .getAsJsonObject()
            .getAsJsonObject("locales");

    for (String locale : checkedLocales()) {
      JsonObject localeRefs = refs.getAsJsonObject(locale);
      Map<String, String> runtime = readRuntimeLang(locale);
      JsonObject pack = readPackLang(locale);
      for (String key : localeRefs.keySet()) {
        String expected = localeRefs.get(key).getAsString();
        assertEquals(expected, runtime.get(key), locale + " runtime mismatch for " + key);
        assertEquals(
            expected,
            pack.get("exort." + key).getAsString(),
            locale + " resource-pack mismatch for " + key);
      }
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

  private static JsonObject readPackLang(String locale) throws IOException {
    String json = Files.readString(PACK_LANG_DIR.resolve(locale + ".json"), StandardCharsets.UTF_8);
    return JsonParser.parseString(json).getAsJsonObject();
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
