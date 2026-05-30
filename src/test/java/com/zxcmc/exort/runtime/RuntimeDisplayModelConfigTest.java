package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeDisplayModelConfigTest {
  @Test
  void resourceModeUsesCurrentDisplayModelDefaults() {
    RuntimeDisplayModelConfig config =
        RuntimeDisplayModelConfig.fromConfig(new YamlConfiguration(), true, "exort");

    assertEquals("exort:storage/storage", config.storage());
    assertEquals("exort:terminal/inventory", config.terminal());
    assertEquals("exort:terminal/inventory_disabled", config.terminalDisabled());
    assertEquals("exort:terminal/workbench", config.craftingTerminal());
    assertEquals("exort:terminal/workbench_disabled", config.craftingTerminalDisabled());
    assertEquals("exort:terminal/monitor", config.monitor());
    assertEquals("exort:terminal/monitor_disabled", config.monitorDisabled());
    assertEquals("exort:bus/import", config.importBus());
    assertEquals("exort:bus/export", config.exportBus());
  }

  @Test
  void vanillaModeUsesCurrentDisplayModelDefaults() {
    RuntimeDisplayModelConfig config =
        RuntimeDisplayModelConfig.fromConfig(new YamlConfiguration(), false, "minecraft");

    assertEquals("minecraft:vault", config.storage());
    assertEquals("minecraft:barrel", config.terminal());
    assertEquals("minecraft:barrel", config.terminalDisabled());
    assertEquals("minecraft:crafting_table", config.craftingTerminal());
    assertEquals("minecraft:crafting_table", config.craftingTerminalDisabled());
    assertEquals("minecraft:smooth_stone", config.monitor());
    assertEquals("minecraft:smooth_stone", config.monitorDisabled());
    assertEquals("minecraft:dispenser", config.importBus());
    assertEquals("minecraft:dropper", config.exportBus());
  }

  @Test
  void configuredModelsAreNormalizedIntoRuntimeNamespace() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.terminal.modelDisabledId", " terminal/custom_disabled ");
    yaml.set("resourceMode.bus.export.modelId", "other:bus/custom_export");

    RuntimeDisplayModelConfig config = RuntimeDisplayModelConfig.fromConfig(yaml, true, "custom");

    assertEquals("custom:terminal/custom_disabled", config.terminalDisabled());
    assertEquals("custom:bus/custom_export", config.exportBus());
  }
}
