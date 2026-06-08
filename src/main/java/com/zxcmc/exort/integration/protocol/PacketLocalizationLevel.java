package com.zxcmc.exort.integration.protocol;

import com.zxcmc.exort.infra.config.ConfigEnums;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public enum PacketLocalizationLevel {
  SIMPLE,
  FULL;

  private static final String PATH = "packetEvents.localizationLevel";

  public static PacketLocalizationLevel fromConfig(ConfigurationSection config) {
    return ConfigEnums.parse(PATH, config == null ? null : config.getString(PATH), SIMPLE);
  }

  public static PacketLocalizationLevel fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return SIMPLE;
    }
    try {
      return PacketLocalizationLevel.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (IllegalArgumentException ignored) {
      return SIMPLE;
    }
  }
}
