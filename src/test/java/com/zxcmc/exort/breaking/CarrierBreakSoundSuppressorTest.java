package com.zxcmc.exort.breaking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CarrierBreakSoundSuppressorTest {
  @Test
  void stopsSoundKeyInBlockCategory() {
    AtomicReference<String> stoppedSound = new AtomicReference<>();
    AtomicReference<SoundCategory> stoppedCategory = new AtomicReference<>();
    Player player =
        proxy(
            Player.class,
            (proxy, method, args) -> {
              if ("stopSound".equals(method.getName())
                  && args != null
                  && args.length == 2
                  && args[0] instanceof String
                  && args[1] instanceof SoundCategory) {
                stoppedSound.set((String) args[0]);
                stoppedCategory.set((SoundCategory) args[1]);
              }
              return defaultValue(method.getReturnType());
            });

    CarrierBreakSoundSuppressor.stop(player, "minecraft:block.barrier.break");

    assertEquals("minecraft:block.barrier.break", stoppedSound.get());
    assertEquals(SoundCategory.BLOCKS, stoppedCategory.get());
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == Void.TYPE) return null;
    if (returnType == Boolean.TYPE) return false;
    if (returnType == Byte.TYPE) return (byte) 0;
    if (returnType == Short.TYPE) return (short) 0;
    if (returnType == Integer.TYPE) return 0;
    if (returnType == Long.TYPE) return 0L;
    if (returnType == Float.TYPE) return 0.0f;
    if (returnType == Double.TYPE) return 0.0d;
    if (returnType == Character.TYPE) return '\0';
    return null;
  }

  private static ClassLoader classLoader() {
    return CarrierBreakSoundSuppressorTest.class.getClassLoader();
  }

  @SuppressWarnings("unchecked")
  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(classLoader(), new Class<?>[] {type}, handler);
  }
}
