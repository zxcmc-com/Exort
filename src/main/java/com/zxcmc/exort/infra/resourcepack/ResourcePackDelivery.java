package com.zxcmc.exort.infra.resourcepack;

import com.zxcmc.exort.infra.config.ConfigEnums;

public enum ResourcePackDelivery {
  AUTO,
  CONFIGURATION,
  JOIN,
  MANUAL;

  public static ResourcePackDelivery fromConfig(String raw) {
    return ConfigEnums.parse("resourcePack.delivery", raw, AUTO);
  }
}
