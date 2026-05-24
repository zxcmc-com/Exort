package com.zxcmc.exort.core.breaking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class BreakSessionManagerTest {
  @Test
  void touchMarksFollowUpOnlyAfterStartTick() {
    BreakSessionManager manager = new BreakSessionManager();
    Block block = block(UUID.randomUUID(), 1, 2, 3);
    BreakSessionManager.BlockKey key = BreakSessionManager.BlockKey.from(block);
    UUID playerId = UUID.randomUUID();

    manager.getOrCreate(block, BreakType.STORAGE, settings());
    manager.attachPlayer(key, playerId, 10L);

    BreakSessionManager.BreakSession session = manager.getSession(key);
    assertNotNull(session);
    assertFalse(session.hasFollowUpSwing());

    session.touch(playerId, 10L);
    assertFalse(session.hasFollowUpSwing());

    session.touch(playerId, 11L);
    assertTrue(session.hasFollowUpSwing());
  }

  @Test
  void detachingLastPlayerRemovesSessionAndMapping() {
    BreakSessionManager manager = new BreakSessionManager();
    Block block = block(UUID.randomUUID(), -4, 64, 9);
    BreakSessionManager.BlockKey key = BreakSessionManager.BlockKey.from(block);
    UUID playerId = UUID.randomUUID();

    manager.getOrCreate(block, BreakType.TERMINAL, settings());
    manager.attachPlayer(key, playerId, 1L);

    assertNotNull(manager.detachPlayer(playerId));
    assertNull(manager.getSession(key));
    assertNull(manager.getPlayerSession(playerId));
  }

  private static BreakSettings settings() {
    return new BreakSettings(1.0, Set.<Material>of());
  }

  private static Block block(UUID worldId, int x, int y, int z) {
    World world =
        (World)
            Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "getUID" -> worldId;
                    case "toString" -> "world(" + worldId + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                  };
                });
    return (Block)
        Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[] {Block.class},
            (proxy, method, args) -> {
              return switch (method.getName()) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "toString" -> "block(" + x + "," + y + "," + z + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> args != null && args.length == 1 && proxy == args[0];
                default -> throw new UnsupportedOperationException(method.toString());
              };
            });
  }
}
