package com.zxcmc.exort.marker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.storage.StoredItemCodec;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class MonitorMarkerTest {
  @Test
  void rejectsOversizedOrMismatchedItemPayloadWithoutPersistingIt() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("monitor-marker-payload", new java.util.UUID(0L, 7L))
            .block(0, 64, 0, Material.BARRIER);
    MonitorMarker.set(plugin, block, org.bukkit.block.BlockFace.NORTH);
    byte[] oversized = new byte[StoredItemCodec.MAX_BLOB_BYTES + 1];

    assertFalse(MonitorMarker.setItem(plugin, block, ItemKeyUtil.sha256Hex(oversized), oversized));
    assertFalse(MonitorMarker.setItem(plugin, block, "wrong-key", new byte[] {1, 2, 3}));
    assertTrue(MonitorMarker.itemKey(plugin, block).isEmpty());
    assertTrue(MonitorMarker.itemBlob(plugin, block).isEmpty());
  }

  @Test
  void acceptsAConsistentBoundedPayload() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("monitor-marker-valid", new java.util.UUID(0L, 8L))
            .block(0, 64, 0, Material.BARRIER);
    MonitorMarker.set(plugin, block, org.bukkit.block.BlockFace.NORTH);
    byte[] blob = new byte[] {1, 2, 3};

    assertTrue(MonitorMarker.setItem(plugin, block, ItemKeyUtil.sha256Hex(blob), blob));
    assertTrue(MonitorMarker.itemKey(plugin, block).isPresent());
    assertTrue(MonitorMarker.itemBlob(plugin, block).isPresent());
  }
}
