package com.zxcmc.exort.marker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class StorageMarkerTest {
  private static final Logger LOGGER = Logger.getLogger(StorageMarkerTest.class.getName());

  @Test
  void missingTierWithSnapshotMigratesToClosestLowerTierPermanently() {
    loadTiers(false);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-fallback", uuid(1))
            .block(0, 64, 0, Material.BARRIER);
    writeRawStorageMarker(plugin, block, "storage-a", "OBSIDIAN", 20L * 45L * 64L);

    StorageMarker.Data migrated = StorageMarker.get(plugin, block).orElseThrow();

    assertEquals("DIAMOND", migrated.tier().key());
    assertEquals(10L * 45L * 64L, migrated.tierMaxItems());

    loadTiers(true);
    StorageMarker.Data afterObsidianReturned = StorageMarker.get(plugin, block).orElseThrow();

    assertEquals("DIAMOND", afterObsidianReturned.tier().key());
    assertEquals(10L * 45L * 64L, afterObsidianReturned.tierMaxItems());
  }

  @Test
  void missingTierWithoutSnapshotFallsBackToSmallestTier() {
    loadTiers(false);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-missing-snapshot", uuid(2))
            .block(0, 64, 0, Material.BARRIER);
    writeRawStorageMarker(plugin, block, "storage-b", "OBSIDIAN", null);

    StorageMarker.Data migrated = StorageMarker.get(plugin, block).orElseThrow();

    assertEquals("GOLD", migrated.tier().key());
    assertTrue(migrated.fallback());
  }

  @Test
  void missingTierRemainsInactiveWhenNoTiersExist() {
    StorageTier.loadFromConfig(new YamlConfiguration(), LOGGER);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-no-tiers", uuid(3))
            .block(0, 64, 0, Material.BARRIER);
    writeRawStorageMarker(plugin, block, "storage-c", "OBSIDIAN", 20L * 45L * 64L);

    assertTrue(StorageMarker.get(plugin, block).isEmpty());
  }

  private static void loadTiers(boolean includeObsidian) {
    YamlConfiguration config = new YamlConfiguration();
    config.set("gold.maxItems", "1p");
    config.set("gold.material", "GOLD_BLOCK");
    config.set("diamond.maxItems", "10p");
    config.set("diamond.material", "DIAMOND_BLOCK");
    if (includeObsidian) {
      config.set("obsidian.maxItems", "20p");
      config.set("obsidian.material", "OBSIDIAN");
    }
    StorageTier.loadFromConfig(config, LOGGER);
  }

  private static void writeRawStorageMarker(
      Plugin plugin, Block block, String storageId, String tierKey, Long tierMaxItems) {
    ChunkMarkerStore.setString(plugin, block, "storage", "id", storageId);
    ChunkMarkerStore.setString(plugin, block, "storage", "tier", tierKey);
    if (tierMaxItems != null) {
      ChunkMarkerStore.setLong(plugin, block, "storage", "tierMaxItems", tierMaxItems);
    }
  }

  private static java.util.UUID uuid(int value) {
    return new java.util.UUID(0L, value);
  }
}
