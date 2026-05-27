package com.zxcmc.exort.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ItemNameServiceTest {
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
}
