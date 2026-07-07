package com.zxcmc.exort.display.wire;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class WireConnectionModelResolverTest {
  @Test
  void wireConnectsVisuallyToTransmitterEndpoint() {
    Plugin plugin = BukkitTestDoubles.plugin();
    var world = BukkitTestDoubles.world("wire-transmitter-connection", new UUID(0L, 701L));
    Block wire = world.block(0, 64, 0, Material.CHORUS_PLANT);
    Block transmitter = world.block(1, 64, 0, Material.BARRIER);
    WireMarker.setWire(plugin, wire);
    TransmitterMarker.set(plugin, transmitter);

    int mask =
        WireConnectionModelResolver.connectionsMask(
            plugin,
            wire,
            Material.CHORUS_PLANT,
            Material.BARRIER,
            Material.BARRIER,
            Material.BARRIER,
            Material.BARRIER,
            Material.BARRIER,
            Material.BARRIER);

    assertTrue((mask & WireModelKeys.bit(BlockFace.EAST)) != 0);
  }
}
