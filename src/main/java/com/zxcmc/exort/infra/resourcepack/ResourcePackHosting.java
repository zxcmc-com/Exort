package com.zxcmc.exort.infra.resourcepack;

import com.zxcmc.exort.infra.config.ConfigEnums;

public enum ResourcePackHosting {
  AUTO,
  EXORT,
  NEXO,
  ITEMSADDER,
  SELFHOST,
  LOBFILE,
  DISABLED;

  public static ResourcePackHosting fromConfig(String raw) {
    return ConfigEnums.parse("resourcePack.hosting", raw, AUTO);
  }
}
