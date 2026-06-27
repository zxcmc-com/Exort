package com.zxcmc.exort.display.refresh;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.display.device.BusDisplayManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.device.RelayDisplayManager;
import com.zxcmc.exort.display.device.StorageDisplayManager;
import com.zxcmc.exort.display.device.TerminalDisplayManager;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

public final class DisplayRefreshService {
  private static final int REFRESH_BUDGET_PER_TICK = 64;
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
  private final Material storageCarrier;
  private final WireDisplayManager wireDisplayManager;
  private final StorageDisplayManager storageDisplayManager;
  private final TerminalDisplayManager terminalDisplayManager;
  private final MonitorDisplayManager monitorDisplayManager;
  private final BusDisplayManager busDisplayManager;
  private final RelayDisplayManager relayDisplayManager;
  private final ExortBlockProxyService blockProxyService;
  private final Set<BlockKey> queuedBlocks = new HashSet<>();
  private final Set<ChunkKey> queuedChunks = new HashSet<>();
  private final Set<BlockKey> queuedNetworkStarts = new HashSet<>();
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
      Material storageCarrier,
      WireDisplayManager wireDisplayManager,
      StorageDisplayManager storageDisplayManager,
      TerminalDisplayManager terminalDisplayManager,
      MonitorDisplayManager monitorDisplayManager,
      BusDisplayManager busDisplayManager,
      RelayDisplayManager relayDisplayManager,
      ExortBlockProxyService blockProxyService) {
    this.plugin = plugin;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = wireMaterial;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.relayCarrier = relayCarrier;
    this.storageCarrier = storageCarrier;
    this.wireDisplayManager = wireDisplayManager;
    this.storageDisplayManager = storageDisplayManager;
    this.terminalDisplayManager = terminalDisplayManager;
    this.monitorDisplayManager = monitorDisplayManager;
    this.busDisplayManager = busDisplayManager;
    this.relayDisplayManager = relayDisplayManager;
    this.blockProxyService = blockProxyService;
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
    boolean[] flags = new boolean[6];
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
        });
    boolean hasWire = flags[0];
    boolean hasStorage = flags[1];
    boolean hasTerminal = flags[2];
    boolean hasMonitor = flags[3];
    boolean hasBus = flags[4];
    boolean hasRelay = flags[5];
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
  }

  public void removeWireDisplay(Block block) {
    if (wireDisplayManager != null) {
      wireDisplayManager.removeWire(block);
    }
  }

  public void refreshNetworkFrom(Block block) {
    if (block == null || block.getWorld() == null) return;
    queuedNetworkStarts.add(
        new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    updateQueueGauge();
    scheduleRefreshDrain();
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
    if (blockProxyService != null) {
      blockProxyService.refreshBlock(block);
    }
  }

  private void refreshNetworkFromNow(Block block) {
    if (block == null || block.getWorld() == null) return;
    if (wireMaterial == null) return;
    int hardCap = Math.max(0, wireHardCap);
    if (hardCap == 0) return;
    boolean isWire =
        Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
    boolean isRelay =
        Carriers.matchesCarrier(block, relayCarrier) && RelayMarker.isRelay(plugin, block);
    if (isWire || isRelay) {
      refreshFromNetworkNode(block, hardCap, wireMaterial);
      return;
    }
    for (var face : FACES) {
      Block neighbor = block.getRelative(face);
      if (!isChunkLoaded(neighbor)) continue;
      if (Carriers.matchesCarrier(neighbor, wireMaterial) && WireMarker.isWire(plugin, neighbor)) {
        refreshFromNetworkNode(neighbor, hardCap, wireMaterial);
      } else if (Carriers.matchesCarrier(neighbor, relayCarrier)
          && RelayMarker.isRelay(plugin, neighbor)) {
        refreshFromNetworkNode(neighbor, hardCap, wireMaterial);
      }
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
    int budget = REFRESH_BUDGET_PER_TICK;
    while (budget > 0 && !queuedBlocks.isEmpty()) {
      Iterator<BlockKey> iterator = queuedBlocks.iterator();
      BlockKey key = iterator.next();
      iterator.remove();
      Block block = loadedBlock(key);
      if (block != null) {
        refreshBlockNow(block);
      }
      budget--;
    }
    while (budget > 0 && !queuedChunks.isEmpty()) {
      Iterator<ChunkKey> iterator = queuedChunks.iterator();
      ChunkKey key = iterator.next();
      iterator.remove();
      Chunk chunk = loadedChunk(key);
      if (chunk != null) {
        refreshChunkNow(chunk);
      }
      budget--;
    }
    while (budget > 0 && !queuedNetworkStarts.isEmpty()) {
      Iterator<BlockKey> iterator = queuedNetworkStarts.iterator();
      BlockKey key = iterator.next();
      iterator.remove();
      Block block = loadedBlock(key);
      if (block != null) {
        refreshNetworkFromNow(block);
      }
      budget--;
    }
    updateQueueGauge();
    if (!queuedBlocks.isEmpty() || !queuedChunks.isEmpty() || !queuedNetworkStarts.isEmpty()) {
      PerfStats.incrementCounter("display.budgetOverrun");
      scheduleRefreshDrain();
    }
  }

  private void updateQueueGauge() {
    PerfStats.setGauge(
        "display.queueDepth",
        (long) queuedBlocks.size() + queuedChunks.size() + queuedNetworkStarts.size());
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

  private record BlockKey(UUID world, int x, int y, int z) {}

  private void refreshFromNetworkNode(Block start, int hardCap, Material wireMaterial) {
    Queue<Block> queue = new ArrayDeque<>();
    Set<Block> visited = new HashSet<>();
    Set<Block> terminals = new HashSet<>();
    Set<Block> monitors = new HashSet<>();
    Set<Block> buses = new HashSet<>();
    Set<Block> relays = new HashSet<>();
    NetworkRefreshBudget budget = new NetworkRefreshBudget(hardCap);
    queue.add(start);
    visited.add(start);
    if (Carriers.matchesCarrier(start, wireMaterial) && WireMarker.isWire(plugin, start)) {
      budget.recordStartNode();
    } else if (Carriers.matchesCarrier(start, relayCarrier) && RelayMarker.isRelay(plugin, start)) {
      relays.add(start);
    }
    while (!queue.isEmpty()) {
      Block current = queue.poll();
      if (Carriers.matchesCarrier(current, relayCarrier) && RelayMarker.isRelay(plugin, current)) {
        Block peer = validRelayPeer(current);
        if (peer != null && !visited.contains(peer)) {
          if (!budget.tryVisitNextNode()) {
            continue;
          }
          visited.add(peer);
          relays.add(peer);
          queue.add(peer);
        }
      }
      for (var face : FACES) {
        Block next = current.getRelative(face);
        if (visited.contains(next)) continue;
        if (!isChunkLoaded(next)) continue;
        if (Carriers.matchesCarrier(next, wireMaterial) && WireMarker.isWire(plugin, next)) {
          if (!budget.tryVisitNextNode()) {
            continue;
          }
          visited.add(next);
          queue.add(next);
          continue;
        }
        if (Carriers.matchesCarrier(next, relayCarrier) && RelayMarker.isRelay(plugin, next)) {
          if (!budget.tryVisitNextNode()) {
            continue;
          }
          visited.add(next);
          relays.add(next);
          queue.add(next);
          continue;
        }
        if (Carriers.matchesCarrier(next, terminalCarrier)
            && TerminalMarker.isTerminal(plugin, next)) {
          terminals.add(next);
        } else if (Carriers.matchesCarrier(next, monitorCarrier)
            && MonitorMarker.isMonitor(plugin, next)) {
          monitors.add(next);
        } else if (Carriers.matchesCarrier(next, busCarrier) && BusMarker.isBus(plugin, next)) {
          buses.add(next);
        } else if (Carriers.matchesCarrier(next, storageCarrier)
            && StorageMarker.get(plugin, next).isPresent()) {
          if (storageDisplayManager != null) {
            storageDisplayManager.refresh(next);
          }
        }
      }
    }
    if (wireDisplayManager != null) {
      for (Block wire : visited) {
        if (Carriers.matchesCarrier(wire, wireMaterial) && WireMarker.isWire(plugin, wire)) {
          wireDisplayManager.updateWireAndNeighbors(wire);
        }
      }
    }
    for (Block relay : relays) {
      refreshRelay(relay);
    }
    PerfStats.addCounter("wire.networkRefreshVisited", visited.size());
    if (budget.skipped() > 0) {
      PerfStats.addCounter("wire.networkRefreshSkipped", budget.skipped());
      PerfStats.incrementCounter("wire.networkRefreshHardCapOverflow");
    }
    for (Block terminal : terminals) {
      refreshTerminal(terminal);
    }
    for (Block monitor : monitors) {
      refreshMonitor(monitor);
    }
    for (Block bus : buses) {
      refreshBus(bus);
    }
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
    if (relayDisplayManager != null) {
      relayDisplayManager.refresh(block);
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
    if (relayDisplayManager != null) {
      relayDisplayManager.removeDisplay(block);
    }
    restoreProxyBlock(block);
  }

  private Block validRelayPeer(Block relay) {
    Block peer = RelayMarker.link(plugin, relay).map(RelayMarker.Link::loadedBlock).orElse(null);
    if (peer == null || !isChunkLoaded(peer)) return null;
    if (!Carriers.matchesCarrier(peer, relayCarrier) || !RelayMarker.isRelay(plugin, peer)) {
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
