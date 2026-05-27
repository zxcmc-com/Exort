package com.zxcmc.exort.marker;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Chunk-level storage of per-block data using nested PDC containers. */
public final class ChunkMarkerStore {
  private ChunkMarkerStore() {}

  private static final String BLOCK_PREFIX = "block_";

  private static NamespacedKey blockKey(Plugin plugin, Block block) {
    return new NamespacedKey(
        plugin, BLOCK_PREFIX + block.getX() + "_" + block.getY() + "_" + block.getZ());
  }

  private static NamespacedKey key(Plugin plugin, String key) {
    return new NamespacedKey(plugin, key);
  }

  private static String namespace(Plugin plugin) {
    return plugin.getName().toLowerCase(Locale.ROOT);
  }

  private static PersistentDataContainer getRoot(PersistentDataContainer pdc, NamespacedKey key) {
    return pdc.get(key, PersistentDataType.TAG_CONTAINER);
  }

  private static PersistentDataContainer getOrCreateRoot(
      PersistentDataContainer pdc, NamespacedKey key) {
    PersistentDataContainer root = getRoot(pdc, key);
    if (root != null) return root;
    return pdc.getAdapterContext().newPersistentDataContainer();
  }

  private static void saveRoot(
      PersistentDataContainer pdc, NamespacedKey key, PersistentDataContainer root) {
    if (root.isEmpty()) {
      pdc.remove(key);
      return;
    }
    pdc.set(key, PersistentDataType.TAG_CONTAINER, root);
  }

  public static Optional<PersistentDataContainer> getSection(
      Plugin plugin, Block block, String section) {
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    PersistentDataContainer root = getRoot(pdc, blockKey(plugin, block));
    if (root == null) return Optional.empty();
    return Optional.ofNullable(root.get(key(plugin, section), PersistentDataType.TAG_CONTAINER));
  }

  public static boolean hasSection(Plugin plugin, Block block, String section) {
    return getSection(plugin, block, section).isPresent();
  }

  public static void setString(
      Plugin plugin, Block block, String section, String field, String value) {
    if (value == null) return;
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    NamespacedKey rootKey = blockKey(plugin, block);
    PersistentDataContainer root = getOrCreateRoot(pdc, rootKey);
    PersistentDataContainer sectionContainer =
        root.get(key(plugin, section), PersistentDataType.TAG_CONTAINER);
    if (sectionContainer == null) {
      sectionContainer = pdc.getAdapterContext().newPersistentDataContainer();
    }
    sectionContainer.set(key(plugin, field), PersistentDataType.STRING, value);
    root.set(key(plugin, section), PersistentDataType.TAG_CONTAINER, sectionContainer);
    saveRoot(pdc, rootKey, root);
  }

  public static Optional<String> getString(
      Plugin plugin, Block block, String section, String field) {
    return getSection(plugin, block, section)
        .map(container -> container.get(key(plugin, field), PersistentDataType.STRING));
  }

  public static void setBytes(
      Plugin plugin, Block block, String section, String field, byte[] value) {
    if (value == null) return;
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    NamespacedKey rootKey = blockKey(plugin, block);
    PersistentDataContainer root = getOrCreateRoot(pdc, rootKey);
    PersistentDataContainer sectionContainer =
        root.get(key(plugin, section), PersistentDataType.TAG_CONTAINER);
    if (sectionContainer == null) {
      sectionContainer = pdc.getAdapterContext().newPersistentDataContainer();
    }
    sectionContainer.set(key(plugin, field), PersistentDataType.BYTE_ARRAY, value);
    root.set(key(plugin, section), PersistentDataType.TAG_CONTAINER, sectionContainer);
    saveRoot(pdc, rootKey, root);
  }

  public static Optional<byte[]> getBytes(
      Plugin plugin, Block block, String section, String field) {
    return getSection(plugin, block, section)
        .map(container -> container.get(key(plugin, field), PersistentDataType.BYTE_ARRAY));
  }

  public static void setByte(Plugin plugin, Block block, String section, String field, byte value) {
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    NamespacedKey rootKey = blockKey(plugin, block);
    PersistentDataContainer root = getOrCreateRoot(pdc, rootKey);
    PersistentDataContainer sectionContainer =
        root.get(key(plugin, section), PersistentDataType.TAG_CONTAINER);
    if (sectionContainer == null) {
      sectionContainer = pdc.getAdapterContext().newPersistentDataContainer();
    }
    sectionContainer.set(key(plugin, field), PersistentDataType.BYTE, value);
    root.set(key(plugin, section), PersistentDataType.TAG_CONTAINER, sectionContainer);
    saveRoot(pdc, rootKey, root);
  }

  public static Optional<Byte> getByte(Plugin plugin, Block block, String section, String field) {
    return getSection(plugin, block, section)
        .map(container -> container.get(key(plugin, field), PersistentDataType.BYTE));
  }

  public static void removeField(Plugin plugin, Block block, String section, String field) {
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    NamespacedKey rootKey = blockKey(plugin, block);
    PersistentDataContainer root = getRoot(pdc, rootKey);
    if (root == null) return;
    NamespacedKey sectionKey = key(plugin, section);
    PersistentDataContainer sectionContainer =
        root.get(sectionKey, PersistentDataType.TAG_CONTAINER);
    if (sectionContainer == null) return;
    sectionContainer.remove(key(plugin, field));
    if (sectionContainer.isEmpty()) {
      root.remove(sectionKey);
    } else {
      root.set(sectionKey, PersistentDataType.TAG_CONTAINER, sectionContainer);
    }
    saveRoot(pdc, rootKey, root);
  }

  public static void clearSection(Plugin plugin, Block block, String section) {
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    NamespacedKey rootKey = blockKey(plugin, block);
    PersistentDataContainer root = getRoot(pdc, rootKey);
    if (root == null) return;
    root.remove(key(plugin, section));
    saveRoot(pdc, rootKey, root);
  }

  public static void clearBlock(Plugin plugin, Block block) {
    PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
    pdc.remove(blockKey(plugin, block));
  }

  public static void forEachBlock(
      Plugin plugin, Chunk chunk, BiConsumer<Block, PersistentDataContainer> consumer) {
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    Set<NamespacedKey> keys = Set.copyOf(pdc.getKeys());
    if (keys.isEmpty()) return;
    String ns = namespace(plugin);
    for (NamespacedKey key : keys) {
      if (!key.getNamespace().equals(ns)) continue;
      String raw = key.getKey();
      if (!raw.startsWith(BLOCK_PREFIX)) continue;
      int[] xyz = MarkerCoords.parseXYZ(raw.substring(BLOCK_PREFIX.length()));
      if (xyz == null) continue;
      PersistentDataContainer root = pdc.get(key, PersistentDataType.TAG_CONTAINER);
      if (root == null || root.isEmpty()) {
        pdc.remove(key);
        continue;
      }
      Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
      consumer.accept(block, root);
    }
  }

  public static boolean hasAnyBlockData(Plugin plugin, Chunk chunk) {
    PersistentDataContainer pdc = chunk.getPersistentDataContainer();
    Set<NamespacedKey> keys = pdc.getKeys();
    if (keys.isEmpty()) return false;
    String ns = namespace(plugin);
    for (NamespacedKey key : keys) {
      if (!key.getNamespace().equals(ns)) continue;
      if (key.getKey().startsWith(BLOCK_PREFIX)) return true;
    }
    return false;
  }
}
