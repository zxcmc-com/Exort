package com.zxcmc.exort.storage;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.StorageMarker;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Keeps loaded Storage markers and durable physical-identity claims mutually consistent. */
public final class StorageClaimReconciler implements Listener, AutoCloseable {
  private static final int STARTUP_CHUNKS_PER_TICK = 8;

  private final Plugin plugin;
  private final StorageClaimRegistry registry;
  private final Material storageCarrier;
  private final ArrayDeque<Chunk> startupChunks = new ArrayDeque<>();
  private final Set<String> warnedConflicts = new HashSet<>();
  private final Set<String> repairingOrphans = new HashSet<>();
  private final Map<String, Set<StorageClaimLocation>> conflictedMarkers = new HashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private BukkitTask startupTask;

  public StorageClaimReconciler(
      Plugin plugin, StorageClaimRegistry registry, Material storageCarrier) {
    this.plugin = plugin;
    this.registry = registry;
    this.storageCarrier = storageCarrier;
  }

  public void start() {
    registry
        .start()
        .whenComplete(
            (ignored, error) -> {
              if (error != null || closed.get()) return;
              PluginTasks.runSyncIfEnabled(plugin, this::queueLoadedChunks);
            });
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    if (closed.get() || registry.state() != StorageClaimRegistry.State.READY) return;
    reconcileChunk(event.getChunk());
  }

  private void queueLoadedChunks() {
    if (closed.get()) return;
    startupChunks.clear();
    for (var world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        startupChunks.addLast(chunk);
      }
    }
    if (startupChunks.isEmpty()) return;
    startupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processStartupChunks, 1L, 1L);
  }

  private void processStartupChunks() {
    int processed = 0;
    while (processed++ < STARTUP_CHUNKS_PER_TICK) {
      Chunk chunk = startupChunks.pollFirst();
      if (chunk == null) {
        cancelStartupTask();
        return;
      }
      if (chunk.isLoaded()) {
        reconcileChunk(chunk);
      }
    }
  }

  void reconcileChunk(Chunk chunk) {
    if (chunk == null || !chunk.isLoaded()) return;
    if (ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      ChunkMarkerStore.forEachBlock(plugin, chunk, (block, ignored) -> reconcileBlock(block));
    }
    for (StorageClaim claim :
        registry.persistedClaimsInChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
      reconcileClaim(chunk, claim);
    }
  }

  private void reconcileBlock(Block block) {
    if (!Carriers.matchesCarrier(block, storageCarrier)) return;
    Optional<StorageMarker.Data> marker = StorageMarker.get(plugin, block);
    if (marker.isEmpty()) return;
    StorageMarker.Data data = marker.get();
    StorageClaimLocation location = StorageClaimLocation.fromBlock(block);
    StorageClaimRegistry.ExactClaim exact = registry.exactClaim(data.storageId(), location);
    if (exact == StorageClaimRegistry.ExactClaim.MATCHED) {
      StorageMarker.setClaimConflict(plugin, block, false);
      clearConflict(data.storageId(), location);
      return;
    }
    if (registry
        .claim(data.storageId())
        .map(StorageClaim::location)
        .filter(location::equals)
        .isPresent()) {
      StorageMarker.setClaimConflict(plugin, block, false);
      clearConflict(data.storageId(), location);
      return;
    }
    if (exact != StorageClaimRegistry.ExactClaim.ABSENT) {
      StorageMarker.setClaimConflict(plugin, block, true);
      rememberConflict(data.storageId(), location);
      warnConflict(data.storageId(), location, exact.name());
      return;
    }
    StorageClaimRegistry.ReservationResult reservation =
        registry.reserve(data.storageId(), location);
    if (!reservation.allowed()) {
      StorageMarker.setClaimConflict(plugin, block, true);
      rememberConflict(data.storageId(), location);
      warnConflict(data.storageId(), location, String.valueOf(reservation.denial()));
      return;
    }
    StorageMarker.setClaimConflict(plugin, block, true);
    registry
        .persist(
            reservation.reservation(), data.tier().key(), data.tierMaxItems(), data.displayName())
        .whenComplete(
            (ignored, error) -> {
              if (error != null) {
                plugin
                    .getLogger()
                    .log(
                        Level.SEVERE,
                        "Failed to import physical storage claim " + data.storageId(),
                        error);
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin,
                  () -> {
                    if (Carriers.matchesCarrier(block, storageCarrier)
                        && StorageMarker.get(plugin, block)
                            .map(StorageMarker.Data::storageId)
                            .filter(data.storageId()::equals)
                            .isPresent()) {
                      StorageMarker.setClaimConflict(plugin, block, false);
                      clearConflict(data.storageId(), location);
                    }
                  });
            });
  }

  private void reconcileClaim(Chunk chunk, StorageClaim claim) {
    if (!claim.worldId().equals(chunk.getWorld().getUID())) return;
    Block block = chunk.getWorld().getBlockAt(claim.x(), claim.y(), claim.z());
    if (StorageMarker.hasMarkerData(plugin, block) || !repairingOrphans.add(claim.storageId())) {
      return;
    }
    StorageClaimLocation location = claim.location();
    plugin
        .getLogger()
        .warning(
            "Repairing orphaned physical storage claim "
                + claim.storageId()
                + " at "
                + location
                + ": the chunk is loaded but the exact position has no Storage marker");
    registry
        .releaseExact(claim.storageId(), location)
        .whenComplete(
            (released, error) ->
                PluginTasks.runSyncIfEnabled(
                    plugin, () -> finishOrphanRepair(claim, released, error)));
  }

  private void finishOrphanRepair(StorageClaim claim, Boolean released, Throwable error) {
    repairingOrphans.remove(claim.storageId());
    if (error != null) {
      plugin
          .getLogger()
          .log(
              Level.SEVERE,
              "Failed to repair orphaned physical storage claim " + claim.storageId(),
              error);
      return;
    }
    if (!Boolean.TRUE.equals(released)) {
      return;
    }
    plugin
        .getLogger()
        .info(
            "Released orphaned physical storage claim "
                + claim.storageId()
                + "; the Storage row and contents were preserved");
    Set<StorageClaimLocation> conflicts = conflictedMarkers.remove(claim.storageId());
    if (conflicts == null) return;
    for (StorageClaimLocation conflict : conflicts) {
      var world = Bukkit.getWorld(conflict.worldId());
      if (world == null || !world.isChunkLoaded(conflict.x() >> 4, conflict.z() >> 4)) continue;
      reconcileBlock(world.getBlockAt(conflict.x(), conflict.y(), conflict.z()));
    }
  }

  private void rememberConflict(String storageId, StorageClaimLocation location) {
    conflictedMarkers.computeIfAbsent(storageId, ignored -> new HashSet<>()).add(location);
  }

  private void clearConflict(String storageId, StorageClaimLocation location) {
    Set<StorageClaimLocation> locations = conflictedMarkers.get(storageId);
    if (locations == null) return;
    locations.remove(location);
    if (locations.isEmpty()) {
      conflictedMarkers.remove(storageId);
    }
  }

  private void warnConflict(String storageId, StorageClaimLocation location, String reason) {
    String key =
        storageId
            + "@"
            + location.worldId()
            + ":"
            + location.x()
            + ":"
            + location.y()
            + ":"
            + location.z();
    if (!warnedConflicts.add(key)) return;
    plugin
        .getLogger()
        .warning(
            "Storage marker "
                + storageId
                + " at "
                + location
                + " is not authoritative ("
                + reason
                + "); the existing durable claim wins and this marker remains read-only.");
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    cancelStartupTask();
    startupChunks.clear();
    warnedConflicts.clear();
    repairingOrphans.clear();
    conflictedMarkers.clear();
  }

  private void cancelStartupTask() {
    if (startupTask != null) {
      startupTask.cancel();
      startupTask = null;
    }
  }
}
