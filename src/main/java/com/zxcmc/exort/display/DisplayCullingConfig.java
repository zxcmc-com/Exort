package com.zxcmc.exort.display;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

public record DisplayCullingConfig(
    boolean enabled,
    Backend backend,
    int intervalTicks,
    double maxDistance,
    double forceVisibleDistance,
    int maxVisibilityChangesPerTick,
    AdaptiveViewRangeConfig adaptiveViewRange,
    ClientCullingBypassConfig clientCullingBypass) {
  private static final String PATH = "performance.displayCulling.";

  public DisplayCullingConfig {
    intervalTicks = Math.max(1, intervalTicks);
    maxDistance = Math.max(1.0, maxDistance);
    forceVisibleDistance = Math.max(0.0, Math.min(forceVisibleDistance, maxDistance));
    maxVisibilityChangesPerTick = Math.max(1, maxVisibilityChangesPerTick);
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
        Backend.fromString(config.getString(PATH + "backend", "AUTO")),
        config.getInt(PATH + "intervalTicks", 10),
        config.getDouble(PATH + "maxDistance", 64.0),
        config.getDouble(PATH + "forceVisibleDistance", 6.0),
        config.getInt(PATH + "maxVisibilityChangesPerTick", 600),
        AdaptiveViewRangeConfig.fromConfig(config),
        ClientCullingBypassConfig.fromConfig(config));
  }

  public enum Backend {
    AUTO,
    PROTOCOL_LIB,
    PAPER;

    static Backend fromString(String raw) {
      if (raw == null || raw.isBlank()) {
        return AUTO;
      }
      try {
        return Backend.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException ignored) {
        return AUTO;
      }
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

    static AdaptiveViewRangeConfig fromConfig(ConfigurationSection config) {
      String path = PATH + "adaptiveViewRange.";
      AdaptiveViewRangeConfig defaults = defaults();
      return new AdaptiveViewRangeConfig(
          config.getBoolean(path + "enabled", true),
          intList(config, path + "entityThresholds", defaults.entityThresholds()),
          intList(config, path + "recoverThresholds", defaults.recoverThresholds()),
          config.getInt(path + "denseIntervalsToStepDown", 3),
          config.getInt(path + "stableIntervalsToStepUp", 60),
          config.getInt(path + "stepDownCooldownTicks", 40),
          config.getInt(path + "stepUpCooldownTicks", 200),
          doubleList(config, path + "roleRanges.block", defaults.block()),
          doubleList(config, path + "roleRanges.wire", defaults.wire()),
          doubleList(config, path + "roleRanges.monitorContent", defaults.monitorContent()),
          doubleList(config, path + "roleRanges.hologram", defaults.hologram()));
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

    private static List<Integer> intList(
        ConfigurationSection config, String path, List<Integer> fallback) {
      List<Integer> values = config.getIntegerList(path);
      return values.isEmpty() ? fallback : values;
    }

    private static List<Double> doubleList(
        ConfigurationSection config, String path, List<Double> fallback) {
      List<Double> values = config.getDoubleList(path);
      return values.isEmpty() ? fallback : values;
    }
  }

  public record ClientCullingBypassConfig(boolean enabled, Set<UUID> players) {
    static ClientCullingBypassConfig defaults() {
      return new ClientCullingBypassConfig(true, Set.of());
    }

    static ClientCullingBypassConfig fromConfig(ConfigurationSection config) {
      String path = PATH + "clientCullingBypass.";
      List<String> rawPlayers = config.getStringList(path + "players");
      Set<UUID> players = new LinkedHashSet<>();
      for (String raw : rawPlayers) {
        if (raw == null || raw.isBlank()) {
          continue;
        }
        try {
          players.add(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
          // Ignore stale or hand-edited invalid UUIDs.
        }
      }
      return new ClientCullingBypassConfig(
          config.getBoolean(path + "enabled", true), Set.copyOf(players));
    }

    List<String> playerStrings() {
      List<String> out = new ArrayList<>();
      players.stream().map(UUID::toString).sorted().forEach(out::add);
      return out;
    }
  }
}
