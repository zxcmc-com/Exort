package com.zxcmc.exort.block;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ExortBlockClassifierTest {
  private final Plugin plugin = BukkitTestDoubles.plugin();

  @Test
  void markedChorusWireIsExortChorusCarrier() {
    ExortBlockClassifier classifier =
        new ExortBlockClassifier(plugin, materials(Material.CHORUS_PLANT));
    Block block = world("marked-chorus-wire", 1).block(0, 64, 0, Material.CHORUS_PLANT);
    WireMarker.setWire(plugin, block);

    assertTrue(classifier.isExortBlock(block));
    assertTrue(classifier.isExortChorusCarrier(block));
  }

  @Test
  void barrierWireIsExortBlockButNotChorusCarrier() {
    ExortBlockClassifier classifier = new ExortBlockClassifier(plugin, materials(Material.BARRIER));
    Block block = world("marked-barrier-wire", 2).block(0, 64, 0, Material.BARRIER);
    WireMarker.setWire(plugin, block);

    assertTrue(classifier.isExortBlock(block));
    assertFalse(classifier.isExortChorusCarrier(block));
  }

  @Test
  void unmarkedFullChorusIsNotExortBlock() {
    ExortBlockClassifier classifier =
        new ExortBlockClassifier(plugin, materials(Material.CHORUS_PLANT));
    Block block = world("unmarked-chorus", 3).block(0, 64, 0, Material.CHORUS_PLANT);

    assertFalse(classifier.isExortBlock(block));
    assertFalse(classifier.isExortChorusCarrier(block));
  }

  @Test
  void staleWireMarkerWithWrongCarrierIsNotExortBlock() {
    ExortBlockClassifier classifier = new ExortBlockClassifier(plugin, materials(Material.BARRIER));
    Block block = world("stale-chorus-wire", 4).block(0, 64, 0, Material.CHORUS_PLANT);
    WireMarker.setWire(plugin, block);

    assertFalse(classifier.isExortBlock(block));
    assertFalse(classifier.isExortChorusCarrier(block));
  }

  @Test
  void barrierMarkersCoverAllExortBlockFamilies() {
    ExortBlockClassifier classifier = new ExortBlockClassifier(plugin, materials(Material.BARRIER));
    BukkitTestDoubles.TestWorld world = world("barrier-families", 5);
    Block storage = world.block(0, 64, 0, Material.BARRIER);
    Block storageCore = world.block(1, 64, 0, Material.BARRIER);
    Block terminal = world.block(2, 64, 0, Material.BARRIER);
    Block monitor = world.block(3, 64, 0, Material.BARRIER);
    Block bus = world.block(4, 64, 0, Material.BARRIER);
    Block relay = world.block(5, 64, 0, Material.BARRIER);

    StorageMarker.setRaw(plugin, storage, "storage-a", "common", 1024L, BlockFace.NORTH);
    StorageCoreMarker.set(plugin, storageCore);
    TerminalMarker.set(plugin, terminal);
    MonitorMarker.set(plugin, monitor, BlockFace.NORTH);
    BusMarker.set(plugin, bus, BusType.IMPORT, BlockFace.NORTH, BusMode.DISABLED);
    RelayMarker.set(plugin, relay);

    assertAll(
        () -> assertTrue(classifier.isExortBlock(storage)),
        () -> assertTrue(classifier.isExortBlock(storageCore)),
        () -> assertTrue(classifier.isExortBlock(terminal)),
        () -> assertTrue(classifier.isExortBlock(monitor)),
        () -> assertTrue(classifier.isExortBlock(bus)),
        () -> assertTrue(classifier.isExortBlock(relay)),
        () -> assertFalse(classifier.isExortChorusCarrier(storage)),
        () -> assertFalse(classifier.isExortChorusCarrier(storageCore)),
        () -> assertFalse(classifier.isExortChorusCarrier(terminal)),
        () -> assertFalse(classifier.isExortChorusCarrier(monitor)),
        () -> assertFalse(classifier.isExortChorusCarrier(bus)),
        () -> assertFalse(classifier.isExortChorusCarrier(relay)));
  }

  @Test
  void nullBlockIsNeverExortBlock() {
    ExortBlockClassifier classifier =
        new ExortBlockClassifier(plugin, materials(Material.CHORUS_PLANT));

    assertFalse(classifier.isExortBlock(null));
    assertFalse(classifier.isExortChorusCarrier(null));
  }

  private static RuntimeMaterials materials(Material wire) {
    return new RuntimeMaterials(
        wire,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER);
  }

  private static BukkitTestDoubles.TestWorld world(String name, int id) {
    return BukkitTestDoubles.world(name, new UUID(0L, id));
  }
}
