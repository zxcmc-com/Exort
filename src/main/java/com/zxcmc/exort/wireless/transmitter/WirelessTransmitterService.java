package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.items.CustomItemClassifier;
import com.zxcmc.exort.items.CustomItemRegistry;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
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
import org.bukkit.persistence.PersistentDataType;
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

  private static TransmitterSpatialIndex.Position position(Block block) {
    return new TransmitterSpatialIndex.Position(
        block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }

  private record CachedStatus(long graphVersion, Status status) {}

  private final Plugin plugin;
  private final StorageKeys keys;
  private final boolean enabled;
  private final WirelessRuntimeConfig wirelessConfig;
  private final int wireLimit;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material transmitterCarrier;
  private final Material relayCarrier;
  private final Supplier<NetworkGraphCache> graphCache;
  private final TransmitterSpatialIndex transmitters = new TransmitterSpatialIndex();
  private final Map<TransmitterSpatialIndex.Position, Integer> effectiveRanges =
      new ConcurrentHashMap<>();
  private final Map<TransmitterSpatialIndex.Position, CachedStatus> statusCache =
      new ConcurrentHashMap<>();

  public WirelessTransmitterService(
      Plugin plugin,
      StorageKeys keys,
      WirelessRuntimeConfig wirelessConfig,
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
    this.wirelessConfig = Objects.requireNonNull(wirelessConfig, "wirelessConfig");
    this.enabled = wirelessConfig.enabled();
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
    return wirelessConfig.rangeBlocks();
  }

  public int effectiveRangeBlocks(Block transmitter) {
    if (transmitter == null || transmitter.getWorld() == null) {
      return wirelessConfig.rangeBlocks();
    }
    TransmitterSpatialIndex.Position pos = position(transmitter);
    return effectiveRanges.computeIfAbsent(pos, ignored -> computeEffectiveRange(transmitter));
  }

  public void refreshRegistration(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    invalidateStatus(block);
    if (enabled && isValidTransmitter(block)) {
      register(block);
    } else {
      unregister(block);
    }
  }

  public void scanLoadedChunks() {
    transmitters.clear();
    effectiveRanges.clear();
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
    TransmitterSpatialIndex.Position pos = position(block);
    int effectiveRange = computeEffectiveRange(block);
    effectiveRanges.put(pos, effectiveRange);
    transmitters.add(pos, effectiveRange < 0);
    statusCache.remove(pos);
  }

  public void unregister(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    TransmitterSpatialIndex.Position pos = position(block);
    transmitters.remove(pos);
    effectiveRanges.remove(pos);
    statusCache.remove(pos);
  }

  public void invalidateStatus(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    statusCache.remove(position(block));
  }

  public Status status(Block block) {
    if (!enabled) {
      return new Status(State.DISABLED, 0, null);
    }
    if (block == null || block.getWorld() == null || !isChunkLoaded(block)) {
      return new Status(State.MISSING, 0, null);
    }
    TransmitterSpatialIndex.Position pos = position(block);
    if (!isValidTransmitter(block)) {
      unregister(block);
      return new Status(State.MISSING, 0, null);
    }
    if (TransmitterStoredTerminal.mode(plugin, block) == TransmitterMode.DISABLED) {
      statusCache.remove(pos);
      return new Status(State.MODE_DISABLED, 0, null);
    }
    Integer effectiveRange = effectiveRanges.get(pos);
    if (effectiveRange == null) {
      register(block);
    } else {
      transmitters.add(pos, effectiveRange < 0);
    }
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
        || transmitter.getWorld() == null
        || playerLocation.getWorld() == null) {
      return false;
    }
    int effectiveRange = effectiveRangeBlocks(transmitter);
    return effectiveRange < 0
        ? transmitter.getWorld().getUID().equals(playerLocation.getWorld().getUID())
        : withinHorizontalRange(
            transmitter.getWorld().getUID(),
            transmitter.getX(),
            transmitter.getZ(),
            playerLocation,
            effectiveRange);
  }

  public boolean hasCoverage(String storageId, Location playerLocation) {
    return PerfStats.measure(
        "wireless.coverage", () -> hasCoverageMeasured(storageId, playerLocation));
  }

  private boolean hasCoverageMeasured(String storageId, Location playerLocation) {
    if (!enabled
        || storageId == null
        || storageId.isBlank()
        || playerLocation == null
        || playerLocation.getWorld() == null) {
      return false;
    }
    UUID worldId = playerLocation.getWorld().getUID();
    TransmitterSpatialIndex.VisitResult global =
        transmitters.visitGlobal(worldId, pos -> matchesGlobalCandidate(pos, storageId));
    recordCoverageCandidates(global.examined());
    if (global.matched()) {
      return true;
    }
    TransmitterSpatialIndex.VisitResult finite =
        transmitters.visitCandidates(
            worldId,
            playerLocation.getBlockX(),
            playerLocation.getBlockZ(),
            wirelessConfig.maxFiniteRangeBlocks(),
            pos -> matchesFiniteCandidate(pos, storageId, playerLocation));
    recordCoverageCandidates(finite.examined());
    return finite.matched();
  }

  private boolean matchesGlobalCandidate(TransmitterSpatialIndex.Position pos, String storageId) {
    Block block = blockAtLoaded(pos);
    if (block == null) {
      unregister(pos);
      return false;
    }
    if (effectiveRangeBlocks(block) >= 0) {
      refreshRegistration(block);
      return false;
    }
    Status status = status(block);
    return status.active() && storageId.equals(status.storage().storageId());
  }

  private boolean matchesFiniteCandidate(
      TransmitterSpatialIndex.Position pos, String storageId, Location playerLocation) {
    Block block = blockAtLoaded(pos);
    if (block == null) {
      unregister(pos);
      return false;
    }
    int effectiveRange = effectiveRangeBlocks(block);
    if (effectiveRange < 0
        || !withinHorizontalRange(
            pos.worldId(), pos.x(), pos.z(), playerLocation, effectiveRange)) {
      return false;
    }
    Status status = status(block);
    return status.active() && storageId.equals(status.storage().storageId());
  }

  private void recordCoverageCandidates(int examined) {
    PerfStats.addCounter("wireless.coverageCandidates", examined);
    PerfStats.setGauge("wireless.coverageCandidatesLast", examined);
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
      UUID worldId, int blockX, int blockZ, Location playerLocation, int rangeBlocks) {
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

  private java.util.Optional<WirelessBoosterTier> boosterTier(Block block) {
    if (block == null || block.getWorld() == null) {
      return java.util.Optional.empty();
    }
    return TransmitterStoredBooster.get(plugin, block, this::isWirelessBooster, ignored -> {})
        .flatMap(this::wirelessBoosterTier);
  }

  private int computeEffectiveRange(Block block) {
    return wirelessConfig.effectiveRangeBlocks(boosterTier(block).orElse(null));
  }

  private boolean isWirelessBooster(org.bukkit.inventory.ItemStack stack) {
    return wirelessBoosterTier(stack).isPresent();
  }

  private java.util.Optional<WirelessBoosterTier> wirelessBoosterTier(
      org.bukkit.inventory.ItemStack stack) {
    if (!CustomItemClassifier.isType(keys, stack, CustomItemRegistry.WIRELESS_BOOSTER.id())) {
      return java.util.Optional.empty();
    }
    String raw =
        stack
            .getItemMeta()
            .getPersistentDataContainer()
            .get(keys.wirelessBoosterTier(), PersistentDataType.STRING);
    return WirelessBoosterTier.fromId(raw);
  }

  private Block blockAtLoaded(TransmitterSpatialIndex.Position pos) {
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
    transmitters.removeChunk(worldId, chunkX, chunkZ);
    effectiveRanges
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

  private void unregister(TransmitterSpatialIndex.Position pos) {
    transmitters.remove(pos);
    effectiveRanges.remove(pos);
    statusCache.remove(pos);
  }

  private long graphVersion() {
    NetworkGraphCache cache = graphCache.get();
    return cache == null ? -1L : cache.currentVersion();
  }
}
