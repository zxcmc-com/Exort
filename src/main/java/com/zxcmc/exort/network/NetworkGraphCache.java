package com.zxcmc.exort.network;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/**
 * Caches network scans until topology changes (wire/terminal/storage/bus/monitor or chunk
 * load/unload).
 */
public final class NetworkGraphCache {
  private static final BlockFace[] FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };

  private record CacheKey(
      UUID world,
      int x,
      int y,
      int z,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      int relayRangeChunks) {}

  private record BlockKey(UUID world, int x, int y, int z) {}

  private record ChunkKey(UUID world, int x, int z) {}

  public record ChunkPosition(UUID worldId, int chunkX, int chunkZ) {}

  private record CacheEntry(
      TerminalLinkFinder.StorageSearchResult result,
      Set<BlockKey> touchedBlocks,
      Set<ChunkKey> touchedChunks) {}

  private record ScanTraceResult(
      TerminalLinkFinder.StorageSearchResult result,
      Set<BlockKey> touchedBlocks,
      Set<ChunkKey> touchedChunks) {}

  private final Plugin owner;
  private final AtomicLong version = new AtomicLong();
  private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

  public NetworkGraphCache(Plugin owner) {
    this.owner = owner;
  }

  public void invalidateAll() {
    version.incrementAndGet();
    cache.clear();
  }

  public void invalidateAround(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    version.incrementAndGet();
    Set<BlockKey> changedBlocks = new HashSet<>();
    touch(block, changedBlocks, new HashSet<>());
    for (BlockFace face : FACES) {
      touch(block.getRelative(face), changedBlocks, new HashSet<>());
    }
    ChunkKey changedChunk =
        new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    cache.entrySet().removeIf(entry -> intersects(entry.getValue(), changedBlocks, changedChunk));
  }

  public void invalidateChunk(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) {
      return;
    }
    invalidateChunks(
        java.util.List.of(
            new ChunkPosition(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())));
  }

  public void invalidateChunks(Collection<ChunkPosition> chunks) {
    if (chunks == null || chunks.isEmpty()) {
      return;
    }
    Set<ChunkKey> changedChunks = new HashSet<>();
    for (ChunkPosition chunk : chunks) {
      if (chunk == null || chunk.worldId() == null) {
        continue;
      }
      changedChunks.add(new ChunkKey(chunk.worldId(), chunk.chunkX(), chunk.chunkZ()));
    }
    if (changedChunks.isEmpty()) {
      return;
    }
    version.incrementAndGet();
    int before = cache.size();
    cache
        .entrySet()
        .removeIf(entry -> !Collections.disjoint(entry.getValue().touchedChunks(), changedChunks));
    PerfStats.addCounter("network.invalidateChunks", changedChunks.size());
    PerfStats.addCounter("network.invalidateEntries", Math.max(0, before - cache.size()));
  }

  public long currentVersion() {
    return version.get();
  }

  public TerminalLinkFinder.StorageSearchResult find(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      int relayRangeChunks) {
    if (terminal == null) {
      return TerminalLinkFinder.StorageSearchResult.none();
    }
    CacheKey key =
        new CacheKey(
            terminal.getWorld().getUID(),
            terminal.getX(),
            terminal.getY(),
            terminal.getZ(),
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            relayRangeChunks);
    CacheEntry entry = cache.get(key);
    if (entry != null) {
      if (isValid(entry.result, storageCarrier)) {
        PerfStats.incrementCounter("network.cacheHit");
        return entry.result;
      }
      cache.remove(key);
    }
    PerfStats.incrementCounter("network.cacheMiss");
    ScanTraceResult result =
        PerfStats.measure(
            "network.scan",
            () ->
                scanWithTrace(
                    terminal,
                    keys,
                    plugin,
                    wireLimit,
                    wireHardCap,
                    wireMaterial,
                    storageCarrier,
                    relayCarrier,
                    relayRangeChunks));
    cache.put(
        key,
        new CacheEntry(
            result.result(),
            Set.copyOf(result.touchedBlocks()),
            Set.copyOf(result.touchedChunks())));
    return result.result();
  }

  private static boolean intersects(
      CacheEntry entry, Set<BlockKey> changedBlocks, ChunkKey changedChunk) {
    if (entry == null) return false;
    for (BlockKey block : changedBlocks) {
      if (entry.touchedBlocks().contains(block)) {
        return true;
      }
    }
    return entry.touchedChunks().contains(changedChunk);
  }

  private boolean isValid(TerminalLinkFinder.StorageSearchResult result, Material storageCarrier) {
    if (result == null) return true;
    var info = result.data();
    if (info == null || info.block() == null) return true;
    var world = info.block().getWorld();
    if (world == null) return false;
    if (!world.isChunkLoaded(info.block().getX() >> 4, info.block().getZ() >> 4)) {
      return false;
    }
    if (!Carriers.matchesCarrier(info.block(), storageCarrier)) {
      return false;
    }
    return StorageMarker.get(owner, info.block())
        .map(StorageMarker.Data::storageId)
        .map(id -> id.equals(info.storageId()))
        .orElse(false);
  }

  static TerminalLinkFinder.StorageSearchResult scan(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      int relayRangeChunks) {
    if (terminal == null) {
      return TerminalLinkFinder.StorageSearchResult.none();
    }
    return scanWithTrace(
            terminal,
            keys,
            plugin,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            relayRangeChunks)
        .result();
  }

  private static ScanTraceResult scanWithTrace(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      int relayRangeChunks) {
    Set<BlockKey> touchedBlocks = new HashSet<>();
    Set<ChunkKey> touchedChunks = new HashSet<>();
    if (terminal == null) {
      return new ScanTraceResult(
          TerminalLinkFinder.StorageSearchResult.none(), touchedBlocks, touchedChunks);
    }
    touch(terminal, touchedBlocks, touchedChunks);
    int found = 0;
    TerminalLinkFinder.StorageBlockInfo data = null;
    Set<BlockKey> foundStorages = new HashSet<>();
    Queue<Block> queue = new ArrayDeque<>();
    Set<Block> visited = new HashSet<>();

    if (isTraversable(terminal, keys, plugin, wireMaterial, relayCarrier)) {
      visited.add(terminal);
      queue.add(terminal);
    } else {
      BlockFace blockedFace = frontFace(terminal, plugin);
      for (BlockFace face : FACES) {
        if (blockedFace != null && face == blockedFace) continue;
        Block neighbor = terminal.getRelative(face);
        touch(neighbor, touchedBlocks, touchedChunks);
        if (!isChunkLoaded(neighbor)) continue;
        Optional<TerminalLinkFinder.StorageBlockInfo> info =
            readStorageInfo(plugin, neighbor, storageCarrier);
        if (info.isPresent() && foundStorages.add(blockKey(info.get().block()))) {
          found++;
          data = info.get();
          if (found > 1) {
            return new ScanTraceResult(
                TerminalLinkFinder.StorageSearchResult.multiple(found, data),
                touchedBlocks,
                touchedChunks);
          }
        } else if (isTraversable(neighbor, keys, plugin, wireMaterial, relayCarrier)) {
          visited.add(neighbor);
          queue.add(neighbor);
        }
      }
      if (found > 0) {
        return new ScanTraceResult(
            TerminalLinkFinder.StorageSearchResult.connected(data), touchedBlocks, touchedChunks);
      }
    }
    int wiresUsed = countWires(visited, keys, plugin, wireMaterial);
    int nodesUsed = visited.size();
    while (!queue.isEmpty() && wiresUsed <= wireLimit && nodesUsed <= wireHardCap) {
      Block current = queue.poll();
      if (isRelay(current, plugin, relayCarrier)) {
        Block peer = validRelayPeer(current, plugin, relayCarrier, relayRangeChunks);
        RelayMarker.link(plugin, current).ifPresent(link -> touch(link, touchedChunks));
        if (peer != null && !visited.contains(peer)) {
          touch(peer, touchedBlocks, touchedChunks);
          visited.add(peer);
          queue.add(peer);
          nodesUsed++;
          if (nodesUsed > wireHardCap) {
            return new ScanTraceResult(
                TerminalLinkFinder.StorageSearchResult.hardCap(), touchedBlocks, touchedChunks);
          }
        }
      }
      for (BlockFace face : FACES) {
        Block next = current.getRelative(face);
        touch(next, touchedBlocks, touchedChunks);
        if (visited.contains(next)) continue;
        if (!isChunkLoaded(next)) continue;
        Optional<TerminalLinkFinder.StorageBlockInfo> info =
            readStorageInfo(plugin, next, storageCarrier);
        if (info.isPresent() && foundStorages.add(blockKey(info.get().block()))) {
          found++;
          data = info.get();
          if (found > 1) {
            return new ScanTraceResult(
                TerminalLinkFinder.StorageSearchResult.multiple(found, data),
                touchedBlocks,
                touchedChunks);
          }
        } else if (isTraversable(next, keys, plugin, wireMaterial, relayCarrier)) {
          visited.add(next);
          queue.add(next);
          nodesUsed++;
          if (isWire(next, keys, plugin, wireMaterial)) {
            wiresUsed++;
          }
          if (nodesUsed > wireHardCap) {
            return new ScanTraceResult(
                TerminalLinkFinder.StorageSearchResult.hardCap(), touchedBlocks, touchedChunks);
          }
        }
      }
    }
    if (!queue.isEmpty()) {
      if (nodesUsed > wireHardCap) {
        return new ScanTraceResult(
            TerminalLinkFinder.StorageSearchResult.hardCap(), touchedBlocks, touchedChunks);
      }
      if (wiresUsed > wireLimit) {
        return new ScanTraceResult(
            TerminalLinkFinder.StorageSearchResult.wireLimit(), touchedBlocks, touchedChunks);
      }
    }
    return new ScanTraceResult(
        found == 1
            ? TerminalLinkFinder.StorageSearchResult.connected(data)
            : TerminalLinkFinder.StorageSearchResult.none(),
        touchedBlocks,
        touchedChunks);
  }

  private static BlockKey blockKey(Block block) {
    return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }

  private static void touch(RelayMarker.Link link, Set<ChunkKey> chunks) {
    if (link == null || chunks == null) {
      return;
    }
    chunks.add(new ChunkKey(link.worldId(), link.x() >> 4, link.z() >> 4));
  }

  private static void touch(Block block, Set<BlockKey> blocks, Set<ChunkKey> chunks) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    UUID world = block.getWorld().getUID();
    blocks.add(new BlockKey(world, block.getX(), block.getY(), block.getZ()));
    chunks.add(new ChunkKey(world, block.getX() >> 4, block.getZ() >> 4));
  }

  private static boolean isWire(
      Block block, StorageKeys keys, Plugin plugin, Material wireMaterial) {
    return Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
  }

  private static boolean isRelay(Block block, Plugin plugin, Material relayCarrier) {
    return relayCarrier != null
        && Carriers.matchesCarrier(block, relayCarrier)
        && RelayMarker.isRelay(plugin, block);
  }

  private static boolean isTraversable(
      Block block, StorageKeys keys, Plugin plugin, Material wireMaterial, Material relayCarrier) {
    return isWire(block, keys, plugin, wireMaterial) || isRelay(block, plugin, relayCarrier);
  }

  private static int countWires(
      Set<Block> blocks, StorageKeys keys, Plugin plugin, Material wireMaterial) {
    int count = 0;
    for (Block block : blocks) {
      if (isWire(block, keys, plugin, wireMaterial)) {
        count++;
      }
    }
    return count;
  }

  private static Block validRelayPeer(
      Block relay, Plugin plugin, Material relayCarrier, int relayRangeChunks) {
    if (relay == null || relay.getWorld() == null) {
      return null;
    }
    Optional<RelayMarker.Link> link = RelayMarker.link(plugin, relay);
    if (link.isEmpty()) {
      return null;
    }
    Block peer = link.get().loadedBlock();
    if (peer == null || peer.getWorld() == null) {
      return null;
    }
    if (!relay.getWorld().getUID().equals(peer.getWorld().getUID())) {
      return null;
    }
    if (!isRelay(peer, plugin, relayCarrier)) {
      return null;
    }
    if (RelayMarker.link(plugin, peer).filter(back -> back.sameBlock(relay)).isEmpty()) {
      return null;
    }
    return inRelayRange(relay, peer, relayRangeChunks) ? peer : null;
  }

  public static boolean inRelayRange(Block first, Block second, int relayRangeChunks) {
    if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
      return false;
    }
    if (!first.getWorld().getUID().equals(second.getWorld().getUID())) {
      return false;
    }
    int dx = Math.abs((first.getX() >> 4) - (second.getX() >> 4));
    int dz = Math.abs((first.getZ() >> 4) - (second.getZ() >> 4));
    return dx + dz <= Math.max(0, relayRangeChunks);
  }

  private static boolean isChunkLoaded(Block block) {
    if (block == null || block.getWorld() == null) return false;
    return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private static Optional<TerminalLinkFinder.StorageBlockInfo> readStorageInfo(
      Plugin plugin, Block block, Material storageCarrier) {
    if (!Carriers.matchesCarrier(block, storageCarrier)) return Optional.empty();
    return StorageMarker.get(plugin, block)
        .map(
            data ->
                new TerminalLinkFinder.StorageBlockInfo(
                    block, data.storageId(), data.tier(), data.displayName()));
  }

  private static BlockFace frontFace(Block block, Plugin plugin) {
    if (TerminalMarker.isTerminal(plugin, block)) {
      return TerminalMarker.facing(plugin, block).orElse(null);
    }
    if (MonitorMarker.isMonitor(plugin, block)) {
      return MonitorMarker.facing(plugin, block).orElse(null);
    }
    if (BusMarker.isBus(plugin, block)) {
      return BusMarker.get(plugin, block).map(BusMarker.Data::facing).orElse(null);
    }
    return null;
  }
}
