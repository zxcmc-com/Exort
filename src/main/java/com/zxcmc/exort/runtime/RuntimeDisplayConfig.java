package com.zxcmc.exort.runtime;

import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record RuntimeDisplayConfig(
    Material displayBaseMaterial,
    double displayScale,
    double offsetX,
    double offsetY,
    double offsetZ) {
  public static RuntimeDisplayConfig fromConfig(
      ConfigurationSection config,
      boolean resourceMode,
      String resourcePath,
      MaterialResolver materialResolver) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(resourcePath, "resourcePath");
    Objects.requireNonNull(materialResolver, "materialResolver");
    if (!resourceMode) {
      return vanilla();
    }
    return new RuntimeDisplayConfig(
        materialResolver.resolve(
            config.getString(resourcePath + ".displayBaseMaterial", "PAPER"), Material.PAPER),
        config.getDouble(resourcePath + ".displayScale", 1.0),
        config.getDouble(resourcePath + ".displayOffset.x", 0.5),
        config.getDouble(resourcePath + ".displayOffset.y", 0.5),
        config.getDouble(resourcePath + ".displayOffset.z", 0.5));
  }

  private static RuntimeDisplayConfig vanilla() {
    return new RuntimeDisplayConfig(Material.PAPER, 1.0, 0.5, 0.5, 0.5);
  }

  @FunctionalInterface
  public interface MaterialResolver {
    Material resolve(String name, Material fallback);
  }
}
