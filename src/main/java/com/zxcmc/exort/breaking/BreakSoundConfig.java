package com.zxcmc.exort.breaking;

import java.util.Locale;

public final class BreakSoundConfig {
  private static final double RANGE = 16.0;
  private static final float PITCH = 0.8f;

  private final boolean enabled;
  private final float volume;
  private final SoundProfile storage;
  private final SoundProfile terminal;
  private final SoundProfile monitor;
  private final SoundProfile bus;
  private final SoundProfile bridge;
  private final SoundProfile wire;

  private BreakSoundConfig(
      boolean enabled,
      float volume,
      SoundProfile storage,
      SoundProfile terminal,
      SoundProfile monitor,
      SoundProfile bus,
      SoundProfile bridge,
      SoundProfile wire) {
    this.enabled = enabled;
    this.volume = volume;
    this.storage = storage;
    this.terminal = terminal;
    this.monitor = monitor;
    this.bus = bus;
    this.bridge = bridge;
    this.wire = wire;
  }

  public boolean enabled() {
    return enabled;
  }

  public double range() {
    return RANGE;
  }

  public float volume() {
    return volume;
  }

  public float hitVolume() {
    return Math.max(0f, volume - 0.4f);
  }

  public float pitch() {
    return PITCH;
  }

  public String hitKey(BreakType type) {
    return profile(type).hitKey();
  }

  public String breakKey(BreakType type) {
    return profile(type).breakKey();
  }

  public String placeKey(BreakType type) {
    return profile(type).placeKey();
  }

  private SoundProfile profile(BreakType type) {
    return switch (type) {
      case TERMINAL -> terminal;
      case MONITOR -> monitor;
      case BUS -> bus;
      case BRIDGE -> bridge;
      case WIRE -> wire;
      case STORAGE -> storage;
      default -> storage;
    };
  }

  public static BreakSoundConfig defaults() {
    boolean enabled = true;
    float volume = 0.8f;
    SoundProfile storage = profile("block.vault.hit", "block.vault.break", "block.vault.place");
    SoundProfile terminal = profile("block.iron.hit", "block.iron.break", "block.iron.place");
    SoundProfile monitor = profile("block.iron.hit", "block.iron.break", "block.iron.place");
    SoundProfile bus = profile("block.iron.hit", "block.iron.break", "block.iron.place");
    SoundProfile bridge = profile("block.iron.hit", "block.iron.break", "block.iron.place");
    SoundProfile wire = profile("block.glass.hit", "block.glass.break", "block.glass.place");
    return new BreakSoundConfig(enabled, volume, storage, terminal, monitor, bus, bridge, wire);
  }

  private static SoundProfile profile(String hitKey, String breakKey, String placeKey) {
    return new SoundProfile(normalizeKey(hitKey), normalizeKey(breakKey), normalizeKey(placeKey));
  }

  private static String normalizeKey(String raw) {
    if (raw == null) return null;
    String normalized = raw.trim();
    if (normalized.contains(":")) {
      return normalized.toLowerCase(Locale.ROOT);
    }
    return "minecraft:" + normalized.toLowerCase(Locale.ROOT);
  }

  private record SoundProfile(String hitKey, String breakKey, String placeKey) {}
}
