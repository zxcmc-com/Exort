package com.zxcmc.exort.wireless;

import com.zxcmc.exort.infra.config.ConfigNumbers;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record WirelessRuntimeConfig(
    boolean enabled, int rangeBlocks, Map<WirelessBoosterTier, Double> boosterRangeMultipliers) {
  public static final int MAX_RANGE_BLOCKS = 32_768;
  public static final int DEFAULT_RANGE_BLOCKS = 48;

  public WirelessRuntimeConfig {
    EnumMap<WirelessBoosterTier, Double> normalized = new EnumMap<>(WirelessBoosterTier.class);
    for (WirelessBoosterTier tier : WirelessBoosterTier.values()) {
      double configured =
          boosterRangeMultipliers == null
              ? tier.defaultRangeMultiplier()
              : boosterRangeMultipliers.getOrDefault(tier, tier.defaultRangeMultiplier());
      normalized.put(tier, sanitizeMultiplier(configured, tier.defaultRangeMultiplier()));
    }
    boosterRangeMultipliers = Collections.unmodifiableMap(normalized);
  }

  public static WirelessRuntimeConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, (Logger) null);
  }

  public static WirelessRuntimeConfig fromConfig(ConfigurationSection config, Logger logger) {
    Objects.requireNonNull(config, "config");
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static WirelessRuntimeConfig fromNumbers(
      ConfigurationSection config, ConfigNumbers numbers) {
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(numbers, "numbers");
    EnumMap<WirelessBoosterTier, Double> multipliers = new EnumMap<>(WirelessBoosterTier.class);
    for (WirelessBoosterTier tier : WirelessBoosterTier.values()) {
      String path = "wireless.boosters.rangeMultipliers." + tier.id();
      multipliers.put(tier, readMultiplier(config, numbers, path, tier.defaultRangeMultiplier()));
    }
    return new WirelessRuntimeConfig(
        config.getBoolean("wireless.enabled", true),
        numbers.integer("wireless.rangeBlocks", DEFAULT_RANGE_BLOCKS, 0, MAX_RANGE_BLOCKS),
        multipliers);
  }

  public static WirelessRuntimeConfig defaults() {
    return new WirelessRuntimeConfig(true, DEFAULT_RANGE_BLOCKS, Map.of());
  }

  public double boosterRangeMultiplier(WirelessBoosterTier tier) {
    if (tier == null) {
      return 1.0D;
    }
    return boosterRangeMultipliers.getOrDefault(tier, tier.defaultRangeMultiplier());
  }

  public boolean isGlobal(WirelessBoosterTier tier) {
    return tier != null && Double.compare(boosterRangeMultiplier(tier), -1.0D) == 0;
  }

  public int effectiveRangeBlocks(WirelessBoosterTier tier) {
    if (isGlobal(tier)) {
      return -1;
    }
    double multiplier = tier == null ? 1.0D : boosterRangeMultiplier(tier);
    long rounded = Math.round(rangeBlocks * multiplier);
    return (int) Math.max(0L, Math.min(MAX_RANGE_BLOCKS, rounded));
  }

  public int maxFiniteRangeBlocks() {
    int maximum = rangeBlocks;
    for (WirelessBoosterTier tier : WirelessBoosterTier.values()) {
      int effective = effectiveRangeBlocks(tier);
      if (effective >= 0) {
        maximum = Math.max(maximum, effective);
      }
    }
    return maximum;
  }

  private static double readMultiplier(
      ConfigurationSection config, ConfigNumbers numbers, String path, double fallback) {
    Object raw = config.get(path);
    if (raw == null) {
      return fallback;
    }
    if (raw instanceof Number number
        && Double.isFinite(number.doubleValue())
        && Double.compare(number.doubleValue(), -1.0D) == 0) {
      return -1.0D;
    }
    return numbers.decimal(path, fallback, 0.0D, Double.MAX_VALUE);
  }

  private static double sanitizeMultiplier(double value, double fallback) {
    if (!Double.isFinite(value)) {
      return fallback;
    }
    if (Double.compare(value, -1.0D) == 0) {
      return -1.0D;
    }
    return Math.max(0.0D, value);
  }
}
