package com.zxcmc.exort.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageMarker;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelayVisualStateResolverTest {
  private static final Material CARRIER = Material.BARRIER;
  private static final Material WIRE = Material.CHORUS_PLANT;

  private Plugin plugin;
  private StorageKeys keys;
  private StorageTier tier;
  private RelaySetupTracker setupTracker;
  private Map<String, StorageTier> savedTiers;

  @BeforeEach
  void setUp() {
    savedTiers = snapshotTiers();
    plugin = BukkitTestDoubles.plugin();
    keys = new StorageKeys(plugin);
    tier = loadTier();
    setupTracker = new RelaySetupTracker(60_000L);
  }

  @AfterEach
  void tearDown() {
    restoreTiers(savedTiers);
  }

  @Test
  void unlinkedRelayWithoutStorageIsBlack() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-visual-black", uuid(1));
    Block relay = relay(world, 0, 64, 0);

    assertEquals(RelayVisualState.BLACK, resolver().resolve(relay, 1_000L));
  }

  @Test
  void storageOnlyRelayIsBlue() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-visual-blue", uuid(2));
    Block relay = relay(world, 0, 64, 0);
    storage(world, 1, 64, 0, "storage-a");

    assertEquals(RelayVisualState.BLUE, resolver().resolve(relay, 1_000L));
  }

  @Test
  void storageAndWorkingPeerRelayIsGreen() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-visual-green", uuid(3));
    Block relay = relay(world, 0, 64, 0);
    Block peer = relay(world, 64, 64, 0);
    storage(world, 1, 64, 0, "storage-a");
    RelayMarker.link(plugin, relay, peer);

    assertEquals(RelayVisualState.GREEN, resolver().resolve(relay, 1_000L));
  }

  @Test
  void workingPeerWithoutStorageIsRed() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-visual-red-link", uuid(4));
    Block relay = relay(world, 0, 64, 0);
    Block peer = relay(world, 64, 64, 0);
    RelayMarker.link(plugin, relay, peer);

    assertEquals(RelayVisualState.RED, resolver().resolve(relay, 1_000L));
  }

  @Test
  void multipleStoragesAreRed() {
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("relay-visual-red-storage", uuid(5));
    Block relay = relay(world, 0, 64, 0);
    storage(world, 1, 64, 0, "storage-a");
    storage(world, 0, 65, 0, "storage-b");

    assertEquals(RelayVisualState.RED, resolver().resolve(relay, 1_000L));
  }

  @Test
  void pendingUnlinkedRelayIsRed() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-visual-pending", uuid(6));
    Block relay = relay(world, 0, 64, 0);
    setupTracker.select(uuid(500), relay, 1_000L);

    assertEquals(RelayVisualState.RED, resolver().resolve(relay, 1_001L));
  }

  @Test
  void unloadedPeerIsNotCountedAsWorkingLink() {
    BukkitTestDoubles.TestWorld world = BukkitTestDoubles.world("relay-visual-unloaded", uuid(7));
    Block relay = relay(world, 0, 64, 0);
    Block peer = relay(world, 64, 64, 0);
    storage(world, 1, 64, 0, "storage-a");
    RelayMarker.link(plugin, relay, peer);
    world.unloadChunk(4, 0);

    assertEquals(RelayVisualState.BLUE, resolver().resolve(relay, 1_000L));
  }

  @Test
  void nonReciprocalPeerIsNotCountedAsWorkingLink() {
    BukkitTestDoubles.TestWorld world =
        BukkitTestDoubles.world("relay-visual-nonreciprocal", uuid(8));
    Block relay = relay(world, 0, 64, 0);
    Block peer = relay(world, 64, 64, 0);
    storage(world, 1, 64, 0, "storage-a");
    RelayMarker.setLink(plugin, relay, RelayMarker.Link.of(peer));

    assertEquals(RelayVisualState.BLUE, resolver().resolve(relay, 1_000L));
  }

  private RelayVisualStateResolver resolver() {
    return new RelayVisualStateResolver(
        plugin, keys, setupTracker, 0, 16, 4, WIRE, CARRIER, CARRIER);
  }

  private Block relay(BukkitTestDoubles.TestWorld world, int x, int y, int z) {
    Block block = world.block(x, y, z, CARRIER);
    RelayMarker.set(plugin, block);
    return block;
  }

  private Block storage(BukkitTestDoubles.TestWorld world, int x, int y, int z, String id) {
    Block block = world.block(x, y, z, CARRIER);
    StorageMarker.set(plugin, block, id, tier);
    return block;
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
        config(Map.of("maxItems", 320, "material", "minecraft:barrel", "name", "Basic"));
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
                yield value == null ? null : value.toString();
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
