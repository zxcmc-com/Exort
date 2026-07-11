package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

final class ChorusBreakExecutorTest {
  @Test
  void usesPaperBreakEffectFlags() {
    RecordingBlock recording = new RecordingBlock();

    boolean result = new ChorusBreakExecutor().breakNaturallyWithFeedback(recording.block());

    assertTrue(result);
    org.junit.jupiter.api.Assertions.assertArrayEquals(
        new Object[] {true, false}, recording.breakNaturallyArgs);
  }

  private static final class RecordingBlock {
    private Object[] breakNaturallyArgs;

    Block block() {
      return proxy(
          Block.class,
          (proxy, method, args) -> {
            if (method.getName().equals("breakNaturally")
                && method.getParameterTypes().length == 2
                && method.getParameterTypes()[0] == boolean.class
                && method.getParameterTypes()[1] == boolean.class) {
              breakNaturallyArgs = args.clone();
              return true;
            }
            return defaultValue(method);
          });
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private static Object defaultValue(java.lang.reflect.Method method) {
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
