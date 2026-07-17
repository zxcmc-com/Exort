package com.zxcmc.exort.display.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.junit.jupiter.api.Test;

class DisplayEntityIndexTest {
  @Test
  void entityRemovalListenerDropsDisplayFromEveryIndex() {
    UUID worldId = UUID.randomUUID();
    World world = world(worldId);
    UUID entityId = UUID.randomUUID();
    Display display = display(entityId, 41, new Location(world, 0.0, 64.0, 0.0));
    DisplayEntityIndex index = new DisplayEntityIndex();
    index.register(display);

    new DisplayEntityIndexCleanupListener(index)
        .onEntityRemove(new EntityRemoveEvent(display, EntityRemoveEvent.Cause.PLUGIN));

    assertTrue(index.query(new Location(world, 0.0, 64.0, 0.0), 8.0).isEmpty());
    assertNull(index.findByNetworkId(41));
  }

  @Test
  void resumableQueryCountsSectionsAndEntitiesWithoutExceedingBudget() {
    UUID worldId = UUID.randomUUID();
    World world = world(worldId);
    DisplayEntityIndex index = new DisplayEntityIndex();
    Set<UUID> expected = new HashSet<>();
    for (int value = 0; value < 5_000; value++) {
      UUID entityId = new UUID(1L, value + 1L);
      expected.add(entityId);
      index.register(display(entityId, value + 1, new Location(world, value % 8, 64, value % 8)));
    }

    DisplayEntityIndex.QueryCursor cursor =
        index.openQuery(new Location(world, 0.0, 64.0, 0.0), 32.0);
    Set<UUID> actual = new HashSet<>();
    int steps = 0;
    while (!cursor.complete()) {
      DisplayEntityIndex.QueryStep step =
          cursor.advance(127, entry -> actual.add(entry.entityUuid()));
      assertTrue(step.examined() <= 127);
      assertTrue(step.examined() > 0);
      steps++;
    }

    assertTrue(steps > 1);
    assertEquals(expected, actual);
  }

  @Test
  void resumableQueryToleratesRemovalWhileTraversingBucket() {
    UUID worldId = UUID.randomUUID();
    World world = world(worldId);
    DisplayEntityIndex index = new DisplayEntityIndex();
    UUID first = new UUID(2L, 1L);
    UUID second = new UUID(2L, 2L);
    index.register(display(first, 1, new Location(world, 0, 64, 0)));
    index.register(display(second, 2, new Location(world, 1, 64, 0)));
    DisplayEntityIndex.QueryCursor cursor =
        index.openQuery(new Location(world, 0.0, 64.0, 0.0), 8.0);
    Set<UUID> actual = new HashSet<>();

    cursor.advance(1, entry -> actual.add(entry.entityUuid()));
    index.unregister(first);
    while (!cursor.complete()) {
      assertDoesNotThrow(() -> cursor.advance(1, entry -> actual.add(entry.entityUuid())));
    }

    assertFalse(actual.contains(first));
    assertTrue(actual.contains(second));
  }

  @Test
  void queryIntoReusesAndClearsCallerList() {
    UUID worldId = UUID.randomUUID();
    World world = world(worldId);
    UUID nearId = UUID.randomUUID();
    UUID farId = UUID.randomUUID();
    DisplayEntityIndex index = new DisplayEntityIndex();
    index.register(display(nearId, 11, new Location(world, 0.0, 64.0, 0.0)));
    index.register(display(farId, 12, new Location(world, 20.0, 64.0, 0.0)));
    List<DisplayEntityIndex.Entry> out = new ArrayList<>();
    out.add(
        new DisplayEntityIndex.Entry(
            UUID.randomUUID(),
            99,
            worldId,
            999,
            999,
            999,
            DisplayRole.BLOCK,
            null,
            new DisplayEntityIndex.SectionKey(worldId, 0, 0, 0)));

    index.queryInto(new Location(world, 0.0, 64.0, 0.0), 4.0, out);

    assertEquals(1, out.size());
    assertEquals(nearId, out.getFirst().entityUuid());

    index.queryInto(null, 4.0, out);

    assertTrue(out.isEmpty());
  }

  @Test
  void packetReadersRemainSafeWhileServerThreadUpdatesIndex() throws Exception {
    UUID worldId = UUID.randomUUID();
    World world = world(worldId);
    DisplayEntityIndex index = new DisplayEntityIndex();
    Display display = display(UUID.randomUUID(), 11, new Location(world, 0.0, 64.0, 0.0));
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    try {
      Future<?> writer =
          executor.submit(
              () -> {
                await(start);
                for (int i = 0; i < 5_000; i++) {
                  index.register(display);
                  index.unregister(display.getUniqueId());
                }
              });
      Future<?> firstReader = executor.submit(() -> readRepeatedly(index, start));
      Future<?> secondReader = executor.submit(() -> readRepeatedly(index, start));

      start.countDown();
      assertDoesNotThrow(() -> writer.get());
      assertDoesNotThrow(() -> firstReader.get());
      assertDoesNotThrow(() -> secondReader.get());
    } finally {
      executor.shutdownNow();
    }
  }

  private static void readRepeatedly(DisplayEntityIndex index, CountDownLatch start) {
    await(start);
    for (int i = 0; i < 10_000; i++) {
      index.findByNetworkId(11);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static World world(UUID worldId) {
    return proxy(
        World.class,
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "getUID" -> worldId;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "World[" + worldId + "]";
            default -> defaultValue(method.getReturnType());
          };
        });
  }

  private static Display display(UUID entityId, int networkId, Location location) {
    return proxy(
        Display.class,
        (proxy, method, args) -> {
          return switch (method.getName()) {
            case "isValid" -> true;
            case "getUniqueId" -> entityId;
            case "getEntityId" -> networkId;
            case "getLocation" -> location;
            case "getScoreboardTags" -> Set.of(DisplayTags.DISPLAY_TAG);
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Display[" + entityId + "]";
            default -> defaultValue(method.getReturnType());
          };
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == Void.TYPE) {
      return null;
    }
    if (returnType == Boolean.TYPE) {
      return false;
    }
    if (returnType == Byte.TYPE) {
      return (byte) 0;
    }
    if (returnType == Short.TYPE) {
      return (short) 0;
    }
    if (returnType == Integer.TYPE) {
      return 0;
    }
    if (returnType == Long.TYPE) {
      return 0L;
    }
    if (returnType == Float.TYPE) {
      return 0.0f;
    }
    if (returnType == Double.TYPE) {
      return 0.0d;
    }
    if (returnType == Character.TYPE) {
      return '\0';
    }
    return null;
  }
}
