package com.zxcmc.exort.chunkloader;

import java.util.EnumSet;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public record ChunkLoaderAuditConfig(
    boolean enabled, EnumSet<ChunkLoaderAuditEvent> events, ChunkLoaderAuditFileConfig file) {
  private static final EnumSet<ChunkLoaderAuditEvent> DEFAULT_CONSOLE_EVENTS =
      EnumSet.of(
          ChunkLoaderAuditEvent.ISSUE,
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
        enabled, events, ChunkLoaderAuditFileConfig.fromConfig(config));
  }

  public boolean shouldLog(ChunkLoaderAuditEvent event) {
    Objects.requireNonNull(event, "event");
    return enabled && events.contains(event);
  }

  public boolean shouldWriteFile() {
    return enabled && file.enabled();
  }
}
