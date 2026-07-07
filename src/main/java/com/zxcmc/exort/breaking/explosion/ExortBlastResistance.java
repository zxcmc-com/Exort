package com.zxcmc.exort.breaking.explosion;

import com.zxcmc.exort.breaking.BreakType;
import org.bukkit.configuration.file.FileConfiguration;

final class ExortBlastResistance {
  public static final float STORAGE = 1200.0F;
  public static final float TERMINAL = 9.0F;
  public static final float MONITOR = 9.0F;
  public static final float BUS = 10.0F;
  public static final float RELAY = 50.0F;
  public static final float TRANSMITTER = 50.0F;
  public static final float CHUNK_LOADER = 50.0F;
  public static final float WIRE = 6.0F;

  private final float storage;
  private final float terminal;
  private final float monitor;
  private final float bus;
  private final float relay;
  private final float transmitter;
  private final float chunkLoader;
  private final float wire;

  ExortBlastResistance(
      float storage,
      float terminal,
      float monitor,
      float bus,
      float relay,
      float transmitter,
      float chunkLoader,
      float wire) {
    this.storage = sanitize(storage, STORAGE);
    this.terminal = sanitize(terminal, TERMINAL);
    this.monitor = sanitize(monitor, MONITOR);
    this.bus = sanitize(bus, BUS);
    this.relay = sanitize(relay, RELAY);
    this.transmitter = sanitize(transmitter, TRANSMITTER);
    this.chunkLoader = sanitize(chunkLoader, CHUNK_LOADER);
    this.wire = sanitize(wire, WIRE);
  }

  static ExortBlastResistance defaults() {
    return new ExortBlastResistance(
        STORAGE, TERMINAL, MONITOR, BUS, RELAY, TRANSMITTER, CHUNK_LOADER, WIRE);
  }

  static ExortBlastResistance fromConfig(FileConfiguration config) {
    if (config == null) {
      return defaults();
    }
    return new ExortBlastResistance(
        configured(config, "break.storage.blastResistance", STORAGE),
        configured(config, "break.terminal.blastResistance", TERMINAL),
        configured(config, "break.monitor.blastResistance", MONITOR),
        configured(config, "break.bus.blastResistance", BUS),
        configured(config, "break.relay.blastResistance", RELAY),
        configured(config, "break.transmitter.blastResistance", TRANSMITTER),
        configured(config, "break.chunkLoader.blastResistance", CHUNK_LOADER),
        configured(config, "break.wire.blastResistance", WIRE));
  }

  float forBreakType(BreakType type) {
    return switch (type) {
      case STORAGE -> storage;
      case TERMINAL -> terminal;
      case MONITOR -> monitor;
      case BUS -> bus;
      case RELAY -> relay;
      case TRANSMITTER -> transmitter;
      case CHUNK_LOADER -> chunkLoader;
      case WIRE -> wire;
      default -> STORAGE;
    };
  }

  private static float configured(FileConfiguration config, String path, float fallback) {
    return sanitize((float) config.getDouble(path, fallback), fallback);
  }

  private static float sanitize(float value, float fallback) {
    return Float.isFinite(value) && value >= 0.0F ? value : fallback;
  }
}
