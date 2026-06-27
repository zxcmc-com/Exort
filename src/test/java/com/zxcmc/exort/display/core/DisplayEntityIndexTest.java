package com.zxcmc.exort.display.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.junit.jupiter.api.Test;

class DisplayEntityIndexTest {
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
