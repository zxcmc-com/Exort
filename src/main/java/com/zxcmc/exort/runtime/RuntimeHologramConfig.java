package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.ItemHologramManager;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record RuntimeHologramConfig(
    ItemHologramManager.Config terminal, ItemHologramManager.Config storage) {
  public RuntimeHologramConfig {
    Objects.requireNonNull(terminal, "terminal");
    Objects.requireNonNull(storage, "storage");
  }

  public static RuntimeHologramConfig fromConfig(
      ConfigurationSection config, boolean resourceMode) {
    Objects.requireNonNull(config, "config");
    return new RuntimeHologramConfig(
        terminalConfig(config, resourceMode), storageConfig(config, resourceMode));
  }

  private static ItemHologramManager.Config terminalConfig(
      ConfigurationSection config, boolean resourceMode) {
    if (resourceMode) {
      return new ItemHologramManager.Config(false, 0.5, 0.95, 0.5, 0.35);
    }
    return new ItemHologramManager.Config(
        config.getBoolean("vanillaMode.terminalHologram.enabled", true),
        config.getDouble("vanillaMode.terminalHologram.offset.x", 0.5),
        config.getDouble("vanillaMode.terminalHologram.offset.y", 0.5),
        config.getDouble("vanillaMode.terminalHologram.offset.z", 0.83),
        config.getDouble("vanillaMode.terminalHologram.scale", 0.35));
  }

  private static ItemHologramManager.Config storageConfig(
      ConfigurationSection config, boolean resourceMode) {
    String base = resourceMode ? "resourceMode.storageHologram" : "vanillaMode.storageHologram";
    return new ItemHologramManager.Config(
        config.getBoolean(base + ".enabled", true),
        config.getDouble(base + ".offset.x", 0.5),
        config.getDouble(base + ".offset.y", 0.5),
        config.getDouble(base + ".offset.z", 0.5),
        config.getDouble(base + ".scale", 0.35));
  }
}
