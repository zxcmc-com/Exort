package com.zxcmc.exort.runtime;

import com.zxcmc.exort.display.device.ItemHologramManager;
import java.util.Objects;

public record RuntimeHologramConfig(
    ItemHologramManager.Config terminal, ItemHologramManager.Config storage) {
  public RuntimeHologramConfig {
    Objects.requireNonNull(terminal, "terminal");
    Objects.requireNonNull(storage, "storage");
  }

  public static RuntimeHologramConfig forMode(boolean resourceMode) {
    return new RuntimeHologramConfig(terminalConfig(resourceMode), storageConfig());
  }

  private static ItemHologramManager.Config terminalConfig(boolean resourceMode) {
    if (resourceMode) {
      return new ItemHologramManager.Config(false, 0.5, 0.95, 0.5, 0.35);
    }
    return new ItemHologramManager.Config(true, 0.5, 0.5, 0.83, 0.35);
  }

  private static ItemHologramManager.Config storageConfig() {
    return new ItemHologramManager.Config(true, 0.5, 0.5, 0.5, 0.35);
  }
}
