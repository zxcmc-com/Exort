package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorldEditMarkerHistoryLifecycleTest {
  @Test
  void clearActorRemovesOnlyThatActorsHistory() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID worldId = new UUID(0L, 1L);
    UUID firstActor = new UUID(0L, 2L);
    UUID secondActor = new UUID(0L, 3L);
    MarkerSnapshot wire = new MarkerSnapshot(null, null, null, null, null, true, false);
    history.remember(firstActor, null, worldId, 1, 64, 1, wire);
    history.remember(secondActor, null, worldId, 2, 64, 2, wire);

    history.clearActor(firstActor);

    assertNull(history.peek(firstActor, HistoryAction.UNDO, worldId, 1, 64, 1));
    assertNotNull(history.peek(secondActor, HistoryAction.UNDO, worldId, 2, 64, 2));
  }

  @Test
  void expirySweepRemovesUniqueMarkerKeysAndFramesWithoutRevisitingThem() {
    WorldEditMarkerHistory history = new WorldEditMarkerHistory();
    UUID actorId = new UUID(0L, 10L);
    UUID worldId = new UUID(0L, 11L);
    MarkerSnapshot wire = new MarkerSnapshot(null, null, null, null, null, true, false);
    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);
    history.remember(actorId, null, worldId, 1, 64, 1, wire, frame);
    history.remember(actorId, null, worldId, 2, 64, 2, wire, frame);

    assertTrue(history.retainedKeyCount() > 0);
    history.pruneExpired(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(11));

    assertEquals(0, history.retainedKeyCount());
    assertEquals(0, history.retainedStateCount());
    assertEquals(0, history.retainedWeightBytes());
  }

  @Test
  void oversizedOperationStopsRetainingBeforePerFrameStateCapIsExceeded() {
    WorldEditMarkerHistory history =
        new WorldEditMarkerHistory(
            new WorldEditMarkerHistory.Limits(2, 10_000, 20, 100_000, 10, 10, 10));
    UUID actorId = new UUID(0L, 20L);
    UUID worldId = new UUID(0L, 21L);
    MarkerSnapshot wire = new MarkerSnapshot(null, null, null, null, null, true, false);
    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);

    history.remember(actorId, null, worldId, 1, 64, 1, wire, frame);
    history.remember(actorId, null, worldId, 2, 64, 2, wire, frame);
    long retainedBeforeOverflow = history.retainedStateCount();
    history.remember(actorId, null, worldId, 3, 64, 3, wire, frame);

    assertTrue(frame.overflowed());
    assertNull(history.peek(frame, HistoryAction.UNDO, 3, 64, 3));
    assertEquals(retainedBeforeOverflow, history.retainedStateCount());
    assertTrue(history.retainedStateCount() <= 4L);
  }

  @Test
  void oversizedSnapshotStopsBeforePerFrameWeightCapIsExceeded() {
    WorldEditMarkerHistory history =
        new WorldEditMarkerHistory(
            new WorldEditMarkerHistory.Limits(10, 700, 20, 100_000, 10, 10, 10));
    UUID actorId = new UUID(0L, 30L);
    UUID worldId = new UUID(0L, 31L);
    MarkerSnapshot transmitter =
        new MarkerSnapshot(
            null,
            null,
            null,
            null,
            null,
            true,
            new TransmitterData("disabled", new byte[300]),
            null,
            false,
            false);
    WorldEditMarkerHistory.Frame frame = history.beginNormalOperation(actorId, worldId, 1L);

    history.remember(actorId, null, worldId, 1, 64, 1, transmitter, frame);

    assertTrue(frame.overflowed());
    assertEquals(0L, history.retainedStateCount());
    assertEquals(0L, history.retainedWeightBytes());
  }

  @Test
  void frameAndFallbackStacksStayBoundedUnderActorAndCoordinateStress() {
    WorldEditMarkerHistory history =
        new WorldEditMarkerHistory(
            new WorldEditMarkerHistory.Limits(10, 10_000, 20, 100_000, 3, 2, 2));
    UUID worldId = new UUID(0L, 40L);
    MarkerSnapshot wire = new MarkerSnapshot(null, null, null, null, null, true, false);

    for (int i = 0; i < 3; i++) {
      WorldEditMarkerHistory.Frame frame =
          history.beginNormalOperation(new UUID(0L, 41L + i), worldId, i);
      assertFalse(frame.overflowed());
    }
    assertTrue(history.beginNormalOperation(new UUID(0L, 50L), worldId, 4L).overflowed());

    UUID actorId = new UUID(0L, 60L);
    history.clear();
    for (int i = 0; i < 5; i++) {
      history.remember(actorId, null, worldId, 1, 64, 1, wire);
    }

    assertEquals(2L, history.retainedStateCount());
    assertNotNull(history.consume(actorId, HistoryAction.UNDO, worldId, 1, 64, 1));
    assertNotNull(history.consume(actorId, HistoryAction.UNDO, worldId, 1, 64, 1));
    assertNull(history.consume(actorId, HistoryAction.UNDO, worldId, 1, 64, 1));
    assertEquals(0L, history.retainedStateCount());
  }
}
