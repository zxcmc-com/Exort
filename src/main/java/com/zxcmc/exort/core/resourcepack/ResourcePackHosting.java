package com.zxcmc.exort.core.resourcepack;

import java.util.Locale;

public enum ResourcePackHosting {
  AUTO,
  NEXO,
  SELFHOST,
  LOBFILE,
  DISABLED;

  public static ResourcePackHosting fromConfig(String raw) {
    if (raw == null || raw.isBlank()) {
      return AUTO;
    }
    try {
      return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return AUTO;
    }
  }
}
