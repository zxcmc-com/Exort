package com.zxcmc.exort.storage;

import com.zxcmc.exort.infra.db.DbItem;
import java.util.List;
import java.util.Map;

/** Valid rows plus structural corruption found without decoding Bukkit item data. */
public record StorageLoadResult(
    Map<String, DbItem> items, List<StorageCorruption> structuralCorruptions) {
  public StorageLoadResult {
    items = items == null ? Map.of() : Map.copyOf(items);
    structuralCorruptions =
        structuralCorruptions == null ? List.of() : List.copyOf(structuralCorruptions);
  }
}
