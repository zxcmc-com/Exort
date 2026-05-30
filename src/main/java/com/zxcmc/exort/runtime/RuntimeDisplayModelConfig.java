package com.zxcmc.exort.runtime;

import static com.zxcmc.exort.runtime.RuntimeItemModelConfig.normalizeModelId;

import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record RuntimeDisplayModelConfig(
    String storage,
    String terminal,
    String terminalDisabled,
    String craftingTerminal,
    String craftingTerminalDisabled,
    String monitor,
    String monitorDisabled,
    String importBus,
    String exportBus) {
  public static RuntimeDisplayModelConfig fromConfig(
      ConfigurationSection config, boolean resourceMode, String namespace) {
    Objects.requireNonNull(config, "config");
    return resourceMode ? resourceConfig(config, namespace) : vanillaConfig(config, namespace);
  }

  private static RuntimeDisplayModelConfig resourceConfig(
      ConfigurationSection config, String namespace) {
    return new RuntimeDisplayModelConfig(
        normalizeModelId(
            config.getString("resourceMode.storage.modelId", "storage/storage"), namespace),
        normalizeModelId(
            config.getString("resourceMode.terminal.modelId", "terminal/inventory"), namespace),
        normalizeModelId(
            config.getString(
                "resourceMode.terminal.modelDisabledId", "terminal/inventory_disabled"),
            namespace),
        normalizeModelId(
            config.getString("resourceMode.craftingTerminal.modelId", "terminal/workbench"),
            namespace),
        normalizeModelId(
            config.getString(
                "resourceMode.craftingTerminal.modelDisabledId", "terminal/workbench_disabled"),
            namespace),
        normalizeModelId(
            config.getString("resourceMode.monitor.modelId", "terminal/monitor"), namespace),
        normalizeModelId(
            config.getString("resourceMode.monitor.modelDisabledId", "terminal/monitor_disabled"),
            namespace),
        normalizeModelId(
            config.getString("resourceMode.bus.import.modelId", "bus/import"), namespace),
        normalizeModelId(
            config.getString("resourceMode.bus.export.modelId", "bus/export"), namespace));
  }

  private static RuntimeDisplayModelConfig vanillaConfig(
      ConfigurationSection config, String namespace) {
    String terminal = config.getString("vanillaMode.terminal.modelId", "barrel");
    String craftingTerminal =
        config.getString("vanillaMode.craftingTerminal.modelId", "crafting_table");
    String monitor = config.getString("vanillaMode.monitor.modelId", "smooth_stone");
    return new RuntimeDisplayModelConfig(
        normalizeModelId(config.getString("vanillaMode.storage.modelId", "vault"), namespace),
        normalizeModelId(terminal, namespace),
        normalizeModelId(terminal, namespace),
        normalizeModelId(craftingTerminal, namespace),
        normalizeModelId(craftingTerminal, namespace),
        normalizeModelId(monitor, namespace),
        normalizeModelId(monitor, namespace),
        normalizeModelId(config.getString("vanillaMode.importBus.modelId", "dispenser"), namespace),
        normalizeModelId(config.getString("vanillaMode.exportBus.modelId", "dropper"), namespace));
  }
}
