package com.zxcmc.exort.core.breaking;

public final class BreakSoundTracker {
  private boolean breakPlayed;

  public boolean isBreakPlayed() {
    return breakPlayed;
  }

  public void markBreakPlayed() {
    breakPlayed = true;
  }
}
