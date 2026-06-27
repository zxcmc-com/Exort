package com.zxcmc.exort.display.culling;

final class AdaptiveViewRangeState {
  private final DisplayCullingConfig.AdaptiveViewRangeConfig config;
  private int levelIndex;
  private int denseStreak;
  private int stableStreak;
  private long nextStepDownTick;
  private long nextStepUpTick;

  AdaptiveViewRangeState(DisplayCullingConfig.AdaptiveViewRangeConfig config) {
    this.config = config;
  }

  boolean update(int nearbyDisplays, long tickSequence, int intervalTicks) {
    if (!config.enabled() || config.maxLevel() <= 0) {
      return false;
    }
    if (nearbyDisplays >= config.thresholdForLevel(levelIndex)) {
      denseStreak++;
    } else {
      denseStreak = 0;
    }

    boolean stable =
        levelIndex > 0 && nearbyDisplays <= config.recoverThresholdForLevel(levelIndex);
    stableStreak = stable ? stableStreak + 1 : 0;

    if (denseStreak >= config.denseIntervalsToStepDown() && canStepDown(tickSequence)) {
      levelIndex = Math.min(levelIndex + 1, config.maxLevel());
      denseStreak = 0;
      stableStreak = 0;
      nextStepDownTick = tickSequence + intervals(config.stepDownCooldownTicks(), intervalTicks);
      nextStepUpTick = tickSequence + intervals(config.stepUpCooldownTicks(), intervalTicks);
      return true;
    }
    if (stableStreak >= config.stableIntervalsToStepUp() && canStepUp(tickSequence)) {
      levelIndex = Math.max(0, levelIndex - 1);
      stableStreak = 0;
      nextStepUpTick = tickSequence + intervals(config.stepUpCooldownTicks(), intervalTicks);
      return true;
    }
    return false;
  }

  int levelIndex() {
    return levelIndex;
  }

  private boolean canStepDown(long tickSequence) {
    return levelIndex < config.maxLevel() && tickSequence >= nextStepDownTick;
  }

  private boolean canStepUp(long tickSequence) {
    return levelIndex > 0 && tickSequence >= nextStepUpTick;
  }

  private static long intervals(int ticks, int intervalTicks) {
    return Math.max(1L, (ticks + Math.max(1, intervalTicks) - 1L) / Math.max(1, intervalTicks));
  }
}
