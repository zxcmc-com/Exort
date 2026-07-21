package com.zxcmc.exort.gui;

import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record GuiRuntimeConfig(
    long sessionDeviceCheckIntervalTicks,
    long craftingConfirmTimeoutMs,
    int indexEntriesPerTick,
    int indexBudgetMicros) {
  private static final long CRAFTING_CONFIRM_TIMEOUT_MS = 10_000L;
  static final long MAX_SESSION_DEVICE_CHECK_INTERVAL_TICKS = 1_200L;

  public static GuiRuntimeConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, null);
  }

  public static GuiRuntimeConfig fromConfig(ConfigurationSection config, Logger logger) {
    Objects.requireNonNull(config, "config");
    return fromConfig(new ConfigNumbers(config, logger));
  }

  public static GuiRuntimeConfig fromConfig(ConfigNumbers numbers) {
    Objects.requireNonNull(numbers, "numbers");
    return new GuiRuntimeConfig(
        numbers.longInteger(
            "performance.sessionDeviceCheckIntervalTicks",
            5L,
            1L,
            MAX_SESSION_DEVICE_CHECK_INTERVAL_TICKS),
        CRAFTING_CONFIRM_TIMEOUT_MS,
        (int) numbers.longInteger("performance.gui.indexEntriesPerTick", 256, 32, 4096),
        (int) numbers.longInteger("performance.gui.indexBudgetMicros", 2000, 250, 10000));
  }
}
