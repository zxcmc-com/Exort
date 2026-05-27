package com.zxcmc.exort.breaking;

public final class BreakSoundTracker {
  private boolean breakPlayed;

  public boolean isBreakPlayed() {
    return breakPlayed;
  }

  public void markBreakPlayed() {
    breakPlayed = true;
  }
}
