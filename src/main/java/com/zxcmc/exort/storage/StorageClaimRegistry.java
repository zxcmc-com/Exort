package com.zxcmc.exort.storage;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main-thread physical identity guard backed by a durable claim store. Async completions only
 * update this class' own synchronized maps and never call Bukkit APIs.
 */
public final class StorageClaimRegistry implements AutoCloseable {
  public enum State {
    NEW,
    LOADING,
    READY,
    FAILED
  }

  public enum Denial {
    NOT_READY,
    STORAGE_ALREADY_CLAIMED,
    POSITION_ALREADY_CLAIMED
  }

  public enum ExactClaim {
    MATCHED,
    NOT_READY,
    ABSENT,
    MISMATCH
  }

  public record ReservationResult(Reservation reservation, Denial denial) {
    public boolean allowed() {
      return reservation != null;
    }
  }

  public record Reservation(String storageId, StorageClaimLocation location, UUID token) {
    public Reservation {
      Objects.requireNonNull(storageId, "storageId");
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(token, "token");
    }
  }

  private record Slot(StorageClaim claim, UUID reservationToken) {}

  private final StorageClaimStore store;
  private final Logger logger;
  private final Clock clock;
  private final Map<String, Slot> byStorageId = new HashMap<>();
  private final Map<StorageClaimLocation, String> byLocation = new HashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private State state = State.NEW;
  private CompletableFuture<Void> readiness = new CompletableFuture<>();

  public StorageClaimRegistry(StorageClaimStore store, Logger logger) {
    this(store, logger, Clock.systemUTC());
  }

  StorageClaimRegistry(StorageClaimStore store, Logger logger, Clock clock) {
    this.store = Objects.requireNonNull(store, "store");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public synchronized State state() {
    return state;
  }

  public CompletableFuture<Void> start() {
    if (closed.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Storage claim registry is closed"));
    }
    synchronized (this) {
      if (state == State.LOADING) {
        return readiness;
      }
      if (state == State.READY) {
        return CompletableFuture.completedFuture(null);
      }
      readiness = new CompletableFuture<>();
      state = State.LOADING;
    }
    CompletableFuture<Void> load = store.loadStorageClaims().thenAccept(this::install);
    load.whenComplete(
        (ignored, error) -> {
          if (closed.get()) {
            return;
          }
          CompletableFuture<Void> currentReadiness;
          synchronized (this) {
            currentReadiness = readiness;
            if (error != null) {
              state = State.FAILED;
              byStorageId.clear();
              byLocation.clear();
            }
          }
          if (error == null) {
            currentReadiness.complete(null);
            return;
          }
          currentReadiness.completeExceptionally(error);
          logger.log(
              Level.SEVERE,
              "Failed to load physical storage claims; storage placement and breaking remain"
                  + " fail-closed until restart",
              error);
        });
    return readiness;
  }

  private synchronized void install(List<StorageClaim> claims) {
    if (closed.get()) {
      throw new CancellationException("Storage claim registry generation was closed");
    }
    Map<String, Slot> candidateById = new HashMap<>();
    Map<StorageClaimLocation, String> candidateByLocation = new HashMap<>();
    for (StorageClaim claim : List.copyOf(claims)) {
      if (candidateById.putIfAbsent(claim.storageId(), new Slot(claim, null)) != null) {
        throw new IllegalStateException("Duplicate storage claim id " + claim.storageId());
      }
      String previous = candidateByLocation.putIfAbsent(claim.location(), claim.storageId());
      if (previous != null) {
        throw new IllegalStateException(
            "Storage claim position "
                + claim.location()
                + " belongs to both "
                + previous
                + " and "
                + claim.storageId());
      }
    }
    byStorageId.clear();
    byStorageId.putAll(candidateById);
    byLocation.clear();
    byLocation.putAll(candidateByLocation);
    state = State.READY;
  }

  @Override
  public synchronized void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    state = State.FAILED;
    byStorageId.clear();
    byLocation.clear();
    readiness.completeExceptionally(
        new CancellationException("Storage claim registry generation was closed"));
  }

  public synchronized ReservationResult reserve(String storageId, StorageClaimLocation location) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(location, "location");
    if (state != State.READY) {
      return new ReservationResult(null, Denial.NOT_READY);
    }
    if (byStorageId.containsKey(storageId)) {
      return new ReservationResult(null, Denial.STORAGE_ALREADY_CLAIMED);
    }
    if (byLocation.containsKey(location)) {
      return new ReservationResult(null, Denial.POSITION_ALREADY_CLAIMED);
    }
    long now = clock.millis();
    UUID token = UUID.randomUUID();
    StorageClaim claim =
        new StorageClaim(
            storageId,
            location.worldId(),
            location.worldKey(),
            location.worldName(),
            location.x(),
            location.y(),
            location.z(),
            now,
            now);
    byStorageId.put(storageId, new Slot(claim, token));
    byLocation.put(location, storageId);
    return new ReservationResult(new Reservation(storageId, location, token), null);
  }

  public CompletableFuture<Void> persist(
      Reservation reservation, String tierKey, long tierMaxItems, String displayName) {
    Objects.requireNonNull(reservation, "reservation");
    StorageClaim claim;
    synchronized (this) {
      Slot slot = byStorageId.get(reservation.storageId());
      if (slot == null || !reservation.token().equals(slot.reservationToken())) {
        return CompletableFuture.failedFuture(
            new IllegalStateException("Storage claim reservation is no longer active"));
      }
      claim = slot.claim();
    }
    return store
        .insertStorageClaim(claim, tierKey, tierMaxItems, displayName)
        .whenComplete(
            (ignored, error) -> {
              synchronized (this) {
                Slot current = byStorageId.get(reservation.storageId());
                if (current == null || !reservation.token().equals(current.reservationToken())) {
                  return;
                }
                if (error == null) {
                  byStorageId.put(reservation.storageId(), new Slot(current.claim(), null));
                } else {
                  byStorageId.remove(reservation.storageId());
                  byLocation.remove(reservation.location(), reservation.storageId());
                }
              }
            });
  }

  public synchronized ExactClaim exactClaim(String storageId, StorageClaimLocation location) {
    if (state != State.READY) return ExactClaim.NOT_READY;
    Slot slot = byStorageId.get(storageId);
    if (slot == null) return ExactClaim.ABSENT;
    return slot.claim().location().equals(location) && slot.reservationToken() == null
        ? ExactClaim.MATCHED
        : ExactClaim.MISMATCH;
  }

  /**
   * Explicit repair/break seam. The in-memory claim remains authoritative until DB deletion wins.
   */
  public CompletableFuture<Boolean> releaseExact(String storageId, StorageClaimLocation location) {
    UUID token;
    StorageClaim claim;
    synchronized (this) {
      if (exactClaim(storageId, location) != ExactClaim.MATCHED) {
        return CompletableFuture.completedFuture(false);
      }
      Slot current = byStorageId.get(storageId);
      claim = current.claim();
      token = UUID.randomUUID();
      byStorageId.put(storageId, new Slot(claim, token));
    }
    UUID operationToken = token;
    StorageClaim releasing = claim;
    return store
        .deleteStorageClaimExact(storageId, location)
        .thenApply(
            deleted -> {
              synchronized (this) {
                Slot current = byStorageId.get(storageId);
                if (current == null || !operationToken.equals(current.reservationToken())) {
                  return false;
                }
                if (Boolean.TRUE.equals(deleted)) {
                  byStorageId.remove(storageId);
                  byLocation.remove(location, storageId);
                  return true;
                }
                byStorageId.put(storageId, new Slot(releasing, null));
                return false;
              }
            })
        .whenComplete(
            (deleted, error) -> {
              if (error == null) return;
              synchronized (this) {
                Slot current = byStorageId.get(storageId);
                if (current != null && operationToken.equals(current.reservationToken())) {
                  byStorageId.put(storageId, new Slot(releasing, null));
                }
              }
            });
  }

  /** Moves one already-claimed identity without ever exposing two active locations. */
  public CompletableFuture<Boolean> moveExact(String storageId, StorageClaimLocation destination) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(destination, "destination");
    StorageClaim source;
    StorageClaim moved;
    UUID token;
    synchronized (this) {
      if (state != State.READY) {
        return CompletableFuture.completedFuture(false);
      }
      Slot current = byStorageId.get(storageId);
      if (current == null || current.reservationToken() != null) {
        return CompletableFuture.completedFuture(false);
      }
      source = current.claim();
      if (source.location().equals(destination)) {
        return CompletableFuture.completedFuture(true);
      }
      String destinationOwner = byLocation.get(destination);
      if (destinationOwner != null && !storageId.equals(destinationOwner)) {
        return CompletableFuture.completedFuture(false);
      }
      long now = clock.millis();
      moved =
          new StorageClaim(
              storageId,
              destination.worldId(),
              destination.worldKey(),
              destination.worldName(),
              destination.x(),
              destination.y(),
              destination.z(),
              source.claimedAt(),
              now);
      token = UUID.randomUUID();
      byLocation.remove(source.location(), storageId);
      byLocation.put(destination, storageId);
      byStorageId.put(storageId, new Slot(moved, token));
    }
    StorageClaim original = source;
    StorageClaim candidate = moved;
    UUID operationToken = token;
    return store
        .moveStorageClaimExact(original, candidate)
        .thenApply(
            updated -> {
              synchronized (this) {
                Slot current = byStorageId.get(storageId);
                if (current == null || !operationToken.equals(current.reservationToken())) {
                  return false;
                }
                if (Boolean.TRUE.equals(updated)) {
                  byStorageId.put(storageId, new Slot(candidate, null));
                  return true;
                }
                restoreMove(original, candidate, operationToken);
                return false;
              }
            })
        .whenComplete(
            (updated, error) -> {
              if (error == null) return;
              synchronized (this) {
                restoreMove(original, candidate, operationToken);
              }
            });
  }

  private void restoreMove(StorageClaim original, StorageClaim candidate, UUID token) {
    Slot current = byStorageId.get(original.storageId());
    if (current == null || !token.equals(current.reservationToken())) {
      return;
    }
    byLocation.remove(candidate.location(), original.storageId());
    byLocation.put(original.location(), original.storageId());
    byStorageId.put(original.storageId(), new Slot(original, null));
  }

  public synchronized java.util.Optional<StorageClaim> claim(String storageId) {
    Slot slot = byStorageId.get(storageId);
    return slot == null ? java.util.Optional.empty() : java.util.Optional.of(slot.claim());
  }
}
