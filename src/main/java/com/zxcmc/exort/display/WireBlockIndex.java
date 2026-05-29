package com.zxcmc.exort.display;

import com.zxcmc.exort.debug.PerfStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class WireBlockIndex {
  private final Map<BlockKey, ChunkKey> byBlock = new HashMap<>();
  private final Map<ChunkKey, Set<BlockKey>> byChunk = new HashMap<>();

  public void register(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    BlockKey blockKey = BlockKey.of(block);
    ChunkKey chunkKey = ChunkKey.of(block);
    ChunkKey previous = byBlock.put(blockKey, chunkKey);
    if (previous != null && !previous.equals(chunkKey)) {
      removeFromChunk(previous, blockKey);
    }
    byChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(blockKey);
    updateGauge();
  }

  public void unregister(Block block) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    unregister(BlockKey.of(block));
  }

  public void unregister(BlockKey blockKey) {
    ChunkKey previous = byBlock.remove(blockKey);
    if (previous != null) {
      removeFromChunk(previous, blockKey);
      updateGauge();
    }
  }

  public int countAround(Block block, int radius) {
    if (block == null || block.getWorld() == null) {
      return 0;
    }
    UUID worldId = block.getWorld().getUID();
    int centerX = block.getX() >> 4;
    int centerZ = block.getZ() >> 4;
    int safeRadius = Math.max(0, radius);
    int count = 0;
    for (int x = centerX - safeRadius; x <= centerX + safeRadius; x++) {
      for (int z = centerZ - safeRadius; z <= centerZ + safeRadius; z++) {
        Set<BlockKey> blocks = byChunk.get(new ChunkKey(worldId, x, z));
        if (blocks != null) {
          count += blocks.size();
        }
      }
    }
    return count;
  }

  public List<BlockKey> snapshotBlocks() {
    return new ArrayList<>(byBlock.keySet());
  }

  public Block loadedBlock(BlockKey key) {
    if (key == null) {
      return null;
    }
    World world = Bukkit.getWorld(key.worldId());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      return null;
    }
    return world.getBlockAt(key.x(), key.y(), key.z());
  }

  private void removeFromChunk(ChunkKey chunkKey, BlockKey blockKey) {
    Set<BlockKey> blocks = byChunk.get(chunkKey);
    if (blocks == null) {
      return;
    }
    blocks.remove(blockKey);
    if (blocks.isEmpty()) {
      byChunk.remove(chunkKey);
    }
  }

  private void updateGauge() {
    PerfStats.setGauge("wire.index.blocks", byBlock.size());
  }

  public record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  private record ChunkKey(UUID worldId, int x, int z) {
    static ChunkKey of(Block block) {
      return new ChunkKey(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
    }
  }
}
