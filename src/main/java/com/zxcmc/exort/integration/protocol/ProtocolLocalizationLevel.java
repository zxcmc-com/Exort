package com.zxcmc.exort.integration.protocol;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public enum ProtocolLocalizationLevel {
  SIMPLE,
  FULL;

  private static final String PATH = "protocolLib.localization.level";

  public static ProtocolLocalizationLevel fromConfig(ConfigurationSection config) {
    return fromString(config == null ? null : config.getString(PATH, SIMPLE.name()));
  }

  public static ProtocolLocalizationLevel fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return SIMPLE;
    }
    try {
      return ProtocolLocalizationLevel.valueOf(
          raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException ignored) {
      return SIMPLE;
    }
  }
}
