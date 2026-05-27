package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiOverlayConfigTest {
  @Test
  void readsCurrentDefaults() {
    GuiOverlayConfig config = GuiOverlayConfig.fromConfig(new YamlConfiguration());

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

  @Test
  void readsConfiguredValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set(GuiOverlayConfig.TERMINAL_KEY, "custom/inventory");
    yaml.set(GuiOverlayConfig.CRAFTING_TERMINAL_KEY, "custom/crafting");
    yaml.set(GuiOverlayConfig.BUS_KEY, "custom/bus");

    GuiOverlayConfig config = GuiOverlayConfig.fromConfig(yaml);

    assertEquals("custom/inventory", config.terminal());
    assertEquals("custom/crafting", config.craftingTerminal());
    assertEquals("custom/bus", config.bus());
  }
}
