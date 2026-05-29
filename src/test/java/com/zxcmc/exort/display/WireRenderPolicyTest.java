package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class WireRenderPolicyTest {
  @Test
  void autoUsesChunkDensityThresholdsWithHysteresis() {
    World world = world(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    Block anchor = block(world, 0, 64, 0);
    WireBlockIndex index = new WireBlockIndex();
    WireRenderPolicy policy =
        new WireRenderPolicy(
            WireRenderMode.AUTO, new WireAutoRenderConfig(1, 4, 2, 96.0, 16), index);

    index.register(anchor);
    index.register(block(world, 1, 64, 0));
    index.register(block(world, 2, 64, 0));
    assertEquals(WireRenderMode.DETAILED, policy.desiredMode(anchor));

    Block fourth = block(world, 3, 64, 0);
    index.register(fourth);
    assertEquals(WireRenderMode.COMPACT, policy.desiredMode(anchor));

    index.unregister(fourth);
    assertEquals(WireRenderMode.COMPACT, policy.desiredMode(anchor));

    index.unregister(block(world, 2, 64, 0));
    assertEquals(WireRenderMode.DETAILED, policy.desiredMode(anchor));
  }

  @Test
  void explicitModesBypassAutoDensity() {
    World world = world(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    Block anchor = block(world, 0, 64, 0);
    WireBlockIndex index = new WireBlockIndex();
    WireAutoRenderConfig config = new WireAutoRenderConfig(1, 4, 2, 96.0, 16);

    assertEquals(
        WireRenderMode.COMPACT,
        new WireRenderPolicy(WireRenderMode.COMPACT, config, index).desiredMode(anchor));
    assertEquals(
        WireRenderMode.DETAILED,
        new WireRenderPolicy(WireRenderMode.DETAILED, config, index).desiredMode(anchor));
  }

  private static World world(UUID id) {
    return (World)
        Proxy.newProxyInstance(
            WireRenderPolicyTest.class.getClassLoader(),
            new Class<?>[] {World.class},
            (proxy, method, args) -> {
              return switch (method.getName()) {
                case "getUID" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                case "toString" -> "TestWorld{" + id + "}";
                default -> throw new UnsupportedOperationException(method.getName());
              };
            });
  }

  private static Block block(World world, int x, int y, int z) {
    return (Block)
        Proxy.newProxyInstance(
            WireRenderPolicyTest.class.getClassLoader(),
            new Class<?>[] {Block.class},
            (proxy, method, args) -> {
              return switch (method.getName()) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "equals" -> sameBlock(world, x, y, z, args[0]);
                case "hashCode" -> blockHash(world, x, y, z);
                case "toString" -> "TestBlock{" + x + "," + y + "," + z + "}";
                default -> throw new UnsupportedOperationException(method.getName());
              };
            });
  }

  private static boolean sameBlock(World world, int x, int y, int z, Object other) {
    if (!(other instanceof Block block)) {
      return false;
    }
    return world.equals(block.getWorld())
        && x == block.getX()
        && y == block.getY()
        && z == block.getZ();
  }

  private static int blockHash(World world, int x, int y, int z) {
    int result = world.hashCode();
    result = 31 * result + x;
    result = 31 * result + y;
    result = 31 * result + z;
    return result;
  }
}
