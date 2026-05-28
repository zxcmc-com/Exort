package com.zxcmc.exort.runtime;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.Locale;
import org.bukkit.Material;

public final class RuntimeMaterialResolver {
  private RuntimeMaterialResolver() {}

  public static Material resolve(String name, Material fallback) {
    if (name == null) {
      warnInvalid("null", fallback);
      return fallback;
    }
    String raw = name.trim();
    if (raw.isEmpty()) {
      warnInvalid(name, fallback);
      return fallback;
    }
    String id = raw;
    int colon = id.indexOf(':');
    if (colon >= 0 && colon + 1 < id.length()) {
      id = id.substring(colon + 1);
    }
    id = id.trim().toUpperCase(Locale.ROOT);
    try {
      return Material.valueOf(id);
    } catch (IllegalArgumentException e) {
      warnInvalid(name, fallback);
      return fallback;
    }
  }

  private static void warnInvalid(String name, Material fallback) {
    if (fallback != null) {
      ExortLog.warn("Invalid material '" + name + "', falling back to " + fallback);
    } else {
      ExortLog.warn("Invalid material '" + name + "'");
    }
  }
}
