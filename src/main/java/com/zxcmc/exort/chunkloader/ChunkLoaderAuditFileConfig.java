package com.zxcmc.exort.chunkloader;

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
    return fromConfig(config, null);
  }

  public static ChunkLoaderAuditFileConfig fromConfig(ConfigurationSection config, Logger logger) {
    if (config == null) {
      return defaults();
    }
    long rawMaxSize = config.getLong("chunkLoader.audit.file.maxSizeBytes", DEFAULT_MAX_SIZE_BYTES);
    int rawMaxFiles = config.getInt("chunkLoader.audit.file.maxFiles", DEFAULT_MAX_FILES);
    ChunkLoaderAuditFileConfig result =
        new ChunkLoaderAuditFileConfig(
            config.getBoolean("chunkLoader.audit.file.enabled", true),
            config.getString("chunkLoader.audit.file.path", DEFAULT_PATH),
            rawMaxSize,
            rawMaxFiles);
    if (logger != null && rawMaxSize != result.maxSizeBytes()) {
      logger.warning(
          "chunkLoader.audit.file.maxSizeBytes="
              + rawMaxSize
              + " is outside 1.."
              + MAX_SIZE_BYTES
              + "; using "
              + result.maxSizeBytes()
              + ".");
    }
    if (logger != null && rawMaxFiles != result.maxFiles()) {
      logger.warning(
          "chunkLoader.audit.file.maxFiles="
              + rawMaxFiles
              + " is outside 1.."
              + MAX_FILES
              + "; using "
              + result.maxFiles()
              + ".");
    }
    return result;
  }
}
