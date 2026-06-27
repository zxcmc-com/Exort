package com.zxcmc.exort.wire.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.Test;

class WireWaterFlowRefreshTest {
  @Test
  void refreshesLoadedAdjacentWaterAfterWireReplacesWater() {
    World world = world(true);
    BlockProbe wire = block(world, Material.CHORUS_PLANT);
    BlockProbe water = block(world, Material.WATER);
    wire.relatives.put(BlockFace.NORTH, water.block);

    WireWaterFlowRefresh.refreshAfterWirePlacement(wire.block, Material.WATER);

    assertEquals(1, water.fluidTicks);
    assertEquals(List.of(new UpdateCall(true, true)), water.updates);
    assertEquals(List.of(new UpdateCall(true, false)), wire.updates);
  }

  @Test
  void skipsUnloadedAdjacentWater() {
    World world = world(true);
    World unloadedWorld = world(false);
    BlockProbe wire = block(world, Material.CHORUS_PLANT);
    BlockProbe water = block(unloadedWorld, Material.WATER);
    wire.relatives.put(BlockFace.NORTH, water.block);

    WireWaterFlowRefresh.refreshAfterWirePlacement(wire.block, Material.STONE);

    assertEquals(0, water.typeReads);
    assertEquals(0, water.fluidTicks);
    assertEquals(List.of(), water.updates);
    assertEquals(List.of(), wire.updates);
  }

  @Test
  void skipsWhenNoWaterWasAffected() {
    World world = world(true);
    BlockProbe wire = block(world, Material.CHORUS_PLANT);

    WireWaterFlowRefresh.refreshAfterWirePlacement(wire.block, Material.STONE);

    assertEquals(List.of(), wire.updates);
  }

  private static BlockProbe block(World world, Material type) {
    return new BlockProbe(world, type);
  }

  private static World world(boolean loaded) {
    InvocationHandler handler =
        (proxy, method, args) -> {
          if (method.getName().equals("isChunkLoaded")) {
            return loaded;
          }
          return defaultValue(method.getReturnType());
        };
    return (World) Proxy.newProxyInstance(classLoader(), new Class<?>[] {World.class}, handler);
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
    return WireWaterFlowRefreshTest.class.getClassLoader();
  }

  private static final class BlockProbe {
    private final World world;
    private final Material type;
    private final Map<BlockFace, Block> relatives = new EnumMap<>(BlockFace.class);
    private final List<UpdateCall> updates = new ArrayList<>();
    private final Block block;
    private int fluidTicks;
    private int typeReads;

    private BlockProbe(World world, Material type) {
      this.world = world;
      this.type = type;
      this.block =
          (Block) Proxy.newProxyInstance(classLoader(), new Class<?>[] {Block.class}, this::invoke);
    }

    private Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
      return switch (method.getName()) {
        case "getWorld" -> world;
        case "getX", "getZ" -> 0;
        case "getType" -> {
          typeReads++;
          yield type;
        }
        case "getRelative" -> relatives.get(args[0]);
        case "getState" -> state();
        case "fluidTick" -> {
          fluidTicks++;
          yield null;
        }
        default -> defaultValue(method.getReturnType());
      };
    }

    private BlockState state() {
      InvocationHandler handler =
          (proxy, method, args) -> {
            if (method.getName().equals("update")
                && args != null
                && args.length == 2
                && args[0] instanceof Boolean force
                && args[1] instanceof Boolean applyPhysics) {
              updates.add(new UpdateCall(force, applyPhysics));
              return true;
            }
            return defaultValue(method.getReturnType());
          };
      return (BlockState)
          Proxy.newProxyInstance(classLoader(), new Class<?>[] {BlockState.class}, handler);
    }
  }

  private record UpdateCall(boolean force, boolean applyPhysics) {}
}
