package com.zxcmc.exort.gui;

final class CraftingBufferFlushPolicy {
  private CraftingBufferFlushPolicy() {}

  static boolean shouldFlushOnClose(boolean readOnly, CraftingState.Buffer buffer) {
    return !readOnly && buffer != null;
  }

  static boolean shouldFlushRenderMismatch(
      boolean readOnly, CraftingState.Buffer buffer, String currentResultKey) {
    return !readOnly
        && buffer != null
        && (currentResultKey == null || !buffer.key().equals(currentResultKey));
  }
}
