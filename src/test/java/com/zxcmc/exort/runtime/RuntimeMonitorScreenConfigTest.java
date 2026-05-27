package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeMonitorScreenConfigTest {
  @Test
  void resourceModeUsesCurrentMonitorScreenDefaults() {
    RuntimeMonitorScreenConfig config =
        RuntimeMonitorScreenConfig.fromConfig(new YamlConfiguration(), true);

    assertEquals(0.56, config.item().offsetY());
    assertEquals(0.93, config.item().offsetZ());
    assertEquals(1.026, config.horizontalBlock().offsetZ());
    assertEquals(0.815, config.fullBlock().offsetZ());
    assertEquals(0.26, config.text().offsetY());
    assertEquals(0.95, config.text().offsetZ());
    assertEquals(0.7, config.textEmpty().scale());
    assertEquals(0, config.textBackgroundAlpha());
  }

  @Test
  void vanillaModeUsesCurrentMonitorScreenDefaults() {
    RuntimeMonitorScreenConfig config =
        RuntimeMonitorScreenConfig.fromConfig(new YamlConfiguration(), false);

    assertEquals(0.62, config.item().offsetY());
    assertEquals(0.99, config.item().offsetZ());
    assertEquals(1.032, config.horizontalBlock().offsetZ());
    assertEquals(0.875, config.fullBlock().offsetZ());
    assertEquals(0.2, config.text().offsetY());
    assertEquals(1.01, config.text().offsetZ());
    assertEquals(0.8, config.textEmpty().scale());
  }

  @Test
  void configuredValuesOverrideDefaultsAndTextEmptyFallsBackToTextPosition() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.monitor.screenBlock.scale", 0.66);
    yaml.set("resourceMode.monitor.screenText.offset.x", 0.71);
    yaml.set("resourceMode.monitor.screenText.offset.z", 0.82);
    yaml.set("resourceMode.monitor.screenTextEmpty.offset.y", 0.43);
    yaml.set("resourceMode.monitor.screenTextEmpty.scale", 0.75);

    RuntimeMonitorScreenConfig config = RuntimeMonitorScreenConfig.fromConfig(yaml, true);

    assertEquals(0.66, config.block().scale());
    assertEquals(0.71, config.textEmpty().offsetX());
    assertEquals(0.43, config.textEmpty().offsetY());
    assertEquals(0.82, config.textEmpty().offsetZ());
    assertEquals(0.75, config.textEmpty().scale());
  }
}
