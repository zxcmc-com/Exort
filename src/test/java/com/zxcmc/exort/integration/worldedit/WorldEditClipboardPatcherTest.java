package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditClipboardPatcherTest {
  @Test
  void newerRequestAndExplicitClearInvalidateOlderPatchGeneration() {
    WorldEditClipboardPatcher.GenerationTracker generations =
        new WorldEditClipboardPatcher.GenerationTracker();
    UUID actorId = new UUID(0L, 1L);

    long first = generations.next(actorId);
    assertTrue(generations.isCurrent(actorId, first));

    long second = generations.next(actorId);
    assertFalse(generations.isCurrent(actorId, first));
    assertTrue(generations.isCurrent(actorId, second));

    generations.clear(actorId);
    assertFalse(generations.isCurrent(actorId, second));
  }

  @Test
  void completingOldActorGenerationDoesNotClearAnotherActorsPatch() {
    WorldEditClipboardPatcher.GenerationTracker generations =
        new WorldEditClipboardPatcher.GenerationTracker();
    UUID firstActor = new UUID(0L, 1L);
    UUID secondActor = new UUID(0L, 2L);
    long first = generations.next(firstActor);
    long second = generations.next(secondActor);

    generations.complete(firstActor, first);

    assertFalse(generations.isCurrent(firstActor, first));
    assertTrue(generations.isCurrent(secondActor, second));
  }
}
