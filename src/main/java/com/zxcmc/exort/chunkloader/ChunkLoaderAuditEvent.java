package com.zxcmc.exort.chunkloader;

public enum ChunkLoaderAuditEvent {
  ISSUE,
  CRAFT,
  INVENTORY_MOVE,
  DROP,
  PICKUP,
  PLACE,
  BREAK,
  ENABLE,
  DISABLE,
  TICKET_ACQUIRE,
  TICKET_RELEASE,
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
      case ENABLE -> "enable";
      case DISABLE -> "disable";
      case TICKET_ACQUIRE -> "ticketAcquire";
      case TICKET_RELEASE -> "ticketRelease";
      case DESTROY -> "destroy";
      case CLEANUP -> "cleanup";
    };
  }
}
