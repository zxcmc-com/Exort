package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.storage.StorageTier;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuiSessionValidatorTest {
  @BeforeAll
  static void loadStorageTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 64);
    config.set("basic.material", "CHEST");
    config.set("basic.displayName", "Basic");
    StorageTier.loadFromConfig(config, Logger.getLogger("test"));
  }

  @Test
  void liveStorageAnchorMatchesExpectedMarkerStorageId() {
    Plugin plugin = plugin();
    AnchorProbe anchor = new AnchorProbe(1, 64, 2);
    StorageMarker.set(
        plugin,
        anchor.block,
        "storage-a",
        StorageTier.fromString("basic").orElseThrow(),
        BlockFace.NORTH);

    assertTrue(validator(plugin).hasLiveStorageAnchor("storage-a", anchor.location()));
  }

  @Test
  void liveStorageAnchorRejectsMissingMarker() {
    Plugin plugin = plugin();
    AnchorProbe anchor = new AnchorProbe(1, 64, 2);

    assertFalse(validator(plugin).hasLiveStorageAnchor("storage-a", anchor.location()));
  }

  @Test
  void liveStorageAnchorRejectsWrongMarkerStorageId() {
    Plugin plugin = plugin();
    AnchorProbe anchor = new AnchorProbe(1, 64, 2);
    StorageMarker.set(
        plugin,
        anchor.block,
        "storage-b",
        StorageTier.fromString("basic").orElseThrow(),
        BlockFace.NORTH);

    assertFalse(validator(plugin).hasLiveStorageAnchor("storage-a", anchor.location()));
  }

  @Test
  void liveStorageAnchorDoesNotResolveBlockInUnloadedChunk() {
    Plugin plugin = plugin();
    AnchorProbe anchor = new AnchorProbe(1, 64, 2);
    anchor.chunkLoaded = false;

    assertFalse(validator(plugin).hasLiveStorageAnchor("storage-a", anchor.location()));
    assertEquals(0, anchor.getBlockAtCalls);
  }

  private static GuiSessionValidator validator(Plugin plugin) {
    return new GuiSessionValidator(
        plugin,
        null,
        () -> 0,
        () -> 0,
        () -> Material.BARRIER,
        () -> Material.BARRIER,
        () -> Material.BARRIER);
  }

  private static Plugin plugin() {
    return proxy(
        Plugin.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getName" -> "Exort";
              case "namespace" -> "exort";
              case "toString" -> "plugin(Exort)";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static final class AnchorProbe {
    private final UUID worldId = UUID.fromString("00000000-0000-0000-0000-000000000321");
    private final int x;
    private final int y;
    private final int z;
    private final SimplePdc chunkPdc = new SimplePdc();
    private World world;
    private Chunk chunk;
    private Block block;
    private boolean chunkLoaded = true;
    private int getBlockAtCalls;

    private AnchorProbe(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.world =
          proxy(
              World.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getUID" -> worldId;
                    case "isChunkLoaded" -> chunkLoaded;
                    case "getBlockAt" -> {
                      getBlockAtCalls++;
                      yield block;
                    }
                    case "toString" -> "world";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
      this.chunk =
          proxy(
              Chunk.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getPersistentDataContainer" -> chunkPdc.proxy();
                    case "getWorld" -> world;
                    case "toString" -> "chunk";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
      this.block =
          proxy(
              Block.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getType" -> Material.BARRIER;
                    case "getWorld" -> world;
                    case "getChunk" -> chunk;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "toString" -> "block(" + x + "," + y + "," + z + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
    }

    private Location location() {
      return new Location(world, x, y, z);
    }
  }

  private static final class SimplePdc {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    private PersistentDataContainer proxy() {
      return GuiSessionValidatorTest.proxy(PersistentDataContainer.class, this::invoke);
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
      return switch (method.getName()) {
        case "set" -> {
          values.put((NamespacedKey) args[0], args[2]);
          yield null;
        }
        case "get" -> values.get((NamespacedKey) args[0]);
        case "getOrDefault" -> values.getOrDefault((NamespacedKey) args[0], args[2]);
        case "has" -> values.containsKey((NamespacedKey) args[0]);
        case "remove" -> {
          values.remove((NamespacedKey) args[0]);
          yield null;
        }
        case "isEmpty" -> values.isEmpty();
        case "getKeys" -> Set.copyOf(values.keySet());
        case "getAdapterContext" -> adapterContext();
        case "toString" -> "pdc" + values;
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> args != null && args.length == 1 && proxy == args[0];
        default -> defaultValue(method.getReturnType());
      };
    }

    private PersistentDataAdapterContext adapterContext() {
      return GuiSessionValidatorTest.proxy(
          PersistentDataAdapterContext.class,
          (proxy, method, args) ->
              switch (method.getName()) {
                case "newPersistentDataContainer" -> new SimplePdc().proxy();
                case "toString" -> "pdc-adapter";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> args != null && args.length == 1 && proxy == args[0];
                default -> defaultValue(method.getReturnType());
              });
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T)
        Proxy.newProxyInstance(
            GuiSessionValidatorTest.class.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == Void.TYPE) return null;
    if (returnType == Boolean.TYPE) return false;
    if (returnType == Byte.TYPE) return (byte) 0;
    if (returnType == Short.TYPE) return (short) 0;
    if (returnType == Integer.TYPE) return 0;
    if (returnType == Long.TYPE) return 0L;
    if (returnType == Float.TYPE) return 0.0f;
    if (returnType == Double.TYPE) return 0.0d;
    if (returnType == Character.TYPE) return '\0';
    return null;
  }
}
