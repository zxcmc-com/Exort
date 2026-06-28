package com.zxcmc.exort.chunkloader;

import org.bukkit.configuration.ConfigurationSection;

public record ChunkLoaderAuditFileConfig(
    boolean enabled, String path, long maxSizeBytes, int maxFiles) {
  public static final String DEFAULT_PATH = "logs/chunkloaders.log";
  public static final long DEFAULT_MAX_SIZE_BYTES = 10L * 1024L * 1024L;
  public static final int DEFAULT_MAX_FILES = 10;

  public ChunkLoaderAuditFileConfig {
    path = path == null || path.isBlank() ? DEFAULT_PATH : path.trim();
    maxSizeBytes = Math.max(1L, maxSizeBytes);
    maxFiles = Math.max(1, maxFiles);
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
    if (config == null) {
      return defaults();
    }
    return new ChunkLoaderAuditFileConfig(
        config.getBoolean("chunkLoader.audit.file.enabled", true),
        config.getString("chunkLoader.audit.file.path", DEFAULT_PATH),
        config.getLong("chunkLoader.audit.file.maxSizeBytes", DEFAULT_MAX_SIZE_BYTES),
        config.getInt("chunkLoader.audit.file.maxFiles", DEFAULT_MAX_FILES));
  }
}
