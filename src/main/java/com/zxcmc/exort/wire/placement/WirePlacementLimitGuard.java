package com.zxcmc.exort.wire.placement;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.marker.WireMarker;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WirePlacementLimitGuard {
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
  private final Material wireMaterial;
  private final int wireHardCap;
  private final PlayerFeedback playerFeedback;

  public WirePlacementLimitGuard(
      Plugin plugin, Material wireMaterial, int wireHardCap, PlayerFeedback playerFeedback) {
    this.plugin = plugin;
    this.wireMaterial = wireMaterial;
    this.wireHardCap = Math.max(1, wireHardCap);
    this.playerFeedback = playerFeedback;
  }

  public boolean canPlace(Player player, Block target) {
    if (target == null || target.getWorld() == null) {
      return false;
    }
    int count =
        mergedWireCount(target, this::neighbors, this::isExistingWire, this::isLoaded, wireHardCap);
    if (count <= wireHardCap) {
      return true;
    }
    if (playerFeedback != null) {
      playerFeedback.error(player, "message.wire.hard_cap", count, wireHardCap);
    }
    return false;
  }

  static <T> int mergedWireCount(
      T target,
      Function<T, Collection<T>> neighbors,
      Predicate<T> existingWire,
      Predicate<T> loaded,
      int hardCap) {
    int safeHardCap = Math.max(1, hardCap);
    Set<T> visited = new HashSet<>();
    Queue<T> queue = new ArrayDeque<>();
    visited.add(target);
    for (T neighbor : neighbors.apply(target)) {
      if (neighbor == null || visited.contains(neighbor) || !loaded.test(neighbor)) {
        continue;
      }
      if (existingWire.test(neighbor)) {
        visited.add(neighbor);
        queue.add(neighbor);
      }
    }
    while (!queue.isEmpty() && visited.size() <= safeHardCap) {
      T current = queue.poll();
      for (T next : neighbors.apply(current)) {
        if (next == null || visited.contains(next) || !loaded.test(next)) {
          continue;
        }
        if (!existingWire.test(next)) {
          continue;
        }
        visited.add(next);
        queue.add(next);
        if (visited.size() > safeHardCap) {
          return visited.size();
        }
      }
    }
    return visited.size();
  }

  private Collection<Block> neighbors(Block block) {
    java.util.ArrayList<Block> out = new java.util.ArrayList<>(FACES.length);
    for (BlockFace face : FACES) {
      out.add(block.getRelative(face));
    }
    return out;
  }

  private boolean isExistingWire(Block block) {
    return Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
  }

  private boolean isLoaded(Block block) {
    return block != null
        && block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }
}
