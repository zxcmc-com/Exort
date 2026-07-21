package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.sanity.DisplayCleanupService;
import com.zxcmc.exort.sanity.MarkerSanityDependencies;
import com.zxcmc.exort.sanity.MarkerSanityService;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

final class WorldEditRefreshScheduler {
  private static final long[] DEFERRED_REFRESH_DELAYS_TICKS = {2L, 10L};
  private static final int GRAPH_INVALIDATE_CHUNKS_PER_TICK = 64;
  private static final long NETWORK_BATCH_QUIET_TICKS = 2L;

  private final WorldEditBridgeDependencies deps;
  private final Runnable queueGaugeUpdater;
  private final Set<ChunkKey> queuedGraphChunks = new LinkedHashSet<>();
  private final Set<ChunkKey> queuedDisplayChunks = new LinkedHashSet<>();
  private final Set<ChunkKey> queuedBusChunks = new LinkedHashSet<>();
  private final Set<BukkitTask> deferredTasks = ConcurrentHashMap.newKeySet();
  private BukkitTask drainTask;
  private BukkitTask networkSealTask;
  private long activeNetworkBatchId = -1L;
  private volatile boolean closed;

  WorldEditRefreshScheduler(WorldEditBridgeDependencies deps, Runnable queueGaugeUpdater) {
    this.deps = deps;
    this.queueGaugeUpdater = queueGaugeUpdater == null ? () -> {} : queueGaugeUpdater;
  }

  void refreshAffectedChunks(
      Set<ChunkKey> chunkKeys, Set<BlockRef> networkRefreshStarts, String reason) {
    refreshAffectedChunks(chunkKeys, networkRefreshStarts, reason, true);
  }

  private void refreshAffectedChunks(
      Set<ChunkKey> chunkKeys,
      Set<BlockRef> networkRefreshStarts,
      String reason,
      boolean invalidateGraph) {
    if (closed || chunkKeys == null || chunkKeys.isEmpty()) {
      return;
    }
    if (!deps.bulkConfig().enabled()) {
      refreshAffectedChunksNow(chunkKeys, networkRefreshStarts, reason, invalidateGraph);
      return;
    }

    if (invalidateGraph) {
      queuedGraphChunks.addAll(chunkKeys);
    }
    queuedDisplayChunks.addAll(chunkKeys);
    queuedBusChunks.addAll(chunkKeys);
    accumulateNetworkStarts(networkRefreshStarts);
    updatePostQueueGauge();
    scheduleDrain();

    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we refresh queued reason="
              + reason
              + " chunks="
              + chunkKeys.size()
              + " starts="
              + (networkRefreshStarts == null ? 0 : networkRefreshStarts.size()),
          NamedTextColor.BLUE);
    }
  }

  void scheduleDeferredRefresh(Set<ChunkKey> chunkKeys) {
    if (closed || chunkKeys == null || chunkKeys.isEmpty()) {
      return;
    }
    Set<ChunkKey> chunks = Set.copyOf(chunkKeys);
    for (long delay : DEFERRED_REFRESH_DELAYS_TICKS) {
      scheduleDeferredTask(
          () -> refreshAffectedChunks(chunks, Set.of(), "deferred_" + delay + "t", false), delay);
    }
  }

  void shutdown() {
    closed = true;
    for (BukkitTask task : deferredTasks) {
      task.cancel();
    }
    deferredTasks.clear();
    if (drainTask != null) {
      drainTask.cancel();
      drainTask = null;
    }
    if (networkSealTask != null) {
      networkSealTask.cancel();
      networkSealTask = null;
    }
    cancelActiveNetworkBatch();
    queuedGraphChunks.clear();
    queuedDisplayChunks.clear();
    queuedBusChunks.clear();
    updatePostQueueGauge();
  }

  void finishNetworkBatch() {
    if (closed) {
      return;
    }
    sealActiveNetworkBatch();
  }

  int queuedTaskCount() {
    return queuedGraphChunks.size() + queuedDisplayChunks.size() + queuedBusChunks.size();
  }

  private void accumulateNetworkStarts(Set<BlockRef> starts) {
    DisplayRefreshService refreshService = deps.displayRefreshService();
    if (refreshService == null || starts == null || starts.isEmpty()) {
      return;
    }
    if (activeNetworkBatchId == -1L) {
      activeNetworkBatchId = refreshService.beginBulkNetworkRefresh();
    }
    for (BlockRef start : starts) {
      refreshService.addBulkNetworkRefreshStart(
          activeNetworkBatchId, start.worldId(), start.x(), start.y(), start.z());
    }
    PerfStats.addCounter("worldedit.networkRefresh.starts", starts.size());
    scheduleQuietNetworkSeal();
  }

  private void scheduleQuietNetworkSeal() {
    if (closed) {
      return;
    }
    if (networkSealTask != null) {
      networkSealTask.cancel();
      networkSealTask = null;
    }
    try {
      networkSealTask =
          Bukkit.getScheduler()
              .runTaskLater(
                  deps.plugin(),
                  () -> {
                    networkSealTask = null;
                    sealActiveNetworkBatch();
                  },
                  NETWORK_BATCH_QUIET_TICKS);
    } catch (RuntimeException ignored) {
      networkSealTask = null;
    }
  }

  private void sealActiveNetworkBatch() {
    if (networkSealTask != null) {
      networkSealTask.cancel();
      networkSealTask = null;
    }
    if (activeNetworkBatchId == -1L) {
      return;
    }
    DisplayRefreshService refreshService = deps.displayRefreshService();
    if (refreshService != null) {
      refreshService.sealBulkNetworkRefresh(activeNetworkBatchId);
    }
    activeNetworkBatchId = -1L;
  }

  private void cancelActiveNetworkBatch() {
    if (activeNetworkBatchId == -1L) {
      return;
    }
    DisplayRefreshService refreshService = deps.displayRefreshService();
    if (refreshService != null) {
      refreshService.cancelBulkNetworkRefresh(activeNetworkBatchId);
    }
    activeNetworkBatchId = -1L;
  }

  private void scheduleDrain() {
    if (closed || drainTask != null) {
      return;
    }
    try {
      drainTask = Bukkit.getScheduler().runTask(deps.plugin(), this::drainQueues);
    } catch (RuntimeException ignored) {
      drainTask = null;
    }
  }

  private void drainQueues() {
    drainTask = null;
    if (closed) {
      return;
    }
    PerfStats.measure("worldedit.drain", this::drainQueuesMeasured);
  }

  private void scheduleDeferredTask(Runnable action, long delayTicks) {
    if (closed) {
      return;
    }
    AtomicReference<BukkitTask> reference = new AtomicReference<>();
    try {
      BukkitTask task =
          Bukkit.getScheduler()
              .runTaskLater(
                  deps.plugin(),
                  () -> {
                    BukkitTask current = reference.get();
                    if (current != null) {
                      deferredTasks.remove(current);
                    }
                    if (closed || !deps.plugin().isEnabled()) {
                      return;
                    }
                    action.run();
                  },
                  delayTicks);
      reference.set(task);
      deferredTasks.add(task);
      if (closed && deferredTasks.remove(task)) {
        task.cancel();
      }
    } catch (RuntimeException ignored) {
      // The plugin may be disabling while a WorldEdit flush finishes.
    }
  }

  private void drainQueuesMeasured() {
    WorldEditBulkConfig config = deps.bulkConfig();
    drainGraphInvalidations();
    drainDisplayRefreshes(config.refreshChunksPerTick());
    drainBusScans(config.busScanChunksPerTick());
    updatePostQueueGauge();
    if (queuedTaskCount() > 0) {
      PerfStats.incrementCounter("worldedit.budgetOverrun");
      scheduleDrain();
    }
  }

  private void drainGraphInvalidations() {
    if (queuedGraphChunks.isEmpty()) {
      return;
    }
    Set<ChunkKey> chunks = takeChunkBatch(queuedGraphChunks, GRAPH_INVALIDATE_CHUNKS_PER_TICK);
    PerfStats.measure("worldedit.graphInvalidate", () -> invalidateGraphChunks(chunks));
  }

  private void drainDisplayRefreshes(int budget) {
    if (queuedDisplayChunks.isEmpty()) {
      return;
    }
    Set<ChunkKey> chunks = takeChunkBatch(queuedDisplayChunks, budget);
    PerfStats.measure(
        "worldedit.displayRefresh",
        () -> {
          DisplayRefreshService refreshService = deps.displayRefreshService();
          DisplayCleanupService cleanupService = newDisplayCleanupService();
          MarkerSanityService markerSanityService = newMarkerSanityService();
          int refreshed = 0;
          for (ChunkKey key : chunks) {
            Chunk chunk = loadedChunk(key);
            if (chunk == null) {
              continue;
            }
            markerSanityService.cleanupStaleMarkers(chunk);
            cleanupService.cleanupDisplays(chunk);
            if (refreshService != null) {
              refreshService.refreshChunk(chunk);
            }
            refreshed++;
          }
          PerfStats.addCounter("worldedit.displayRefresh.chunks", refreshed);
        });
  }

  private void drainBusScans(int budget) {
    if (queuedBusChunks.isEmpty()) {
      return;
    }
    Set<ChunkKey> chunks = takeChunkBatch(queuedBusChunks, budget);
    PerfStats.measure(
        "worldedit.busScan",
        () -> {
          BusService busService = deps.busService();
          if (busService == null) {
            return;
          }
          int scanned = 0;
          for (ChunkKey key : chunks) {
            Chunk chunk = loadedChunk(key);
            if (chunk == null) {
              continue;
            }
            busService.scanChunk(chunk);
            scanned++;
          }
          PerfStats.addCounter("worldedit.busScan.chunks", scanned);
        });
  }

  private void refreshAffectedChunksNow(
      Set<ChunkKey> chunkKeys,
      Set<BlockRef> networkRefreshStarts,
      String reason,
      boolean invalidateGraph) {
    if (invalidateGraph) {
      invalidateGraphChunks(chunkKeys);
    }
    DisplayRefreshService refreshService = deps.displayRefreshService();
    BusService busService = deps.busService();
    DisplayCleanupService cleanupService = newDisplayCleanupService();
    MarkerSanityService markerSanityService = newMarkerSanityService();
    int refreshedChunks = 0;
    for (ChunkKey key : chunkKeys) {
      Chunk chunk = loadedChunk(key);
      if (chunk == null) {
        continue;
      }
      markerSanityService.cleanupStaleMarkers(chunk);
      cleanupService.cleanupDisplays(chunk);
      if (refreshService != null) {
        refreshService.refreshChunk(chunk);
      }
      if (busService != null) {
        busService.scanChunk(chunk);
      }
      refreshedChunks++;
    }
    if (refreshService != null && networkRefreshStarts != null) {
      for (BlockRef ref : networkRefreshStarts) {
        Block block = ref.block();
        if (block == null) {
          continue;
        }
        refreshService.refreshNetworkFrom(block);
      }
    }
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we refresh pass reason="
              + reason
              + " chunks="
              + refreshedChunks
              + " starts="
              + (networkRefreshStarts == null ? 0 : networkRefreshStarts.size()),
          NamedTextColor.BLUE);
    }
  }

  private void invalidateGraphChunks(Set<ChunkKey> chunks) {
    NetworkGraphCache graphCache = deps.networkGraphCache();
    if (graphCache == null || chunks == null || chunks.isEmpty()) {
      return;
    }
    Set<NetworkGraphCache.ChunkPosition> positions = new HashSet<>();
    for (ChunkKey chunk : chunks) {
      positions.add(
          new NetworkGraphCache.ChunkPosition(chunk.worldId(), chunk.chunkX(), chunk.chunkZ()));
    }
    graphCache.invalidateChunks(positions);
    PerfStats.addCounter("worldedit.graphInvalidate.chunks", positions.size());
  }

  private Chunk loadedChunk(ChunkKey key) {
    World world = Bukkit.getWorld(key.worldId());
    if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
      return null;
    }
    return world.getChunkAt(key.chunkX(), key.chunkZ());
  }

  private DisplayCleanupService newDisplayCleanupService() {
    return new DisplayCleanupService(
        deps.plugin(),
        deps.wireMaterial(),
        deps.storageCarrier(),
        deps.terminalCarrier(),
        deps.monitorCarrier(),
        deps.busCarrier(),
        deps.relayCarrier(),
        deps.transmitterCarrier(),
        deps.chunkLoaderCarrier());
  }

  private MarkerSanityService newMarkerSanityService() {
    return new MarkerSanityService(
        new MarkerSanityDependencies(
            deps.plugin(),
            deps.displayRefreshService(),
            deps::hologramManager,
            deps::busService,
            deps::wirelessTransmitterService,
            deps::transmitterSessionManager,
            deps.database(),
            deps.wireMaterial(),
            deps.storageCarrier(),
            deps.terminalCarrier(),
            deps.monitorCarrier(),
            deps.busCarrier(),
            deps.relayCarrier(),
            deps.transmitterCarrier(),
            deps.chunkLoaderCarrier()));
  }

  private void updatePostQueueGauge() {
    PerfStats.setGauge("worldedit.postQueueDepth", queuedTaskCount());
    queueGaugeUpdater.run();
  }

  private static Set<ChunkKey> takeChunkBatch(Set<ChunkKey> source, int budget) {
    Set<ChunkKey> batch = new LinkedHashSet<>();
    Iterator<ChunkKey> iterator = source.iterator();
    while (iterator.hasNext() && batch.size() < budget) {
      batch.add(iterator.next());
      iterator.remove();
    }
    return batch;
  }
}
