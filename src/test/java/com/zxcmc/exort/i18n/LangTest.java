package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LangTest {
  @TempDir Path tempDir;

  @Test
  void constructorLoadsBundledEnglishAndRussianDefaults() {
    Lang lang = new Lang(null);

    assertEquals("Storage Core", lang.trLanguage("en_us", "item.storage_core"));
    assertEquals("Основа хранилища", lang.trLanguage("ru_ru", "item.storage_core"));
  }

  @Test
  void cleanupPassKeysResolve() {
    Lang lang = new Lang(null);
    Set<String> requiredKeys =
        Set.of(
            "message.wireless.bound",
            "message.pack_service_not_started",
            "message.pack_status.status",
            "message.pack_status.configured_hosting",
            "message.pack_status.effective_hosting",
            "message.pack_status.configured_delivery",
            "message.pack_status.effective_delivery",
            "message.pack_status.raw_pack",
            "message.pack_status.pack",
            "message.pack_status.obfuscated",
            "message.pack_status.handoff",
            "message.pack_status.sha1",
            "message.pack_status.url",
            "message.pack_status.note",
            "message.pack_status.provider_note",
            "message.pack_status.error",
            "message.resource_pack.required_failure",
            "message.command_click",
            "message.help_inventory",
            "message.help_resourcepack",
            "message.help_language",
            "message.usage_give_header",
            "message.usage_give_storage",
            "message.usage_give_item",
            "message.usage_give_items",
            "message.usage_language_header",
            "message.usage_language_status",
            "message.usage_language_set",
            "message.usage_language_refresh",
            "message.usage_resourcepack_header",
            "message.usage_resourcepack_status",
            "message.usage_resourcepack_rebuild",
            "message.usage_resourcepack_send",
            "message.usage_mode_header",
            "message.usage_mode_info",
            "message.usage_mode_set",
            "message.usage_mode_fix",
            "message.usage_debug_header",
            "message.usage_debug_player",
            "message.usage_debug_storage",
            "message.usage_debug_cache",
            "message.usage_debug_verbose",
            "message.usage_debug_culling_client",
            "message.usage_debug_benchmark",
            "message.debug_culling_unavailable",
            "message.debug_culling_started",
            "message.debug_culling_stopped",
            "message.debug_culling_mode_invalid",
            "message.debug_culling_client_invalid",
            "message.debug_culling_client_status",
            "message.mode_carrier_warning",
            "message.mode_fix_not_resource",
            "message.wire.hard_cap");

    for (String key : requiredKeys) {
      assertNotEquals(
          key, lang.trLanguage("en_us", key, "value"), "unresolved English key: " + key);
      assertNotEquals(
          key, lang.trLanguage("ru_ru", key, "value"), "unresolved Russian key: " + key);
    }
    assertTrue(lang.tr("message.pack_status.status", "READY").contains("READY"));
  }

  @Test
  void pluginTextLanguageUsesBundledLocaleWhenLoaded() {
    Lang lang = new Lang(null);
    lang.load("en_us");

    assertEquals("ru_ru", lang.pluginTextLanguage("ru_ru"));
    assertEquals("de_de", lang.pluginTextLanguage("de_de"));
    assertEquals("en_us", lang.pluginTextLanguage("zz_zz"));

    lang.load("ru_ru");
    assertEquals("ru_ru", lang.pluginTextLanguage("zz_zz"));
  }

  @Test
  void parameterFormattingPreservesLiteralApostrophes() {
    Lang lang = new Lang(null);

    assertEquals(
        "Alex's inventory was full; dropped 1 item(s) nearby.",
        lang.tr("message.give_dropped", 1, "Alex"));
  }

  @Test
  void configuredTranslationUsesConfiguredLanguage() {
    Lang lang = new Lang(null);
    lang.load("en_us");

    assertEquals("Storage Core", lang.trConfigured("item.storage_core"));
    assertEquals("Основа хранилища", lang.trLanguage("ru_ru", "item.storage_core"));
  }

  @Test
  void defaultLoadDoesNotMaterializeBundledLanguages() throws Exception {
    Lang lang = testLang();

    lang.load("en_us");

    Path langDir = tempDir.resolve("lang");
    long languageFiles;
    try (var files = Files.walk(langDir)) {
      languageFiles = files.filter(path -> path.toString().endsWith(".yml")).count();
    }
    assertEquals(0, languageFiles);
  }

  @Test
  void localLanguageFileOverridesBundledKeysWithBundledFallback() throws Exception {
    Path langDir = tempDir.resolve("lang");
    Files.createDirectories(langDir);
    Files.writeString(langDir.resolve("en_us.yml"), "message:\n  no_permission: Local override\n");

    Lang lang = testLang();
    lang.load("en_us");

    assertEquals("Local override", lang.tr("message.no_permission"));
    assertEquals("Storage Core", lang.tr("item.storage_core"));
  }

  @Test
  void clientComponentFallsBackToPlainTextForServerOnlyKeys() {
    Lang lang = new Lang(null);

    assertFalse(lang.clientComponent(true, "message.no_permission").toString().contains("exort."));
  }

  private Lang testLang() {
    return new Lang(null, tempDir.toFile(), Path.of("src/main/resources"));
  }
}
