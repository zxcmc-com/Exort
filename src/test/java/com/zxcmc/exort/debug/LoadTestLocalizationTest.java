package com.zxcmc.exort.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.Lang;
import org.junit.jupiter.api.Test;

class LoadTestLocalizationTest {
  @Test
  void loadTestStartedMessageKeepsPlaceholderCoverageAcrossBundledLanguages() {
    Lang lang = new Lang(null);
    lang.load("en_us");

    for (String language : new String[] {"en_us", "ru_ru"}) {
      String message = lang.trLanguage(language, "message.debug_load_started", 25);
      assertTrue(message.contains("25"), language);
      assertFalse(message.contains("{0}"), language);
      assertFalse(message.contains("message.debug_load_started"), language);
    }
  }
}
