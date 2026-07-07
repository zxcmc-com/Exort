package com.zxcmc.exort.text;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.gui.GuiOverlayConfig;
import org.junit.jupiter.api.Test;

class GuiOverlayGlyphsTest {
  @Test
  void transmitterOverlayGlyphIsBundled() {
    assertTrue(
        GuiOverlayGlyphs.overlay(GuiOverlayConfig.TRANSMITTER_KEY, "gui/transmitter", null)
            .isPresent());
  }
}
