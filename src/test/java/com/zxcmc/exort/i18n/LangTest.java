package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LangTest {
  @Test
  void defaultEnglishAndRussianKeysMatch() throws Exception {
    Lang lang = new Lang(null);

    assertEquals(defaults(lang, "defaultsEn").keySet(), defaults(lang, "defaultsRu").keySet());
  }

  @Test
  void cleanupPassKeysResolve() throws Exception {
    Lang lang = new Lang(null);
    Map<String, String> en = defaults(lang, "defaultsEn");
    Map<String, String> ru = defaults(lang, "defaultsRu");
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
            "message.wire.hard_cap");

    for (String key : requiredKeys) {
      assertTrue(en.containsKey(key), "missing English key: " + key);
      assertTrue(ru.containsKey(key), "missing Russian key: " + key);
      assertNotEquals(key, lang.tr(key, "value"), "unresolved key: " + key);
    }
    assertTrue(lang.tr("message.pack_status.status", "READY").contains("READY"));
  }

  @Test
  void pluginTextLanguageUsesClientLocaleOnlyWhenExortLanguageExists() throws Exception {
    Lang lang = new Lang(null);

    assertEquals("ru_ru", lang.pluginTextLanguage("ru_ru"));
    assertEquals("en_us", lang.pluginTextLanguage("de_de"));

    setField(lang, "activeLanguage", "ru_ru");
    assertEquals("ru_ru", lang.pluginTextLanguage("de_de"));

    setField(lang, "activeLanguage", "zz_zz");
    assertEquals("en_us", lang.pluginTextLanguage("de_de"));
  }

  @Test
  void parameterFormattingPreservesLiteralApostrophes() throws Exception {
    Lang lang = new Lang(null);
    Map<String, String> active = defaults(lang, "defaultsEn");
    active.put("test.apostrophe", "{0}'s storage");

    assertEquals("Alex's storage", lang.tr("test.apostrophe", "Alex"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> defaults(Lang lang, String fieldName) throws Exception {
    Field field = Lang.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<String, String>) field.get(lang);
  }

  private void setField(Lang lang, String fieldName, Object value) throws Exception {
    Field field = Lang.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(lang, value);
  }
}
