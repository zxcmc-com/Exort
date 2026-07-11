package com.zxcmc.exort.chunkloader;

import java.util.function.IntUnaryOperator;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public record ChunkLoaderLimits(
    int maxActiveLoaders,
    int maxActiveLoadersPerWorld,
    int maxActiveLoadersPerPlayer,
    int maxUniqueChunks,
    int maxUniqueChunksPerWorld) {
  public static final int MIN_ACTIVE_LOADERS = 1;
  public static final int MAX_ACTIVE_LOADERS = 1_024;
  public static final int MIN_UNIQUE_CHUNKS = 1;
  public static final int MAX_UNIQUE_CHUNKS = 8_192;

  public static final int DEFAULT_MAX_ACTIVE_LOADERS = 128;
  public static final int DEFAULT_MAX_ACTIVE_LOADERS_PER_WORLD = 64;
  public static final int DEFAULT_MAX_ACTIVE_LOADERS_PER_PLAYER = 8;
  public static final int DEFAULT_MAX_UNIQUE_CHUNKS = 2_048;
  public static final int DEFAULT_MAX_UNIQUE_CHUNKS_PER_WORLD = 1_024;

  public ChunkLoaderLimits {
    maxActiveLoaders = clampActiveLoaders(maxActiveLoaders);
    maxActiveLoadersPerWorld = clampActiveLoaders(maxActiveLoadersPerWorld);
    maxActiveLoadersPerPlayer = clampActiveLoaders(maxActiveLoadersPerPlayer);
    maxUniqueChunks = clampUniqueChunks(maxUniqueChunks);
    maxUniqueChunksPerWorld = clampUniqueChunks(maxUniqueChunksPerWorld);
  }

  static ChunkLoaderLimits fromConfig(ConfigurationSection config, Logger logger) {
    return new ChunkLoaderLimits(
        readBounded(
            config,
            logger,
            "chunkLoader.limits.maxActiveLoaders",
            DEFAULT_MAX_ACTIVE_LOADERS,
            ChunkLoaderLimits::clampActiveLoaders),
        readBounded(
            config,
            logger,
            "chunkLoader.limits.maxActiveLoadersPerWorld",
            DEFAULT_MAX_ACTIVE_LOADERS_PER_WORLD,
            ChunkLoaderLimits::clampActiveLoaders),
        readBounded(
            config,
            logger,
            "chunkLoader.limits.maxActiveLoadersPerPlayer",
            DEFAULT_MAX_ACTIVE_LOADERS_PER_PLAYER,
            ChunkLoaderLimits::clampActiveLoaders),
        readBounded(
            config,
            logger,
            "chunkLoader.limits.maxUniqueChunks",
            DEFAULT_MAX_UNIQUE_CHUNKS,
            ChunkLoaderLimits::clampUniqueChunks),
        readBounded(
            config,
            logger,
            "chunkLoader.limits.maxUniqueChunksPerWorld",
            DEFAULT_MAX_UNIQUE_CHUNKS_PER_WORLD,
            ChunkLoaderLimits::clampUniqueChunks));
  }

  public static ChunkLoaderLimits defaults() {
    return new ChunkLoaderLimits(
        DEFAULT_MAX_ACTIVE_LOADERS,
        DEFAULT_MAX_ACTIVE_LOADERS_PER_WORLD,
        DEFAULT_MAX_ACTIVE_LOADERS_PER_PLAYER,
        DEFAULT_MAX_UNIQUE_CHUNKS,
        DEFAULT_MAX_UNIQUE_CHUNKS_PER_WORLD);
  }

  private static int readBounded(
      ConfigurationSection config,
      Logger logger,
      String path,
      int fallback,
      IntUnaryOperator limiter) {
    int raw = config == null ? fallback : config.getInt(path, fallback);
    int bounded = limiter.applyAsInt(raw);
    if (raw != bounded && logger != null) {
      logger.warning(path + "=" + raw + " is outside its safe range; using " + bounded + ".");
    }
    return bounded;
  }

  private static int clampActiveLoaders(int value) {
    return Math.max(MIN_ACTIVE_LOADERS, Math.min(MAX_ACTIVE_LOADERS, value));
  }

  private static int clampUniqueChunks(int value) {
    return Math.max(MIN_UNIQUE_CHUNKS, Math.min(MAX_UNIQUE_CHUNKS, value));
  }
}
