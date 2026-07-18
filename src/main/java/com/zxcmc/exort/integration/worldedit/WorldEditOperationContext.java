package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Objects;
import java.util.UUID;

/** Immutable command preparation shared by every extent stage for one WorldEdit operation. */
record WorldEditOperationContext(
    UUID actorId,
    long generation,
    String commandKind,
    String commandSignature,
    UUID worldId,
    BlockVector3 origin,
    WorldEditBounds selectionBounds,
    WorldEditBounds affectedBounds,
    PendingOperationSnapshot markerSnapshot,
    Object clipboardBaseline,
    long operationId,
    long timestampMs) {
  WorldEditOperationContext {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(worldId, "worldId");
    commandKind = commandKind == null ? "" : commandKind;
    commandSignature = commandSignature == null ? "" : commandSignature;
  }

  WorldEditOperationContext withGeneration(long nextGeneration) {
    return new WorldEditOperationContext(
        actorId,
        nextGeneration,
        commandKind,
        commandSignature,
        worldId,
        origin,
        selectionBounds,
        affectedBounds,
        markerSnapshot,
        clipboardBaseline,
        operationId,
        timestampMs);
  }

  boolean appliesTo(UUID targetWorldId) {
    return worldId.equals(targetWorldId);
  }
}
