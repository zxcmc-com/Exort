package com.zxcmc.exort.display;

import com.zxcmc.exort.infra.config.ConfigEnums;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public record DisplayCullingConfig(
    boolean enabled,
    Backend backend,
    int intervalTicks,
    double maxDistance,
    double forceVisibleDistance,
    int maxVisibilityChangesPerTick,
    BlockProxyConfig blockProxy,
    AdaptiveViewRangeConfig adaptiveViewRange,
    ClientCullingBypassConfig clientCullingBypass) {
  private static final String PATH = "performance.displayCulling.";

  public DisplayCullingConfig {
    intervalTicks = Math.max(1, intervalTicks);
    maxDistance = Math.max(1.0, maxDistance);
    forceVisibleDistance = Math.max(0.0, Math.min(forceVisibleDistance, maxDistance));
    maxVisibilityChangesPerTick = Math.max(1, maxVisibilityChangesPerTick);
    blockProxy = blockProxy == null ? BlockProxyConfig.defaults() : blockProxy.normalized();
    adaptiveViewRange =
        adaptiveViewRange == null
            ? AdaptiveViewRangeConfig.defaults()
            : adaptiveViewRange.normalized();
    clientCullingBypass =
        clientCullingBypass == null ? ClientCullingBypassConfig.defaults() : clientCullingBypass;
  }

  public static DisplayCullingConfig fromConfig(ConfigurationSection config) {
    return new DisplayCullingConfig(
        config.getBoolean(PATH + "enabled", true),
        Backend.fromConfig(config),
        10,
        64.0,
        8.0,
        600,
        BlockProxyConfig.defaults(),
        AdaptiveViewRangeConfig.defaults(),
        ClientCullingBypassConfig.fromConfig(config));
  }

  public enum Backend {
    AUTO,
    PACKET_EVENTS,
    PAPER;

    static Backend fromConfig(ConfigurationSection config) {
      return ConfigEnums.parse(
          PATH + "backend", config == null ? null : config.getString(PATH + "backend"), AUTO);
    }
  }

  public record BlockProxyConfig(
      boolean enabled,
      double baseRenderDistanceBlocks,
      double enterBufferBlocks,
      double restoreBufferBlocks,
      double forceRealDistance,
      int maxBlockChangesPerTick) {
    static BlockProxyConfig defaults() {
      return new BlockProxyConfig(true, 64.0, 2.0, 6.0, 8.0, 1200);
    }

    BlockProxyConfig normalized() {
      return new BlockProxyConfig(
          enabled,
          Math.max(1.0, baseRenderDistanceBlocks),
          Math.max(0.0, enterBufferBlocks),
          Math.max(0.0, restoreBufferBlocks),
          Math.max(0.0, forceRealDistance),
          Math.max(1, maxBlockChangesPerTick));
    }
  }

  public record AdaptiveViewRangeConfig(
      boolean enabled,
      List<Integer> entityThresholds,
      List<Integer> recoverThresholds,
      int denseIntervalsToStepDown,
      int stableIntervalsToStepUp,
      int stepDownCooldownTicks,
      int stepUpCooldownTicks,
      List<Double> block,
      List<Double> wire,
      List<Double> monitorContent,
      List<Double> hologram) {
    private static final List<Integer> DEFAULT_ENTITY_THRESHOLDS = List.of(320, 640, 1200);
    private static final List<Integer> DEFAULT_RECOVER_THRESHOLDS = List.of(240, 520, 960);
    private static final List<Double> DEFAULT_BLOCK = List.of(1.0, 0.9, 0.8, 0.7);
    private static final List<Double> DEFAULT_WIRE = List.of(0.5, 0.35, 0.25, 0.12);
    private static final List<Double> DEFAULT_MONITOR_CONTENT = List.of(0.45, 0.3, 0.2, 0.1);
    private static final List<Double> DEFAULT_HOLOGRAM = List.of(0.35, 0.25, 0.15, 0.1);

    static AdaptiveViewRangeConfig defaults() {
      return new AdaptiveViewRangeConfig(
          true,
          DEFAULT_ENTITY_THRESHOLDS,
          DEFAULT_RECOVER_THRESHOLDS,
          3,
          60,
          40,
          200,
          DEFAULT_BLOCK,
          DEFAULT_WIRE,
          DEFAULT_MONITOR_CONTENT,
          DEFAULT_HOLOGRAM);
    }

    public int maxLevel() {
      return Math.max(
              Math.max(block.size(), wire.size()), Math.max(monitorContent.size(), hologram.size()))
          - 1;
    }

    public int thresholdForLevel(int currentLevel) {
      if (entityThresholds.isEmpty()) {
        return Integer.MAX_VALUE;
      }
      int index = Math.max(0, Math.min(currentLevel, entityThresholds.size() - 1));
      return entityThresholds.get(index);
    }

    public int recoverThresholdForLevel(int currentLevel) {
      if (recoverThresholds.isEmpty()) {
        return 0;
      }
      int index = Math.max(0, Math.min(currentLevel - 1, recoverThresholds.size() - 1));
      return recoverThresholds.get(index);
    }

    public double rangeMultiplier(DisplayRole role, int level) {
      List<Double> values =
          switch (role == null ? DisplayRole.BLOCK : role) {
            case BLOCK -> block;
            case WIRE -> wire;
            case MONITOR_CONTENT -> monitorContent;
            case HOLOGRAM -> hologram;
          };
      if (values.isEmpty()) {
        return 1.0;
      }
      int index = Math.max(0, Math.min(level, values.size() - 1));
      return values.get(index);
    }

    AdaptiveViewRangeConfig normalized() {
      List<Integer> normalizedEntityThresholds =
          normalizeThresholds(entityThresholds, DEFAULT_ENTITY_THRESHOLDS);
      List<Integer> normalizedRecoverThresholds =
          normalizeRecoverThresholds(
              recoverThresholds, DEFAULT_RECOVER_THRESHOLDS, normalizedEntityThresholds);
      int levelCount =
          Math.max(
              normalizedEntityThresholds.size() + 1,
              Math.max(
                  Math.max(safeSize(block), safeSize(wire)),
                  Math.max(safeSize(monitorContent), safeSize(hologram))));
      return new AdaptiveViewRangeConfig(
          enabled,
          normalizedEntityThresholds,
          normalizedRecoverThresholds,
          Math.max(1, denseIntervalsToStepDown),
          Math.max(1, stableIntervalsToStepUp),
          Math.max(1, stepDownCooldownTicks),
          Math.max(1, stepUpCooldownTicks),
          normalizeRanges(block, DEFAULT_BLOCK, levelCount),
          normalizeRanges(wire, DEFAULT_WIRE, levelCount),
          normalizeRanges(monitorContent, DEFAULT_MONITOR_CONTENT, levelCount),
          normalizeRanges(hologram, DEFAULT_HOLOGRAM, levelCount));
    }

    private static int safeSize(List<?> values) {
      return values == null ? 0 : values.size();
    }

    private static List<Integer> normalizeThresholds(List<Integer> raw, List<Integer> fallback) {
      List<Integer> values = raw == null || raw.isEmpty() ? fallback : raw;
      List<Integer> normalized =
          values.stream().filter(value -> value != null && value > 0).distinct().sorted().toList();
      return normalized.isEmpty() ? fallback : normalized;
    }

    private static List<Integer> normalizeRecoverThresholds(
        List<Integer> raw, List<Integer> fallback, List<Integer> entityThresholds) {
      List<Integer> values = raw == null || raw.isEmpty() ? fallback : raw;
      List<Integer> out = new ArrayList<>();
      for (int i = 0; i < entityThresholds.size(); i++) {
        int threshold = entityThresholds.get(i);
        int value = i < values.size() && values.get(i) != null ? values.get(i) : threshold;
        out.add(Math.max(0, Math.min(value, threshold)));
      }
      return List.copyOf(out);
    }

    private static List<Double> normalizeRanges(
        List<Double> raw, List<Double> fallback, int levelCount) {
      List<Double> values = raw == null || raw.isEmpty() ? fallback : raw;
      List<Double> out = new ArrayList<>();
      for (Double value : values) {
        if (value == null || value.isNaN()) {
          continue;
        }
        out.add(Math.max(0.05, Math.min(1.0, value)));
      }
      if (out.isEmpty()) {
        out.add(1.0);
      }
      while (out.size() < levelCount) {
        out.add(out.get(out.size() - 1));
      }
      return List.copyOf(out);
    }
  }

  public record ClientCullingBypassConfig(
      boolean enabled, TranslationProbeConfig translationProbe) {
    public ClientCullingBypassConfig {
      translationProbe =
          translationProbe == null
              ? TranslationProbeConfig.defaults()
              : translationProbe.normalized();
    }

    static ClientCullingBypassConfig defaults() {
      return new ClientCullingBypassConfig(true, TranslationProbeConfig.defaults());
    }

    static ClientCullingBypassConfig fromConfig(ConfigurationSection config) {
      String path = PATH + "clientCullingBypass.";
      return new ClientCullingBypassConfig(
          config.getBoolean(path + "enabled", true),
          TranslationProbeConfig.withEnabled(config.getBoolean(path + "translationProbe", true)));
    }
  }

  public record TranslationProbeConfig(
      boolean enabled,
      boolean requireModdedBrand,
      Set<String> brandTokens,
      List<String> translationKeys,
      int joinDelayTicks,
      int retryDelayTicks,
      int maxAttempts,
      int openDelayTicks,
      int timeoutTicks) {
    private static final Set<String> DEFAULT_BRAND_TOKENS =
        Set.of("fabric", "quilt", "forge", "neoforge");
    private static final List<String> DEFAULT_TRANSLATION_KEYS =
        List.of("text.entityculling.title", "key.entityculling.toggle");

    public TranslationProbeConfig {
      brandTokens = brandTokens == null ? Set.of() : Set.copyOf(brandTokens);
      translationKeys = translationKeys == null ? List.of() : List.copyOf(translationKeys);
    }

    static TranslationProbeConfig defaults() {
      return new TranslationProbeConfig(
          true, true, DEFAULT_BRAND_TOKENS, DEFAULT_TRANSLATION_KEYS, 20, 20, 10, 2, 20);
    }

    static TranslationProbeConfig withEnabled(boolean enabled) {
      TranslationProbeConfig defaults = defaults();
      return new TranslationProbeConfig(
          enabled,
          defaults.requireModdedBrand(),
          defaults.brandTokens(),
          defaults.translationKeys(),
          defaults.joinDelayTicks(),
          defaults.retryDelayTicks(),
          defaults.maxAttempts(),
          defaults.openDelayTicks(),
          defaults.timeoutTicks());
    }

    TranslationProbeConfig normalized() {
      return new TranslationProbeConfig(
          enabled,
          requireModdedBrand,
          stringSet(brandTokens.stream().toList(), DEFAULT_BRAND_TOKENS),
          stringList(translationKeys, DEFAULT_TRANSLATION_KEYS),
          Math.max(1, joinDelayTicks),
          Math.max(1, retryDelayTicks),
          Math.max(1, maxAttempts),
          Math.max(1, openDelayTicks),
          Math.max(5, timeoutTicks));
    }

    private static Set<String> stringSet(List<String> raw, Set<String> fallback) {
      Set<String> out = new LinkedHashSet<>();
      if (raw != null) {
        for (String value : raw) {
          if (value != null && !value.isBlank()) {
            out.add(value.trim().toLowerCase(Locale.ROOT));
          }
        }
      }
      return out.isEmpty() ? fallback : Set.copyOf(out);
    }

    private static List<String> stringList(List<String> raw, List<String> fallback) {
      List<String> out = new ArrayList<>();
      if (raw != null) {
        for (String value : raw) {
          if (value != null && !value.isBlank()) {
            out.add(value.trim());
          }
        }
      }
      return out.isEmpty() ? fallback : List.copyOf(out);
    }
  }
}
