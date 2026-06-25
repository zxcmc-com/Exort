package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class EmbeddedChorusfixConfigTest {
  @Test
  void defaultsToEnabled() {
    EmbeddedChorusfixConfig config = EmbeddedChorusfixConfig.from(new YamlConfiguration());

    assertTrue(config.enabled());
    assertTrue(config.onlyWhenPaperDisabled());
    assertTrue(config.ignoredMasks().contains(ChorusFaceMask.ALL));
  }

  @Test
  void readsTopLevelDisableFlag() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("chorusfix", false);

    assertFalse(EmbeddedChorusfixConfig.from(yaml).enabled());
  }
}
