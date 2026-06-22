package com.zxcmc.exort.marker;

import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierResolver;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for storage blocks (all modes). */
public final class StorageMarker {
  private StorageMarker() {}

  private static final String SECTION = "storage";
  private static final String FIELD_ID = "id";
  private static final String FIELD_TIER = "tier";
  private static final String FIELD_TIER_MAX_ITEMS = "tierMaxItems";
  private static final String FIELD_FACING = "facing";
  private static final String FIELD_NAME = "name";
  private static final Set<String> WARNED_FALLBACKS = ConcurrentHashMap.newKeySet();
  private static final Set<String> WARNED_UNUSABLE = ConcurrentHashMap.newKeySet();

  public record Data(
      String storageId,
      StorageTier tier,
      BlockFace facing,
      long tierMaxItems,
      boolean fallback,
      String displayName) {
    public Data(
        String storageId, StorageTier tier, BlockFace facing, long tierMaxItems, boolean fallback) {
      this(storageId, tier, facing, tierMaxItems, fallback, null);
    }

    public Data {
      displayName = StorageDisplayName.normalize(displayName);
    }
  }

  public static void set(Plugin plugin, Block block, String storageId, StorageTier tier) {
    set(plugin, block, storageId, tier, null, null);
  }

  public static void set(
      Plugin plugin, Block block, String storageId, StorageTier tier, BlockFace facing) {
    set(plugin, block, storageId, tier, facing, null);
  }

  public static void set(
      Plugin plugin,
      Block block,
      String storageId,
      StorageTier tier,
      BlockFace facing,
      String displayName) {
    if (storageId == null || tier == null) return;
    setRaw(plugin, block, storageId, tier.key(), tier.maxItems(), facing, displayName);
  }

  public static void setRaw(
      Plugin plugin,
      Block block,
      String storageId,
      String tierKey,
      Long tierMaxItems,
      BlockFace facing) {
    setRaw(plugin, block, storageId, tierKey, tierMaxItems, facing, null);
  }

  public static void setRaw(
      Plugin plugin,
      Block block,
      String storageId,
      String tierKey,
      Long tierMaxItems,
      BlockFace facing,
      String displayName) {
    if (storageId == null || tierKey == null) return;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_ID, storageId);
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_TIER, tierKey);
    if (tierMaxItems != null && tierMaxItems > 0) {
      ChunkMarkerStore.setLong(plugin, block, SECTION, FIELD_TIER_MAX_ITEMS, tierMaxItems);
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_TIER_MAX_ITEMS);
    }
    if (facing != null) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_FACING, facing.name());
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_FACING);
    }
    String normalizedName = StorageDisplayName.normalize(displayName);
    if (normalizedName != null) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_NAME, normalizedName);
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_NAME);
    }
  }

  public static Optional<Data> get(Plugin plugin, Block block) {
    if (!ChunkMarkerStore.hasSection(plugin, block, SECTION)) return Optional.empty();
    String storageId = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_ID).orElse(null);
    String tierKey = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_TIER).orElse(null);
    if (storageId == null || tierKey == null) return Optional.empty();
    Long storedMaxItems =
        ChunkMarkerStore.getLong(plugin, block, SECTION, FIELD_TIER_MAX_ITEMS).orElse(null);
    var resolution = StorageTierResolver.resolve(tierKey, storedMaxItems);
    if (resolution.isEmpty()) {
      warnUnusable(plugin, storageId, tierKey);
      return Optional.empty();
    }
    StorageTierResolver.Resolution resolved = resolution.get();
    BlockFace facing = null;
    String facingRaw =
        ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_FACING).orElse(null);
    if (facingRaw != null) {
      try {
        facing = BlockFace.valueOf(facingRaw);
      } catch (IllegalArgumentException ignored) {
        facing = null;
      }
    }
    boolean rewriteTier = !resolved.tier().key().equals(tierKey);
    boolean rewriteMaxItems =
        storedMaxItems == null || storedMaxItems.longValue() != resolved.tierMaxItems();
    if (rewriteTier || rewriteMaxItems) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_TIER, resolved.tier().key());
      ChunkMarkerStore.setLong(
          plugin, block, SECTION, FIELD_TIER_MAX_ITEMS, resolved.tierMaxItems());
    }
    if (resolved.fallback()) {
      warnFallback(plugin, storageId, tierKey, storedMaxItems, resolved);
    }
    String nameRaw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_NAME).orElse(null);
    String displayName = StorageDisplayName.normalize(nameRaw);
    if (displayName == null && nameRaw != null) {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_NAME);
    } else if (displayName != null && !displayName.equals(nameRaw)) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_NAME, displayName);
    }
    return Optional.of(
        new Data(
            storageId,
            resolved.tier(),
            facing,
            resolved.tierMaxItems(),
            resolved.fallback(),
            displayName));
  }

  public static boolean isMarkedStorage(Plugin plugin, Block block) {
    if (!ChunkMarkerStore.hasSection(plugin, block, SECTION)) return false;
    return ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_ID).isPresent()
        && ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_TIER).isPresent();
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }

  private static void warnFallback(
      Plugin plugin,
      String storageId,
      String missingTier,
      Long storedMaxItems,
      StorageTierResolver.Resolution resolved) {
    if (plugin == null || !WARNED_FALLBACKS.add(storageId + ":" + missingTier)) return;
    if (resolved.missingSnapshot()) {
      plugin
          .getLogger()
          .warning(
              "Storage "
                  + storageId
                  + " references missing tier '"
                  + missingTier
                  + "' without tierMaxItems snapshot; migrated to smallest configured tier "
                  + resolved.tier().key()
                  + ".");
      return;
    }
    plugin
        .getLogger()
        .warning(
            "Storage "
                + storageId
                + " references missing tier '"
                + missingTier
                + "' with tierMaxItems="
                + storedMaxItems
                + "; migrated to "
                + resolved.tier().key()
                + ".");
  }

  private static void warnUnusable(Plugin plugin, String storageId, String missingTier) {
    if (plugin == null || !WARNED_UNUSABLE.add(storageId + ":" + missingTier)) return;
    plugin
        .getLogger()
        .warning(
            "Storage "
                + storageId
                + " references tier '"
                + missingTier
                + "', but no storage tiers are configured; leaving marker inactive.");
  }
}
