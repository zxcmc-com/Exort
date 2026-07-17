package com.zxcmc.exort.display.refresh;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.ChunkLoaderDisplayManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.RelayDisplayManager;
import com.zxcmc.exort.display.device.StorageDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.device.TransmitterDisplayManager;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.relay.RelaySetupTracker;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

public final class DisplayRefreshService {
  private static final int BLOCK_REFRESH_BUDGET_PER_TICK = 512;
  private static final int CHUNK_REFRESH_BUDGET_PER_TICK = 2;
  private static final int NETWORK_WORK_BUDGET_PER_TICK = 2048;
  private static final BlockFace[] FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };

  private final Plugin plugin;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Material relayCarrier;
  private final Material relayTraversalCarrier;
  private final Material transmitterCarrier;
  private final Material chunkLoaderCarrier;
  private final Material storageCarrier;
  private final WireDisplayManager wireDisplayManager;
  private final StorageDisplayManager storageDisplayManager;
  private final TerminalDisplayManager terminalDisplayManager;
  private final MonitorDisplayManager monitorDisplayManager;
  private final BusDisplayManager busDisplayManager;
  private final RelayDisplayManager relayDisplayManager;
  private final TransmitterDisplayManager transmitterDisplayManager;
  private final ChunkLoaderDisplayManager chunkLoaderDisplayManager;
  private final ExortBlockProxyService blockProxyService;
  private final RelaySetupTracker relaySetupTracker;
  private final Set<BlockKey> queuedBlocks = new HashSet<>();
  private final Set<ChunkKey> queuedChunks = new HashSet<>();
  private final NetworkRefreshWorkQueue<BlockKey> networkRefreshWork =
      new NetworkRefreshWorkQueue<>();
  private final NetworkTopology networkTopology = new NetworkTopology();
  private long implicitNetworkBatchId = -1L;
  private int refreshTaskId = -1;

  public DisplayRefreshService(
      Plugin plugin,
      int wireHardCap,
      int relayRangeChunks,
      Material wireMaterial,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier,
      Material relayTraversalCarrier,
      Material transmitterCarrier,
      Material chunkLoaderCarrier,
      Material storageCarrier,
      WireDisplayManager wireDisplayManager,
      StorageDisplayManager storageDisplayManager,
      TerminalDisplayManager terminalDisplayManager,
      MonitorDisplayManager monitorDisplayManager,
      BusDisplayManager busDisplayManager,
      RelayDisplayManager relayDisplayManager,
      TransmitterDisplayManager transmitterDisplayManager,
      ChunkLoaderDisplayManager chunkLoaderDisplayManager,
      ExortBlockProxyService blockProxyService,
      RelaySetupTracker relaySetupTracker) {
    this.plugin = plugin;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = wireMaterial;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.relayCarrier = relayCarrier;
    this.relayTraversalCarrier = relayTraversalCarrier;
    this.transmitterCarrier = transmitterCarrier;
    this.chunkLoaderCarrier = chunkLoaderCarrier;
    this.storageCarrier = storageCarrier;
    this.wireDisplayManager = wireDisplayManager;
    this.storageDisplayManager = storageDisplayManager;
    this.terminalDisplayManager = terminalDisplayManager;
    this.monitorDisplayManager = monitorDisplayManager;
    this.busDisplayManager = busDisplayManager;
    this.relayDisplayManager = relayDisplayManager;
    this.transmitterDisplayManager = transmitterDisplayManager;
    this.chunkLoaderDisplayManager = chunkLoaderDisplayManager;
    this.blockProxyService = blockProxyService;
    this.relaySetupTracker = relaySetupTracker;
  }

  public void refreshChunk(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) return;
    queuedChunks.add(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
    updateQueueGauge();
    scheduleRefreshDrain();
  }

  public void refreshBlockAndNeighbors(Block block) {
    if (block == null || block.getWorld() == null) return;
    enqueueBlock(block);
    for (BlockFace face : FACES) {
      enqueueBlock(block.getRelative(face));
    }
    updateQueueGauge();
    scheduleRefreshDrain();
  }

  private void enqueueBlock(Block block) {
    if (block == null || block.getWorld() == null) return;
    queuedBlocks.add(
        new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
  }

  private void refreshChunkNow(Chunk chunk) {
    boolean[] flags = new boolean[8];
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) return;
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (!flags[0] && WireMarker.isWire(plugin, block)) flags[0] = true;
          if (!flags[1] && StorageMarker.get(plugin, block).isPresent()) flags[1] = true;
          if (!flags[2] && TerminalMarker.isTerminal(plugin, block)) flags[2] = true;
          if (!flags[3] && MonitorMarker.isMonitor(plugin, block)) flags[3] = true;
          if (!flags[4] && BusMarker.isBus(plugin, block)) flags[4] = true;
          if (!flags[5] && RelayMarker.isRelay(plugin, block)) flags[5] = true;
          if (!flags[6] && TransmitterMarker.isTransmitter(plugin, block)) flags[6] = true;
          if (!flags[7] && ChunkLoaderMarker.isChunkLoader(plugin, block)) flags[7] = true;
        });
    boolean hasWire = flags[0];
    boolean hasStorage = flags[1];
    boolean hasTerminal = flags[2];
    boolean hasMonitor = flags[3];
    boolean hasBus = flags[4];
    boolean hasRelay = flags[5];
    boolean hasTransmitter = flags[6];
    boolean hasChunkLoader = flags[7];
    if (hasWire && wireDisplayManager != null) {
      wireDisplayManager.refreshChunk(chunk);
    }
    if (hasStorage && storageDisplayManager != null) {
      storageDisplayManager.refreshChunk(chunk);
    }
    if (hasTerminal && terminalDisplayManager != null) {
      terminalDisplayManager.refreshChunk(chunk);
    }
    if (hasMonitor && monitorDisplayManager != null) {
      monitorDisplayManager.refreshChunk(chunk);
    }
    if (hasBus && busDisplayManager != null) {
      busDisplayManager.refreshChunk(chunk);
    }
    if (hasRelay && relayDisplayManager != null) {
      relayDisplayManager.refreshChunk(chunk);
    }
    if (hasTransmitter && transmitterDisplayManager != null) {
      transmitterDisplayManager.refreshChunk(chunk);
    }
    if (hasChunkLoader && chunkLoaderDisplayManager != null) {
      chunkLoaderDisplayManager.refreshChunk(chunk);
    }
    if (blockProxyService != null) {
      blockProxyService.refreshChunk(chunk);
    }
  }

  public void refreshWireAndNeighbors(Block block) {
    if (wireDisplayManager != null) {
      wireDisplayManager.updateWireAndNeighbors(block);
    }
  }

  public void removeBlockDisplays(Block block) {
    if (block == null) return;
    removeWireDisplay(block);
    removeStorageDisplay(block);
    removeTerminalDisplay(block);
    removeMonitorDisplay(block);
    removeBusDisplay(block);
    removeRelayDisplay(block);
    removeTransmitterDisplay(block);
    removeChunkLoaderDisplay(block);
  }

  public void removeWireDisplay(Block block) {
    if (wireDisplayManager != null) {
      wireDisplayManager.removeWire(block);
    }
  }

  public void refreshNetworkFrom(Block block) {
    if (block == null || block.getWorld() == null) return;
    if (implicitNetworkBatchId == -1L) {
      implicitNetworkBatchId = networkRefreshWork.openBatch();
    }
    networkRefreshWork.addStart(implicitNetworkBatchId, BlockKey.from(block));
    updateQueueGauge();
    scheduleRefreshDrain();
  }

  public long beginBulkNetworkRefresh() {
    return networkRefreshWork.openBatch();
  }

  public void addBulkNetworkRefreshStart(long batchId, UUID worldId, int x, int y, int z) {
    if (worldId == null) return;
    networkRefreshWork.addStart(batchId, new BlockKey(worldId, x, y, z));
    updateQueueGauge();
  }

  public void sealBulkNetworkRefresh(long batchId) {
    if (networkRefreshWork.seal(batchId)) {
      updateQueueGauge();
      scheduleRefreshDrain();
    }
  }

  public void cancelBulkNetworkRefresh(long batchId) {
    networkRefreshWork.cancel(batchId);
    updateQueueGauge();
  }

  public void shutdown() {
    if (refreshTaskId != -1) {
      Bukkit.getScheduler().cancelTask(refreshTaskId);
      refreshTaskId = -1;
    }
    queuedBlocks.clear();
    queuedChunks.clear();
    networkRefreshWork.clear();
    implicitNetworkBatchId = -1L;
    updateQueueGauge();
    PerfStats.setGauge("display.blockRefreshWorkThisTick", 0L);
    PerfStats.setGauge("display.chunkRefreshWorkThisTick", 0L);
    PerfStats.setGauge("wire.networkRefreshWorkThisTick", 0L);
    PerfStats.setGauge("wire.networkRefreshPendingStarts", 0L);
    PerfStats.setGauge("wire.networkRefreshPending", 0L);
    PerfStats.setGauge("wire.networkRefreshActive", 0L);
  }

  private void refreshBlockNow(Block block) {
    if (block == null || block.getWorld() == null) return;
    if (!isChunkLoaded(block)) return;
    if (Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block)) {
      refreshWireAndNeighbors(block);
    }
    if (Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent()) {
      refreshStorage(block);
    }
    if (Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block)) {
      refreshTerminal(block);
    }
    if (Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block)) {
      refreshMonitor(block);
    }
    if (Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block)) {
      refreshBus(block);
    }
    if (Carriers.matchesCarrier(block, relayCarrier) && RelayMarker.isRelay(plugin, block)) {
      refreshRelay(block);
    }
    if (Carriers.matchesCarrier(block, transmitterCarrier)
        && TransmitterMarker.isTransmitter(plugin, block)) {
      refreshTransmitter(block);
    }
    if (Carriers.matchesCarrier(block, chunkLoaderCarrier)
        && ChunkLoaderMarker.isChunkLoader(plugin, block)) {
      refreshChunkLoader(block);
    }
    if (blockProxyService != null) {
      blockProxyService.refreshBlock(block);
    }
  }

  private void scheduleRefreshDrain() {
    if (refreshTaskId != -1) return;
    try {
      refreshTaskId =
          Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::drainRefreshQueues, 1L);
    } catch (IllegalStateException ignored) {
      refreshTaskId = -1;
    }
  }

  private void drainRefreshQueues() {
    refreshTaskId = -1;
    PerfStats.measure(PerfStats.Area.DISPLAY, this::drainRefreshQueuesMeasured);
  }

  private void drainRefreshQueuesMeasured() {
    sealImplicitNetworkBatch();
    NetworkRefreshWorkQueue.DrainResult networkResult =
        networkRefreshWork.drain(NETWORK_WORK_BUDGET_PER_TICK, wireHardCap, networkTopology);
    recordNetworkRefreshMetrics(networkResult);

    int blockBudget = BLOCK_REFRESH_BUDGET_PER_TICK;
    int blocksRefreshed = 0;
    while (blockBudget > 0 && !queuedBlocks.isEmpty()) {
      Iterator<BlockKey> iterator = queuedBlocks.iterator();
      BlockKey key = iterator.next();
      iterator.remove();
      Block block = loadedBlock(key);
      if (block != null) {
        refreshBlockNow(block);
      }
      blockBudget--;
      blocksRefreshed++;
    }
    int chunkBudget = CHUNK_REFRESH_BUDGET_PER_TICK;
    int chunksRefreshed = 0;
    while (chunkBudget > 0 && !queuedChunks.isEmpty()) {
      Iterator<ChunkKey> iterator = queuedChunks.iterator();
      ChunkKey key = iterator.next();
      iterator.remove();
      Chunk chunk = loadedChunk(key);
      if (chunk != null) {
        refreshChunkNow(chunk);
      }
      chunkBudget--;
      chunksRefreshed++;
    }
    PerfStats.setGauge("display.blockRefreshWorkThisTick", blocksRefreshed);
    PerfStats.setGauge("display.chunkRefreshWorkThisTick", chunksRefreshed);
    updateQueueGauge();
    if (!queuedBlocks.isEmpty() || !queuedChunks.isEmpty() || networkRefreshWork.hasSealedWork()) {
      PerfStats.incrementCounter("display.budgetOverrun");
      scheduleRefreshDrain();
    }
  }

  private void sealImplicitNetworkBatch() {
    if (implicitNetworkBatchId == -1L) {
      return;
    }
    networkRefreshWork.seal(implicitNetworkBatchId);
    implicitNetworkBatchId = -1L;
  }

  private void recordNetworkRefreshMetrics(NetworkRefreshWorkQueue.DrainResult result) {
    PerfStats.addCounter("wire.networkRefreshVisited", result.refreshes());
    PerfStats.addCounter("wire.networkRefreshComponentsStarted", result.componentsStarted());
    PerfStats.addCounter("wire.networkRefreshComponentsCompleted", result.componentsCompleted());
    PerfStats.addCounter("wire.networkRefreshSkipped", result.skippedStarts());
    PerfStats.addCounter("wire.networkRefreshHardCapOverflow", result.overflowedBatches());
    PerfStats.setGauge("wire.networkRefreshWorkThisTick", result.examined());
    PerfStats.setGauge("wire.networkRefreshPendingStarts", result.pendingStarts());
    PerfStats.setGauge("wire.networkRefreshPending", result.pendingWork());
    PerfStats.setGauge("wire.networkRefreshActive", networkRefreshWork.hasSealedWork() ? 1L : 0L);
  }

  private void updateQueueGauge() {
    PerfStats.setGauge(
        "display.queueDepth",
        (long) queuedBlocks.size() + queuedChunks.size() + networkRefreshWork.pendingWork());
  }

  private Chunk loadedChunk(ChunkKey key) {
    World world = Bukkit.getWorld(key.world());
    if (world == null || !world.isChunkLoaded(key.x(), key.z())) return null;
    return world.getChunkAt(key.x(), key.z());
  }

  private Block loadedBlock(BlockKey key) {
    World world = Bukkit.getWorld(key.world());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) return null;
    return world.getBlockAt(key.x(), key.y(), key.z());
  }

  private record ChunkKey(UUID world, int x, int z) {}

  private record BlockKey(UUID world, int x, int y, int z) {
    private static BlockKey from(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  private final class NetworkTopology implements NetworkRefreshWorkQueue.Topology<BlockKey> {
    @Override
    public boolean isNode(BlockKey key) {
      return isNetworkNode(loadedBlock(key));
    }

    @Override
    public void forEachConnectedNode(BlockKey key, Consumer<BlockKey> consumer) {
      Block block = loadedBlock(key);
      if (block == null) {
        return;
      }
      boolean currentIsNode = isNetworkNode(block);
      if (currentIsNode
          && Carriers.matchesCarrier(block, relayTraversalCarrier)
          && RelayMarker.isRelay(plugin, block)) {
        Block peer = validRelayPeer(block);
        if (peer != null) {
          consumer.accept(BlockKey.from(peer));
        }
      }
      for (BlockFace face : FACES) {
        Block neighbor = block.getRelative(face);
        if (!isChunkLoaded(neighbor)) {
          continue;
        }
        if (isNetworkNode(neighbor)) {
          consumer.accept(BlockKey.from(neighbor));
        } else if (currentIsNode && isNetworkEndpoint(neighbor)) {
          queuedBlocks.add(BlockKey.from(neighbor));
        }
      }
    }

    @Override
    public void enqueueRefresh(BlockKey node) {
      queuedBlocks.add(node);
    }
  }

  private boolean isNetworkNode(Block block) {
    return block != null
        && ((Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block))
            || (Carriers.matchesCarrier(block, relayTraversalCarrier)
                && RelayMarker.isRelay(plugin, block)));
  }

  private boolean isNetworkEndpoint(Block block) {
    return (Carriers.matchesCarrier(block, terminalCarrier)
            && TerminalMarker.isTerminal(plugin, block))
        || (Carriers.matchesCarrier(block, monitorCarrier)
            && MonitorMarker.isMonitor(plugin, block))
        || (Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block))
        || (Carriers.matchesCarrier(block, transmitterCarrier)
            && TransmitterMarker.isTransmitter(plugin, block))
        || (Carriers.matchesCarrier(block, storageCarrier)
            && StorageMarker.get(plugin, block).isPresent());
  }

  private boolean isChunkLoaded(Block block) {
    if (block == null || block.getWorld() == null) return false;
    return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  public void refreshStorage(Block block) {
    if (storageDisplayManager != null) {
      storageDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void refreshTerminal(Block block) {
    if (terminalDisplayManager != null) {
      terminalDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void refreshMonitor(Block block) {
    if (monitorDisplayManager != null) {
      monitorDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void refreshBus(Block block) {
    if (busDisplayManager != null) {
      busDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void refreshRelay(Block block) {
    boolean validRelay =
        block != null
            && Carriers.matchesCarrier(block, relayCarrier)
            && RelayMarker.isRelay(plugin, block);
    if (relaySetupTracker != null && !validRelay) {
      relaySetupTracker.clearBlock(block);
    }
    if (relayDisplayManager != null) {
      relayDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void refreshTransmitter(Block block) {
    if (transmitterDisplayManager != null) {
      transmitterDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void refreshChunkLoader(Block block) {
    if (chunkLoaderDisplayManager != null) {
      chunkLoaderDisplayManager.refresh(block);
    }
    refreshProxyBlock(block);
  }

  public void removeStorageDisplay(Block block) {
    if (storageDisplayManager != null) {
      storageDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  public void removeTerminalDisplay(Block block) {
    if (terminalDisplayManager != null) {
      terminalDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  public void removeMonitorDisplay(Block block) {
    if (monitorDisplayManager != null) {
      monitorDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  public void removeBusDisplay(Block block) {
    if (busDisplayManager != null) {
      busDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  public void removeRelayDisplay(Block block) {
    if (relaySetupTracker != null) {
      relaySetupTracker.clearBlock(block);
    }
    if (relayDisplayManager != null) {
      relayDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  public void removeTransmitterDisplay(Block block) {
    if (transmitterDisplayManager != null) {
      transmitterDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  public void removeChunkLoaderDisplay(Block block) {
    if (chunkLoaderDisplayManager != null) {
      chunkLoaderDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  private Block validRelayPeer(Block relay) {
    Block peer = RelayMarker.link(plugin, relay).map(RelayMarker.Link::loadedBlock).orElse(null);
    if (peer == null || !isChunkLoaded(peer)) return null;
    if (!Carriers.matchesCarrier(peer, relayTraversalCarrier)
        || !RelayMarker.isRelay(plugin, peer)) {
      return null;
    }
    if (RelayMarker.link(plugin, peer).filter(link -> link.sameBlock(relay)).isEmpty()) {
      return null;
    }
    return com.zxcmc.exort.network.NetworkGraphCache.inRelayRange(relay, peer, relayRangeChunks)
        ? peer
        : null;
  }

  private void refreshProxyBlock(Block block) {
    if (blockProxyService != null) {
      blockProxyService.refreshBlock(block);
    }
  }

  private void restoreProxyBlock(Block block) {
    if (blockProxyService != null) {
      blockProxyService.restoreAndForget(block);
    }
  }
}
