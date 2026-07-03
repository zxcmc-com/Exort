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

  private final Map<Key, Integer> counts;

  private ChunkLoaderItemSnapshot(Map<Key, Integer> counts) {
    this.counts = Collections.unmodifiableMap(new HashMap<>(counts));
  }

  static ChunkLoaderItemSnapshot empty() {
    return new ChunkLoaderItemSnapshot(Map.of());
  }

  static ChunkLoaderItemSnapshot ofCounts(Map<UUID, Integer> counts) {
    Map<Key, Integer> keyed = new HashMap<>();
    if (counts != null) {
      counts.forEach((id, amount) -> keyed.put(new Key(id, ChunkLoaderType.defaultType()), amount));
    }
    return new ChunkLoaderItemSnapshot(keyed);
  }

  static ChunkLoaderItemSnapshot ofTypedCounts(Map<Key, Integer> counts) {
    return new ChunkLoaderItemSnapshot(counts == null ? Map.of() : counts);
  }

  static ChunkLoaderItemSnapshot of(Iterable<ItemStack> stacks, Resolver resolver) {
    Objects.requireNonNull(resolver, "resolver");
    Map<Key, Integer> counts = new HashMap<>();
    if (stacks != null) {
      for (ItemStack stack : stacks) {
        addStack(counts, stack, resolver, 0);
      }
    }
    return new ChunkLoaderItemSnapshot(counts);
  }

  static ChunkLoaderItemSnapshot of(ItemStack stack, Resolver resolver) {
    Objects.requireNonNull(resolver, "resolver");
    Map<Key, Integer> counts = new HashMap<>();
    addStack(counts, stack, resolver, 0);
    return new ChunkLoaderItemSnapshot(counts);
  }

  boolean isEmpty() {
    return counts.isEmpty();
  }

  int count(UUID id) {
    int total = 0;
    for (Map.Entry<Key, Integer> entry : counts.entrySet()) {
      if (Objects.equals(entry.getKey().id(), id)) {
        total += entry.getValue();
      }
    }
    return total;
  }

  int count(Key key) {
    return counts.getOrDefault(key, 0);
  }

  Set<UUID> ids() {
    Set<UUID> ids = new HashSet<>();
    for (Key key : counts.keySet()) {
      ids.add(key.id());
    }
    return ids;
  }

  Set<Key> keys() {
    return counts.keySet();
  }

  Map<Key, Integer> counts() {
    return counts;
  }

  Set<Key> unionKeys(ChunkLoaderItemSnapshot other) {
    Set<Key> result = new HashSet<>(keys());
    if (other != null) {
      result.addAll(other.keys());
    }
    return result;
  }

  private static void addStack(
      Map<Key, Integer> counts, ItemStack stack, Resolver resolver, int depth) {
    if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
      return;
    }
    if (resolver.isChunkLoader(stack)) {
      counts.merge(
          new Key(resolver.chunkLoaderId(stack).orElse(null), resolver.chunkLoaderType(stack)),
          stack.getAmount(),
          Integer::sum);
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

    default ChunkLoaderType chunkLoaderType(ItemStack stack) {
      return ChunkLoaderType.defaultType();
    }
  }

  record Key(UUID id, ChunkLoaderType type) {
    Key {
      type = type == null ? ChunkLoaderType.defaultType() : type;
    }
  }
}
