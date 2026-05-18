package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.core.db.DbItem;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageCacheTest {
  @Test
  void loadFromDbSkipsRowsThatAreInvalidBeforeItemDeserialization() {
    byte[] blob = new byte[] {42};
    byte[] oversized = new byte[1_048_577];

    StorageCache cache = new StorageCache("storage", null, null);
    cache.loadFromDb(
        Map.of(
            "negative",
            new DbItem("negative", blob, -2),
            "empty",
            new DbItem("empty", new byte[0], 1),
            "oversized",
            new DbItem("oversized", oversized, 1)));

    assertTrue(cache.isLoaded());
    assertEquals(0, cache.totalAmount());
    assertEquals(0, cache.effectiveTotal());
  }
}
