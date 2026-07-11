package com.zxcmc.exort.marker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ChunkLoaderMarkerTest {
  @Test
  void limitBypassRoundTripsWithPlacedMarkerState() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("chunk-loader-bypass", new UUID(0L, 1L))
            .block(0, 64, 0, Material.BARRIER);

    ChunkLoaderMarker.set(
        plugin,
        block,
        new UUID(0L, 2L),
        ChunkLoaderType.CHUNK_LOADER,
        new UUID(0L, 3L),
        "Admin",
        100L,
        true,
        true);

    assertTrue(ChunkLoaderMarker.get(plugin, block).orElseThrow().bypassLimits());
  }

  @Test
  void markerWithoutBypassFieldDefaultsFailClosed() {
    Plugin plugin = BukkitTestDoubles.plugin();
    Block block =
        BukkitTestDoubles.world("chunk-loader-no-bypass", new UUID(0L, 4L))
            .block(0, 64, 0, Material.BARRIER);
    ChunkMarkerStore.setString(plugin, block, "chunk_loader", "id", new UUID(0L, 5L).toString());

    assertFalse(ChunkLoaderMarker.get(plugin, block).orElseThrow().bypassLimits());
  }
}
