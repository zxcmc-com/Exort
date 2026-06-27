package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeDisplayModelConfigTest {
  @Test
  void resourceModeNormalizesAllModelIdsIntoRequestedNamespace() {
    RuntimeDisplayModelConfig config = RuntimeDisplayModelConfig.forMode(true, "custom");

    assertAllModelsUseNamespace(config, "custom:");
  }

  @Test
  void blankNamespaceFallsBackToMinecraftNamespace() {
    RuntimeDisplayModelConfig config = RuntimeDisplayModelConfig.forMode(false, " ");

    assertAllModelsUseNamespace(config, "minecraft:");
  }

  private static void assertAllModelsUseNamespace(RuntimeDisplayModelConfig config, String prefix) {
    for (String model : models(config)) {
      assertTrue(model.startsWith(prefix), model);
    }
  }

  private static List<String> models(RuntimeDisplayModelConfig config) {
    return List.of(
        config.storage(),
        config.terminal(),
        config.terminalDisabled(),
        config.craftingTerminal(),
        config.craftingTerminalDisabled(),
        config.monitor(),
        config.monitorDisabled(),
        config.importBus(),
        config.exportBus(),
        config.relay());
  }
}
