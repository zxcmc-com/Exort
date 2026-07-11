package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.i18n.Lang;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ExortBrigadierHelpTest {
  @Test
  void publicHelpExposesVersionWithoutAdvertisingPrivilegedCommands() {
    List<String> lines = plain(ExortBrigadier.rootHelpLines(new Lang(null), null, false, false));

    assertTrue(lines.stream().anyMatch(line -> line.contains("/exort version")));
    assertFalse(lines.stream().anyMatch(line -> line.contains("/exort give")));
    assertFalse(lines.stream().anyMatch(line -> line.contains("/exort reload")));
  }

  @Test
  void giveAndAdminHelpExposeOnlyTheirAuthorizedSurfaces() {
    Lang lang = new Lang(null);

    List<String> give = plain(ExortBrigadier.rootHelpLines(lang, null, false, true));
    List<String> admin = plain(ExortBrigadier.rootHelpLines(lang, null, true, true));

    assertTrue(give.stream().anyMatch(line -> line.contains("/exort give")));
    assertFalse(give.stream().anyMatch(line -> line.contains("/exort reload")));
    assertTrue(admin.stream().anyMatch(line -> line.contains("/exort reload")));
  }

  private static List<String> plain(List<Component> components) {
    PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
    return components.stream().map(serializer::serialize).toList();
  }
}
