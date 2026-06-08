package com.zxcmc.exort.carrier;

import com.zxcmc.exort.infra.config.ConfigEnums;
import org.bukkit.configuration.ConfigurationSection;

public enum WireCarrierMode {
  CHORUS_PLANT,
  BARRIER;

  public static final String PATH = "wire.carrier";
  public static final WireCarrierMode DEFAULT = CHORUS_PLANT;

  public static WireCarrierMode fromConfig(ConfigurationSection config) {
    return ConfigEnums.parse(PATH, config == null ? null : config.getString(PATH), DEFAULT);
  }
}
