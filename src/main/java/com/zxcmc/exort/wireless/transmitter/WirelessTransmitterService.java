package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

public final class WirelessTransmitterService implements Listener {
  public enum State {
    DISABLED,
    MODE_DISABLED,
    MISSING,
    NO_STORAGE,
    MULTIPLE_STORAGES,
    ACTIVE
  }

  public record Status(State state, int storageCount, TerminalLinkFinder.StorageBlockInfo storage) {
    public boolean active() {
      return state == State.ACTIVE && storage != null;
    }
  }

  private record TransmitterPos(UUID worldId, int x, int y, int z) {
    static TransmitterPos of(Block block) {
      return new TransmitterPos(
          block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  private record CachedStatus(long graphVersion, Status status) {}

  private final Plugin plugin;
  private final StorageKeys keys;
  private final boolean enabled;
  private final int rangeBlocks;
  private final int wireLimit;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material transmitterCarrier;
  private final Material relayCarrier;
  private final Supplier<NetworkGraphCache> graphCache;
  private final Map<TransmitterPos, Boolean> transmitters = new ConcurrentHashMap<>();
  private final Map<TransmitterPos, CachedStatus> statusCache = new ConcurrentHashMap<>();

  public WirelessTransmitterService(
      Plugin plugin,
      StorageKeys keys,
      boolean enabled,
      int rangeBlocks,
      int wireLimit,
      int wireHardCap,
      int relayRangeChunks,
      Material wireMaterial,
      Material storageCarrier,
      Material transmitterCarrier,
      Material relayCarrier,
      Supplier<NetworkGraphCache> graphCache) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.enabled = enabled;
    this.rangeBlocks = Math.max(0, rangeBlocks);
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = Objects.requireNonNull(wireMaterial, "wireMaterial");
    this.storageCarrier = Objects.requireNonNull(storageCarrier, "storageCarrier");
    this.transmitterCarrier = Objects.requireNonNull(transmitterCarrier, "transmitterCarrier");
    this.relayCarrier = relayCarrier;
    this.graphCache = Objects.requireNonNull(graphCache, "graphCache");
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int rangeBlocks() {
    return rangeBlocks;
  }

  public void scanLoadedChunks() {
    transmitters.clear();
    statusCache.clear();
    if (!enabled) {
      return;
    }
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        scanChunk(chunk);
      }
    }
  }

  public void scanChunk(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) {
      return;
    }
    unregisterChunk(chunk);
    if (!enabled || !ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      return;
    }
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (isValidTransmitter(block)) {
            register(block);
          }
        });
  }

  public void register(Block block) {
    if (!enabled || !isValidTransmitter(block)) {
      return;
    }
    TransmitterPos pos = TransmitterPos.of(block);
    transmitters.put(pos, Boolean.TRUE);
    statusCache.remove(pos);
  }

  public void unregister(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    TransmitterPos pos = TransmitterPos.of(block);
    transmitters.remove(pos);
    statusCache.remove(pos);
  }

  public void invalidateStatus(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    statusCache.remove(TransmitterPos.of(block));
  }

  public Status status(Block block) {
    if (!enabled) {
      return new Status(State.DISABLED, 0, null);
    }
    if (block == null || block.getWorld() == null || !isChunkLoaded(block)) {
      return new Status(State.MISSING, 0, null);
    }
    TransmitterPos pos = TransmitterPos.of(block);
    if (!isValidTransmitter(block)) {
      unregister(block);
      return new Status(State.MISSING, 0, null);
    }
    if (TransmitterStoredTerminal.mode(plugin, block) == TransmitterMode.DISABLED) {
      statusCache.remove(pos);
      return new Status(State.MODE_DISABLED, 0, null);
    }
    transmitters.put(pos, Boolean.TRUE);
    long version = graphVersion();
    CachedStatus cached = statusCache.get(pos);
    if (version >= 0 && cached != null && cached.graphVersion() == version) {
      return cached.status();
    }
    Status resolved = resolveStatus(block);
    if (version >= 0) {
      statusCache.put(pos, new CachedStatus(version, resolved));
    }
    return resolved;
  }

  public boolean coversPlayer(Block transmitter, Location playerLocation) {
    if (!enabled
        || transmitter == null
        || playerLocation == null
        || transmitter.getWorld() == null) {
      return false;
    }
    return withinHorizontalRange(
        transmitter.getWorld().getUID(), transmitter.getX(), transmitter.getZ(), playerLocation);
  }

  public boolean hasCoverage(String storageId, Location playerLocation) {
    if (!enabled || storageId == null || storageId.isBlank() || playerLocation == null) {
      return false;
    }
    for (TransmitterPos pos : transmitters.keySet()) {
      if (!withinHorizontalRange(pos.worldId(), pos.x(), pos.z(), playerLocation)) {
        continue;
      }
      Block block = blockAtLoaded(pos);
      if (block == null) {
        unregister(pos);
        continue;
      }
      Status status = status(block);
      if (status.active() && storageId.equals(status.storage().storageId())) {
        return true;
      }
    }
    return false;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    scanChunk(event.getChunk());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkUnload(ChunkUnloadEvent event) {
    unregisterChunk(event.getChunk());
  }

  private Status resolveStatus(Block block) {
    TerminalLinkFinder.StorageSearchResult result =
        TerminalLinkFinder.find(
            block,
            keys,
            plugin,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            relayRangeChunks);
    if (result.count() == 1 && result.data() != null && result.data().block() != null) {
      return new Status(State.ACTIVE, 1, result.data());
    }
    if (result.count() > 1) {
      return new Status(State.MULTIPLE_STORAGES, result.count(), result.data());
    }
    return new Status(State.NO_STORAGE, 0, null);
  }

  private boolean isValidTransmitter(Block block) {
    return block != null
        && block.getWorld() != null
        && Carriers.matchesCarrier(block, transmitterCarrier)
        && TransmitterMarker.isTransmitter(plugin, block);
  }

  private boolean withinHorizontalRange(
      UUID worldId, int blockX, int blockZ, Location playerLocation) {
    if (worldId == null || playerLocation == null || playerLocation.getWorld() == null) {
      return false;
    }
    if (!worldId.equals(playerLocation.getWorld().getUID())) {
      return false;
    }
    double dx = playerLocation.getX() - (blockX + 0.5D);
    double dz = playerLocation.getZ() - (blockZ + 0.5D);
    double range = rangeBlocks;
    return dx * dx + dz * dz <= range * range;
  }

  private Block blockAtLoaded(TransmitterPos pos) {
    if (pos == null || pos.worldId() == null) {
      return null;
    }
    World world = Bukkit.getWorld(pos.worldId());
    if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
      return null;
    }
    return world.getBlockAt(pos.x(), pos.y(), pos.z());
  }

  private boolean isChunkLoaded(Block block) {
    return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private void unregisterChunk(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) {
      return;
    }
    UUID worldId = chunk.getWorld().getUID();
    int chunkX = chunk.getX();
    int chunkZ = chunk.getZ();
    transmitters
        .keySet()
        .removeIf(
            pos ->
                pos.worldId().equals(worldId) && pos.x() >> 4 == chunkX && pos.z() >> 4 == chunkZ);
    statusCache
        .keySet()
        .removeIf(
            pos ->
                pos.worldId().equals(worldId) && pos.x() >> 4 == chunkX && pos.z() >> 4 == chunkZ);
  }

  private void unregister(TransmitterPos pos) {
    transmitters.remove(pos);
    statusCache.remove(pos);
  }

  private long graphVersion() {
    NetworkGraphCache cache = graphCache.get();
    return cache == null ? -1L : cache.currentVersion();
  }
}
