package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WireAutoRenderConfigTest {
  @Test
  void readsDefaults() {
    WireAutoRenderConfig config = WireAutoRenderConfig.fromConfig(new YamlConfiguration());

    assertEquals(1, config.chunkRadius());
    assertEquals(48, config.enterCompactWires());
    assertEquals(32, config.exitCompactWires());
    assertEquals(96.0, config.idlePlayerRadiusBlocks());
    assertEquals(16, config.maintenanceBlocksPerTick());
  }

  @Test
  void clampsUnsafeValues() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.wire.autoRender.chunkRadius", -1);
    yaml.set("resourceMode.wire.autoRender.enterCompactWires", 0);
    yaml.set("resourceMode.wire.autoRender.exitCompactWires", 999);
    yaml.set("resourceMode.wire.autoRender.idlePlayerRadiusBlocks", -10.0);
    yaml.set("resourceMode.wire.autoRender.maintenanceBlocksPerTick", 0);

    WireAutoRenderConfig config = WireAutoRenderConfig.fromConfig(yaml);

    assertEquals(0, config.chunkRadius());
    assertEquals(1, config.enterCompactWires());
    assertEquals(1, config.exitCompactWires());
    assertEquals(0.0, config.idlePlayerRadiusBlocks());
    assertEquals(1, config.maintenanceBlocksPerTick());
  }
}
