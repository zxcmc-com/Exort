package com.zxcmc.exort.infra.resourcepack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ResourcePackLanguageFilesTest {
  private static final Path LANG_DIR = Path.of("src/main/resources/pack/assets/exort/lang");
  private static final Path LOCALES_FIXTURE =
      Path.of("src/test/resources/minecraft-lang-locales.txt");
  private static final Pattern MINECRAFT_PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?(?:s|%)");

  @Test
  void everyPinnedMinecraftLocaleHasBundledExortLanguageFile() throws IOException {
    for (String locale : pinnedLocales()) {
      assertTrue(
          Files.isRegularFile(LANG_DIR.resolve(locale + ".json")),
          "missing Exort resource-pack language file for " + locale);
    }
  }

  @Test
  void allBundledLanguageFilesMatchEnglishKeysAndPlaceholders() throws IOException {
    JsonObject english = readLang("en_us");
    Set<String> expectedKeys = english.keySet();

    for (String locale : checkedLocales()) {
      JsonObject localized = readLang(locale);
      assertEquals(expectedKeys, localized.keySet(), locale + " has a mismatched key set");
      for (String key : expectedKeys) {
        assertEquals(
            placeholders(english.get(key).getAsString()),
            placeholders(localized.get(key).getAsString()),
            locale + " has mismatched placeholders for " + key);
      }
    }
  }

  @Test
  void russianPreReformLocaleHasRealTranslationForStorageTerminal() throws IOException {
    String terminal = readLang("rpr").get("exort.item.terminal").getAsString();

    assertFalse(
        "Storage Terminal".equals(terminal),
        "rpr must be a real bundled translation, not an English fallback");
    assertTrue(terminal.contains("Терминалъ"), "unexpected rpr storage terminal translation");
  }

  private static Set<String> checkedLocales() throws IOException {
    Set<String> locales = new LinkedHashSet<>();
    locales.add("en_us");
    locales.addAll(pinnedLocales());
    return locales;
  }

  private static List<String> pinnedLocales() throws IOException {
    return Files.readAllLines(LOCALES_FIXTURE, StandardCharsets.UTF_8).stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }

  private static JsonObject readLang(String locale) throws IOException {
    Path path = LANG_DIR.resolve(locale + ".json");
    String json = Files.readString(path, StandardCharsets.UTF_8);
    return JsonParser.parseString(json).getAsJsonObject();
  }

  private static List<String> placeholders(String value) {
    List<String> result = new ArrayList<>();
    var matcher = MINECRAFT_PLACEHOLDER.matcher(value);
    while (matcher.find()) {
      result.add(matcher.group());
    }
    Collections.sort(result);
    return result;
  }
}
