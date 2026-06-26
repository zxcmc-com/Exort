package com.zxcmc.exort.integration.worldedit;

final class PendingUpdate {
  MarkerUpdate update;
  int attempts;
  long nextTick;

  PendingUpdate(MarkerUpdate update) {
    this.update = update;
  }
}
