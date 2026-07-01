package com.zxcmc.exort.relay;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class RelaySetupTracker {
  public static final long DEFAULT_TTL_MS = 60_000L;

  private final Plugin plugin;
  private final long ttlMillis;
  private final Consumer<Block> expiryCallback;
  private final Map<UUID, PendingRelay> pending = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> expiryTasks = new ConcurrentHashMap<>();

  public RelaySetupTracker() {
    this(null, DEFAULT_TTL_MS, block -> {});
  }

  public RelaySetupTracker(long ttlMillis) {
    this(null, ttlMillis, block -> {});
  }

  public RelaySetupTracker(Plugin plugin, long ttlMillis, Consumer<Block> expiryCallback) {
    this.plugin = plugin;
    this.ttlMillis = Math.max(1L, ttlMillis);
    this.expiryCallback = expiryCallback == null ? block -> {} : expiryCallback;
  }

  public PendingRelay pending(UUID playerId, long nowMillis) {
    if (playerId == null) {
      return null;
    }
    PendingRelay current = pending.get(playerId);
    if (current == null) {
      return null;
    }
    if (current.expired(nowMillis, ttlMillis)) {
      clearPlayer(playerId);
      return null;
    }
    return current;
  }

  public Block select(UUID playerId, Block block, long nowMillis) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(block, "block");
    PendingRelay previous = pending.put(playerId, new PendingRelay(block, nowMillis));
    cancelExpiry(playerId);
    scheduleExpiry(playerId, block);
    return previous == null ? null : previous.block();
  }

  public Block clearPlayer(UUID playerId) {
    if (playerId == null) {
      return null;
    }
    cancelExpiry(playerId);
    PendingRelay removed = pending.remove(playerId);
    return removed == null ? null : removed.block();
  }

  public boolean clearBlock(Block block) {
    if (block == null) {
      return false;
    }
    boolean cleared = false;
    for (UUID playerId : Set.copyOf(pending.keySet())) {
      PendingRelay current = pending.get(playerId);
      if (current != null && sameBlock(current.block(), block)) {
        clearPlayer(playerId);
        cleared = true;
      }
    }
    return cleared;
  }

  public boolean isPending(Block block, long nowMillis) {
    if (block == null) {
      return false;
    }
    purgeExpired(nowMillis);
    for (PendingRelay current : pending.values()) {
      if (sameBlock(current.block(), block)) {
        return true;
      }
    }
    return false;
  }

  public int pendingCount() {
    return pending.size();
  }

  public void stop() {
    clearAll();
  }

  public void clearAll() {
    for (UUID playerId : Set.copyOf(expiryTasks.keySet())) {
      cancelExpiry(playerId);
    }
    pending.clear();
  }

  private void purgeExpired(long nowMillis) {
    for (UUID playerId : Set.copyOf(pending.keySet())) {
      PendingRelay current = pending.get(playerId);
      if (current != null && current.expired(nowMillis, ttlMillis)) {
        clearPlayer(playerId);
      }
    }
  }

  private void scheduleExpiry(UUID playerId, Block block) {
    if (plugin == null) {
      return;
    }
    long delayTicks = Math.max(1L, (ttlMillis + 49L) / 50L);
    try {
      int taskId =
          Bukkit.getScheduler()
              .scheduleSyncDelayedTask(plugin, () -> expire(playerId, block), delayTicks);
      if (taskId != -1) {
        expiryTasks.put(playerId, taskId);
      }
    } catch (IllegalStateException ignored) {
      // Scheduler is unavailable during shutdown/reload; lazy expiry still protects reads.
    }
  }

  private void expire(UUID playerId, Block block) {
    expiryTasks.remove(playerId);
    PendingRelay current = pending.get(playerId);
    if (current == null || !sameBlock(current.block(), block)) {
      return;
    }
    long nowMillis = System.currentTimeMillis();
    if (!current.expired(nowMillis, ttlMillis)) {
      scheduleExpiry(playerId, current.block());
      return;
    }
    pending.remove(playerId);
    expiryCallback.accept(block);
  }

  private void cancelExpiry(UUID playerId) {
    Integer taskId = expiryTasks.remove(playerId);
    if (taskId != null) {
      try {
        Bukkit.getScheduler().cancelTask(taskId);
      } catch (IllegalStateException ignored) {
        // Scheduler may already be stopped.
      }
    }
  }

  private static boolean sameBlock(Block first, Block second) {
    return first != null
        && second != null
        && first.getWorld() != null
        && second.getWorld() != null
        && first.getWorld().getUID().equals(second.getWorld().getUID())
        && first.getX() == second.getX()
        && first.getY() == second.getY()
        && first.getZ() == second.getZ();
  }

  public record PendingRelay(Block block, long createdMillis) {
    private boolean expired(long nowMillis, long ttlMillis) {
      return nowMillis - createdMillis >= ttlMillis;
    }
  }
}
