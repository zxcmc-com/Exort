package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuiOverlayConfigTest {
  @Test
  void defaultsMatchResourceGuiOverlays() {
    GuiOverlayConfig config = GuiOverlayConfig.defaults();

    assertEquals("gui/inventory", config.terminal());
    assertEquals("gui/crafting", config.craftingTerminal());
    assertEquals("gui/bus", config.bus());
    assertEquals("gui/inventory", config.storageTerminal(SessionType.STORAGE));
    assertEquals("gui/crafting", config.storageTerminal(SessionType.CRAFTING));
    assertEquals(
        GuiOverlayConfig.TERMINAL_KEY, GuiOverlayConfig.storageTerminalKey(SessionType.STORAGE));
    assertEquals(
        GuiOverlayConfig.CRAFTING_TERMINAL_KEY,
        GuiOverlayConfig.storageTerminalKey(SessionType.CRAFTING));
  }
}
