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
  private static final Set<String> PLAIN_ENGLISH_LOCALES =
      Set.of("en_us", "en_au", "en_ca", "en_gb", "en_nz");
  private static final Set<String> RECENT_TRANSLATION_KEYS =
      Set.of(
          "message.chunk_loader_initializing",
          "message.chunk_loader_limit_reached",
          "message.wireless.transmitter_inactive",
          "message.wireless.no_terminal_in_hand",
          "message.wireless.transmitter_slot_occupied",
          "message.wireless.transmitter_terminal_only",
          "item.transmitter",
          "gui.transmitter.title",
          "gui.transmitter.status.item",
          "gui.transmitter.status.active",
          "gui.transmitter.status.storage",
          "gui.transmitter.status.range",
          "gui.transmitter.status.covered",
          "gui.transmitter.status.not_covered",
          "gui.transmitter.status.missing",
          "gui.transmitter.status.no_storage",
          "gui.transmitter.status.multiple",
          "gui.transmitter.status.disabled",
          "gui.transmitter.bind.item",
          "gui.transmitter.bind.hint",
          "gui.transmitter.mode.charge_only",
          "gui.transmitter.mode.bind",
          "gui.transmitter.mode.disabled",
          "gui.transmitter.mode.hint",
          "gui.transmitter.mode.charge_only_lore",
          "gui.transmitter.mode.bind_lore",
          "gui.transmitter.mode.disabled_lore",
          "gui.transmitter.slot.terminal_empty",
          "gui.transmitter.slot.terminal_present");
  private static final Set<String> CRITICAL_PLAYER_FACING_KEYS =
      Set.of(
          "item.storage",
          "item.storage_core",
          "item.terminal",
          "item.crafting_terminal",
          "item.wire",
          "item.chunk_loader",
          "item.personal_chunk_loader",
          "item.dormant_chunk_loader",
          "item.monitor",
          "item.import_bus",
          "item.export_bus",
          "item.wireless_terminal",
          "lore.storage.tier",
          "tier.common",
          "tier.rare",
          "tier.mythical",
          "tier.legendary",
          "tier.immortal",
          "gui.give.title",
          "gui.bus.import_title",
          "gui.bus.export_title",
          "gui.bus.info.exort_storage",
          "message.storage_load_failed",
          "message.storage_loading",
          "message.operation_failed",
          "message.relay_disabled",
          "message.chunk_loader_feature_disabled",
          "message.chunk_loader_initializing",
          "message.chunk_loader_limit_reached",
          "message.chunk_loader_enabled",
          "message.chunk_loader_disabled",
          "message.chunk_loader_already_enabled",
          "message.chunk_loader_already_disabled",
          "message.chunk_loader_toggle_denied",
          "message.chunk_loader_toggle_failed");

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
  void translatedLocalesDoNotUseRecentEnglishFallbacks() throws IOException {
    Map<String, String> english = readRuntimeLang("en_us");

    for (String locale : checkedLocales()) {
      if (PLAIN_ENGLISH_LOCALES.contains(locale)) {
        continue;
      }
      Map<String, String> localized = readRuntimeLang(locale);
      for (String key : RECENT_TRANSLATION_KEYS) {
        assertTrue(
            !english.get(key).equals(localized.get(key)),
            locale + " still equals the English fallback for " + key);
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
  void giveItemListsUseFixedChunkLoaderIds() throws IOException {
    for (String locale : checkedLocales()) {
      String value = readRuntimeLang(locale).get("message.usage_give_items");
      assertTrue(value.contains("chunk_loader"), locale + " missing base chunk loader id");
      assertTrue(value.contains("personal_chunk_loader"), locale + " missing personal loader id");
      assertTrue(value.contains("dormant_chunk_loader"), locale + " missing dormant loader id");
      assertTrue(
          !value.contains("chunk_loader <immortal|personal|dormant>"),
          locale + " still uses old chunk loader variant syntax");
    }
  }

  @Test
  void giveChunkLoaderUsageDoesNotDescribeRemovedVariantArgument() throws IOException {
    Pattern removedVariantWords =
        Pattern.compile("(?i)\\b(variant|variante|kind|flavr|varijanti)\\b|варијант|wariant");
    for (String locale : checkedLocales()) {
      String value = readRuntimeLang(locale).get("message.usage_give_chunk_loader");
      assertTrue(
          !removedVariantWords.matcher(value).find(),
          locale + " still describes the removed chunk loader variant argument");
    }
  }

  @Test
  void russianTransmitterDisabledModeUsesPlainE() {
    Map<String, String> russian = readRuntimeLang("ru_ru");

    assertEquals("Передатчик: отключен", russian.get("gui.transmitter.status.disabled"));
    assertEquals("Режим: отключен", russian.get("gui.transmitter.mode.disabled"));
  }

  @Test
  void chunkLoaderStatusUsesSingleLoaderNameAndGenericUuidLabel() throws IOException {
    for (String locale : checkedLocales()) {
      assertEquals(
          "{0}. UUID: {1}",
          readRuntimeLang(locale).get("chunk_loader.status"),
          locale + " has duplicated or reordered chunk loader status wording");
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
