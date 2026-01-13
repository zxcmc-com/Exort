package com.zxcmc.exort.core.breaking;

public final class BreakSoundTracker {
  private long lastHitTick = Long.MIN_VALUE;
  private boolean breakPlayed;

  public BreakSoundEvent update(
      BreakSoundConfig config, long tick, double progress, double totalDamage) {
    if (config == null || !config.enabled() || totalDamage <= 0.0) return BreakSoundEvent.NONE;
    double remaining = 1.0 - progress;
    double ticksRemaining = remaining / totalDamage;
    boolean stopHits = ticksRemaining <= Math.max(0, config.preBreakTicks() + 6);
    boolean hit = false;
    boolean brk = false;
    if (!breakPlayed && !stopHits && tick - lastHitTick >= config.intervalTicks()) {
      lastHitTick = tick;
      hit = true;
    }
    if (!breakPlayed) {
      if (ticksRemaining <= config.preBreakTicks()) {
        breakPlayed = true;
        brk = true;
      }
    }
    if (hit && brk) return BreakSoundEvent.BOTH;
    if (brk) return BreakSoundEvent.BREAK;
    if (hit) return BreakSoundEvent.HIT;
    return BreakSoundEvent.NONE;
  }

  public boolean isBreakPlayed() {
    return breakPlayed;
  }

  public void markBreakPlayed() {
    breakPlayed = true;
  }

  public void markHitAt(long tick) {
    lastHitTick = tick;
  }
}
