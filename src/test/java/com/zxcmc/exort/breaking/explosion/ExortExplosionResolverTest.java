package com.zxcmc.exort.breaking.explosion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ExortExplosionResolverTest {
  private static final float TNT_RADIUS = 4.0F;
  private static final float STRONG_RADIUS = 400.0F;
  private final Plugin plugin = BukkitTestDoubles.plugin();

  @Test
  void blastResistanceDefaultsStayCentralized() {
    ExortBlastResistance resistance = ExortBlastResistance.defaults();

    assertAll(
        () -> assertEquals(6.0F, ExortBlastResistance.WIRE),
        () -> assertEquals(9.0F, ExortBlastResistance.TERMINAL),
        () -> assertEquals(9.0F, ExortBlastResistance.MONITOR),
        () -> assertEquals(10.0F, ExortBlastResistance.BUS),
        () -> assertEquals(50.0F, ExortBlastResistance.RELAY),
        () -> assertEquals(1200.0F, ExortBlastResistance.STORAGE),
        () -> assertEquals(6.0F, resistance.forBreakType(BreakType.WIRE)),
        () -> assertEquals(9.0F, resistance.forBreakType(BreakType.TERMINAL)),
        () -> assertEquals(9.0F, resistance.forBreakType(BreakType.MONITOR)),
        () -> assertEquals(10.0F, resistance.forBreakType(BreakType.BUS)),
        () -> assertEquals(50.0F, resistance.forBreakType(BreakType.RELAY)),
        () -> assertEquals(1200.0F, resistance.forBreakType(BreakType.STORAGE)));
  }

  @Test
  void blastResistanceReadsBreakSectionOverrides() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("break.storage.blastResistance", 2400.0D);
    config.set("break.terminal.blastResistance", 18.0D);
    config.set("break.monitor.blastResistance", 19.0D);
    config.set("break.bus.blastResistance", 20.0D);
    config.set("break.relay.blastResistance", 100.0D);
    config.set("break.wire.blastResistance", 3.0D);

    ExortBlastResistance resistance = ExortBlastResistance.fromConfig(config);

    assertAll(
        () -> assertEquals(2400.0F, resistance.forBreakType(BreakType.STORAGE)),
        () -> assertEquals(18.0F, resistance.forBreakType(BreakType.TERMINAL)),
        () -> assertEquals(19.0F, resistance.forBreakType(BreakType.MONITOR)),
        () -> assertEquals(20.0F, resistance.forBreakType(BreakType.BUS)),
        () -> assertEquals(100.0F, resistance.forBreakType(BreakType.RELAY)),
        () -> assertEquals(3.0F, resistance.forBreakType(BreakType.WIRE)));
  }

  @Test
  void exortBlocksAreRemovedFromVanillaExplosionListButVanillaBlocksRemain() {
    BukkitTestDoubles.TestWorld world = world("explosion-list", 1);
    Block wire = world.block(0, 64, 0, Material.CHORUS_PLANT);
    Block stone = world.block(1, 64, 0, Material.STONE);
    WireMarker.setWire(plugin, wire);
    List<Block> vanillaBlocks = new ArrayList<>(List.of(wire, stone));
    List<Block> broken = new ArrayList<>();

    listener(Material.CHORUS_PLANT, broken, BlockBreakHandler.BreakResult.BROKEN)
        .handleExplosion(center(world, 0, 64, 0), TNT_RADIUS, vanillaBlocks);

    assertFalse(vanillaBlocks.contains(wire));
    assertTrue(vanillaBlocks.contains(stone));
    assertEquals(List.of(wire), broken);
  }

  @Test
  void tntScaleExplosionBreaksNearbyWireThroughExortPath() {
    BukkitTestDoubles.TestWorld world = world("wire-break", 2);
    Block wire = world.block(0, 64, 0, Material.CHORUS_PLANT);
    WireMarker.setWire(plugin, wire);
    List<Block> broken = new ArrayList<>();

    listener(Material.CHORUS_PLANT, broken, BlockBreakHandler.BreakResult.BROKEN)
        .handleExplosion(center(world, 0, 64, 0), TNT_RADIUS, new ArrayList<>());

    assertEquals(List.of(wire), broken);
  }

  @Test
  void tntScaleExplosionBreaksLowResistanceUtilityBlocks() {
    BukkitTestDoubles.TestWorld world = world("utility-breaks", 3);
    Block bus = world.block(-1, 64, 0, Material.BARRIER);
    Block terminal = world.block(0, 64, 0, Material.BARRIER);
    Block monitor = world.block(1, 64, 0, Material.BARRIER);
    BusMarker.set(plugin, bus, BusType.IMPORT, BlockFace.NORTH, BusMode.DISABLED);
    TerminalMarker.set(plugin, terminal);
    MonitorMarker.set(plugin, monitor, BlockFace.NORTH);
    List<Block> vanillaBlocks = new ArrayList<>(List.of(bus, terminal, monitor));
    List<Block> broken = new ArrayList<>();

    listener(Material.CHORUS_PLANT, broken, BlockBreakHandler.BreakResult.BROKEN)
        .handleExplosion(center(world, 0, 64, 0), TNT_RADIUS, vanillaBlocks);

    assertFalse(vanillaBlocks.contains(bus));
    assertFalse(vanillaBlocks.contains(terminal));
    assertFalse(vanillaBlocks.contains(monitor));
    assertEquals(List.of(bus, terminal, monitor), broken);
  }

  @Test
  void tntScaleExplosionDoesNotBreakBlastResistantStorageOrRelay() {
    BukkitTestDoubles.TestWorld world = world("resistant-survives", 7);
    Block storage = world.block(0, 64, 0, Material.BARRIER);
    Block relay = world.block(1, 64, 0, Material.BARRIER);
    StorageMarker.setRaw(plugin, storage, "storage-a", "common", 1024L, BlockFace.NORTH);
    RelayMarker.set(plugin, relay);
    List<Block> vanillaBlocks = new ArrayList<>(List.of(storage, relay));
    List<Block> broken = new ArrayList<>();

    listener(Material.CHORUS_PLANT, broken, BlockBreakHandler.BreakResult.BROKEN)
        .handleExplosion(center(world, 0, 64, 0), TNT_RADIUS, vanillaBlocks);

    assertFalse(vanillaBlocks.contains(storage));
    assertFalse(vanillaBlocks.contains(relay));
    assertTrue(broken.isEmpty());
  }

  @Test
  void strongExplosionCanBreakBlastResistantStorage() {
    BukkitTestDoubles.TestWorld world = world("storage-breaks", 4);
    Block storage = world.block(0, 64, 0, Material.BARRIER);
    StorageMarker.setRaw(plugin, storage, "storage-a", "common", 1024L, BlockFace.NORTH);
    List<Block> broken = new ArrayList<>();

    listener(Material.CHORUS_PLANT, broken, BlockBreakHandler.BreakResult.BROKEN)
        .handleExplosion(center(world, 0, 64, 0), STRONG_RADIUS, new ArrayList<>());

    assertEquals(List.of(storage), broken);
  }

  @Test
  void configuredBlastResistanceChangesDestructionPolicy() {
    BukkitTestDoubles.TestWorld world = world("configured-terminal-survives", 8);
    Block terminal = world.block(0, 64, 0, Material.BARRIER);
    TerminalMarker.set(plugin, terminal);
    YamlConfiguration config = new YamlConfiguration();
    config.set("break.terminal.blastResistance", 1200.0D);
    List<Block> vanillaBlocks = new ArrayList<>(List.of(terminal));
    List<Block> broken = new ArrayList<>();

    listener(
            Material.CHORUS_PLANT,
            broken,
            BlockBreakHandler.BreakResult.BROKEN,
            ExortBlastResistance.fromConfig(config))
        .handleExplosion(center(world, 0, 64, 0), TNT_RADIUS, vanillaBlocks);

    assertFalse(vanillaBlocks.contains(terminal));
    assertTrue(broken.isEmpty());
  }

  @Test
  void unloadedStorageBreakDenialStillSuppressesVanillaDestruction() {
    BukkitTestDoubles.TestWorld world = world("storage-fail-closed", 5);
    Block storage = world.block(0, 64, 0, Material.BARRIER);
    StorageMarker.setRaw(plugin, storage, "storage-a", "common", 1024L, BlockFace.NORTH);
    List<Block> vanillaBlocks = new ArrayList<>(List.of(storage));
    List<Block> attempted = new ArrayList<>();

    listener(Material.CHORUS_PLANT, attempted, BlockBreakHandler.BreakResult.DENIED)
        .handleExplosion(center(world, 0, 64, 0), STRONG_RADIUS, vanillaBlocks);

    assertFalse(vanillaBlocks.contains(storage));
    assertEquals(List.of(storage), attempted);
    assertEquals(Material.BARRIER, storage.getType());
  }

  @Test
  void allExortBlockFamiliesResolveToExpectedResistance() {
    BukkitTestDoubles.TestWorld world = world("families", 6);
    Block wire = world.block(0, 64, 0, Material.CHORUS_PLANT);
    Block storage = world.block(1, 64, 0, Material.BARRIER);
    Block core = world.block(2, 64, 0, Material.BARRIER);
    Block terminal = world.block(3, 64, 0, Material.BARRIER);
    Block monitor = world.block(4, 64, 0, Material.BARRIER);
    Block bus = world.block(5, 64, 0, Material.BARRIER);
    Block relay = world.block(6, 64, 0, Material.BARRIER);
    WireMarker.setWire(plugin, wire);
    StorageMarker.setRaw(plugin, storage, "storage-a", "common", 1024L, BlockFace.NORTH);
    StorageCoreMarker.set(plugin, core);
    TerminalMarker.set(plugin, terminal);
    MonitorMarker.set(plugin, monitor, BlockFace.NORTH);
    BusMarker.set(plugin, bus, BusType.IMPORT, BlockFace.NORTH, BusMode.DISABLED);
    RelayMarker.set(plugin, relay);
    ExortExplosionResolver resolver = resolver(Material.CHORUS_PLANT);

    assertAll(
        () -> assertTarget(resolver, wire, BreakType.WIRE, ExortBlastResistance.WIRE),
        () -> assertTarget(resolver, storage, BreakType.STORAGE, ExortBlastResistance.STORAGE),
        () -> assertTarget(resolver, core, BreakType.STORAGE, ExortBlastResistance.STORAGE),
        () -> assertTarget(resolver, terminal, BreakType.TERMINAL, ExortBlastResistance.TERMINAL),
        () -> assertTarget(resolver, monitor, BreakType.MONITOR, ExortBlastResistance.MONITOR),
        () -> assertTarget(resolver, bus, BreakType.BUS, ExortBlastResistance.BUS),
        () -> assertTarget(resolver, relay, BreakType.RELAY, ExortBlastResistance.RELAY));
  }

  private static void assertTarget(
      ExortExplosionResolver resolver, Block block, BreakType type, float resistance) {
    ExortExplosionResolver.ExortExplosionBlock target = resolver.resolve(block);
    assertEquals(type, target.type());
    assertEquals(resistance, target.resistance());
  }

  private ExortExplosionListener listener(
      Material wireMaterial, List<Block> broken, BlockBreakHandler.BreakResult result) {
    return listener(wireMaterial, broken, result, ExortBlastResistance.defaults());
  }

  private ExortExplosionListener listener(
      Material wireMaterial,
      List<Block> broken,
      BlockBreakHandler.BreakResult result,
      ExortBlastResistance resistance) {
    return new ExortExplosionListener(
        resolver(wireMaterial, resistance),
        block -> {
          broken.add(block);
          return result;
        });
  }

  private ExortExplosionResolver resolver(Material wireMaterial) {
    return resolver(wireMaterial, ExortBlastResistance.defaults());
  }

  private ExortExplosionResolver resolver(Material wireMaterial, ExortBlastResistance resistance) {
    return new ExortExplosionResolver(plugin, materials(wireMaterial), resistance);
  }

  private static RuntimeMaterials materials(Material wireMaterial) {
    return new RuntimeMaterials(
        wireMaterial,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER,
        Material.BARRIER);
  }

  private static Location center(BukkitTestDoubles.TestWorld world, int x, int y, int z) {
    return new Location(world.world(), x + 0.5D, y + 0.5D, z + 0.5D);
  }

  private static BukkitTestDoubles.TestWorld world(String name, int id) {
    return BukkitTestDoubles.world(name, new UUID(0L, id));
  }
}
