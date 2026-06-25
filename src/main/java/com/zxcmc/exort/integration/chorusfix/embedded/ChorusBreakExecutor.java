package com.zxcmc.exort.integration.chorusfix.embedded;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Effect;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public final class ChorusBreakExecutor implements ChorusBlockBreaker {
  private final EffectBreakOperation effectBreakOperation;
  private final FallbackBreakOperation fallbackBreakOperation;

  public ChorusBreakExecutor() {
    this(new ReflectiveEffectBreakOperation(), new BukkitFallbackBreakOperation());
  }

  ChorusBreakExecutor(
      EffectBreakOperation effectBreakOperation, FallbackBreakOperation fallbackBreakOperation) {
    this.effectBreakOperation = effectBreakOperation;
    this.fallbackBreakOperation = fallbackBreakOperation;
  }

  @Override
  public boolean breakNaturallyWithFeedback(Block block) {
    BlockData blockData = block.getBlockData();
    BreakAttempt attempt = effectBreakOperation.breakNaturallyWithEffect(block);
    if (attempt.used()) {
      return attempt.destroyed();
    }
    fallbackBreakOperation.playBreakEffect(block, blockData);
    return fallbackBreakOperation.breakNaturally(block);
  }

  interface EffectBreakOperation {
    BreakAttempt breakNaturallyWithEffect(Block block);
  }

  interface FallbackBreakOperation {
    void playBreakEffect(Block block, BlockData capturedBlockData);

    boolean breakNaturally(Block block);
  }

  record BreakAttempt(boolean used, boolean destroyed) {
    static BreakAttempt used(boolean destroyed) {
      return new BreakAttempt(true, destroyed);
    }

    static BreakAttempt unavailable() {
      return new BreakAttempt(false, false);
    }
  }

  static final class ReflectiveEffectBreakOperation implements EffectBreakOperation {
    private final Method breakNaturallyWithEffect;

    ReflectiveEffectBreakOperation() {
      this.breakNaturallyWithEffect = findBreakNaturallyWithEffect();
    }

    @Override
    public BreakAttempt breakNaturallyWithEffect(Block block) {
      if (breakNaturallyWithEffect == null) {
        return BreakAttempt.unavailable();
      }
      try {
        Object result = breakNaturallyWithEffect.invoke(block, true, false);
        return BreakAttempt.used(Boolean.TRUE.equals(result));
      } catch (IllegalAccessException
          | InvocationTargetException
          | LinkageError
          | SecurityException
          | IllegalArgumentException e) {
        return BreakAttempt.unavailable();
      }
    }

    private static Method findBreakNaturallyWithEffect() {
      try {
        return Block.class.getMethod("breakNaturally", boolean.class, boolean.class);
      } catch (NoSuchMethodException | SecurityException e) {
        return null;
      }
    }
  }

  static final class BukkitFallbackBreakOperation implements FallbackBreakOperation {
    @Override
    public void playBreakEffect(Block block, BlockData capturedBlockData) {
      block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, capturedBlockData);
    }

    @Override
    public boolean breakNaturally(Block block) {
      return block.breakNaturally();
    }
  }
}
