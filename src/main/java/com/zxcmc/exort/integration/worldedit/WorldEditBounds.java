package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import java.util.Objects;

/** Immutable inclusive block bounds used to constrain marker capture. */
record WorldEditBounds(BlockVector3 minimum, BlockVector3 maximum) {
  WorldEditBounds {
    Objects.requireNonNull(minimum, "minimum");
    Objects.requireNonNull(maximum, "maximum");
    BlockVector3 first = minimum;
    BlockVector3 second = maximum;
    minimum =
        BlockVector3.at(
            Math.min(first.x(), second.x()),
            Math.min(first.y(), second.y()),
            Math.min(first.z(), second.z()));
    maximum =
        BlockVector3.at(
            Math.max(first.x(), second.x()),
            Math.max(first.y(), second.y()),
            Math.max(first.z(), second.z()));
  }

  static WorldEditBounds from(Region region) {
    return region == null
        ? null
        : new WorldEditBounds(region.getMinimumPoint(), region.getMaximumPoint());
  }

  static WorldEditBounds around(BlockVector3 center, int horizontal, int vertical) {
    if (center == null) return null;
    int h = Math.max(0, horizontal);
    int v = Math.max(0, vertical);
    return new WorldEditBounds(
        BlockVector3.at(
            saturatedAdd(center.x(), -h),
            saturatedAdd(center.y(), -v),
            saturatedAdd(center.z(), -h)),
        BlockVector3.at(
            saturatedAdd(center.x(), h), saturatedAdd(center.y(), v), saturatedAdd(center.z(), h)));
  }

  boolean contains(BlockVector3 position) {
    return position != null
        && position.x() >= minimum.x()
        && position.x() <= maximum.x()
        && position.y() >= minimum.y()
        && position.y() <= maximum.y()
        && position.z() >= minimum.z()
        && position.z() <= maximum.z();
  }

  WorldEditBounds union(WorldEditBounds other) {
    if (other == null) return this;
    return new WorldEditBounds(
        BlockVector3.at(
            Math.min(minimum.x(), other.minimum.x()),
            Math.min(minimum.y(), other.minimum.y()),
            Math.min(minimum.z(), other.minimum.z())),
        BlockVector3.at(
            Math.max(maximum.x(), other.maximum.x()),
            Math.max(maximum.y(), other.maximum.y()),
            Math.max(maximum.z(), other.maximum.z())));
  }

  WorldEditBounds translate(BlockVector3 offset) {
    if (offset == null) return this;
    return new WorldEditBounds(
        BlockVector3.at(
            saturatedAdd(minimum.x(), offset.x()),
            saturatedAdd(minimum.y(), offset.y()),
            saturatedAdd(minimum.z(), offset.z())),
        BlockVector3.at(
            saturatedAdd(maximum.x(), offset.x()),
            saturatedAdd(maximum.y(), offset.y()),
            saturatedAdd(maximum.z(), offset.z())));
  }

  int minChunkX() {
    return minimum.x() >> 4;
  }

  int maxChunkX() {
    return maximum.x() >> 4;
  }

  int minChunkZ() {
    return minimum.z() >> 4;
  }

  int maxChunkZ() {
    return maximum.z() >> 4;
  }

  int chunkCountCapped(int cap) {
    if (cap <= 0) return 0;
    long width = (long) maxChunkX() - minChunkX() + 1L;
    long depth = (long) maxChunkZ() - minChunkZ() + 1L;
    if (width <= 0L || depth <= 0L || width > cap || depth > cap) return cap;
    long count = width * depth;
    return count >= cap ? cap : (int) count;
  }

  int sizeX() {
    return saturatedSize(minimum.x(), maximum.x());
  }

  int sizeY() {
    return saturatedSize(minimum.y(), maximum.y());
  }

  int sizeZ() {
    return saturatedSize(minimum.z(), maximum.z());
  }

  static int saturatedMultiply(int value, int multiplier) {
    long result = (long) value * multiplier;
    return result > Integer.MAX_VALUE
        ? Integer.MAX_VALUE
        : result < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) result;
  }

  private static int saturatedAdd(int value, int delta) {
    long result = (long) value + delta;
    return result > Integer.MAX_VALUE
        ? Integer.MAX_VALUE
        : result < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) result;
  }

  private static int saturatedSize(int min, int max) {
    long result = (long) max - min + 1L;
    return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }
}
