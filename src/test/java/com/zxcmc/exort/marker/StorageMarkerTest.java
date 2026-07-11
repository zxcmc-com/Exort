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
  void missingTierWithSnapshotResolvesWithoutRewritingMarker() {
    loadTiers(false);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-fallback", uuid(1))
            .block(0, 64, 0, Material.BARRIER);
    writeRawStorageMarker(plugin, block, "storage-a", "OBSIDIAN", 20L * 45L * 64L);

    StorageMarker.Data resolved = StorageMarker.get(plugin, block).orElseThrow();

    assertEquals("OBSIDIAN", resolved.tier().key());
    assertEquals(20L * 45L * 64L, resolved.tierMaxItems());
    assertTrue(resolved.orphaned());
    assertTrue(resolved.tier().isReadOnly());
    assertEquals(
        "OBSIDIAN", ChunkMarkerStore.getString(plugin, block, "storage", "tier").orElseThrow());
    assertEquals(
        20L * 45L * 64L,
        ChunkMarkerStore.getLong(plugin, block, "storage", "tierMaxItems").orElseThrow());

    loadTiers(true);
    StorageMarker.Data afterObsidianReturned = StorageMarker.get(plugin, block).orElseThrow();

    assertEquals("OBSIDIAN", afterObsidianReturned.tier().key());
    assertEquals(20L * 45L * 64L, afterObsidianReturned.tierMaxItems());
  }

  @Test
  void missingTierWithoutSnapshotFailsClosed() {
    loadTiers(false);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-missing-snapshot", uuid(2))
            .block(0, 64, 0, Material.BARRIER);
    writeRawStorageMarker(plugin, block, "storage-b", "OBSIDIAN", null);

    assertTrue(StorageMarker.get(plugin, block).isEmpty());
  }

  @Test
  void markerStoresNormalizesAndClearsDisplayName() {
    loadTiers(true);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-display-name", uuid(4))
            .block(0, 64, 0, Material.BARRIER);
    StorageTier tier = StorageTier.fromString("gold").orElseThrow();

    StorageMarker.set(plugin, block, "storage-d", tier, null, "  Main\u0000 Vault  ");

    StorageMarker.Data named = StorageMarker.get(plugin, block).orElseThrow();
    assertEquals("Main Vault", named.displayName());

    StorageMarker.set(plugin, block, "storage-d", tier, null, " ");

    StorageMarker.Data cleared = StorageMarker.get(plugin, block).orElseThrow();
    assertEquals(null, cleared.displayName());
  }

  @Test
  void readingLegacyDisplayNameDoesNotRewriteMarker() {
    loadTiers(true);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-pure-read", uuid(5))
            .block(0, 64, 0, Material.BARRIER);
    writeRawStorageMarker(plugin, block, "storage-e", "GOLD", 45L * 64L);
    ChunkMarkerStore.setString(plugin, block, "storage", "name", "  Main\u0000 Vault  ");

    StorageMarker.Data data = StorageMarker.get(plugin, block).orElseThrow();

    assertEquals("Main Vault", data.displayName());
    assertEquals(
        "  Main\u0000 Vault  ",
        ChunkMarkerStore.getString(plugin, block, "storage", "name").orElseThrow());
  }

  @Test
  void claimConflictMakesConfiguredTierReadOnlyUntilReconciled() {
    loadTiers(true);
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("storage-marker-claim-conflict", uuid(6))
            .block(0, 64, 0, Material.BARRIER);
    StorageTier tier = StorageTier.fromString("gold").orElseThrow();
    StorageMarker.set(plugin, block, "storage-f", tier);

    StorageMarker.setClaimConflict(plugin, block, true);

    StorageMarker.Data conflicted = StorageMarker.get(plugin, block).orElseThrow();
    assertEquals("GOLD", conflicted.tier().key());
    assertEquals(tier.maxItems(), conflicted.tierMaxItems());
    assertTrue(conflicted.tier().isReadOnly());

    StorageMarker.setClaimConflict(plugin, block, false);

    StorageMarker.Data reconciled = StorageMarker.get(plugin, block).orElseThrow();
    assertTrue(!reconciled.tier().isReadOnly());
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
