package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingBufferFlushPolicyTest {
  @Test
  void readOnlyCloseDoesNotFlushSharedOutputBuffer() {
    CraftingState.Buffer buffer = buffer("minecraft:stone");

    boolean shouldFlush = CraftingBufferFlushPolicy.shouldFlushOnClose(true, buffer);

    assertFalse(shouldFlush);
  }

  @Test
  void writerCloseFlushesOnlyWhenBufferExists() {
    CraftingState.Buffer buffer = buffer("minecraft:stone");

    assertTrue(CraftingBufferFlushPolicy.shouldFlushOnClose(false, buffer));
    assertFalse(CraftingBufferFlushPolicy.shouldFlushOnClose(false, null));
  }

  @Test
  void readOnlyRenderMismatchDoesNotFlushSharedOutputBuffer() {
    CraftingState.Buffer buffer = buffer("minecraft:stone");

    boolean shouldFlush =
        CraftingBufferFlushPolicy.shouldFlushRenderMismatch(true, buffer, "minecraft:dirt");

    assertFalse(shouldFlush);
  }

  @Test
  void writerRenderFlushesOnlyMismatchedBuffer() {
    CraftingState.Buffer buffer = buffer("minecraft:stone");

    assertFalse(
        CraftingBufferFlushPolicy.shouldFlushRenderMismatch(false, buffer, "minecraft:stone"));
    assertTrue(
        CraftingBufferFlushPolicy.shouldFlushRenderMismatch(false, buffer, "minecraft:dirt"));
    assertTrue(CraftingBufferFlushPolicy.shouldFlushRenderMismatch(false, buffer, null));
    assertFalse(CraftingBufferFlushPolicy.shouldFlushRenderMismatch(false, null, null));
  }

  private static CraftingState.Buffer buffer(String key) {
    return new CraftingState.Buffer(key, null, 4);
  }
}
