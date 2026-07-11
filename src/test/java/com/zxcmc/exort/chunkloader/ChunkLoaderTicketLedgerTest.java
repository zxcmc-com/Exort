package com.zxcmc.exort.chunkloader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkLoaderTicketLedgerTest {
  private static final UUID WORLD_A = new UUID(0L, 1L);
  private static final UUID WORLD_B = new UUID(0L, 2L);
  private static final UUID PLAYER_A = new UUID(0L, 3L);
  private static final UUID PLAYER_B = new UUID(0L, 4L);

  @Test
  void overlappingAreasCountOnlyNewUniqueChunks() {
    ChunkLoaderLimits limits = new ChunkLoaderLimits(10, 10, 10, 12, 12);
    ChunkLoaderTicketLedger ledger = new ChunkLoaderTicketLedger(limits);

    ChunkLoaderTicketLedger.Reservation first =
        ledger.reserve(record(1, WORLD_A, PLAYER_A, 0, 0), 1);
    ChunkLoaderTicketLedger.Reservation second =
        ledger.reserve(record(2, WORLD_A, PLAYER_B, 1, 0), 1);
    ChunkLoaderTicketLedger.Reservation denied =
        ledger.reserve(record(3, WORLD_A, PLAYER_B, 2, 0), 1);

    assertEquals(ChunkLoaderTicketLedger.Status.RESERVED, first.status());
    assertEquals(9, first.newTickets().size());
    assertEquals(ChunkLoaderTicketLedger.Status.RESERVED, second.status());
    assertEquals(3, second.newTickets().size());
    assertEquals(12, ledger.uniqueTicketCount());
    assertEquals(ChunkLoaderTicketLedger.Status.GLOBAL_CHUNK_LIMIT, denied.status());
    assertFalse(ledger.isReserved(new UUID(0L, 3L)));
  }

  @Test
  void loaderLimitsApplyGloballyPerWorldAndPerPlayer() {
    ChunkLoaderTicketLedger playerLedger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(10, 10, 1, 100, 100));
    assertTrue(playerLedger.reserve(record(10, WORLD_A, PLAYER_A, 0, 0), 0).allowed());
    assertEquals(
        ChunkLoaderTicketLedger.Status.PLAYER_LOADER_LIMIT,
        playerLedger.reserve(record(11, WORLD_A, PLAYER_A, 2, 0), 0).status());

    ChunkLoaderTicketLedger worldLedger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(10, 1, 10, 100, 100));
    assertTrue(worldLedger.reserve(record(20, WORLD_A, PLAYER_A, 0, 0), 0).allowed());
    assertEquals(
        ChunkLoaderTicketLedger.Status.WORLD_LOADER_LIMIT,
        worldLedger.reserve(record(21, WORLD_A, PLAYER_B, 2, 0), 0).status());
    assertTrue(worldLedger.reserve(record(22, WORLD_B, PLAYER_B, 0, 0), 0).allowed());

    ChunkLoaderTicketLedger globalLedger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(1, 10, 10, 100, 100));
    assertTrue(globalLedger.reserve(record(30, WORLD_A, PLAYER_A, 0, 0), 0).allowed());
    assertEquals(
        ChunkLoaderTicketLedger.Status.GLOBAL_LOADER_LIMIT,
        globalLedger.reserve(record(31, WORLD_B, PLAYER_B, 0, 0), 0).status());
  }

  @Test
  void uniqueChunkLimitAlsoAppliesPerWorld() {
    ChunkLoaderTicketLedger ledger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(10, 10, 10, 20, 9));

    assertTrue(ledger.reserve(record(35, WORLD_A, PLAYER_A, 0, 0), 1).allowed());
    assertEquals(
        ChunkLoaderTicketLedger.Status.WORLD_CHUNK_LIMIT,
        ledger.reserve(record(36, WORLD_A, PLAYER_B, 1, 0), 1).status());
    assertTrue(ledger.reserve(record(37, WORLD_B, PLAYER_B, 0, 0), 1).allowed());
    assertEquals(18, ledger.uniqueTicketCount());
  }

  @Test
  void releaseKeepsOverlappingTicketUntilLastLoaderReleasesIt() {
    ChunkLoaderTicketLedger ledger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(10, 10, 10, 100, 100));
    UUID firstId = new UUID(0L, 40L);
    UUID secondId = new UUID(0L, 41L);
    ledger.reserve(record(firstId, WORLD_A, PLAYER_A, 0, 0), 1);
    ledger.reserve(record(secondId, WORLD_A, PLAYER_B, 1, 0), 1);

    ChunkLoaderTicketLedger.Release firstRelease = ledger.release(firstId);

    assertEquals(3, firstRelease.removedTickets().size());
    assertEquals(9, ledger.uniqueTicketCount());

    ChunkLoaderTicketLedger.Release secondRelease = ledger.release(secondId);

    assertEquals(9, secondRelease.removedTickets().size());
    assertEquals(0, ledger.uniqueTicketCount());
  }

  @Test
  void existingReservationIsIdempotent() {
    ChunkLoaderTicketLedger ledger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(10, 10, 10, 100, 100));
    ChunkLoaderRecord record = record(50, WORLD_A, PLAYER_A, 0, 0);

    assertTrue(ledger.reserve(record, 1).allowed());
    assertEquals(
        ChunkLoaderTicketLedger.Status.ALREADY_RESERVED, ledger.reserve(record, 1).status());
    assertEquals(9, ledger.uniqueTicketCount());
  }

  @Test
  void persistedBypassSkipsChecksButStillConsumesVisibleCapacity() {
    ChunkLoaderTicketLedger ledger =
        new ChunkLoaderTicketLedger(new ChunkLoaderLimits(1, 1, 1, 1, 1));
    UUID bypassedId = new UUID(0L, 60L);
    UUID normalId = new UUID(0L, 61L);

    ChunkLoaderTicketLedger.Reservation bypassed =
        ledger.reserve(record(bypassedId, WORLD_A, PLAYER_A, 0, 0, true), 1);
    ChunkLoaderTicketLedger.Reservation normal =
        ledger.reserve(record(normalId, WORLD_A, PLAYER_B, 4, 0, false), 0);

    assertTrue(bypassed.allowed());
    assertEquals(9, ledger.uniqueTicketCount());
    assertEquals(ChunkLoaderTicketLedger.Status.GLOBAL_LOADER_LIMIT, normal.status());
    assertEquals(9, ledger.release(bypassedId).removedTickets().size());
    assertEquals(0, ledger.uniqueTicketCount());
  }

  private static ChunkLoaderRecord record(
      long id, UUID worldId, UUID playerId, int chunkX, int chunkZ) {
    return record(new UUID(0L, id), worldId, playerId, chunkX, chunkZ);
  }

  private static ChunkLoaderRecord record(
      UUID id, UUID worldId, UUID playerId, int chunkX, int chunkZ) {
    return record(id, worldId, playerId, chunkX, chunkZ, false);
  }

  private static ChunkLoaderRecord record(
      UUID id, UUID worldId, UUID playerId, int chunkX, int chunkZ, boolean bypassLimits) {
    return new ChunkLoaderRecord(
        id,
        ChunkLoaderType.CHUNK_LOADER,
        worldId,
        "minecraft:world",
        "world",
        chunkX << 4,
        64,
        chunkZ << 4,
        chunkX,
        chunkZ,
        playerId,
        "Alex",
        1,
        true,
        bypassLimits,
        100L,
        100L);
  }
}
