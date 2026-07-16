package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.infra.config.ConfigNumbers;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record ChunkLoaderAuditFileConfig(
    boolean enabled, String path, long maxSizeBytes, int maxFiles) {
  public static final String DEFAULT_PATH = "logs/chunkloaders.log";
  public static final long DEFAULT_MAX_SIZE_BYTES = 10L * 1024L * 1024L;
  public static final int DEFAULT_MAX_FILES = 10;
  public static final long MAX_SIZE_BYTES = 64L * 1024L * 1024L;
  public static final int MAX_FILES = 32;

  public ChunkLoaderAuditFileConfig {
    path = path == null || path.isBlank() ? DEFAULT_PATH : path.trim();
    maxSizeBytes = Math.max(1L, Math.min(MAX_SIZE_BYTES, maxSizeBytes));
    maxFiles = Math.max(1, Math.min(MAX_FILES, maxFiles));
  }

  public static ChunkLoaderAuditFileConfig defaults() {
    return new ChunkLoaderAuditFileConfig(
        true, DEFAULT_PATH, DEFAULT_MAX_SIZE_BYTES, DEFAULT_MAX_FILES);
  }

  public static ChunkLoaderAuditFileConfig disabled() {
    return new ChunkLoaderAuditFileConfig(
        false, DEFAULT_PATH, DEFAULT_MAX_SIZE_BYTES, DEFAULT_MAX_FILES);
  }

  public static ChunkLoaderAuditFileConfig fromConfig(ConfigurationSection config) {
    return fromConfig(config, (Logger) null);
  }

  public static ChunkLoaderAuditFileConfig fromConfig(ConfigurationSection config, Logger logger) {
    if (config == null) {
      return defaults();
    }
    return fromNumbers(config, new ConfigNumbers(config, logger));
  }

  public static ChunkLoaderAuditFileConfig fromNumbers(
      ConfigurationSection config, ConfigNumbers numbers) {
    return new ChunkLoaderAuditFileConfig(
        config.getBoolean("chunkLoader.audit.file.enabled", true),
        config.getString("chunkLoader.audit.file.path", DEFAULT_PATH),
        numbers.longInteger(
            "chunkLoader.audit.file.maxSizeBytes", DEFAULT_MAX_SIZE_BYTES, 1, MAX_SIZE_BYTES),
        numbers.integer("chunkLoader.audit.file.maxFiles", DEFAULT_MAX_FILES, 1, MAX_FILES));
  }
}
