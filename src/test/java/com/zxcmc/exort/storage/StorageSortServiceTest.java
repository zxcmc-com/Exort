package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.storage.sort.SortMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageSortServiceTest {
  @Test
  void storedModeWinsWithoutPersistingDefault() {
    List<String> persisted = new ArrayList<>();
    StorageSortService service =
        new StorageSortService(() -> SortMode.CATEGORY.name(), (id, mode) -> persisted.add(mode));

    SortMode mode = service.resolveAndPersistDefault("storage-a", Optional.of("id"));

    assertEquals(SortMode.ID, mode);
    assertTrue(persisted.isEmpty());
  }

  @Test
  void missingModeUsesCurrentDefaultAndPersistsIt() {
    List<String> persisted = new ArrayList<>();
    StorageSortService service =
        new StorageSortService(
            () -> SortMode.NAME.name(), (id, mode) -> persisted.add(id + ":" + mode));

    SortMode mode = service.resolveAndPersistDefault("storage-a", Optional.empty());

    assertEquals(SortMode.NAME, mode);
    assertEquals(List.of("storage-a:NAME"), persisted);
  }

  @Test
  void invalidStoredModeFallsBackWithoutReplacingStoredValue() {
    List<String> persisted = new ArrayList<>();
    StorageSortService service =
        new StorageSortService(() -> SortMode.CATEGORY.name(), (id, mode) -> persisted.add(mode));

    SortMode mode = service.resolveAndPersistDefault("storage-a", Optional.of("bad"));

    assertEquals(SortMode.AMOUNT, mode);
    assertTrue(persisted.isEmpty());
  }
}
