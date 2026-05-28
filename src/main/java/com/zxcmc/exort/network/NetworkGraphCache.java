package com.zxcmc.exort.network;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
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
      Material storageCarrier) {}

  private record BlockKey(UUID world, int x, int y, int z) {}

  private record ChunkKey(UUID world, int x, int z) {}

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
    version.incrementAndGet();
    ChunkKey changedChunk = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    cache.entrySet().removeIf(entry -> entry.getValue().touchedChunks().contains(changedChunk));
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
      Material storageCarrier) {
    if (terminal == null) {
      return new TerminalLinkFinder.StorageSearchResult(0, null);
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
            storageCarrier);
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
                    terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier));
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
      Material storageCarrier) {
    if (terminal == null) {
      return new TerminalLinkFinder.StorageSearchResult(0, null);
    }
    return scanWithTrace(
            terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier)
        .result();
  }

  private static ScanTraceResult scanWithTrace(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier) {
    Set<BlockKey> touchedBlocks = new HashSet<>();
    Set<ChunkKey> touchedChunks = new HashSet<>();
    if (terminal == null) {
      return new ScanTraceResult(
          new TerminalLinkFinder.StorageSearchResult(0, null), touchedBlocks, touchedChunks);
    }
    touch(terminal, touchedBlocks, touchedChunks);
    int found = 0;
    TerminalLinkFinder.StorageBlockInfo data = null;
    BlockFace blockedFace = frontFace(terminal, plugin);
    for (BlockFace face : FACES) {
      if (blockedFace != null && face == blockedFace) continue;
      Block neighbor = terminal.getRelative(face);
      touch(neighbor, touchedBlocks, touchedChunks);
      if (!isChunkLoaded(neighbor)) continue;
      Optional<TerminalLinkFinder.StorageBlockInfo> info =
          readStorageInfo(plugin, neighbor, storageCarrier);
      if (info.isPresent()) {
        found++;
        data = info.get();
        if (found > 1) {
          return new ScanTraceResult(
              new TerminalLinkFinder.StorageSearchResult(found, data),
              touchedBlocks,
              touchedChunks);
        }
      }
    }
    if (found > 0) {
      return new ScanTraceResult(
          new TerminalLinkFinder.StorageSearchResult(found, data), touchedBlocks, touchedChunks);
    }
    Queue<Block> queue = new ArrayDeque<>();
    Set<Block> visited = new HashSet<>();
    for (BlockFace face : FACES) {
      if (blockedFace != null && face == blockedFace) continue;
      Block neighbor = terminal.getRelative(face);
      touch(neighbor, touchedBlocks, touchedChunks);
      if (!isChunkLoaded(neighbor)) continue;
      if (isWire(neighbor, keys, plugin, wireMaterial)) {
        queue.add(neighbor);
        visited.add(neighbor);
      }
    }
    int wiresUsed = visited.size();
    while (!queue.isEmpty() && wiresUsed <= wireLimit && wiresUsed <= wireHardCap) {
      Block current = queue.poll();
      for (BlockFace face : FACES) {
        Block next = current.getRelative(face);
        touch(next, touchedBlocks, touchedChunks);
        if (visited.contains(next)) continue;
        if (!isChunkLoaded(next)) continue;
        Optional<TerminalLinkFinder.StorageBlockInfo> info =
            readStorageInfo(plugin, next, storageCarrier);
        if (info.isPresent()) {
          found++;
          data = info.get();
          if (found > 1) {
            return new ScanTraceResult(
                new TerminalLinkFinder.StorageSearchResult(found, data),
                touchedBlocks,
                touchedChunks);
          }
        } else if (isWire(next, keys, plugin, wireMaterial)) {
          visited.add(next);
          queue.add(next);
          wiresUsed++;
          if (wiresUsed > wireHardCap) {
            return new ScanTraceResult(
                new TerminalLinkFinder.StorageSearchResult(found, data),
                touchedBlocks,
                touchedChunks);
          }
        }
      }
    }
    return new ScanTraceResult(
        new TerminalLinkFinder.StorageSearchResult(found, data), touchedBlocks, touchedChunks);
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

  private static boolean isChunkLoaded(Block block) {
    if (block == null || block.getWorld() == null) return false;
    return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private static Optional<TerminalLinkFinder.StorageBlockInfo> readStorageInfo(
      Plugin plugin, Block block, Material storageCarrier) {
    if (!Carriers.matchesCarrier(block, storageCarrier)) return Optional.empty();
    return StorageMarker.get(plugin, block)
        .map(data -> new TerminalLinkFinder.StorageBlockInfo(block, data.storageId(), data.tier()));
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
