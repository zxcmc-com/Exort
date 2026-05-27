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
            "message.resource_pack.required_failure");

    for (String key : requiredKeys) {
      assertTrue(en.containsKey(key), "missing English key: " + key);
      assertTrue(ru.containsKey(key), "missing Russian key: " + key);
      assertNotEquals(key, lang.tr(key, "value"), "unresolved key: " + key);
    }
    assertTrue(lang.tr("message.pack_status.status", "READY").contains("READY"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> defaults(Lang lang, String fieldName) throws Exception {
    Field field = Lang.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (Map<String, String>) field.get(lang);
  }
}
