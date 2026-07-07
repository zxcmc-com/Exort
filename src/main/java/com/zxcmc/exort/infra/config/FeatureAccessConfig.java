package com.zxcmc.exort.infra.config;

import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public record FeatureAccessConfig(
    boolean relayEnabled, boolean chunkLoaderEnabled, boolean wirelessEnabled) {
  public static FeatureAccessConfig defaults() {
    return new FeatureAccessConfig(true, true, true);
  }

  public static FeatureAccessConfig fromConfig(ConfigurationSection config) {
    if (config == null) {
      return defaults();
    }
    return new FeatureAccessConfig(
        config.getBoolean("relay.enabled", true),
        config.getBoolean("chunkLoader.enabled", true),
        config.getBoolean("wireless.enabled", true));
  }

  public boolean allowsRecipeResult(String rawItemId) {
    return isCatalogVisible(rawItemId);
  }

  public boolean isCatalogVisible(String rawItemId) {
    return switch (feature(normalize(rawItemId))) {
      case RELAY -> relayEnabled;
      case CHUNK_LOADER -> chunkLoaderEnabled;
      case WIRELESS -> wirelessEnabled;
      case NONE -> true;
    };
  }

  private static String normalize(String rawItemId) {
    if (rawItemId == null) {
      return "";
    }
    String id = rawItemId.trim().toLowerCase(Locale.ROOT);
    if (id.startsWith("exort:")) {
      id = id.substring("exort:".length());
    }
    return id;
  }

  private static Feature feature(String id) {
    return switch (id) {
      case "relay" -> Feature.RELAY;
      case "chunk_loader", "personal_chunk_loader", "dormant_chunk_loader" -> Feature.CHUNK_LOADER;
      case "transmitter", "wireless_terminal" -> Feature.WIRELESS;
      default -> Feature.NONE;
    };
  }

  private enum Feature {
    RELAY,
    CHUNK_LOADER,
    WIRELESS,
    NONE
  }
}
