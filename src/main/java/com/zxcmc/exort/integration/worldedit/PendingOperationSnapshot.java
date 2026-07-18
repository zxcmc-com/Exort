package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record PendingOperationSnapshot(
    UUID worldId,
    Map<Long, MarkerSnapshot> markers,
    Set<ChunkKey> chunks,
    WorldEditBounds bounds,
    String reason) {
  PendingOperationSnapshot {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
    chunks = chunks == null ? Set.of() : Set.copyOf(chunks);
    reason = reason == null ? "worldedit_operation" : reason;
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
}
