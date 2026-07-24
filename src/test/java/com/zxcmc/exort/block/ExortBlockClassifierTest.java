package com.zxcmc.exort.block;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.api.model.ExortContentType;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.CarrierMaterials;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
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
    ExortBlockClassifier classifier =
        new ExortBlockClassifier(plugin, materials(Material.BARRIER), this::tierCatalog);
    BukkitTestDoubles.TestWorld world = world("barrier-families", 5);
    Block storage = world.block(0, 64, 0, Material.BARRIER);
    Block storageCore = world.block(1, 64, 0, Material.BARRIER);
    Block terminal = world.block(2, 64, 0, Material.BARRIER);
    Block craftingTerminal = world.block(3, 64, 0, Material.BARRIER);
    Block monitor = world.block(4, 64, 0, Material.BARRIER);
    Block importBus = world.block(5, 64, 0, Material.BARRIER);
    Block exportBus = world.block(6, 64, 0, Material.BARRIER);
    Block relay = world.block(7, 64, 0, Material.BARRIER);
    Block transmitter = world.block(8, 64, 0, Material.BARRIER);
    Block chunkLoader = world.block(9, 64, 0, Material.BARRIER);
    Block personalLoader = world.block(10, 64, 0, Material.BARRIER);
    Block dormantLoader = world.block(11, 64, 0, Material.BARRIER);

    StorageMarker.setRaw(
        plugin, storage, "00000000-0000-0000-0000-000000000005", "common", 1024L, BlockFace.NORTH);
    StorageCoreMarker.set(plugin, storageCore);
    TerminalMarker.set(plugin, terminal);
    TerminalMarker.set(plugin, craftingTerminal, TerminalKind.CRAFTING, BlockFace.NORTH);
    MonitorMarker.set(plugin, monitor, BlockFace.NORTH);
    BusMarker.set(plugin, importBus, BusType.IMPORT, BlockFace.NORTH, BusMode.DISABLED);
    BusMarker.set(plugin, exportBus, BusType.EXPORT, BlockFace.NORTH, BusMode.DISABLED);
    RelayMarker.set(plugin, relay);
    TransmitterMarker.set(plugin, transmitter);
    ChunkLoaderMarker.set(
        plugin, chunkLoader, new UUID(0L, 6L), ChunkLoaderType.CHUNK_LOADER, null, "Alex", 100L);
    ChunkLoaderMarker.set(
        plugin,
        personalLoader,
        new UUID(0L, 7L),
        ChunkLoaderType.PERSONAL_CHUNK_LOADER,
        null,
        "Alex",
        100L);
    ChunkLoaderMarker.set(
        plugin,
        dormantLoader,
        new UUID(0L, 8L),
        ChunkLoaderType.DORMANT_CHUNK_LOADER,
        null,
        "Alex",
        100L);
    int worldReadsBeforeInspection = world.getBlockAtCalls();

    assertAll(
        () ->
            assertEquals(
                ExortContentType.STORAGE, classifier.inspect(storage).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.STORAGE_CORE,
                classifier.inspect(storageCore).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.TERMINAL, classifier.inspect(terminal).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.CRAFTING_TERMINAL,
                classifier.inspect(craftingTerminal).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.MONITOR, classifier.inspect(monitor).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.IMPORT_BUS, classifier.inspect(importBus).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.EXPORT_BUS, classifier.inspect(exportBus).orElseThrow().type()),
        () -> assertEquals(ExortContentType.RELAY, classifier.inspect(relay).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.TRANSMITTER, classifier.inspect(transmitter).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.CHUNK_LOADER,
                classifier.inspect(chunkLoader).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.PERSONAL_CHUNK_LOADER,
                classifier.inspect(personalLoader).orElseThrow().type()),
        () ->
            assertEquals(
                ExortContentType.DORMANT_CHUNK_LOADER,
                classifier.inspect(dormantLoader).orElseThrow().type()),
        () -> assertFalse(classifier.isExortChorusCarrier(storage)),
        () -> assertFalse(classifier.isExortChorusCarrier(storageCore)),
        () -> assertFalse(classifier.isExortChorusCarrier(terminal)),
        () -> assertFalse(classifier.isExortChorusCarrier(monitor)),
        () -> assertFalse(classifier.isExortChorusCarrier(importBus)),
        () -> assertFalse(classifier.isExortChorusCarrier(relay)),
        () -> assertFalse(classifier.isExortChorusCarrier(chunkLoader)),
        () -> assertEquals(worldReadsBeforeInspection, world.getBlockAtCalls()));
  }

  @Test
  void nullBlockIsNeverExortBlock() {
    ExortBlockClassifier classifier =
        new ExortBlockClassifier(plugin, materials(Material.CHORUS_PLANT));

    assertFalse(classifier.isExortBlock(null));
    assertFalse(classifier.isExortChorusCarrier(null));
  }

  @Test
  void typedInspectionRejectsMalformedMarkerState() {
    ExortBlockClassifier classifier =
        new ExortBlockClassifier(plugin, materials(Material.BARRIER), this::tierCatalog);
    BukkitTestDoubles.TestWorld world = world("malformed-descriptors", 9);
    Block terminal = world.block(0, 64, 0, Material.BARRIER);
    Block bus = world.block(1, 64, 0, Material.BARRIER);
    Block storage = world.block(2, 64, 0, Material.BARRIER);
    Block missingTierStorage = world.block(3, 64, 0, Material.BARRIER);
    Block loader = world.block(4, 64, 0, Material.BARRIER);

    TerminalMarker.set(plugin, terminal);
    ChunkMarkerStore.setString(plugin, terminal, "terminal", "type", "INVALID");
    BusMarker.set(plugin, bus, BusType.IMPORT, BlockFace.NORTH, BusMode.DISABLED);
    ChunkMarkerStore.setString(plugin, bus, "bus", "type", "INVALID");
    StorageMarker.setRaw(plugin, storage, "invalid-uuid", "common", 1024L, BlockFace.NORTH);
    StorageMarker.setRaw(
        plugin,
        missingTierStorage,
        "00000000-0000-0000-0000-000000000009",
        "missing",
        1024L,
        BlockFace.NORTH);
    ChunkMarkerStore.setString(plugin, loader, "chunk_loader", "id", "invalid-uuid");
    ChunkMarkerStore.setString(plugin, loader, "chunk_loader", "type", "chunk_loader");
    AtomicBoolean orphanWarning = new AtomicBoolean();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            if (record.getMessage() != null
                && record.getMessage().contains("00000000-0000-0000-0000-000000000009")) {
              orphanWarning.set(true);
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    plugin.getLogger().addHandler(handler);

    try {
      assertAll(
          () -> assertTrue(classifier.isExortBlock(terminal)),
          () -> assertTrue(classifier.inspect(terminal).isEmpty()),
          () -> assertTrue(classifier.inspect(bus).isEmpty()),
          () -> assertTrue(classifier.inspect(storage).isEmpty()),
          () -> assertTrue(classifier.inspect(missingTierStorage).isEmpty()),
          () -> assertTrue(classifier.inspect(loader).isEmpty()),
          () -> assertFalse(orphanWarning.get()));
    } finally {
      plugin.getLogger().removeHandler(handler);
    }
  }

  private static CarrierMaterials materials(Material wire) {
    return new CarrierMaterials(
        wire,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER);
  }

  private static BukkitTestDoubles.TestWorld world(String name, int id) {
    return BukkitTestDoubles.world(name, new UUID(0L, id));
  }

  private StorageTierCatalog tierCatalog() {
    YamlConfiguration tiers = new YamlConfiguration();
    tiers.set("common.maxItems", 1024L);
    tiers.set("common.material", "CHEST");
    return StorageTierCatalog.parse(tiers, Logger.getLogger(getClass().getName()));
  }
}
