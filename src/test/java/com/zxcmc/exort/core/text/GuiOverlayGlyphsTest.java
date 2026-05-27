package com.zxcmc.exort.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class GuiOverlayGlyphsTest {
  private static final PlainTextComponentSerializer PLAIN =
      PlainTextComponentSerializer.plainText();

  @Test
  void readsOverlayGlyphsFromDefaultFont() {
    assertEquals("ᾖ", GuiOverlayGlyphs.glyphs().get("gui/inventory"));
    assertEquals("૱", GuiOverlayGlyphs.glyphs().get("gui/crafting"));
    assertEquals("ἢ", GuiOverlayGlyphs.glyphs().get("gui/bus"));
  }

  @Test
  void buildsWhiteDefaultFontOverlayComponent() {
    Component overlay =
        GuiOverlayGlyphs.overlay("resourceMode.terminal.gui.overlayTexture", "gui/inventory", null)
            .orElseThrow();

    assertEquals("\uE104ᾖ\uE108\uE106\uE104\uE101", PLAIN.serialize(overlay));
    assertEquals(NamedTextColor.WHITE, overlay.color());
    assertEquals(Key.key("exort:default"), overlay.font());
  }

  @Test
  void overlayPrefixDoesNotStyleTitle() {
    Component overlay =
        GuiOverlayGlyphs.overlay("resourceMode.terminal.gui.overlayTexture", "gui/inventory", null)
            .orElseThrow();
    Component title = ExortText.plain("Terminal");

    Component combined = ExortText.withPrefix(overlay, title);

    assertEquals("\uE104ᾖ\uE108\uE106\uE104\uE101Terminal", PLAIN.serialize(combined));
    assertEquals(NamedTextColor.WHITE, combined.children().get(0).color());
    assertEquals(Key.key("exort:default"), combined.children().get(0).font());
    assertNull(combined.children().get(1).color());
    assertNull(combined.children().get(1).font());
  }

  @Test
  void unknownOverlayTextureReturnsEmptyAndWarnsOncePerKey() {
    List<String> warnings = new ArrayList<>();

    assertTrue(
        GuiOverlayGlyphs.overlay(
                "resourceMode.terminal.gui.overlayTexture", "gui/missing", warnings::add)
            .isEmpty());
    assertTrue(
        GuiOverlayGlyphs.overlay(
                "resourceMode.terminal.gui.overlayTexture", "gui/missing", warnings::add)
            .isEmpty());

    assertEquals(1, warnings.size());
  }

  @Test
  void invalidOverlayTextureRejectsNamespaceAndPngSuffix() {
    assertFalse(GuiOverlayGlyphs.normalizeConfigTexture("exort:gui/inventory").isPresent());
    assertFalse(GuiOverlayGlyphs.normalizeConfigTexture("gui/inventory.png").isPresent());
    assertEquals(
        "gui/inventory", GuiOverlayGlyphs.normalizeConfigTexture("gui/inventory").orElseThrow());
  }
}
