package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.EnumSet;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record ChunkLoaderAuditConfig(
    boolean enabled, EnumSet<ChunkLoaderAuditEvent> events, ChunkLoaderAuditFileConfig file) {
  private static final EnumSet<ChunkLoaderAuditEvent> DEFAULT_CONSOLE_EVENTS =
      EnumSet.of(
          ChunkLoaderAuditEvent.ISSUE,
          ChunkLoaderAuditEvent.DUPLICATE,
          ChunkLoaderAuditEvent.CRAFT,
          ChunkLoaderAuditEvent.INVENTORY_MOVE,
          ChunkLoaderAuditEvent.DROP,
          ChunkLoaderAuditEvent.PICKUP,
          ChunkLoaderAuditEvent.PLACE,
          ChunkLoaderAuditEvent.BREAK,
          ChunkLoaderAuditEvent.ENABLE,
          ChunkLoaderAuditEvent.DISABLE,
          ChunkLoaderAuditEvent.DESTROY,
          ChunkLoaderAuditEvent.CLEANUP);

  public ChunkLoaderAuditConfig(boolean enabled, EnumSet<ChunkLoaderAuditEvent> events) {
    this(enabled, events, ChunkLoaderAuditFileConfig.defaults());
  }

  public ChunkLoaderAuditConfig {
    events = events == null ? EnumSet.noneOf(ChunkLoaderAuditEvent.class) : EnumSet.copyOf(events);
    file = file == null ? ChunkLoaderAuditFileConfig.defaults() : file;
  }

  public static ChunkLoaderAuditConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, (Logger) null);
  }

  public static ChunkLoaderAuditConfig fromConfig(ConfigurationSection config, Logger logger) {
    if (config == null) {
      return fromNumbers(null, null);
    }
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static ChunkLoaderAuditConfig fromNumbers(
      ConfigurationSection config, ConfigNumbers numbers) {
    boolean enabled = config == null || config.getBoolean("chunkLoader.audit.enabled", true);
    EnumSet<ChunkLoaderAuditEvent> events = EnumSet.noneOf(ChunkLoaderAuditEvent.class);
    for (ChunkLoaderAuditEvent event : ChunkLoaderAuditEvent.values()) {
      String path = "chunkLoader.audit.events." + event.configKey();
      boolean defaultEnabled = DEFAULT_CONSOLE_EVENTS.contains(event);
      if (config == null ? defaultEnabled : config.getBoolean(path, defaultEnabled)) {
        events.add(event);
      }
    }
    return new ChunkLoaderAuditConfig(
        enabled,
        events,
        config == null
            ? ChunkLoaderAuditFileConfig.defaults()
            : ChunkLoaderAuditFileConfig.fromNumbers(config, numbers));
  }

  public boolean shouldLog(ChunkLoaderAuditEvent event) {
    Objects.requireNonNull(event, "event");
    return enabled && events.contains(event);
  }

  public boolean shouldWriteFile() {
    return enabled && file.enabled();
  }
}
