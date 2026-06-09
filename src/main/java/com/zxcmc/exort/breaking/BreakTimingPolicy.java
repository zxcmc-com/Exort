package com.zxcmc.exort.breaking;

final class BreakTimingPolicy {
  static final int HIT_SOUND_INTERVAL_TICKS = 4;

  private BreakTimingPolicy() {}

  static boolean canApplyDamage(long nowTick, long startedTick, double damage) {
    if (damage >= 1.0) {
      return true;
    }
    return nowTick - startedTick > 1L;
  }

  static boolean canPlayHitSound(long nowTick, long lastHitSoundTick) {
    return lastHitSoundTick == Long.MIN_VALUE
        || nowTick - lastHitSoundTick >= HIT_SOUND_INTERVAL_TICKS;
  }
}
