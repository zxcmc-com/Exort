package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LangCommandStatusTest {
  @Test
  void keepsDetailedDictionaryLinesForSmallLists() {
    Lang lang = new Lang(null);
    ItemNameService.Status status = status("ru_ru", "en_us", "de_de", "fr_fr", "es_es");

    List<LangCommand.DictionaryStatusLine> entries = LangCommand.dictionaryStatusEntries(status);
    List<String> lines = LangCommand.dictionaryStatusLines(lang, null, status);

    assertEquals("message.lang_status_dict", entries.getFirst().key());
    assertEquals(List.of("de_de", "v2", 102), entries.getFirst().args());
    assertEquals(5, lines.size());
    assertTrue(lines.get(0).startsWith("Dictionary de_de:"));
    assertTrue(lines.get(4).startsWith("Dictionary ru_ru:"));
  }

  @Test
  void usesCompactHorizontalDictionaryListForSixOrMoreDictionaries() {
    Lang lang = new Lang(null);
    lang.load("ru_ru");
    ItemNameService.Status status = status("ru_ru", "en_us", "de_de", "fr_fr", "es_es", "it_it");

    List<LangCommand.DictionaryStatusLine> entries = LangCommand.dictionaryStatusEntries(status);
    List<String> lines = LangCommand.dictionaryStatusLines(lang, null, status);

    assertEquals(1, entries.size());
    assertEquals("message.lang_status_dict_compact", entries.getFirst().key());
    assertEquals(List.of(6, "de_de, en_us, es_es, fr_fr, it_it, ru_ru"), entries.getFirst().args());
    assertEquals(1, lines.size());
    assertEquals("Словари (6): de_de, en_us, es_es, fr_fr, it_it, ru_ru", lines.getFirst());
  }

  private static ItemNameService.Status status(String... languages) {
    Map<String, String> versions = new LinkedHashMap<>();
    Map<String, Integer> sizes = new LinkedHashMap<>();
    for (int i = 0; i < languages.length; i++) {
      versions.put(languages[i], "v" + i);
      sizes.put(languages[i], 100 + i);
    }
    return new ItemNameService.Status(
        "en_us", "1.21.11", true, false, languages.length, versions, sizes);
  }
}
