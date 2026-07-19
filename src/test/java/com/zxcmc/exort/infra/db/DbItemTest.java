package com.zxcmc.exort.infra.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DbItemTest {
  @Test
  void copiesBlobOnConstructionAndAccess() {
    byte[] source = {1, 2, 3};
    DbItem item = new DbItem("key", source, 4);

    source[0] = 9;
    byte[] returned = item.blob();
    returned[1] = 8;

    assertArrayEquals(new byte[] {1, 2, 3}, item.blob());
    assertEquals(new DbItem("key", new byte[] {1, 2, 3}, 4), item);
    assertNotEquals(new DbItem("key", new byte[] {1, 2, 4}, 4), item);
  }

  @Test
  void snapshotsQueuedCollectionsAndRows() {
    byte[] blob = {4, 5};
    DbItem item = new DbItem("key", blob, 6);
    List<DbItem> source = new ArrayList<>();
    source.add(item);

    List<DbItem> snapshot = StorageRepository.snapshotItems(source);
    blob[0] = 9;
    source.clear();
    item.blob()[0] = 8;

    assertEquals(List.of(new DbItem("key", new byte[] {4, 5}, 6)), snapshot);
    assertThrows(UnsupportedOperationException.class, () -> snapshot.add(item));
  }

  @Test
  void deltaWriteSnapshotsCollections() {
    List<DbItem> upserts = new ArrayList<>(List.of(new DbItem("key", new byte[] {1}, 2)));
    List<String> removals = new ArrayList<>(List.of("old"));
    Database.DeltaWrite write = new Database.DeltaWrite("storage", upserts, removals);

    upserts.clear();
    removals.clear();

    assertEquals(1, write.upserts().size());
    assertEquals(List.of("old"), write.removals());
    assertThrows(UnsupportedOperationException.class, () -> write.upserts().clear());
    assertThrows(UnsupportedOperationException.class, () -> write.removals().clear());
  }
}
