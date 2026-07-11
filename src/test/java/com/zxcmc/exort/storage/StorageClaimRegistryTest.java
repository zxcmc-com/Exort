package com.zxcmc.exort.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class StorageClaimRegistryTest {
  private static final UUID WORLD = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final StorageClaimLocation A =
      new StorageClaimLocation(WORLD, "minecraft:overworld", "world", 1, 64, 2);
  private static final StorageClaimLocation B =
      new StorageClaimLocation(WORLD, "minecraft:overworld", "world", 3, 64, 4);

  @Test
  void failsClosedUntilDurableClaimsAreLoaded() {
    FakeStore store = new FakeStore();
    store.load = new CompletableFuture<>();
    StorageClaimRegistry registry = registry(store);

    CompletableFuture<Void> first = registry.start();

    assertEquals(StorageClaimRegistry.State.LOADING, registry.state());
    assertSame(first, registry.start());
    assertFalse(first.isDone());
    assertEquals(StorageClaimRegistry.Denial.NOT_READY, registry.reserve("storage-a", A).denial());
  }

  @Test
  void existingPhysicalClaimWinsForBothIdAndPosition() {
    FakeStore store = new FakeStore();
    store.claims.add(claim("storage-a", A));
    StorageClaimRegistry registry = registry(store);
    registry.start().join();

    assertEquals(
        StorageClaimRegistry.Denial.STORAGE_ALREADY_CLAIMED,
        registry.reserve("storage-a", B).denial());
    assertEquals(
        StorageClaimRegistry.Denial.POSITION_ALREADY_CLAIMED,
        registry.reserve("storage-b", A).denial());
  }

  @Test
  void failedPersistenceAtomicallyReleasesOnlyItsReservation() {
    FakeStore store = new FakeStore();
    StorageClaimRegistry registry = registry(store);
    registry.start().join();
    var reservation = registry.reserve("storage-a", A).reservation();
    store.insertFailure = new IllegalStateException("disk full");

    assertThrows(
        RuntimeException.class, () -> registry.persist(reservation, "BASIC", 100, null).join());

    assertTrue(registry.reserve("storage-a", B).allowed());
  }

  @Test
  void releaseRequiresExactPersistedLocationAndDbConfirmation() {
    FakeStore store = new FakeStore();
    store.claims.add(claim("storage-a", A));
    StorageClaimRegistry registry = registry(store);
    registry.start().join();

    assertFalse(registry.releaseExact("storage-a", B).join());
    assertEquals(StorageClaimRegistry.ExactClaim.MATCHED, registry.exactClaim("storage-a", A));

    store.deleteResult = false;
    assertFalse(registry.releaseExact("storage-a", A).join());
    assertEquals(StorageClaimRegistry.ExactClaim.MATCHED, registry.exactClaim("storage-a", A));

    store.deleteResult = true;
    assertTrue(registry.releaseExact("storage-a", A).join());
    assertEquals(StorageClaimRegistry.ExactClaim.ABSENT, registry.exactClaim("storage-a", A));
  }

  @Test
  void durableMovePublishesOnlyOneLocationAndRollsBackOnFailure() {
    FakeStore store = new FakeStore();
    store.claims.add(claim("storage-a", A));
    StorageClaimRegistry registry = registry(store);
    registry.start().join();

    assertTrue(registry.moveExact("storage-a", B).join());
    assertEquals(StorageClaimRegistry.ExactClaim.MISMATCH, registry.exactClaim("storage-a", A));
    assertEquals(StorageClaimRegistry.ExactClaim.MATCHED, registry.exactClaim("storage-a", B));

    store.moveFailure = new IllegalStateException("disk full");
    assertThrows(RuntimeException.class, () -> registry.moveExact("storage-a", A).join());
    assertEquals(StorageClaimRegistry.ExactClaim.MATCHED, registry.exactClaim("storage-a", B));
    assertEquals(StorageClaimRegistry.ExactClaim.MISMATCH, registry.exactClaim("storage-a", A));
  }

  private static StorageClaimRegistry registry(FakeStore store) {
    return new StorageClaimRegistry(
        store,
        Logger.getLogger("StorageClaimRegistryTest"),
        Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC));
  }

  private static StorageClaim claim(String id, StorageClaimLocation location) {
    return new StorageClaim(
        id,
        location.worldId(),
        location.worldKey(),
        location.worldName(),
        location.x(),
        location.y(),
        location.z(),
        1,
        1);
  }

  private static final class FakeStore implements StorageClaimStore {
    private final List<StorageClaim> claims = new ArrayList<>();
    private CompletableFuture<List<StorageClaim>> load;
    private RuntimeException insertFailure;
    private RuntimeException moveFailure;
    private boolean deleteResult = true;

    @Override
    public CompletableFuture<List<StorageClaim>> loadStorageClaims() {
      return load != null ? load : CompletableFuture.completedFuture(List.copyOf(claims));
    }

    @Override
    public CompletableFuture<Void> insertStorageClaim(
        StorageClaim claim, String tierKey, long tierMaxItems, String displayName) {
      if (insertFailure != null) return CompletableFuture.failedFuture(insertFailure);
      claims.add(claim);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> deleteStorageClaimExact(
        String storageId, StorageClaimLocation location) {
      return CompletableFuture.completedFuture(deleteResult);
    }

    @Override
    public CompletableFuture<Boolean> moveStorageClaimExact(
        StorageClaim source, StorageClaim destination) {
      if (moveFailure != null) return CompletableFuture.failedFuture(moveFailure);
      claims.remove(source);
      claims.add(destination);
      return CompletableFuture.completedFuture(true);
    }
  }
}
