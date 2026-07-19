package com.zxcmc.exort.storage;

import com.zxcmc.exort.storage.sort.SortMode;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class StorageSortService {
  private final Supplier<String> defaultSortModeName;
  private final BiConsumer<String, String> persistSortMode;

  public StorageSortService(
      Supplier<String> defaultSortModeName, BiConsumer<String, String> persistSortMode) {
    this.defaultSortModeName = Objects.requireNonNull(defaultSortModeName, "defaultSortModeName");
    this.persistSortMode = Objects.requireNonNull(persistSortMode, "persistSortMode");
  }

  public SortMode resolveAndPersistDefault(String storageId, Optional<String> stored) {
    SortMode defaultMode = SortMode.fromString(defaultSortModeName.get());
    SortMode mode = stored.isEmpty() ? defaultMode : SortMode.fromString(stored.orElse(null));
    if (stored.isEmpty()) {
      persistSortMode.accept(storageId, mode.name());
    }
    return mode;
  }
}
