package com.zxcmc.exort.breaking.explosion;

import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

final class ExortExplosionResolver {
  private static final double RAY_STEP = 0.3D;
  private static final float INITIAL_STRENGTH_MULTIPLIER = 1.3F;
  private static final float AIR_ATTENUATION_PER_STEP = 0.22500001F;
  private static final double SCAN_PADDING_BLOCKS = 1.0D;

  private final Plugin plugin;
  private final RuntimeMaterials materials;
  private final ExortBlastResistance blastResistance;

  ExortExplosionResolver(Plugin plugin, RuntimeMaterials materials) {
    this(plugin, materials, ExortBlastResistance.defaults());
  }

  ExortExplosionResolver(
      Plugin plugin, RuntimeMaterials materials, ExortBlastResistance blastResistance) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.materials = Objects.requireNonNull(materials, "materials");
    this.blastResistance = Objects.requireNonNull(blastResistance, "blastResistance");
  }

  ExplosionPlan plan(Location center, float radius, Collection<Block> knownBlocks) {
    Map<BlockKey, ExortExplosionBlock> candidates = new HashMap<>();
    collectKnownBlocks(candidates, knownBlocks);
    collectLoadedChunkMarkers(candidates, center, radius);
    List<ExortExplosionBlock> destroyed =
        candidates.values().stream()
            .filter(target -> shouldDestroy(center, radius, target))
            .sorted(Comparator.comparing(target -> BlockKey.of(target.block())))
            .toList();
    return new ExplosionPlan(Set.copyOf(candidates.keySet()), destroyed);
  }

  float estimateRadius(Location center, Collection<Block> blocks) {
    if (center == null || center.getWorld() == null || blocks == null || blocks.isEmpty()) {
      return 0.0F;
    }
    World world = center.getWorld();
    double maxDistance = 0.0D;
    for (Block block : blocks) {
      if (block == null || block.getWorld() == null) continue;
      if (!sameWorld(world, block.getWorld())) continue;
      double dx = block.getX() + 0.5D - center.getX();
      double dy = block.getY() + 0.5D - center.getY();
      double dz = block.getZ() + 0.5D - center.getZ();
      maxDistance = Math.max(maxDistance, Math.sqrt(dx * dx + dy * dy + dz * dz));
    }
    if (maxDistance <= 0.0D) {
      return blocks.isEmpty() ? 0.0F : 1.0F;
    }
    return (float) Math.max(1.0D, (maxDistance + 0.5D) / 2.0D);
  }

  ExortExplosionBlock resolve(Block block) {
    if (block == null) return null;
    if (Carriers.matchesCarrier(block, materials.terminalCarrier())
        && TerminalMarker.isTerminal(plugin, block)) {
      return block(block, BreakType.TERMINAL);
    }
    if (Carriers.matchesCarrier(block, materials.monitorCarrier())
        && MonitorMarker.isMonitor(plugin, block)) {
      return block(block, BreakType.MONITOR);
    }
    if (Carriers.matchesCarrier(block, materials.busCarrier()) && BusMarker.isBus(plugin, block)) {
      return block(block, BreakType.BUS);
    }
    if (Carriers.matchesCarrier(block, materials.relayCarrier())
        && RelayMarker.isRelay(plugin, block)) {
      return block(block, BreakType.RELAY);
    }
    if (Carriers.matchesCarrier(block, materials.wire()) && WireMarker.isWire(plugin, block)) {
      return block(block, BreakType.WIRE);
    }
    if (Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageCoreMarker.isCore(plugin, block)) {
      return block(block, BreakType.STORAGE);
    }
    if (Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageMarker.isMarkedStorage(plugin, block)) {
      return block(block, BreakType.STORAGE);
    }
    return null;
  }

  private ExortExplosionBlock block(Block block, BreakType type) {
    return new ExortExplosionBlock(block, type, blastResistance.forBreakType(type));
  }

  private void collectKnownBlocks(
      Map<BlockKey, ExortExplosionBlock> candidates, Collection<Block> knownBlocks) {
    if (knownBlocks == null) return;
    for (Block block : knownBlocks) {
      addCandidate(candidates, block);
    }
  }

  private void collectLoadedChunkMarkers(
      Map<BlockKey, ExortExplosionBlock> candidates, Location center, float radius) {
    if (center == null || center.getWorld() == null) return;
    World world = center.getWorld();
    double scanRadius = scanRadius(radius);
    int minChunk = floorChunk(center.getX() - scanRadius);
    int maxChunk = floorChunk(center.getX() + scanRadius);
    int minChunkZ = floorChunk(center.getZ() - scanRadius);
    int maxChunkZ = floorChunk(center.getZ() + scanRadius);
    for (Chunk chunk : world.getLoadedChunks()) {
      if (chunk.getX() < minChunk || chunk.getX() > maxChunk) continue;
      if (chunk.getZ() < minChunkZ || chunk.getZ() > maxChunkZ) continue;
      if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) continue;
      ChunkMarkerStore.forEachBlock(
          plugin, chunk, (block, root) -> addCandidate(candidates, block));
    }
  }

  private void addCandidate(Map<BlockKey, ExortExplosionBlock> candidates, Block block) {
    ExortExplosionBlock target = resolve(block);
    if (target == null) return;
    BlockKey key = BlockKey.of(block);
    if (key == null) return;
    candidates.putIfAbsent(key, target);
  }

  private boolean shouldDestroy(Location center, float radius, ExortExplosionBlock target) {
    if (center == null || center.getWorld() == null || target == null || target.block() == null) {
      return false;
    }
    Block block = target.block();
    if (block.getWorld() == null || !sameWorld(center.getWorld(), block.getWorld())) {
      return false;
    }
    float strength = cleanRadius(radius) * INITIAL_STRENGTH_MULTIPLIER;
    if (strength <= 0.0F) {
      return false;
    }
    double dx = block.getX() + 0.5D - center.getX();
    double dy = block.getY() + 0.5D - center.getY();
    double dz = block.getZ() + 0.5D - center.getZ();
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance > scanRadius(radius)) {
      return false;
    }
    int airSteps = Math.max(0, (int) Math.floor(distance / RAY_STEP));
    strength -= airSteps * AIR_ATTENUATION_PER_STEP;
    strength -= (target.resistance() + 0.3F) * 0.3F;
    return strength > 0.0F;
  }

  private double scanRadius(float radius) {
    return cleanRadius(radius) * 2.0D + SCAN_PADDING_BLOCKS;
  }

  private static float cleanRadius(float radius) {
    return Float.isFinite(radius) && radius > 0.0F ? radius : 0.0F;
  }

  private static int floorChunk(double blockCoordinate) {
    return ((int) Math.floor(blockCoordinate)) >> 4;
  }

  private static boolean sameWorld(World first, World second) {
    UUID firstId = first.getUID();
    UUID secondId = second.getUID();
    return firstId != null && firstId.equals(secondId);
  }

  record ExplosionPlan(Set<BlockKey> exortBlocks, List<ExortExplosionBlock> destroyed) {
    public ExplosionPlan {
      exortBlocks = Set.copyOf(exortBlocks);
      destroyed = List.copyOf(destroyed);
    }

    boolean suppressesVanilla(Block block) {
      BlockKey key = BlockKey.of(block);
      return key != null && exortBlocks.contains(key);
    }
  }

  record ExortExplosionBlock(Block block, BreakType type, float resistance) {}

  record BlockKey(UUID worldId, int x, int y, int z) implements Comparable<BlockKey> {
    static BlockKey of(Block block) {
      if (block == null || block.getWorld() == null || block.getWorld().getUID() == null) {
        return null;
      }
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    @Override
    public int compareTo(BlockKey other) {
      int worldCompare = worldId.compareTo(other.worldId);
      if (worldCompare != 0) return worldCompare;
      int xCompare = Integer.compare(x, other.x);
      if (xCompare != 0) return xCompare;
      int yCompare = Integer.compare(y, other.y);
      if (yCompare != 0) return yCompare;
      return Integer.compare(z, other.z);
    }
  }
}
