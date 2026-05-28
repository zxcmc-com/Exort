package com.zxcmc.exort.runtime;

import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class RuntimeTaskScheduler {
  private final Plugin plugin;
  private final Supplier<StorageManager> storageManager;
  private final Supplier<StorageRuntimeConfig> storageConfig;
  private int flushTaskId = -1;
  private int cacheEvictTaskId = -1;

  public RuntimeTaskScheduler(
      Plugin plugin,
      Supplier<StorageManager> storageManager,
      Supplier<StorageRuntimeConfig> storageConfig) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.storageManager = Objects.requireNonNull(storageManager, "storageManager");
    this.storageConfig = Objects.requireNonNull(storageConfig, "storageConfig");
  }

  public void schedule() {
    scheduleFlushTask();
    scheduleCacheEviction();
  }

  public void cancel() {
    if (flushTaskId != -1) {
      Bukkit.getScheduler().cancelTask(flushTaskId);
      flushTaskId = -1;
    }
    if (cacheEvictTaskId != -1) {
      Bukkit.getScheduler().cancelTask(cacheEvictTaskId);
      cacheEvictTaskId = -1;
    }
  }

  private void scheduleCacheEviction() {
    if (cacheEvictTaskId != -1) {
      Bukkit.getScheduler().cancelTask(cacheEvictTaskId);
      cacheEvictTaskId = -1;
    }
    StorageManager manager = storageManager.get();
    if (manager == null) return;
    StorageRuntimeConfig config = storageConfig.get();
    long idleSeconds = config.cacheIdleUnloadSeconds();
    long checkSeconds = config.cacheIdleCheckSeconds();
    if (idleSeconds <= 0 || checkSeconds <= 0) return;
    long idleMs = idleSeconds * 1000L;
    cacheEvictTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> manager.evictIdleCaches(idleMs),
                checkSeconds * 20L,
                checkSeconds * 20L);
  }

  private void scheduleFlushTask() {
    if (flushTaskId != -1) {
      Bukkit.getScheduler().cancelTask(flushTaskId);
      flushTaskId = -1;
    }
    StorageManager manager = storageManager.get();
    if (manager == null) return;
    int flushSeconds = storageConfig.get().flushIntervalSeconds();
    if (flushSeconds <= 0) return;
    flushTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin, manager::flushDirtyCaches, flushSeconds * 20L, flushSeconds * 20L);
  }
}
