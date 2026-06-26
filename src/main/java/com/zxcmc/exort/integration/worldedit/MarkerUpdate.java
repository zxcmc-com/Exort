package com.zxcmc.exort.integration.worldedit;

import java.util.UUID;

record MarkerUpdate(
    long operationId,
    UUID worldId,
    int x,
    int y,
    int z,
    MarkerSnapshot snapshot,
    String removedStorageId,
    boolean storageCloneRequired,
    boolean moveOperation) {
  int chunkX() {
    return x >> 4;
  }

  int chunkZ() {
    return z >> 4;
  }
}
