package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.MonitorDisplayManager;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record RuntimeMonitorScreenConfig(
    MonitorDisplayManager.ScreenConfig item,
    MonitorDisplayManager.ScreenConfig block,
    MonitorDisplayManager.ScreenConfig thinBlock,
    MonitorDisplayManager.ScreenConfig horizontalBlock,
    MonitorDisplayManager.ScreenConfig fullBlock,
    MonitorDisplayManager.ScreenConfig text,
    MonitorDisplayManager.ScreenConfig textEmpty,
    int textBackgroundAlpha) {
  public RuntimeMonitorScreenConfig {
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(block, "block");
    Objects.requireNonNull(thinBlock, "thinBlock");
    Objects.requireNonNull(horizontalBlock, "horizontalBlock");
    Objects.requireNonNull(fullBlock, "fullBlock");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(textEmpty, "textEmpty");
  }

  public static RuntimeMonitorScreenConfig fromConfig(
      ConfigurationSection config, boolean resourceMode) {
    Objects.requireNonNull(config, "config");
    String basePath = resourceMode ? "resourceMode.monitor" : "vanillaMode.monitor";
    MonitorDefaults defaults = MonitorDefaults.forMode(resourceMode);

    var item =
        screenConfig(
            config, basePath + ".screenItem", 0.5, defaults.itemY(), defaults.itemZ(), 0.35);
    var block =
        screenConfig(
            config, basePath + ".screenBlock", 0.5, defaults.blockY(), defaults.blockZ(), 0.6);
    var thinBlock =
        screenConfig(
            config,
            basePath + ".screenThinBlock",
            0.5,
            defaults.thinBlockY(),
            defaults.thinBlockZ(),
            0.3);
    var horizontalBlock =
        screenConfig(
            config,
            basePath + ".screenHorizontalBlock",
            0.5,
            defaults.horizontalBlockY(),
            defaults.horizontalBlockZ(),
            0.4);
    var fullBlock =
        screenConfig(
            config,
            basePath + ".screenFullBlock",
            0.5,
            defaults.fullBlockY(),
            defaults.fullBlockZ(),
            0.58);
    var text =
        screenConfig(
            config, basePath + ".screenText", 0.51, defaults.textY(), defaults.textZ(), 0.55);
    var textEmpty =
        screenConfig(
            config,
            basePath + ".screenTextEmpty",
            text.offsetX(),
            defaults.textEmptyY(),
            text.offsetZ(),
            defaults.textEmptyScale());

    return new RuntimeMonitorScreenConfig(
        item, block, thinBlock, horizontalBlock, fullBlock, text, textEmpty, 0);
  }

  private static MonitorDisplayManager.ScreenConfig screenConfig(
      ConfigurationSection config,
      String path,
      double defaultX,
      double defaultY,
      double defaultZ,
      double defaultScale) {
    return new MonitorDisplayManager.ScreenConfig(
        config.getDouble(path + ".offset.x", defaultX),
        config.getDouble(path + ".offset.y", defaultY),
        config.getDouble(path + ".offset.z", defaultZ),
        config.getDouble(path + ".scale", defaultScale));
  }

  private record MonitorDefaults(
      double itemY,
      double itemZ,
      double blockY,
      double blockZ,
      double thinBlockY,
      double thinBlockZ,
      double horizontalBlockY,
      double horizontalBlockZ,
      double fullBlockY,
      double fullBlockZ,
      double textY,
      double textZ,
      double textEmptyY,
      double textEmptyScale) {
    private static MonitorDefaults forMode(boolean resourceMode) {
      if (resourceMode) {
        return new MonitorDefaults(
            0.56, 0.93, 0.56, 0.93, 0.56, 0.93, 0.55, 1.026, 0.56, 0.815, 0.26, 0.95, 0.41, 0.7);
      }
      return new MonitorDefaults(
          0.62, 0.99, 0.62, 0.99, 0.62, 0.99, 0.61, 1.032, 0.62, 0.875, 0.2, 1.01, 0.41, 0.8);
    }
  }
}
