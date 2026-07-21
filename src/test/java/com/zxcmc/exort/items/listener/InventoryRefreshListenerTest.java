package com.zxcmc.exort.items.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class InventoryRefreshListenerTest {
  @Test
  void containerDeduplicationIsBoundedAndAccessOrdered() {
    InventoryRefreshListener listener = listener(3);

    assertTrue(listener.shouldRefresh("a", 1));
    assertTrue(listener.shouldRefresh("b", 1));
    assertTrue(listener.shouldRefresh("c", 1));
    assertFalse(listener.shouldRefresh("a", 1));
    assertTrue(listener.shouldRefresh("d", 1));

    assertEquals(3, listener.trackedContainerCount());
    assertTrue(listener.shouldRefresh("b", 1), "least-recently-used entry must be evicted");
    assertFalse(listener.shouldRefresh("a", 1), "recent access must keep the entry retained");
  }

  @Test
  void epochAndCloseReleaseRetainedKeys() {
    InventoryRefreshListener listener = listener(3);

    assertTrue(listener.shouldRefresh("a", 1));
    assertFalse(listener.shouldRefresh("a", 1));
    assertTrue(listener.shouldRefresh("a", 2));
    assertEquals(1, listener.trackedContainerCount());

    listener.close();

    assertEquals(0, listener.trackedContainerCount());
    assertTrue(listener.shouldRefresh("a", 2));
  }

  private static InventoryRefreshListener listener(int maxEntries) {
    Plugin plugin =
        (Plugin)
            Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    return new InventoryRefreshListener(plugin, () -> 1, player -> {}, inventory -> {}, maxEntries);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0.0F;
    if (type == double.class) return 0.0D;
    return null;
  }
}
