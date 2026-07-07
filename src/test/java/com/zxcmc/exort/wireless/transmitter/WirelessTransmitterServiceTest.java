package com.zxcmc.exort.wireless.transmitter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class WirelessTransmitterServiceTest {
  @Test
  void coverageUsesHorizontalEuclideanBlocksFromBlockCenter() {
    Plugin plugin = BukkitTestDoubles.plugin();
    var world = BukkitTestDoubles.world("wireless-transmitter", new UUID(0L, 401L));
    Block transmitter = world.block(10, 64, 10, Material.BARRIER);
    WirelessTransmitterService service = service(plugin, true, 48);

    assertTrue(
        service.coversPlayer(transmitter, new Location(world.world(), 58.5D, 320.0D, 10.5D)));
    assertFalse(
        service.coversPlayer(transmitter, new Location(world.world(), 58.51D, -64.0D, 10.5D)));
  }

  @Test
  void coverageRequiresEnabledServiceAndSameWorld() {
    Plugin plugin = BukkitTestDoubles.plugin();
    var first = BukkitTestDoubles.world("wireless-transmitter-a", new UUID(0L, 402L));
    var second = BukkitTestDoubles.world("wireless-transmitter-b", new UUID(0L, 403L));
    Block transmitter = first.block(0, 64, 0, Material.BARRIER);

    assertFalse(
        service(plugin, false, 48)
            .coversPlayer(transmitter, new Location(first.world(), 0.5D, 64.0D, 0.5D)));
    assertFalse(
        service(plugin, true, 48)
            .coversPlayer(transmitter, new Location(second.world(), 0.5D, 64.0D, 0.5D)));
  }

  @Test
  void disabledModeMakesTransmitterInactive() {
    Plugin plugin = BukkitTestDoubles.plugin();
    var world = BukkitTestDoubles.world("wireless-transmitter-mode-disabled", new UUID(0L, 404L));
    Block transmitter = world.block(0, 64, 0, Material.BARRIER);
    TransmitterMarker.set(plugin, transmitter);
    TransmitterStoredTerminal.setMode(plugin, transmitter, TransmitterMode.DISABLED);

    WirelessTransmitterService.Status status = service(plugin, true, 48).status(transmitter);

    assertFalse(status.active());
    assertTrue(status.state() == WirelessTransmitterService.State.MODE_DISABLED);
  }

  private static WirelessTransmitterService service(
      Plugin plugin, boolean enabled, int rangeBlocks) {
    return new WirelessTransmitterService(
        plugin,
        new StorageKeys(plugin),
        enabled,
        rangeBlocks,
        256,
        4096,
        3,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        () -> null);
  }
}
