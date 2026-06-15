package com.zxcmc.exort.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MonitorDisplayManagerLifecycleTest {
  @Test
  void stopForReloadClearsRuntimeStateWithoutRemovingWorldDisplays() throws Exception {
    TestMonitorDisplayManager manager = new TestMonitorDisplayManager();
    seedRuntimeState(manager);

    manager.stopForReload();

    assertEquals(0, manager.removeAllCalls);
    assertRuntimeStateCleared(manager);
  }

  @Test
  void stopKeepsFinalCleanupDestructive() throws Exception {
    TestMonitorDisplayManager manager = new TestMonitorDisplayManager();
    seedRuntimeState(manager);

    manager.stop();

    assertEquals(1, manager.removeAllCalls);
    assertRuntimeStateCleared(manager);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void seedRuntimeState(MonitorDisplayManager manager) throws Exception {
    ((Map) field(manager, "monitors")).put("monitor", "state");
    ((Map) field(manager, "monitorsByStorage")).put("storage", Set.of("monitor"));
    ((Set) field(manager, "queuedMonitorRefreshes")).add("monitor");
    ((Map) field(manager, "loadAttempts")).put("storage", 1L);
    Field sanityCursor = manager.getClass().getSuperclass().getDeclaredField("sanityCursor");
    sanityCursor.setAccessible(true);
    sanityCursor.setInt(manager, 7);
  }

  private static void assertRuntimeStateCleared(MonitorDisplayManager manager) throws Exception {
    assertEquals(0, ((Map<?, ?>) field(manager, "monitors")).size());
    assertEquals(0, ((Map<?, ?>) field(manager, "monitorsByStorage")).size());
    assertEquals(0, ((Set<?>) field(manager, "queuedMonitorRefreshes")).size());
    assertEquals(0, ((Map<?, ?>) field(manager, "loadAttempts")).size());

    Field sanityCursor = manager.getClass().getSuperclass().getDeclaredField("sanityCursor");
    sanityCursor.setAccessible(true);
    assertEquals(0, sanityCursor.getInt(manager));
  }

  private static Object field(MonitorDisplayManager manager, String name) throws Exception {
    Field field = manager.getClass().getSuperclass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(manager);
  }

  private static final class TestMonitorDisplayManager extends MonitorDisplayManager {
    private int removeAllCalls;

    private TestMonitorDisplayManager() {
      super(
          null,
          null,
          null,
          Material.BARRIER,
          "exort:terminal/monitor",
          "exort:terminal/monitor_disabled",
          Material.BARRIER,
          1.0,
          0.0,
          0.0,
          0.0,
          null,
          null,
          0,
          0,
          0,
          Material.BARRIER,
          Material.BARRIER,
          Material.BARRIER,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          0);
    }

    @Override
    protected void removeAllManagedDisplays() {
      removeAllCalls++;
    }
  }
}
