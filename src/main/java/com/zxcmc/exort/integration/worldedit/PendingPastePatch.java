package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;

record PendingPastePatch(
    Map<Long, MarkerSnapshot> destinationMarkers, Map<Long, MarkerSnapshot> undoMarkers) {
  PendingPastePatch {
    destinationMarkers = destinationMarkers == null ? Map.of() : Map.copyOf(destinationMarkers);
    undoMarkers = undoMarkers == null ? Map.of() : Map.copyOf(undoMarkers);
  }

  PendingPastePatch(Map<Long, MarkerSnapshot> destinationMarkers) {
    this(destinationMarkers, Map.of());
  }

  MarkerSnapshot get(BlockVector3 position) {
    if (position == null) return null;
    return destinationMarkers.get(
        WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  MarkerSnapshot undo(BlockVector3 position) {
    if (position == null) return null;
    return undoMarkers.get(WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }
}
