package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.device.MonitorDisplayManager;
import java.util.Objects;

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

  public static RuntimeMonitorScreenConfig forMode(boolean resourceMode) {
    MonitorDefaults defaults = MonitorDefaults.forMode(resourceMode);

    var item = screenConfig(0.5, defaults.itemY(), defaults.itemZ(), 0.35);
    var block = screenConfig(0.5, defaults.blockY(), defaults.blockZ(), 0.6);
    var thinBlock = screenConfig(0.5, defaults.thinBlockY(), defaults.thinBlockZ(), 0.3);
    var horizontalBlock =
        screenConfig(0.5, defaults.horizontalBlockY(), defaults.horizontalBlockZ(), 0.4);
    var fullBlock = screenConfig(0.5, defaults.fullBlockY(), defaults.fullBlockZ(), 0.58);
    var text = screenConfig(0.51, defaults.textY(), defaults.textZ(), 0.55);
    var textEmpty =
        screenConfig(
            text.offsetX(), defaults.textEmptyY(), text.offsetZ(), defaults.textEmptyScale());

    return new RuntimeMonitorScreenConfig(
        item, block, thinBlock, horizontalBlock, fullBlock, text, textEmpty, 0);
  }

  private static MonitorDisplayManager.ScreenConfig screenConfig(
      double defaultX, double defaultY, double defaultZ, double defaultScale) {
    return new MonitorDisplayManager.ScreenConfig(defaultX, defaultY, defaultZ, defaultScale);
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
