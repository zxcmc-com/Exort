package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemNameServiceTest {
  @TempDir Path tempDir;

  @Test
  void dictionaryEntriesReadNestedYamlKeysAsFlatTranslationKeys() {
    YamlConfiguration cfg = new YamlConfiguration();
    cfg.set("__version", "26.1.2");
    cfg.set("stone", "Stone");
    cfg.set("banner.triangles_top.black", "Black Chief Indented");

    Map<String, String> entries = ItemNameService.dictionaryEntries(cfg);

    assertEquals(2, entries.size());
    assertEquals("Stone", entries.get("stone"));
    assertEquals("Black Chief Indented", entries.get("banner.triangles_top.black"));
    assertFalse(entries.containsKey("__version"));
    assertFalse(entries.containsKey("banner"));
    assertFalse(entries.containsKey("banner.triangles_top"));
  }

  @Test
  void dictionaryLanguageUsesClientLocaleWhenMinecraftDictionaryIsKnown() throws Exception {
    ItemNameService service = serviceWithLanguages(Set.of("de_de", "en_us"));

    assertEquals("de_de", service.dictionaryLanguage("de_de", "en_us"));
  }

  @Test
  void dictionaryLanguageFallsBackToConfiguredLanguageWhenClientDictionaryIsUnknown()
      throws Exception {
    ItemNameService service = serviceWithLanguages(Set.of("en_us", "ru_ru"));

    assertEquals("ru_ru", service.dictionaryLanguage("zz_zz", "ru_ru"));
  }

  @Test
  void dictionaryRefreshIgnoresBundledPluginLanguageFilesUntilItemDictionaryExists()
      throws Exception {
    Files.createDirectories(tempDir.resolve("lang/items"));
    Files.writeString(tempDir.resolve("lang/de_de.yml"), "message:\n  no_permission: Nein\n");
    Files.writeString(tempDir.resolve("lang/ja_jp.yml"), "message:\n  no_permission: なし\n");
    Files.writeString(tempDir.resolve("lang/items/fr_fr.yml"), "__version: 1.21.11\n");
    ItemNameService service =
        serviceWithLanguages(Set.of("de_de", "en_us", "fr_fr", "ja_jp", "ru_ru"));
    setField(service, "activeLanguage", "de_de");

    Set<String> requested = service.dictionaryRefreshLanguages();

    assertEquals(Set.of("de_de", "en_us", "fr_fr", "ru_ru"), requested);
  }

  private ItemNameService serviceWithLanguages(Set<String> languages) throws Exception {
    ItemNameService service = new ItemNameService(null, tempDir.toFile());
    setField(service, "availableLanguages", new HashSet<>(languages));
    setField(service, "activeLanguage", "en_us");
    return service;
  }

  private static void setField(ItemNameService service, String fieldName, Object value)
      throws Exception {
    Field field = ItemNameService.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(service, value);
  }
}
