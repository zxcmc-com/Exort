package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class EmbeddedChorusfixConfigTest {
  @Test
  void readsTopLevelDisableFlag() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("chorusfix", false);

    assertFalse(EmbeddedChorusfixConfig.from(yaml).enabled());
  }
}
