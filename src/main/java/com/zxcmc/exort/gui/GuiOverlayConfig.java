package com.zxcmc.exort.gui;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record GuiOverlayConfig(String terminal, String craftingTerminal, String bus) {
  public static final String TERMINAL_KEY = "resourceMode.terminal.gui.overlayTexture";
  public static final String CRAFTING_TERMINAL_KEY =
      "resourceMode.craftingTerminal.gui.overlayTexture";
  public static final String BUS_KEY = "resourceMode.bus.gui.overlayTexture";

  public GuiOverlayConfig {
    terminal = Objects.requireNonNull(terminal, "terminal");
    craftingTerminal = Objects.requireNonNull(craftingTerminal, "craftingTerminal");
    bus = Objects.requireNonNull(bus, "bus");
  }

  public static GuiOverlayConfig fromConfig(ConfigurationSection config) {
    Objects.requireNonNull(config, "config");
    return new GuiOverlayConfig(
        config.getString(TERMINAL_KEY, "gui/inventory"),
        config.getString(CRAFTING_TERMINAL_KEY, "gui/crafting"),
        config.getString(BUS_KEY, "gui/bus"));
  }

  public String storageTerminal(SessionType type) {
    return type == SessionType.CRAFTING ? craftingTerminal : terminal;
  }

  public static String storageTerminalKey(SessionType type) {
    return type == SessionType.CRAFTING ? CRAFTING_TERMINAL_KEY : TERMINAL_KEY;
  }
}
