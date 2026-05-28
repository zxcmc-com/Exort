package com.zxcmc.exort.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.i18n.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ExortBrigadierHelpTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void rootHelpUsesCanonicalCommandsInImportanceOrder() {
    Lang lang = new Lang(null);
    String plain =
        PLAIN.serialize(
            CommandFeedback.block(
                Component.text(lang.tr("message.help_header")),
                ExortBrigadier.rootHelpLines(lang, true)));

    assertInOrder(
        plain,
        "/exort inventory",
        "/exort give",
        "/exort resourcepack",
        "/exort language",
        "/exort mode",
        "/exort debug",
        "/exort version",
        "/exort reload");
    assertFalse(plain.contains("/exort pack -"));
    assertFalse(plain.contains("/exort lang -"));
  }

  @Test
  void rootHelpForGiveOnlyPermissionOmitsAdminCommands() {
    Lang lang = new Lang(null);
    String plain =
        PLAIN.serialize(
            CommandFeedback.block(
                Component.text(lang.tr("message.help_header")),
                ExortBrigadier.rootHelpLines(lang, false)));

    assertTrue(plain.contains("/exort inventory"));
    assertTrue(plain.contains("/exort give"));
    assertFalse(plain.contains("/exort resourcepack"));
    assertFalse(plain.contains("/exort reload"));
  }

  private static void assertInOrder(String value, String... needles) {
    int previous = -1;
    for (String needle : needles) {
      int index = value.indexOf(needle);
      assertTrue(index > previous, "Expected " + needle + " after index " + previous);
      previous = index;
    }
  }
}
