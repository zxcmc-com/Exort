package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.sk89q.worldedit.extension.platform.Actor;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditActiveCommandActorsTest {
  @Test
  void isolatesActorsByCommandThreadAndExpiresThem() {
    WorldEditActiveCommandActors actors = new WorldEditActiveCommandActors(100L);
    Actor first = actor(new UUID(1L, 2L));
    Actor second = actor(new UUID(3L, 4L));

    actors.remember(10L, first, "world", 1_000L);
    actors.remember(20L, second, "world_nether", 1_000L);

    assertSame(first, actors.resolve(10L, 1_050L));
    assertSame(second, actors.resolve(20L, 1_050L));
    assertSame(first, actors.resolve("world", 1_050L));
    assertNull(actors.resolve(10L, 1_101L));
    assertEquals(0, actors.size());
  }

  @Test
  void replacementAndExplicitClearCannotLeakPreviousActor() {
    WorldEditActiveCommandActors actors = new WorldEditActiveCommandActors(5_000L);
    Actor first = actor(new UUID(5L, 6L));
    Actor second = actor(new UUID(7L, 8L));

    actors.remember(30L, first, "world", 1_000L);
    actors.remember(30L, second, "world", 1_001L);
    assertSame(second, actors.resolve(30L, 1_002L));

    actors.clear(30L);
    assertNull(actors.resolve(30L, 1_003L));
    assertNull(actors.resolve("world", 1_003L));
  }

  @Test
  void refusesAmbiguousActorlessSessionsInTheSameWorld() {
    WorldEditActiveCommandActors actors = new WorldEditActiveCommandActors(5_000L);
    actors.remember(40L, actor(new UUID(9L, 10L)), "world", 1_000L);
    actors.remember(50L, actor(new UUID(11L, 12L)), "world", 1_001L);

    assertNull(actors.resolve("world", 1_002L));
  }

  private static Actor actor(UUID actorId) {
    return (Actor)
        Proxy.newProxyInstance(
            Actor.class.getClassLoader(),
            new Class<?>[] {Actor.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getUniqueId")) return actorId;
              if (method.getName().equals("toString")) return "actor:" + actorId;
              Class<?> returnType = method.getReturnType();
              if (!returnType.isPrimitive()) return null;
              if (returnType == boolean.class) return false;
              if (returnType == char.class) return '\0';
              return 0;
            });
  }
}
