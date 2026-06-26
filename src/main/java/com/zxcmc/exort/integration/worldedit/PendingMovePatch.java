package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;

record PendingMovePatch(
    Map<Long, MarkerSnapshot> sourceMarkers,
    Map<Long, MarkerSnapshot> destinationMarkers,
    BlockVector3 offset,
    long timestampMs,
    int usesRemaining) {
  PendingMovePatch {
    sourceMarkers = sourceMarkers == null ? Map.of() : Map.copyOf(sourceMarkers);
    destinationMarkers = destinationMarkers == null ? Map.of() : Map.copyOf(destinationMarkers);
    offset = offset == null ? BlockVector3.at(0, 0, 0) : offset;
  }

  PendingMovePatch consume() {
    return new PendingMovePatch(
        sourceMarkers, destinationMarkers, offset, timestampMs, usesRemaining - 1);
  }

  MarkerSnapshot get(BlockVector3 position) {
    if (position == null) return null;
    return destinationMarkers.get(
        WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  MarkerSnapshot source(BlockVector3 position) {
    if (position == null) return null;
    return sourceMarkers.get(
        WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  String offsetText() {
    return offset.x() + "," + offset.y() + "," + offset.z();
  }
}
