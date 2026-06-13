package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

class BreakProgressCalculatorTest {
  @Test
  void collisionShapeBelowPlayerCountsAsGroundEvenWhenCarrierIsNotSolid() {
    Player player = player(Material.CHORUS_PLANT);

    assertTrue(BreakProgressCalculator.isOnGround(player));
  }

  @Test
  void emptyShapeBelowPlayerDoesNotCountAsGround() {
    Player player = player(Material.AIR);

    assertFalse(BreakProgressCalculator.isOnGround(player));
  }

  private static Player player(Material blockBelow) {
    PlayerInventory inventory = inventory();
    World world = world(blockBelow);
    BoundingBox box = new BoundingBox(0.2, 65.0, 0.2, 0.8, 66.8, 0.8);
    return proxy(
        Player.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getInventory" -> inventory;
              case "getPotionEffect" -> null;
              case "isInWater" -> false;
              case "getWorld" -> world;
              case "getBoundingBox" -> box;
              case "toString" -> "player(onCarrier=" + blockBelow + ")";
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
              case "getHelmet" -> null;
              case "toString" -> "inventory(empty)";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static World world(Material blockBelow) {
    return proxy(
        World.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getBlockAt" -> {
                int y = (Integer) args[1];
                yield y == 64 && blockBelow != Material.AIR
                    ? block(blockBelow, 0, 64, 0, supportShape())
                    : airBlock(y);
              }
              case "toString" -> "world(" + blockBelow + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static Block airBlock(int y) {
    return block(Material.AIR, 0, y, 0, emptyShape());
  }

  private static Block block(
      Material material, int x, int y, int z, BoundingBox collisionBoundingBox) {
    return proxy(
        Block.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "getType" -> material;
              case "isSolid" -> false;
              case "getX" -> x;
              case "getY" -> y;
              case "getZ" -> z;
              case "getBoundingBox" -> collisionBoundingBox;
              case "getCollisionShape" -> shape(collisionBoundingBox);
              case "toString" -> "block(" + material + ")";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> args != null && args.length == 1 && proxy == args[0];
              default -> defaultValue(method.getReturnType());
            });
  }

  private static BoundingBox supportShape() {
    return new BoundingBox(0.3, 64.9, 0.3, 0.7, 65.0, 0.7);
  }

  private static BoundingBox emptyShape() {
    return new BoundingBox(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
  }

  private static VoxelShape shape(BoundingBox boundingBox) {
    return proxy(
        VoxelShape.class,
        (proxy, method, args) ->
            switch (method.getName()) {
              case "overlaps" -> boundingBox.overlaps((BoundingBox) args[0]);
              case "getBoundingBoxes" -> Set.of(boundingBox);
              case "toString" -> "shape(" + boundingBox + ")";
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
    return BreakProgressCalculatorTest.class.getClassLoader();
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(classLoader(), new Class<?>[] {type}, handler);
  }
}
