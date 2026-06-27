package com.zxcmc.exort.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class WireCarrierModeTest {
  @Test
  void readsExplicitBarrier() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.carrier", "BARRIER");

    assertEquals(WireCarrierMode.BARRIER, WireCarrierMode.fromConfig(yaml));
  }

  @Test
  void invalidValueFallsBackToDefaultAtRuntime() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("wire.carrier", "bad");

    assertEquals(WireCarrierMode.CHORUS_PLANT, WireCarrierMode.fromConfig(yaml));
  }
}
