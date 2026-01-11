package com.zxcmc.exort.core.network;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches network scans until topology changes (wire/terminal/storage/bus/monitor or chunk load/unload).
 */
public final class NetworkGraphCache {
    private static final BlockFace[] FACES = new BlockFace[]{
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private record CacheKey(UUID world,
                            int x,
                            int y,
                            int z,
                            int wireLimit,
                            int wireHardCap,
                            Material wireMaterial,
                            Material storageCarrier) {
    }

    private record CacheEntry(long version, TerminalLinkFinder.StorageSearchResult result) {
    }

    private final ExortPlugin plugin;
    private final AtomicLong version = new AtomicLong();
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    public NetworkGraphCache(ExortPlugin plugin) {
        this.plugin = plugin;
    }

    public void invalidateAll() {
        version.incrementAndGet();
        cache.clear();
    }

    public TerminalLinkFinder.StorageSearchResult find(Block terminal,
                                                       StorageKeys keys,
                                                       Plugin plugin,
                                                       int wireLimit,
                                                       int wireHardCap,
                                                       Material wireMaterial,
                                                       Material storageCarrier) {
        if (terminal == null) {
            return new TerminalLinkFinder.StorageSearchResult(0, null);
        }
        CacheKey key = new CacheKey(
                terminal.getWorld().getUID(),
                terminal.getX(),
                terminal.getY(),
                terminal.getZ(),
                wireLimit,
                wireHardCap,
                wireMaterial,
                storageCarrier
        );
        long current = version.get();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.version == current) {
            if (isValid(entry.result, storageCarrier)) {
                return entry.result;
            }
            cache.remove(key);
        }
        TerminalLinkFinder.StorageSearchResult result = scan(terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
        cache.put(key, new CacheEntry(current, result));
        return result;
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
        return StorageMarker.get(plugin, info.block())
                .map(StorageMarker.Data::storageId)
                .map(id -> id.equals(info.storageId()))
                .orElse(false);
    }

    static TerminalLinkFinder.StorageSearchResult scan(Block terminal,
                                                       StorageKeys keys,
                                                       Plugin plugin,
                                                       int wireLimit,
                                                       int wireHardCap,
                                                       Material wireMaterial,
                                                       Material storageCarrier) {
        if (terminal == null) {
            return new TerminalLinkFinder.StorageSearchResult(0, null);
        }
        int found = 0;
        TerminalLinkFinder.StorageBlockInfo data = null;
        BlockFace blockedFace = frontFace(terminal, plugin);
        for (BlockFace face : FACES) {
            if (blockedFace != null && face == blockedFace) continue;
            Block neighbor = terminal.getRelative(face);
            if (!isChunkLoaded(neighbor)) continue;
            Optional<TerminalLinkFinder.StorageBlockInfo> info = readStorageInfo(plugin, neighbor, storageCarrier);
            if (info.isPresent()) {
                found++;
                data = info.get();
                if (found > 1) {
                    return new TerminalLinkFinder.StorageSearchResult(found, data);
                }
            }
        }
        if (found > 0) {
            return new TerminalLinkFinder.StorageSearchResult(found, data);
        }
        Queue<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        for (BlockFace face : FACES) {
            if (blockedFace != null && face == blockedFace) continue;
            Block neighbor = terminal.getRelative(face);
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
                if (visited.contains(next)) continue;
                if (!isChunkLoaded(next)) continue;
                Optional<TerminalLinkFinder.StorageBlockInfo> info = readStorageInfo(plugin, next, storageCarrier);
                if (info.isPresent()) {
                    found++;
                    data = info.get();
                    if (found > 1) {
                        return new TerminalLinkFinder.StorageSearchResult(found, data);
                    }
                } else if (isWire(next, keys, plugin, wireMaterial)) {
                    visited.add(next);
                    queue.add(next);
                    wiresUsed++;
                    if (wiresUsed > wireHardCap) {
                        return new TerminalLinkFinder.StorageSearchResult(found, data);
                    }
                }
            }
        }
        return new TerminalLinkFinder.StorageSearchResult(found, data);
    }

    private static boolean isWire(Block block, StorageKeys keys, Plugin plugin, Material wireMaterial) {
        return Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
    }

    private static boolean isChunkLoaded(Block block) {
        if (block == null || block.getWorld() == null) return false;
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    private static Optional<TerminalLinkFinder.StorageBlockInfo> readStorageInfo(Plugin plugin, Block block, Material storageCarrier) {
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
