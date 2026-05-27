package com.zxcmc.exort.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RuntimeDisplayConfigTest {
  @Test
  void vanillaModeUsesPackFreeDefaultsAndDoesNotResolveResourceMaterial() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.wire.displayBaseMaterial", "DIAMOND");
    yaml.set("resourceMode.wire.displayScale", 2.0);
    AtomicInteger resolverCalls = new AtomicInteger();

    RuntimeDisplayConfig config =
        RuntimeDisplayConfig.fromConfig(
            yaml,
            false,
            "resourceMode.wire",
            (name, fallback) -> {
              resolverCalls.incrementAndGet();
              return Material.DIAMOND;
            });

    assertEquals(Material.PAPER, config.displayBaseMaterial());
    assertEquals(1.0, config.displayScale());
    assertEquals(0.5, config.offsetX());
    assertEquals(0, resolverCalls.get());
  }

  @Test
  void resourceModeReadsFeatureDisplaySettings() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourceMode.storage.displayBaseMaterial", "minecraft:stone");
    yaml.set("resourceMode.storage.displayScale", 1.25);
    yaml.set("resourceMode.storage.displayOffset.x", 0.2);
    yaml.set("resourceMode.storage.displayOffset.y", 0.3);
    yaml.set("resourceMode.storage.displayOffset.z", 0.4);

    RuntimeDisplayConfig config =
        RuntimeDisplayConfig.fromConfig(
            yaml,
            true,
            "resourceMode.storage",
            (name, fallback) -> "minecraft:stone".equals(name) ? Material.STONE : fallback);

    assertEquals(Material.STONE, config.displayBaseMaterial());
    assertEquals(1.25, config.displayScale());
    assertEquals(0.2, config.offsetX());
    assertEquals(0.3, config.offsetY());
    assertEquals(0.4, config.offsetZ());
  }

  @Test
  void resourceModeUsesCurrentDefaultsWhenFeatureSettingsAreMissing() {
    RuntimeDisplayConfig config =
        RuntimeDisplayConfig.fromConfig(
            new YamlConfiguration(), true, "resourceMode.monitor", (name, fallback) -> fallback);

    assertEquals(Material.PAPER, config.displayBaseMaterial());
    assertEquals(1.0, config.displayScale());
    assertEquals(0.5, config.offsetX());
    assertEquals(0.5, config.offsetY());
    assertEquals(0.5, config.offsetZ());
  }
}
