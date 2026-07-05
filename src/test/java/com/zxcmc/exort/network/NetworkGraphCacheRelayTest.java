package com.zxcmc.exort.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkGraphCacheRelayTest {
  private static final Material CARRIER = Material.BARRIER;
  private static final Material WIRE = Material.CHORUS_PLANT;

  private Plugin plugin;
  private StorageKeys keys;
  private StorageTier tier;
  private Map<String, StorageTier> savedTiers;

  @BeforeEach
  void setUp() {
    savedTiers = snapshotTiers();
    plugin = BukkitTestDoubles.plugin();
    keys = new StorageKeys(plugin);
    tier = loadTier();
  }

  @AfterEach
  void tearDown() {
    restoreTiers(savedTiers);
  }

  @Test
  void linkedReciprocalRelaysConnectStorageWithoutIncreasingWireLength() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-graph-link", uuid(10));
    Block terminal = world.block(0, 64, 0, CARRIER);
    Block firstRelay = world.block(1, 64, 0, CARRIER);
    Block secondRelay = world.block(64, 64, 0, CARRIER);
    Block storage = world.block(65, 64, 0, CARRIER);
    TerminalMarker.set(plugin, terminal);
    RelayMarker.link(plugin, firstRelay, secondRelay);
    StorageMarker.set(plugin, storage, "storage-a", tier);

    TerminalLinkFinder.StorageSearchResult result = scan(terminal, 0, 16, 4);

    assertEquals(1, result.count());
    assertEquals("storage-a", result.data().storageId());
  }

  @Test
  void disabledRelayTraversalDoesNotConnectLinkedRelays() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-graph-disabled", uuid(16));
    Block terminal = world.block(0, 64, 0, CARRIER);
    Block firstRelay = world.block(1, 64, 0, CARRIER);
    Block secondRelay = world.block(64, 64, 0, CARRIER);
    Block storage = world.block(65, 64, 0, CARRIER);
    TerminalMarker.set(plugin, terminal);
    RelayMarker.link(plugin, firstRelay, secondRelay);
    StorageMarker.set(plugin, storage, "storage-a", tier);

    TerminalLinkFinder.StorageSearchResult result =
        NetworkGraphCache.scan(terminal, keys, plugin, 0, 16, WIRE, CARRIER, null, 4);

    assertEquals(0, result.count());
  }

  @Test
  void scanUsesStorageMarkerFallbackWhenTierWasRemoved() {
    ConfigurationSection gold =
        config(Map.of("maxItems", 45L * 64L, "material", "minecraft:gold_block"));
    ConfigurationSection diamond =
        config(Map.of("maxItems", 10L * 45L * 64L, "material", "minecraft:diamond_block"));
    StorageTier.loadFromConfig(
        config(Map.of("gold", gold, "diamond", diamond), Set.of("gold", "diamond")),
        Logger.getLogger("ExortTest"));
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("relay-graph-missing-tier", uuid(11));
    Block terminal = world.block(0, 64, 0, CARRIER);
    Block firstRelay = world.block(1, 64, 0, CARRIER);
    Block secondRelay = world.block(64, 64, 0, CARRIER);
    Block storage = world.block(65, 64, 0, CARRIER);
    TerminalMarker.set(plugin, terminal);
    RelayMarker.link(plugin, firstRelay, secondRelay);
    ChunkMarkerStore.setString(plugin, storage, "storage", "id", "storage-a");
    ChunkMarkerStore.setString(plugin, storage, "storage", "tier", "OBSIDIAN");
    ChunkMarkerStore.setLong(plugin, storage, "storage", "tierMaxItems", 20L * 45L * 64L);

    TerminalLinkFinder.StorageSearchResult result = scan(terminal, 0, 16, 4);

    assertEquals(1, result.count());
    assertEquals("storage-a", result.data().storageId());
    assertEquals("DIAMOND", result.data().tier().key());
  }

  @Test
  void linkedReciprocalRelaysConnectMonitorAndBusStartsToStorage() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-graph-components", uuid(15));
    Block monitor = world.block(0, 64, 0, CARRIER);
    Block monitorRelay = world.block(1, 64, 0, CARRIER);
    Block bus = world.block(0, 70, 0, CARRIER);
    Block busRelay = world.block(1, 70, 0, CARRIER);
    Block farMonitorRelay = world.block(64, 64, 0, CARRIER);
    Block monitorStorage = world.block(65, 64, 0, CARRIER);
    Block farBusRelay = world.block(64, 70, 0, CARRIER);
    Block busStorage = world.block(65, 70, 0, CARRIER);
    MonitorMarker.set(plugin, monitor, BlockFace.WEST);
    BusMarker.set(plugin, bus, BusType.IMPORT, BlockFace.WEST, BusMode.DISABLED);
    RelayMarker.link(plugin, monitorRelay, farMonitorRelay);
    RelayMarker.link(plugin, busRelay, farBusRelay);
    StorageMarker.set(plugin, monitorStorage, "storage-a", tier);
    StorageMarker.set(plugin, busStorage, "storage-b", tier);

    assertEquals(1, scan(monitor, 0, 16, 4).count());
    assertEquals(1, scan(bus, 0, 16, 4).count());
  }

  @Test
  void relayRangeUsesChunkManhattanDistance() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-graph-range", uuid(11));
    Block origin = world.block(0, 64, 0, CARRIER);
    Block diagonalInside = world.block(32, 64, 16, CARRIER);
    Block diagonalOutside = world.block(32, 64, 32, CARRIER);

    assertTrue(NetworkGraphCache.inRelayRange(origin, diagonalInside, 3));
    assertFalse(NetworkGraphCache.inRelayRange(origin, diagonalOutside, 3));
  }

  @Test
  void unloadedPeerChunkIsNotLoadedAndDoesNotActAsLink() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-graph-unloaded", uuid(12));
    Block terminal = world.block(0, 64, 0, CARRIER);
    Block firstRelay = world.block(1, 64, 0, CARRIER);
    Block secondRelay = world.block(64, 64, 0, CARRIER);
    Block storage = world.block(65, 64, 0, CARRIER);
    TerminalMarker.set(plugin, terminal);
    RelayMarker.link(plugin, firstRelay, secondRelay);
    StorageMarker.set(plugin, storage, "storage-a", tier);
    world.unloadChunk(4, 0);

    TerminalLinkFinder.StorageSearchResult result = scan(terminal, 0, 16, 4);

    assertEquals(0, result.count());
    assertEquals(0, world.getBlockAtCalls());
  }

  @Test
  void crossWorldRelayLinkDoesNotConnectStorage() {
    BukkitTestDoubles.TestWorld firstWorld =
        BukkitTestDoubles.world("relay-graph-world-a", uuid(13));
    BukkitTestDoubles.TestWorld secondWorld =
        BukkitTestDoubles.world("relay-graph-world-b", uuid(14));
    Block terminal = firstWorld.block(0, 64, 0, CARRIER);
    Block firstRelay = firstWorld.block(1, 64, 0, CARRIER);
    Block secondRelay = secondWorld.block(64, 64, 0, CARRIER);
    Block storage = secondWorld.block(65, 64, 0, CARRIER);
    TerminalMarker.set(plugin, terminal);
    RelayMarker.link(plugin, firstRelay, secondRelay);
    StorageMarker.set(plugin, storage, "storage-a", tier);

    TerminalLinkFinder.StorageSearchResult result = scan(terminal, 0, 16, 8);

    assertEquals(0, result.count());
  }

  private TerminalLinkFinder.StorageSearchResult scan(
      Block start, int wireLimit, int hardCap, int relayRangeChunks) {
    return NetworkGraphCache.scan(
        start, keys, plugin, wireLimit, hardCap, WIRE, CARRIER, CARRIER, relayRangeChunks);
  }

  private static StorageTier loadTier() {
    StorageTier.loadFromConfig(tiersSection(), Logger.getLogger("ExortTest"));
    return StorageTier.fromString("BASIC").orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, StorageTier> snapshotTiers() {
    try {
      Field field = StorageTier.class.getDeclaredField("REGISTRY");
      field.setAccessible(true);
      return new LinkedHashMap<>((Map<String, StorageTier>) field.get(null));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to snapshot storage tiers", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static void restoreTiers(Map<String, StorageTier> tiers) {
    try {
      Field field = StorageTier.class.getDeclaredField("REGISTRY");
      field.setAccessible(true);
      Map<String, StorageTier> registry = (Map<String, StorageTier>) field.get(null);
      registry.clear();
      if (tiers != null) {
        registry.putAll(tiers);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to restore storage tiers", e);
    }
  }

  private static ConfigurationSection tiersSection() {
    ConfigurationSection basic =
        config(
            Map.of(
                "maxItems", 320,
                "material", "minecraft:barrel",
                "name", "Basic"));
    return config(Map.of("BASIC", basic), Set.of("BASIC"));
  }

  private static ConfigurationSection config(Map<String, Object> values) {
    return config(values, values.keySet());
  }

  private static ConfigurationSection config(Map<String, Object> values, Set<String> keys) {
    InvocationHandler handler =
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getKeys" -> keys;
              case "getConfigurationSection" -> values.get((String) args[0]);
              case "get" -> values.get((String) args[0]);
              case "getString" -> {
                Object value = values.get((String) args[0]);
                yield value == null
                    ? (args != null && args.length > 1 ? args[1] : null)
                    : value.toString();
              }
              case "toString" -> "config" + values;
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> BukkitTestDoubles.defaultValue(method.getReturnType());
            };
    return BukkitTestDoubles.proxy(ConfigurationSection.class, handler);
  }

  private static UUID uuid(int value) {
    return new UUID(0L, value);
  }
}
