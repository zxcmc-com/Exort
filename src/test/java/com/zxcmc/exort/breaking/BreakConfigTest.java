package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BreakConfigTest {
  @Test
  void fallbackHardnessDefaultsMatchBundledConfig() {
    YamlConfiguration bundled =
        YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    BreakConfig fallback = BreakConfig.fromConfig(new YamlConfiguration(), null);

    assertAll(
        () -> assertHardness(bundled, "storage", fallback.storage()),
        () -> assertHardness(bundled, "terminal", fallback.terminal()),
        () -> assertHardness(bundled, "monitor", fallback.monitor()),
        () -> assertHardness(bundled, "bus", fallback.bus()),
        () -> assertHardness(bundled, "relay", fallback.relay()),
        () -> assertHardness(bundled, "chunkLoader", fallback.chunkLoader()),
        () -> assertHardness(bundled, "wire", fallback.wire()));
  }

  @Test
  void invalidHardnessIsFiniteAndNonNegative() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("break.storage.hardness", -5.0D);
    config.set("break.wire.hardness", Double.NaN);

    BreakConfig result = BreakConfig.fromConfig(config, null);

    assertEquals(0.0D, result.storage().hardness());
    assertEquals(2.0D, result.wire().hardness());
  }

  private static void assertHardness(
      YamlConfiguration bundled, String blockType, BreakSettings settings) {
    assertEquals(bundled.getDouble("break." + blockType + ".hardness"), settings.hardness());
  }
}
