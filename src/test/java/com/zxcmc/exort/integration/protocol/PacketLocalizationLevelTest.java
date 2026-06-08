package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PacketLocalizationLevelTest {
  @Test
  void parsesKnownLevelsCaseInsensitively() {
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString("SIMPLE"));
    assertEquals(PacketLocalizationLevel.FULL, PacketLocalizationLevel.fromString("full"));
  }

  @Test
  void defaultsInvalidValuesToSimple() {
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString(null));
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString(""));
    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromString("wide"));
  }

  @Test
  void invalidConfigLevelFallsBackToSimpleAtRuntime() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("packetEvents.localizationLevel", "wide");

    assertEquals(PacketLocalizationLevel.SIMPLE, PacketLocalizationLevel.fromConfig(yaml));
  }
}
