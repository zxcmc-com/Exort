package com.zxcmc.exort.integration.worldedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldEditDeferredUpdatesTest {
  @Test
  void groupsUpdatesByChunkUntilRemoved() {
    WorldEditDeferredUpdates deferred = new WorldEditDeferredUpdates();
    ChunkKey key = new ChunkKey(UUID.randomUUID(), 2, -3);

    int count = deferred.defer(key, List.of(updateAt(32, 64, -48), updateAt(33, 64, -47)));

    assertEquals(2, count);
    assertEquals(1, deferred.chunkCount());
    assertEquals(2, deferred.updateCount());

    ChunkUpdateBatch batch = deferred.remove(key);

    assertEquals(2, batch.updates.size());
    assertEquals(0, deferred.chunkCount());
    assertEquals(0, deferred.updateCount());
    assertNull(deferred.remove(key));
  }

  private static PendingUpdate updateAt(int x, int y, int z) {
    return new PendingUpdate(
        new MarkerUpdate(1L, UUID.randomUUID(), x, y, z, null, null, true, false));
  }
}
