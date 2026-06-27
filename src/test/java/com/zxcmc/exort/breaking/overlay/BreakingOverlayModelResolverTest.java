package com.zxcmc.exort.breaking.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.storage.StorageTier;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BreakingOverlayModelResolverTest {
  private static final Material CARRIER = Carriers.CARRIER_BARRIER;

  @BeforeAll
  static void loadStorageTier() {
    YamlConfiguration config = new YamlConfiguration();
    config.set("basic.maxItems", 64);
    config.set("basic.material", "CHEST");
    config.set("basic.name", "Basic");
    StorageTier.loadFromConfig(config, Logger.getLogger("test"));
  }

  @Test
  void storageCoreUsesIdentityStorageModel() {
    Plugin plugin = plugin();
    BlockProbe block = block(0, 64, 0);
    StorageCoreMarker.set(plugin, block.block);

    assertEquals("storage/core", resolver(plugin).modelKey(block.block, BreakType.STORAGE));
  }

  @Test
  void storageUsesSharedModelWhileTerminalAndMonitorFacingsUsePreBakedHorizontalKeys() {
    Plugin plugin = plugin();
    BlockProbe storage = block(1, 64, 0);
    BlockProbe terminal = block(2, 64, 0);
    BlockProbe monitor = block(3, 64, 0);

    StorageMarker.set(
        plugin,
        storage.block,
        "storage-1",
        StorageTier.fromString("basic").orElseThrow(),
        BlockFace.EAST);
    TerminalMarker.set(plugin, terminal.block, TerminalKind.CRAFTING, BlockFace.WEST);
    MonitorMarker.set(plugin, monitor.block, BlockFace.NORTH);

    BreakingOverlayModelResolver resolver = resolver(plugin);
    assertEquals("storage/core", resolver.modelKey(storage.block, BreakType.STORAGE));
    assertEquals("terminal/west", resolver.modelKey(terminal.block, BreakType.TERMINAL));
    assertEquals("terminal/north", resolver.modelKey(monitor.block, BreakType.MONITOR));
  }

  @Test
  void busFullFacingUsesPreBakedTypeAndDirectionKey() {
    Plugin plugin = plugin();
    BlockProbe bus = block(4, 64, 0);
    BusMarker.set(plugin, bus.block, BusType.EXPORT, BlockFace.UP, BusMode.ALL);

    assertEquals("bus/export/up", resolver(plugin).modelKey(bus.block, BreakType.BUS));
  }

  @Test
  void wireMasksUseSharedCenterModel() {
    Plugin plugin = plugin();
    BlockProbe wire = block(5, 64, 0);
    WireMarker.setWire(plugin, wire.block);

    BreakingOverlayModelResolver resolver = resolver(plugin);
    assertEquals("wire/center", resolver.modelKey(wire.block, BreakType.WIRE));

    BlockProbe northWire = block(5, 64, -1);
    WireMarker.setWire(plugin, northWire.block);
    wire.relative(BlockFace.NORTH, northWire);

    assertEquals("wire/center", resolver.modelKey(wire.block, BreakType.WIRE));
  }

  private static BreakingOverlayModelResolver resolver(Plugin plugin) {
    return new BreakingOverlayModelResolver(
        plugin, CARRIER, CARRIER, CARRIER, CARRIER, CARRIER, CARRIER);
  }

  private static BlockProbe block(int x, int y, int z) {
    return new BlockProbe(x, y, z);
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

  private static ClassLoader classLoader() {
    return BreakingOverlayModelResolverTest.class.getClassLoader();
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(classLoader(), new Class<?>[] {type}, handler);
  }

  private static final class BlockProbe {
    private static final SimplePdc SHARED_CHUNK_PDC = new SimplePdc();
    private static final Chunk SHARED_CHUNK =
        proxy(
            Chunk.class,
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getPersistentDataContainer" -> SHARED_CHUNK_PDC.proxy();
                  case "toString" -> "chunk";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> args != null && args.length == 1 && proxy == args[0];
                  default -> defaultValue(method.getReturnType());
                });

    private final int x;
    private final int y;
    private final int z;
    private final Map<BlockFace, Block> relatives = new EnumMap<>(BlockFace.class);
    private final Block block;

    private BlockProbe(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.block =
          proxy(
              Block.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getType" -> Material.BARRIER;
                    case "getChunk" -> SHARED_CHUNK;
                    case "getX" -> this.x;
                    case "getY" -> this.y;
                    case "getZ" -> this.z;
                    case "getRelative" -> relatives.get((BlockFace) args[0]);
                    case "toString" -> "block(" + x + "," + y + "," + z + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
    }

    private void relative(BlockFace face, BlockProbe other) {
      relatives.put(face, other.block);
    }
  }

  private static final class SimplePdc {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    private PersistentDataContainer proxy() {
      return BreakingOverlayModelResolverTest.proxy(PersistentDataContainer.class, this::invoke);
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
      return BreakingOverlayModelResolverTest.proxy(
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
}
