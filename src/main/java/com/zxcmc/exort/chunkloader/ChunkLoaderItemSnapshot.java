package com.zxcmc.exort.chunkloader;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

final class ChunkLoaderItemSnapshot {
  private static final int MAX_NESTED_DEPTH = 4;

  private final Map<UUID, Integer> counts;

  private ChunkLoaderItemSnapshot(Map<UUID, Integer> counts) {
    this.counts = Collections.unmodifiableMap(new HashMap<>(counts));
  }

  static ChunkLoaderItemSnapshot empty() {
    return new ChunkLoaderItemSnapshot(Map.of());
  }

  static ChunkLoaderItemSnapshot ofCounts(Map<UUID, Integer> counts) {
    return new ChunkLoaderItemSnapshot(counts == null ? Map.of() : counts);
  }

  static ChunkLoaderItemSnapshot of(Iterable<ItemStack> stacks, Resolver resolver) {
    Objects.requireNonNull(resolver, "resolver");
    Map<UUID, Integer> counts = new HashMap<>();
    if (stacks != null) {
      for (ItemStack stack : stacks) {
        addStack(counts, stack, resolver, 0);
      }
    }
    return new ChunkLoaderItemSnapshot(counts);
  }

  static ChunkLoaderItemSnapshot of(ItemStack stack, Resolver resolver) {
    Objects.requireNonNull(resolver, "resolver");
    Map<UUID, Integer> counts = new HashMap<>();
    addStack(counts, stack, resolver, 0);
    return new ChunkLoaderItemSnapshot(counts);
  }

  boolean isEmpty() {
    return counts.isEmpty();
  }

  int count(UUID id) {
    return counts.getOrDefault(id, 0);
  }

  Set<UUID> ids() {
    return counts.keySet();
  }

  Map<UUID, Integer> counts() {
    return counts;
  }

  Set<UUID> unionIds(ChunkLoaderItemSnapshot other) {
    Set<UUID> result = new HashSet<>(ids());
    if (other != null) {
      result.addAll(other.ids());
    }
    return result;
  }

  private static void addStack(
      Map<UUID, Integer> counts, ItemStack stack, Resolver resolver, int depth) {
    if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
      return;
    }
    if (resolver.isChunkLoader(stack)) {
      counts.merge(resolver.chunkLoaderId(stack).orElse(null), stack.getAmount(), Integer::sum);
    }
    if (depth >= MAX_NESTED_DEPTH || !stack.hasItemMeta()) {
      return;
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
      for (ItemStack nested : bundle.getItems()) {
        addStack(counts, nested, resolver, depth + 1);
      }
    }
    if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.hasBlockState()) {
      BlockState state = blockStateMeta.getBlockState();
      if (state instanceof InventoryHolder holder) {
        for (ItemStack nested : holder.getInventory().getContents()) {
          addStack(counts, nested, resolver, depth + 1);
        }
      }
    }
  }

  interface Resolver {
    boolean isChunkLoader(ItemStack stack);

    Optional<UUID> chunkLoaderId(ItemStack stack);
  }
}
