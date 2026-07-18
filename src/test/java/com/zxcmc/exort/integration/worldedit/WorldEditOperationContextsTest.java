package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sk89q.worldedit.extension.platform.Actor;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditOperationContextsTest {
  @Test
  void onePreparationIsReusedAcrossExtentStagesUntilReplaced() {
    WorldEditOperationContexts contexts = new WorldEditOperationContexts(5_000L);
    UUID actorId = new UUID(1L, 2L);
    UUID worldId = new UUID(3L, 4L);
    Actor actor = actor(actorId);

    WorldEditOperationContext first =
        contexts.remember(actor, context(actorId, worldId, 11L, 1_000L), 1_000L);

    assertSame(first, contexts.resolve(actorId, worldId, 1_001L).context());
    assertSame(first, contexts.resolve(actorId, worldId, 1_002L).context());

    WorldEditOperationContext second =
        contexts.remember(actor, context(actorId, worldId, 12L, 1_003L), 1_003L);
    assertTrue(second.generation() > first.generation());
    assertSame(second, contexts.resolve(actorId, worldId, 1_004L).context());
  }

  @Test
  void actorlessResolutionFailsClosedWhenWorldIsAmbiguous() {
    WorldEditOperationContexts contexts = new WorldEditOperationContexts(5_000L);
    UUID worldId = new UUID(5L, 6L);
    UUID firstId = new UUID(7L, 8L);
    UUID secondId = new UUID(9L, 10L);
    contexts.remember(actor(firstId), context(firstId, worldId, 1L, 1_000L), 1_000L);
    contexts.remember(actor(secondId), context(secondId, worldId, 2L, 1_000L), 1_000L);

    WorldEditOperationContexts.Resolution resolution = contexts.resolve(null, worldId, 1_001L);

    assertTrue(resolution.ambiguous());
    assertNull(resolution.actor());
    assertNull(resolution.context());
  }

  @Test
  void ttlHardCapAndExplicitClearBoundRetainedActors() {
    WorldEditOperationContexts contexts = new WorldEditOperationContexts(100L);
    UUID worldId = new UUID(11L, 12L);
    for (int index = 0; index < 300; index++) {
      UUID actorId = new UUID(0L, index);
      contexts.remember(actor(actorId), context(actorId, worldId, index, 1_000L), 1_000L);
    }

    assertEquals(256, contexts.size(1_001L));
    assertNull(contexts.resolve(new UUID(0L, 0L), worldId, 1_001L));
    assertFalse(contexts.resolve(new UUID(0L, 299L), worldId, 1_001L).ambiguous());
    assertEquals(0, contexts.size(1_101L));

    UUID actorId = new UUID(13L, 14L);
    contexts.remember(actor(actorId), context(actorId, worldId, 3L, 2_000L), 2_000L);
    contexts.clear(actorId);
    assertNull(contexts.resolve(actorId, worldId, 2_001L));
  }

  private static WorldEditOperationContext context(
      UUID actorId, UUID worldId, long operationId, long timestamp) {
    return new WorldEditOperationContext(
        actorId,
        0L,
        "set",
        "set stone",
        worldId,
        null,
        null,
        null,
        null,
        null,
        operationId,
        timestamp);
  }

  private static Actor actor(UUID actorId) {
    return (Actor)
        Proxy.newProxyInstance(
            Actor.class.getClassLoader(),
            new Class<?>[] {Actor.class},
            (proxy, method, args) -> {
              if ("getUniqueId".equals(method.getName())) return actorId;
              if ("toString".equals(method.getName())) return "actor:" + actorId;
              if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
              if ("equals".equals(method.getName())) return proxy == args[0];
              Class<?> type = method.getReturnType();
              if (!type.isPrimitive()) return null;
              if (type == boolean.class) return false;
              if (type == char.class) return '\0';
              return 0;
            });
  }
}
