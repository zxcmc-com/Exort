package com.zxcmc.exort.gui;

import java.util.Objects;

public record GuiOverlayConfig(
    String terminal, String craftingTerminal, String bus, String transmitter) {
  public static final String TERMINAL_KEY = "gui.overlay.terminal";
  public static final String CRAFTING_TERMINAL_KEY = "gui.overlay.crafting_terminal";
  public static final String BUS_KEY = "gui.overlay.bus";
  public static final String TRANSMITTER_KEY = "gui.overlay.transmitter";

  public GuiOverlayConfig {
    terminal = Objects.requireNonNull(terminal, "terminal");
    craftingTerminal = Objects.requireNonNull(craftingTerminal, "craftingTerminal");
    bus = Objects.requireNonNull(bus, "bus");
    transmitter = Objects.requireNonNull(transmitter, "transmitter");
  }

  public static GuiOverlayConfig defaults() {
    return new GuiOverlayConfig("gui/inventory", "gui/crafting", "gui/bus", "gui/transmitter");
  }

  public String storageTerminal(SessionType type) {
    return type == SessionType.CRAFTING ? craftingTerminal : terminal;
  }

  public static String storageTerminalKey(SessionType type) {
    return type == SessionType.CRAFTING ? CRAFTING_TERMINAL_KEY : TERMINAL_KEY;
  }
}
