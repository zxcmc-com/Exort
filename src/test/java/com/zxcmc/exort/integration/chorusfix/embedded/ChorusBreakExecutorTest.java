package com.zxcmc.exort.integration.chorusfix.embedded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

final class ChorusBreakExecutorTest {
  @Test
  void reflectivePrimaryPathUsesPaperBreakEffectFlags() {
    RecordingBlock recording = new RecordingBlock();

    ChorusBreakExecutor.BreakAttempt result =
        new ChorusBreakExecutor.ReflectiveEffectBreakOperation()
            .breakNaturallyWithEffect(recording.block());

    assertTrue(result.used());
    assertTrue(result.destroyed());
    assertArrayEquals(new Object[] {true, false}, recording.breakNaturallyArgs);
  }

  @Test
  void executorFallsBackWithCapturedBlockDataWhenEffectBreakIsUnavailable() {
    BlockData blockData = proxy(BlockData.class, (proxy, method, args) -> defaultValue(method));
    RecordingFallback fallback = new RecordingFallback();
    ChorusBreakExecutor executor =
        new ChorusBreakExecutor(block -> ChorusBreakExecutor.BreakAttempt.unavailable(), fallback);
    Block block =
        proxy(
            Block.class,
            (proxy, method, args) -> {
              if (method.getName().equals("getBlockData")) {
                return blockData;
              }
              return defaultValue(method);
            });

    assertTrue(executor.breakNaturallyWithFeedback(block));
    assertSame(blockData, fallback.effectData);
    assertTrue(fallback.breakCalled);
  }

  @Test
  void bukkitFallbackSendsStepSoundWithCapturedBlockData() {
    BlockData blockData = proxy(BlockData.class, (proxy, method, args) -> defaultValue(method));
    RecordingWorld recordingWorld = new RecordingWorld();
    World world = recordingWorld.world();
    Location location = new Location(world, 1.0, 2.0, 3.0);
    Block block =
        proxy(
            Block.class,
            (proxy, method, args) -> {
              return switch (method.getName()) {
                case "getWorld" -> world;
                case "getLocation" -> location;
                default -> defaultValue(method);
              };
            });

    new ChorusBreakExecutor.BukkitFallbackBreakOperation().playBreakEffect(block, blockData);

    assertSame(location, recordingWorld.location);
    assertEquals(Effect.STEP_SOUND, recordingWorld.effect);
    assertSame(blockData, recordingWorld.data);
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

  private static final class RecordingFallback
      implements ChorusBreakExecutor.FallbackBreakOperation {
    private BlockData effectData;
    private boolean breakCalled;

    @Override
    public void playBreakEffect(Block block, BlockData capturedBlockData) {
      effectData = capturedBlockData;
    }

    @Override
    public boolean breakNaturally(Block block) {
      breakCalled = true;
      return true;
    }
  }

  private static final class RecordingWorld {
    private Location location;
    private Effect effect;
    private Object data;

    World world() {
      return proxy(
          World.class,
          (proxy, method, args) -> {
            if (method.getName().equals("playEffect") && args.length == 3) {
              location = (Location) args[0];
              effect = (Effect) args[1];
              data = args[2];
              return null;
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
