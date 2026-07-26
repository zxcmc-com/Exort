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
    boolean moveOperation,
    String storageCloneSourceId,
    boolean clearCarrierToAir,
    String replacementBlockState,
    boolean authoritativeFinalState) {
  MarkerUpdate(
      long operationId,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot,
      String removedStorageId,
      boolean storageCloneRequired,
      boolean moveOperation,
      String storageCloneSourceId,
      boolean clearCarrierToAir,
      boolean authoritativeFinalState) {
    this(
        operationId,
        worldId,
        x,
        y,
        z,
        snapshot,
        removedStorageId,
        storageCloneRequired,
        moveOperation,
        storageCloneSourceId,
        clearCarrierToAir,
        null,
        authoritativeFinalState);
  }

  MarkerUpdate(
      long operationId,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot,
      String removedStorageId,
      boolean storageCloneRequired,
      boolean moveOperation,
      String storageCloneSourceId,
      boolean clearCarrierToAir) {
    this(
        operationId,
        worldId,
        x,
        y,
        z,
        snapshot,
        removedStorageId,
        storageCloneRequired,
        moveOperation,
        storageCloneSourceId,
        clearCarrierToAir,
        null,
        false);
  }

  MarkerUpdate(
      long operationId,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot,
      String removedStorageId,
      boolean storageCloneRequired,
      boolean moveOperation,
      String storageCloneSourceId) {
    this(
        operationId,
        worldId,
        x,
        y,
        z,
        snapshot,
        removedStorageId,
        storageCloneRequired,
        moveOperation,
        storageCloneSourceId,
        false);
  }

  MarkerUpdate(
      long operationId,
      UUID worldId,
      int x,
      int y,
      int z,
      MarkerSnapshot snapshot,
      String removedStorageId,
      boolean storageCloneRequired,
      boolean moveOperation) {
    this(
        operationId,
        worldId,
        x,
        y,
        z,
        snapshot,
        removedStorageId,
        storageCloneRequired,
        moveOperation,
        null);
  }

  int chunkX() {
    return x >> 4;
  }

  int chunkZ() {
    return z >> 4;
  }

  MarkerUpdate asMove() {
    return moveOperation
        ? this
        : new MarkerUpdate(
            operationId,
            worldId,
            x,
            y,
            z,
            snapshot,
            removedStorageId,
            storageCloneRequired,
            true,
            storageCloneSourceId,
            clearCarrierToAir,
            replacementBlockState,
            authoritativeFinalState);
  }

  MarkerUpdate asAuthoritativeFinalState() {
    return authoritativeFinalState
        ? this
        : new MarkerUpdate(
            operationId,
            worldId,
            x,
            y,
            z,
            snapshot,
            removedStorageId,
            storageCloneRequired,
            moveOperation,
            storageCloneSourceId,
            clearCarrierToAir,
            replacementBlockState,
            true);
  }

  MarkerUpdate asAuthoritativeFinalStateIf(boolean authoritative) {
    return authoritative ? asAuthoritativeFinalState() : this;
  }

  static boolean hasHigherPriority(
      long candidateOperationId,
      boolean candidateAuthoritative,
      long currentOperationId,
      boolean currentAuthoritative) {
    return candidateOperationId > currentOperationId
        || candidateOperationId == currentOperationId
            && candidateAuthoritative
            && !currentAuthoritative;
  }
}
