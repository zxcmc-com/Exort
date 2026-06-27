package com.zxcmc.exort.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ExortTextTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void configRichTextParsesMiniMessageColor() {
    Component component = ExortText.configRichText("<dark_red>99%</dark_red>");

    assertEquals("99%", PLAIN.serialize(component));
    assertEquals(NamedTextColor.DARK_RED, firstColor(component));
  }

  @Test
  void configRichTextParsesLegacySectionColor() {
    Component component = ExortText.configRichText("§4Full");

    assertEquals("Full", PLAIN.serialize(component));
    assertEquals(NamedTextColor.DARK_RED, firstColor(component));
  }

  @Test
  void configRichTextFallsBackToPlainTextForInvalidMiniMessage() {
    String invalid = "<#zzzzzz>bad";

    Component component = ExortText.configRichText(invalid);

    assertEquals(invalid, PLAIN.serialize(component));
    assertNull(firstColor(component));
  }

  @Test
  void itemTextDisablesItalicDecoration() {
    Component component = ExortText.itemText("Name");

    assertEquals(TextDecoration.State.FALSE, component.decoration(TextDecoration.ITALIC));
  }

  @Test
  void withPrefixDoesNotLetPrefixColorInheritIntoContent() {
    Component prefix = ExortText.configRichText("§f*");
    Component content = ExortText.plain("Terminal");

    Component combined = ExortText.withPrefix(prefix, content);

    assertEquals("*Terminal", PLAIN.serialize(combined));
    assertEquals(NamedTextColor.WHITE, firstColor(combined.children().get(0)));
    assertNull(combined.children().get(1).color());
  }

  private static TextColor firstColor(Component component) {
    TextColor color = component.color();
    if (color != null) {
      return color;
    }
    for (Component child : component.children()) {
      TextColor childColor = firstColor(child);
      if (childColor != null) {
        return childColor;
      }
    }
    return null;
  }
}
