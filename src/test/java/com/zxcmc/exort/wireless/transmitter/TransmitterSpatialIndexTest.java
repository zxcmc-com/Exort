package com.zxcmc.exort.wireless.transmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransmitterSpatialIndexTest {
  @Test
  void indexesAndRemovesSparseChunkCandidatesWithoutCrossWorldLeakage() {
    UUID world = new UUID(0L, 1L);
    UUID otherWorld = new UUID(0L, 2L);
    TransmitterSpatialIndex index = new TransmitterSpatialIndex();
    var nearby = new TransmitterSpatialIndex.Position(world, 15, 64, 0);
    var adjacentChunk = new TransmitterSpatialIndex.Position(world, 17, 64, 0);
    var far = new TransmitterSpatialIndex.Position(world, 1_000, 64, 1_000);
    var other = new TransmitterSpatialIndex.Position(otherWorld, 0, 64, 0);
    index.add(nearby);
    index.add(adjacentChunk);
    index.add(far);
    index.add(other);

    var candidates = index.candidates(world, 16, 0, 16);

    assertTrue(candidates.contains(nearby));
    assertTrue(candidates.contains(adjacentChunk));
    assertFalse(candidates.contains(other));
    index.removeChunk(world, 0, 0);
    assertFalse(index.candidates(world, 16, 0, 16).contains(nearby));
    assertEquals(3, index.size());
  }

  @Test
  void duplicateRegistrationIsIdempotent() {
    TransmitterSpatialIndex index = new TransmitterSpatialIndex();
    var position = new TransmitterSpatialIndex.Position(new UUID(0L, 3L), 0, 64, 0);

    index.add(position);
    index.add(position);
    index.remove(position);

    assertEquals(0, index.size());
  }

  @Test
  void maximumIntegerRangeDoesNotOverflowToTheCenterChunk() {
    UUID world = new UUID(0L, 4L);
    TransmitterSpatialIndex index = new TransmitterSpatialIndex();
    var far = new TransmitterSpatialIndex.Position(world, 1_000_000, 64, 1_000_000);
    index.add(far);

    assertTrue(index.candidates(world, 0, 0, Integer.MAX_VALUE).contains(far));
  }

  @Test
  void acceptedTwoHundredFiftyTransmitterPopulationUsesSparseNearbyCandidates() {
    UUID world = new UUID(0L, 5L);
    TransmitterSpatialIndex index = new TransmitterSpatialIndex();
    for (int value = 0; value < 250; value++) {
      index.add(new TransmitterSpatialIndex.Position(world, value * 128, 64, value * 128));
    }

    var nearby = index.candidates(world, 0, 0, 48);

    assertEquals(250, index.size());
    assertTrue(nearby.size() < index.size());
    assertTrue(nearby.contains(new TransmitterSpatialIndex.Position(world, 0, 64, 0)));
  }

  @Test
  void globalClassificationIsWorldScopedAndCanBeReclassified() {
    UUID world = new UUID(0L, 6L);
    UUID otherWorld = new UUID(0L, 7L);
    TransmitterSpatialIndex index = new TransmitterSpatialIndex();
    var position = new TransmitterSpatialIndex.Position(world, 0, 64, 0);
    var other = new TransmitterSpatialIndex.Position(otherWorld, 0, 64, 0);

    index.add(position, true);
    index.add(other, true);

    assertEquals(List.of(position), index.globalCandidates(world));
    assertEquals(List.of(other), index.globalCandidates(otherWorld));
    index.add(position, false);
    assertTrue(index.globalCandidates(world).isEmpty());
    assertEquals(2, index.size());
  }
}
