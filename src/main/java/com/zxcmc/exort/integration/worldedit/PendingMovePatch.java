package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

record PendingMovePatch(
    Map<Long, MarkerSnapshot> sourceMarkers,
    Map<Long, MarkerSnapshot> destinationMarkers,
    Set<Long> markerChunks,
    BlockVector3 offset,
    long timestampMs,
    int usesRemaining) {
  PendingMovePatch {
    sourceMarkers = sourceMarkers == null ? Map.of() : Map.copyOf(sourceMarkers);
    destinationMarkers = destinationMarkers == null ? Map.of() : Map.copyOf(destinationMarkers);
    markerChunks =
        markerChunks == null
            ? markerChunks(sourceMarkers, destinationMarkers)
            : Set.copyOf(markerChunks);
    offset = offset == null ? BlockVector3.at(0, 0, 0) : offset;
  }

  PendingMovePatch(
      Map<Long, MarkerSnapshot> sourceMarkers,
      Map<Long, MarkerSnapshot> destinationMarkers,
      BlockVector3 offset,
      long timestampMs,
      int usesRemaining) {
    this(sourceMarkers, destinationMarkers, null, offset, timestampMs, usesRemaining);
  }

  PendingMovePatch consume() {
    return new PendingMovePatch(
        sourceMarkers, destinationMarkers, markerChunks, offset, timestampMs, usesRemaining - 1);
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

  boolean hasMarkerAt(BlockVector3 position) {
    if (position == null) return false;
    long key = WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z());
    return sourceMarkers.containsKey(key) || destinationMarkers.containsKey(key);
  }

  boolean hasMarkerIn(Region region) {
    if (region == null || markerChunks.isEmpty()) return false;
    BlockVector3 min = region.getMinimumPoint();
    BlockVector3 max = region.getMaximumPoint();
    int minChunkX = min.x() >> 4;
    int maxChunkX = max.x() >> 4;
    int minChunkZ = min.z() >> 4;
    int maxChunkZ = max.z() >> 4;
    for (long key : markerChunks) {
      int chunkX = (int) (key >> 32);
      int chunkZ = (int) key;
      if (chunkX >= minChunkX
          && chunkX <= maxChunkX
          && chunkZ >= minChunkZ
          && chunkZ <= maxChunkZ) {
        return true;
      }
    }
    return false;
  }

  String offsetText() {
    return offset.x() + "," + offset.y() + "," + offset.z();
  }

  private static Set<Long> markerChunks(
      Map<Long, MarkerSnapshot> sourceMarkers, Map<Long, MarkerSnapshot> destinationMarkers) {
    Set<Long> chunks = new HashSet<>();
    addMarkerChunks(chunks, sourceMarkers);
    addMarkerChunks(chunks, destinationMarkers);
    return Set.copyOf(chunks);
  }

  private static void addMarkerChunks(Set<Long> chunks, Map<Long, MarkerSnapshot> markers) {
    for (long markerKey : markers.keySet()) {
      int chunkX = WorldEditMarkerMath.blockX(markerKey) >> 4;
      int chunkZ = WorldEditMarkerMath.blockZ(markerKey) >> 4;
      chunks.add(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
    }
  }
}
