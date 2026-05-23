package com.zxcmc.exort.core.breaking;

public final class BreakAnimationStages {
  private BreakAnimationStages() {}

  public static int stageForProgress(double progress) {
    if (!Double.isFinite(progress)) {
      return 0;
    }
    int stage = (int) Math.floor(progress * 10.0);
    return Math.max(0, Math.min(9, stage));
  }
}
