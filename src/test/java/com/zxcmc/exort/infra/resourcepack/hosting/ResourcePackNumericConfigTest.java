package com.zxcmc.exort.infra.resourcepack.hosting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ResourcePackNumericConfigTest {
  @Test
  void clampsTimeoutAndPortToOperationalBounds() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourcePack.configurationTimeoutSeconds", Long.MAX_VALUE);
    yaml.set("resourcePack.selfHost.port", 65_536L);

    ResourcePackNumericConfig config = ResourcePackNumericConfig.fromConfig(yaml, null);

    assertEquals(
        ResourcePackNumericConfig.MAX_CONFIGURATION_TIMEOUT_SECONDS,
        config.configurationTimeoutSeconds());
    assertEquals(ResourcePackNumericConfig.MAX_PORT, config.selfHostPort());
  }

  @Test
  void preservesEphemeralPortAndMinimumTimeout() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("resourcePack.configurationTimeoutSeconds", 1);
    yaml.set("resourcePack.selfHost.port", 0);

    ResourcePackNumericConfig config = ResourcePackNumericConfig.fromConfig(yaml, null);

    assertEquals(1, config.configurationTimeoutSeconds());
    assertEquals(0, config.selfHostPort());
  }
}
