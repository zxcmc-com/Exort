package com.zxcmc.exort.core.marker;

import com.zxcmc.exort.storage.StorageTier;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/** Chunk-level marker for storage blocks (all modes). Format: id:tier[:facing:FACING] */
public final class StorageMarker {
  private StorageMarker() {}

  private static final String SECTION = "storage";
  private static final String FIELD_ID = "id";
  private static final String FIELD_TIER = "tier";
  private static final String FIELD_FACING = "facing";

  public record Data(String storageId, StorageTier tier, BlockFace facing) {}

  public static void set(Plugin plugin, Block block, String storageId, StorageTier tier) {
    if (storageId == null || tier == null) return;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_ID, storageId);
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_TIER, tier.key());
    ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_FACING);
  }

  public static void set(
      Plugin plugin, Block block, String storageId, StorageTier tier, BlockFace facing) {
    if (storageId == null || tier == null) return;
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_ID, storageId);
    ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_TIER, tier.key());
    if (facing != null) {
      ChunkMarkerStore.setString(plugin, block, SECTION, FIELD_FACING, facing.name());
    } else {
      ChunkMarkerStore.removeField(plugin, block, SECTION, FIELD_FACING);
    }
  }

  public static Optional<Data> get(Plugin plugin, Block block) {
    if (!ChunkMarkerStore.hasSection(plugin, block, SECTION)) return Optional.empty();
    String storageId = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_ID).orElse(null);
    String tierKey = ChunkMarkerStore.getString(plugin, block, SECTION, FIELD_TIER).orElse(null);
    if (storageId == null || tierKey == null) return Optional.empty();
    var tierOpt = StorageTier.fromString(tierKey);
    if (tierOpt.isEmpty()) return Optional.empty();
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
    return Optional.of(new Data(storageId, tierOpt.get(), facing));
  }

  public static void clear(Plugin plugin, Block block) {
    ChunkMarkerStore.clearSection(plugin, block, SECTION);
  }
}
