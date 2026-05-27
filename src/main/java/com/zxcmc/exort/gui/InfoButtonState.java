package com.zxcmc.exort.gui;

final class InfoButtonState {
  private static final int CONFIRM_STEPS = 4;

  private final long confirmTimeoutMs;
  private int confirmRemaining;
  private long confirmLastAt;
  private long blockedUntilMs;
  private boolean showStorageId;

  InfoButtonState(long confirmTimeoutMs) {
    this.confirmTimeoutMs = Math.max(0L, confirmTimeoutMs);
  }

  boolean showStorageId() {
    return showStorageId;
  }

  void revealStorageId() {
    showStorageId = true;
  }

  boolean isConfirming() {
    return isConfirming(System.currentTimeMillis());
  }

  int confirmRemaining() {
    return isConfirming() ? confirmRemaining : 0;
  }

  void startConfirm() {
    startConfirm(System.currentTimeMillis());
  }

  int decrementConfirm() {
    return decrementConfirm(System.currentTimeMillis());
  }

  void resetConfirm() {
    confirmRemaining = 0;
    confirmLastAt = 0L;
  }

  void markBlocked() {
    markBlocked(System.currentTimeMillis());
  }

  boolean isBlocked() {
    return isBlocked(System.currentTimeMillis());
  }

  boolean isConfirming(long nowMs) {
    if (confirmRemaining <= 0) return false;
    if (confirmTimeoutMs > 0 && nowMs - confirmLastAt > confirmTimeoutMs) {
      resetConfirm();
      return false;
    }
    return true;
  }

  int confirmRemaining(long nowMs) {
    return isConfirming(nowMs) ? confirmRemaining : 0;
  }

  void startConfirm(long nowMs) {
    confirmRemaining = CONFIRM_STEPS;
    confirmLastAt = nowMs;
    blockedUntilMs = 0L;
  }

  int decrementConfirm(long nowMs) {
    if (!isConfirming(nowMs)) return 0;
    confirmRemaining = Math.max(0, confirmRemaining - 1);
    confirmLastAt = nowMs;
    return confirmRemaining;
  }

  void markBlocked(long nowMs) {
    resetConfirm();
    if (confirmTimeoutMs > 0) {
      blockedUntilMs = nowMs + confirmTimeoutMs;
    }
  }

  boolean isBlocked(long nowMs) {
    return blockedUntilMs > nowMs;
  }
}
