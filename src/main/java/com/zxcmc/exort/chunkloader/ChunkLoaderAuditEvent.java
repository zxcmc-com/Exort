package com.zxcmc.exort.chunkloader;

public enum ChunkLoaderAuditEvent {
  ISSUE,
  CRAFT,
  INVENTORY_MOVE,
  DROP,
  PICKUP,
  PLACE,
  BREAK,
  DESTROY,
  CLEANUP;

  String configKey() {
    return switch (this) {
      case ISSUE -> "issue";
      case CRAFT -> "craft";
      case INVENTORY_MOVE -> "inventoryMove";
      case DROP -> "drop";
      case PICKUP -> "pickup";
      case PLACE -> "place";
      case BREAK -> "break";
      case DESTROY -> "destroy";
      case CLEANUP -> "cleanup";
    };
  }
}
