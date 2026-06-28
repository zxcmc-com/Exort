package com.zxcmc.exort.runtime;

import static com.zxcmc.exort.runtime.RuntimeItemModelConfig.normalizeModelId;

public record RuntimeDisplayModelConfig(
    String storage,
    String terminal,
    String terminalDisabled,
    String craftingTerminal,
    String craftingTerminalDisabled,
    String monitor,
    String monitorDisabled,
    String importBus,
    String exportBus,
    String relay,
    String chunkLoader) {
  public static RuntimeDisplayModelConfig forMode(boolean resourceMode, String namespace) {
    return resourceMode ? resourceConfig(namespace) : vanillaConfig(namespace);
  }

  private static RuntimeDisplayModelConfig resourceConfig(String namespace) {
    return new RuntimeDisplayModelConfig(
        normalizeModelId("storage/storage", namespace),
        normalizeModelId("terminal/inventory", namespace),
        normalizeModelId("terminal/inventory_disabled", namespace),
        normalizeModelId("terminal/workbench", namespace),
        normalizeModelId("terminal/workbench_disabled", namespace),
        normalizeModelId("terminal/monitor", namespace),
        normalizeModelId("terminal/monitor_disabled", namespace),
        normalizeModelId("bus/import", namespace),
        normalizeModelId("bus/export", namespace),
        normalizeModelId("relay/relay", namespace),
        normalizeModelId("chunkloader/chunkloader", namespace));
  }

  private static RuntimeDisplayModelConfig vanillaConfig(String namespace) {
    String terminal = "barrel";
    String craftingTerminal = "crafting_table";
    String monitor = "smooth_stone";
    return new RuntimeDisplayModelConfig(
        normalizeModelId("vault", namespace),
        normalizeModelId(terminal, namespace),
        normalizeModelId(terminal, namespace),
        normalizeModelId(craftingTerminal, namespace),
        normalizeModelId(craftingTerminal, namespace),
        normalizeModelId(monitor, namespace),
        normalizeModelId(monitor, namespace),
        normalizeModelId("dispenser", namespace),
        normalizeModelId("dropper", namespace),
        normalizeModelId("lodestone", namespace),
        normalizeModelId("respawn_anchor", namespace));
  }
}
