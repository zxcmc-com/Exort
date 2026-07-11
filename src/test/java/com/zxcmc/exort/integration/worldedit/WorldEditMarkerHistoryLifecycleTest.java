package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  }
}
