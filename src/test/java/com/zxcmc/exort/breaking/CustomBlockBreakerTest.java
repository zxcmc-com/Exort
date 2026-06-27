package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.marker.WireMarker;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class CustomBlockBreakerTest {
  @Test
  void markedFullChorusWireUsesCustomBreaker() {
    Plugin plugin = plugin();
    BlockProbe wire = block(Material.CHORUS_PLANT, true);
    WireMarker.setWire(plugin, wire.block);

    assertEquals(BreakType.WIRE, breaker(plugin, Carriers.CHORUS_MATERIAL).resolveType(wire.block));
  }

  @Test
  void unmarkedFullChorusDoesNotUseCustomBreaker() {
    Plugin plugin = plugin();
    BlockProbe chorus = block(Material.CHORUS_PLANT, true);

    assertEquals(
        BreakType.NONE, breaker(plugin, Carriers.CHORUS_MATERIAL).resolveType(chorus.block));
  }

  @Test
  void markedBarrierWireStillUsesCustomBreaker() {
    Plugin plugin = plugin();
    BlockProbe wire = block(Material.BARRIER, false);
    WireMarker.setWire(plugin, wire.block);

    assertEquals(BreakType.WIRE, breaker(plugin, Carriers.CARRIER_BARRIER).resolveType(wire.block));
  }

  @Test
  void survivalSwingStartsCustomBreakerWithoutPriorDamageEvent() {
    Plugin plugin = plugin();
    BlockProbe wire = block(Material.BARRIER, false);
    WireMarker.setWire(plugin, wire.block);
    CustomBlockBreaker breaker = breaker(plugin, Carriers.CARRIER_BARRIER);
    Player player = player(GameMode.SURVIVAL, wire.block);

    assertTrue(breaker.processSwing(player));
    assertTrue(breaker.isCurrentSession(player, wire.block));
  }

  @Test
  void adventureAndSpectatorSwingsDoNotStartWithoutPriorDamageEvent() {
    Plugin plugin = plugin();
    BlockProbe wire = block(Material.BARRIER, false);
    WireMarker.setWire(plugin, wire.block);
    CustomBlockBreaker breaker = breaker(plugin, Carriers.CARRIER_BARRIER);
    Player adventure = player(GameMode.ADVENTURE, wire.block);
    Player spectator = player(GameMode.SPECTATOR, wire.block);

    assertFalse(breaker.processSwing(adventure));
    assertFalse(breaker.isCurrentSession(adventure, wire.block));
    assertFalse(breaker.processSwing(spectator));
    assertFalse(breaker.isCurrentSession(spectator, wire.block));
  }

  @Test
  void rightClickSwingDoesNotStartCustomBreaker() {
    Plugin plugin = plugin();
    BlockProbe wire = block(Material.BARRIER, false);
    WireMarker.setWire(plugin, wire.block);
    CustomBlockBreaker breaker = breaker(plugin, Carriers.CARRIER_BARRIER);
    Player player = player(GameMode.SURVIVAL, wire.block);

    breaker.rememberRightClick(player);

    assertFalse(breaker.processSwing(player));
    assertFalse(breaker.isCurrentSession(player, wire.block));
  }

  private static CustomBlockBreaker breaker(Plugin plugin, Material wireMaterial) {
    BreakSettings settings = new BreakSettings(1.0, Set.of());
    BreakConfig breakConfig =
        new BreakConfig(settings, settings, settings, settings, settings, settings);
    return new CustomBlockBreaker(
        plugin,
        RegionProtection.allowAll(),
        WorldEditWandGuard.ALLOW,
        null,
        breakConfig,
        null,
        BreakAnimationSender.NOOP,
        wireMaterial,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        Carriers.CARRIER_BARRIER,
        new ClientBreakSpeedSuppressor(plugin, player -> null));
  }

  private static BlockProbe block(Material material, boolean fullChorus) {
    return new BlockProbe(material, fullChorus);
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

  private static Player player(GameMode gameMode, Block target) {
    UUID playerId = UUID.randomUUID();
    PlayerInventory inventory = inventory();
    return proxy(
        Player.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getUniqueId" -> playerId;
              case "isOnline" -> true;
              case "getGameMode" -> gameMode;
              case "getInventory" -> inventory;
              case "getTargetBlockExact" ->
                  ((Integer) args[0]) >= 5 && args[1] == FluidCollisionMode.NEVER ? target : null;
              case "toString" -> "player(" + gameMode + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static PlayerInventory inventory() {
    return proxy(
        PlayerInventory.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getItemInMainHand" -> null;
              case "toString" -> "inventory(empty)";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static MultipleFacing fullChorusData() {
    Set<BlockFace> faces =
        EnumSet.of(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST);
    return proxy(
        MultipleFacing.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getAllowedFaces" -> faces;
              case "hasFace" -> faces.contains(args[0]);
              case "toString" -> "full-chorus-data";
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
    return CustomBlockBreakerTest.class.getClassLoader();
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(classLoader(), new Class<?>[] {type}, handler);
  }

  private static final class BlockProbe {
    private final SimplePdc chunkPdc = new SimplePdc();
    private final World world = world();
    private final Block block;

    private BlockProbe(Material material, boolean fullChorus) {
      Chunk chunk =
          proxy(
              Chunk.class,
              (proxy, method, args) ->
                  switch (method.getName()) {
                    case "getPersistentDataContainer" -> chunkPdc.proxy();
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
                    case "getType" -> material;
                    case "getBlockData" -> fullChorus ? fullChorusData() : null;
                    case "getChunk" -> chunk;
                    case "getWorld" -> world;
                    case "getX" -> 4;
                    case "getY" -> 64;
                    case "getZ" -> -2;
                    case "toString" -> "block(" + material + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                  });
    }
  }

  private static World world() {
    UUID worldId = UUID.randomUUID();
    return proxy(
        World.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getUID" -> worldId;
              case "toString" -> "world(" + worldId + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static final class SimplePdc {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    private PersistentDataContainer proxy() {
      return CustomBlockBreakerTest.proxy(PersistentDataContainer.class, this::invoke);
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
      return CustomBlockBreakerTest.proxy(
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
