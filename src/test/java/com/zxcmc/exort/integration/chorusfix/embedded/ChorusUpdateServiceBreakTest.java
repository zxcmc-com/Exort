package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

final class ChorusUpdateServiceBreakTest {
  @Test
  void collapsePathUsesInjectedBreakExecutor() throws Exception {
    AtomicBoolean executorCalled = new AtomicBoolean();
    ChorusUpdateService service =
        new ChorusUpdateService(
            plugin(),
            disabledConfig(),
            detector(),
            EmbeddedChorusfixRuntimeState.known(true),
            block -> {
              executorCalled.set(true);
              return true;
            });

    Method breakAndCascade =
        ChorusUpdateService.class.getDeclaredMethod(
            "breakAndCascade", Block.class, int.class, ChorusUpdateService.ProcessingMode.class);
    breakAndCascade.setAccessible(true);
    breakAndCascade.invoke(
        service, blockThatFailsOnDirectBreak(), 0, ChorusUpdateService.ProcessingMode.NORMAL);

    assertTrue(executorCalled.get());
    assertEquals(1, service.status().brokenTotal());
  }

  private static EmbeddedChorusfixConfig disabledConfig() {
    return new EmbeddedChorusfixConfig(false, true, 0, 0, 0, Set.of(), false);
  }

  private static ExortChorusCarrierDetector detector() {
    return new ExortChorusCarrierDetector() {
      @Override
      public boolean isCustom(Block block, ChorusFaceMask mask) {
        return false;
      }
    };
  }

  private static Plugin plugin() {
    return proxy(
        Plugin.class,
        (proxy, method, args) -> {
          if (method.getName().equals("getLogger")) {
            return Logger.getLogger("chorusfix-test");
          }
          return defaultValue(method);
        });
  }

  private static Block blockThatFailsOnDirectBreak() {
    return proxy(
        Block.class,
        (proxy, method, args) -> {
          if (method.getName().equals("breakNaturally")) {
            throw new AssertionError("collapse path must use ChorusBlockBreaker");
          }
          return defaultValue(method);
        });
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultValue(Method method) {
    if (method.getDeclaringClass() == Object.class) {
      return switch (method.getName()) {
        case "toString" -> "proxy";
        case "hashCode" -> 0;
        case "equals" -> false;
        default -> null;
      };
    }
    Class<?> returnType = method.getReturnType();
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == double.class) {
      return 0.0D;
    }
    if (returnType == float.class) {
      return 0.0F;
    }
    if (returnType == void.class) {
      return null;
    }
    return null;
  }
}
