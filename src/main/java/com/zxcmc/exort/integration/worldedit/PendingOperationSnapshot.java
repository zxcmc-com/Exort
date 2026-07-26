package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record PendingOperationSnapshot(
    UUID worldId,
    Map<Long, MarkerSnapshot> markers,
    Set<ChunkKey> chunks,
    Set<ChunkKey> coveredChunks,
    List<WorldEditBounds> coverageBounds,
    WorldEditBounds bounds,
    String reason,
    boolean complete) {
  PendingOperationSnapshot {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
    chunks = chunks == null ? Set.of() : Set.copyOf(chunks);
    coveredChunks = coveredChunks == null ? Set.of() : Set.copyOf(coveredChunks);
    coverageBounds = coverageBounds == null ? List.of() : List.copyOf(coverageBounds);
    reason = reason == null ? "worldedit_operation" : reason;
  }

  PendingOperationSnapshot(
      UUID worldId,
      Map<Long, MarkerSnapshot> markers,
      Set<ChunkKey> chunks,
      WorldEditBounds bounds,
      String reason) {
    this(
        worldId,
        markers,
        chunks,
        chunks,
        bounds == null ? List.of() : List.of(bounds),
        bounds,
        reason,
        true);
  }

  boolean isEmpty() {
    return markers.isEmpty() && chunks.isEmpty();
  }

  boolean appliesTo(UUID targetWorldId) {
    return worldId != null && worldId.equals(targetWorldId);
  }

  MarkerSnapshot get(UUID targetWorldId, BlockVector3 position) {
    if (!appliesTo(targetWorldId) || position == null) {
      return null;
    }
    return markers.get(WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  boolean covers(UUID targetWorldId, BlockVector3 position) {
    if (!appliesTo(targetWorldId) || position == null || !coveredByBounds(position)) {
      return false;
    }
    return coveredChunks.contains(
        new ChunkKey(targetWorldId, position.x() >> 4, position.z() >> 4));
  }

  boolean safelyPrepared(UUID targetWorldId, BlockVector3 position) {
    return covers(targetWorldId, position);
  }

  boolean hasMarkerIn(UUID targetWorldId, Region region) {
    if (!appliesTo(targetWorldId) || region == null || markers.isEmpty()) {
      return false;
    }
    for (long key : markers.keySet()) {
      if (region.contains(
          BlockVector3.at(
              WorldEditMarkerMath.blockX(key),
              WorldEditMarkerMath.blockY(key),
              WorldEditMarkerMath.blockZ(key)))) {
        return true;
      }
    }
    return false;
  }

  private boolean coveredByBounds(BlockVector3 position) {
    if (coverageBounds.isEmpty()) {
      return bounds != null && bounds.contains(position);
    }
    for (WorldEditBounds coverage : coverageBounds) {
      if (coverage != null && coverage.contains(position)) {
        return true;
      }
    }
    return false;
  }
}
