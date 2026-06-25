package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;

final class ChorusUpdateListenerTest {
  @Test
  void blockPlaceHandlerUsesMonitorWithoutSynchronousCancellation() throws Exception {
    Method method =
        ChorusUpdateListener.class.getDeclaredMethod("onBlockPlace", BlockPlaceEvent.class);
    EventHandler handler = method.getAnnotation(EventHandler.class);

    assertEquals(EventPriority.MONITOR, handler.priority());
    assertTrue(handler.ignoreCancelled());
    assertFalse(method.getReturnType() == boolean.class);
  }
}
