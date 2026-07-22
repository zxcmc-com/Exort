package com.zxcmc.exort.marker;

import com.zxcmc.exort.storage.StorageNameNormalizer;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.storage.StorageTierCatalogSource;
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
  private static final String FIELD_CLAIM_CONFLICT = "claimConflict";
  private static final Set<String> WARNED_ORPHANS = ConcurrentHashMap.newKeySet();
  private static final Set<String> WARNED_UNUSABLE = ConcurrentHashMap.newKeySet();

  public record Data(
      String storageId,
      StorageTier tier,
      BlockFace facing,
      long tierMaxItems,
      boolean orphaned,
      String displayName) {
    public Data(
        String storageId, StorageTier tier, BlockFace facing, long tierMaxItems, boolean orphaned) {
      this(storageId, tier, facing, tierMaxItems, orphaned, null);
    }

    public Data {
      displayName = StorageNameNormalizer.normalize(displayName);
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
    String normalizedName = StorageNameNormalizer.normalize(displayName);
    if (normalizedName != null) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_NAME, normalizedName);
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_NAME);
    }
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_CLAIM_CONFLICT);
  }

  /** Keeps a non-authoritative physical duplicate visible but prevents item mutations. */
  public static void setClaimConflict(Plugin plugin, Block block, boolean conflict) {
    if (conflict) {
      ChunkMarkerStore.setLong(plugin, block, SECTION, FIELD_CLAIM_CONFLICT, 1L);
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_CLAIM_CONFLICT);
    }
  }

  public static Optional<Data> get(Plugin plugin, Block block) {
    StorageTierCatalog catalog =
        plugin instanceof StorageTierCatalogSource source
            ? source.storageTierCatalog()
            : StorageTierCatalog.empty();
    return get(plugin, block, catalog);
  }

  public static Optional<Data> get(
      Plugin plugin, Block block, StorageTierCatalog storageTierCatalog) {
    if (!ChunkMarkerStore.hasSection(plugin, block, SECTION)) return Optional.empty();
    String storageId = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_ID).orElse(null);
    String tierKey = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_TIER).orElse(null);
    if (storageId == null || tierKey == null) return Optional.empty();
    Long storedMaxItems =
        ChunkMarkerStore.getLong(plugin, block, SECTION, FIELD_TIER_MAX_ITEMS).orElse(null);
    var resolution = StorageTierResolver.resolve(storageTierCatalog, tierKey, storedMaxItems);
    if (resolution.isEmpty()) {
      warnUnusable(plugin, storageId, tierKey);
      return Optional.empty();
    }
    StorageTierResolver.Resolution resolved = resolution.get();
    boolean claimConflict =
        ChunkMarkerStore.getLong(plugin, block, SECTION, FIELD_CLAIM_CONFLICT).orElse(0L) != 0L;
    StorageTier resolvedTier =
        claimConflict ? StorageTier.readOnlySnapshot(resolved.tier()) : resolved.tier();
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
    if (resolved.orphaned()) {
      warnOrphaned(plugin, storageId, tierKey, storedMaxItems);
    }
    String nameRaw = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_NAME).orElse(null);
    String displayName = StorageNameNormalizer.normalize(nameRaw);
    return Optional.of(
        new Data(
            storageId,
            resolvedTier,
            facing,
            resolved.tierMaxItems(),
            resolved.orphaned(),
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

  private static void warnOrphaned(
      Plugin plugin, String storageId, String missingTier, Long storedMaxItems) {
    if (plugin == null || !WARNED_ORPHANS.add(storageId + ":" + missingTier)) return;
    plugin
        .getLogger()
        .warning(
            "Storage "
                + storageId
                + " references missing tier '"
                + missingTier
                + "' with tierMaxItems="
                + storedMaxItems
                + "; preserving the raw tier snapshot in read-only orphaned state. Restore the"
                + " tier or explicitly repair the storage metadata before allowing mutations.");
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
