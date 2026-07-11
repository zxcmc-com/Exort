package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.storage.StorageCache;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/** Failure-atomic item movement primitives used by the bus engine. */
final class BusItemTransfer {
  @FunctionalInterface
  interface Destination {
    /**
     * Inserts at most {@code amount}; implementations must roll back their own partial mutation.
     */
    int insert(ItemStack sample, int amount);
  }

  private BusItemTransfer() {}

  static boolean importItem(
      StorageCache destination,
      String key,
      ItemStack sample,
      int amount,
      Runnable sourceCommit,
      Runnable sourceRollback) {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(sourceCommit, "sourceCommit");
    Objects.requireNonNull(sourceRollback, "sourceRollback");
    if (amount <= 0) return false;

    StorageCache.PreparedAdd prepared = destination.prepareAdd(key, sample, amount);
    if (!prepared.accepted()) {
      return false;
    }
    boolean sourceMutationStarted = false;
    boolean sourceRollbackAttempted = false;
    long destinationBefore = destination.peekAmount(prepared.result().key());
    try {
      sourceMutationStarted = true;
      sourceCommit.run();
      StorageCache.AddResult added = destination.commitPrepared(prepared);
      if (added.accepted()) {
        return true;
      }
      sourceRollbackAttempted = true;
      rollbackSource(sourceRollback, null);
      return false;
    } catch (RuntimeException failure) {
      long destinationDelta = destination.peekAmount(prepared.result().key()) - destinationBefore;
      if (destinationDelta > 0) {
        try {
          destination.removeItem(prepared.result().key(), Math.min(destinationDelta, amount));
        } catch (RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
      }
      if (sourceMutationStarted && !sourceRollbackAttempted) {
        rollbackSource(sourceRollback, failure);
      }
      throw failure;
    }
  }

  static int exportItem(StorageCache source, String key, int amount, Destination destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    if (amount <= 0) return 0;
    StorageCache.ReservedItem reserved = source.reserveItem(key, amount).orElse(null);
    if (reserved == null || reserved.amount() <= 0) {
      return 0;
    }
    try {
      int moved = destination.insert(reserved.sample(), (int) reserved.amount());
      if (moved < 0 || moved > reserved.amount()) {
        throw new IllegalStateException(
            "Bus destination returned invalid moved amount "
                + moved
                + " for reservation "
                + reserved.amount());
      }
      long remaining = reserved.amount() - moved;
      if (remaining > 0) {
        source.restoreReserved(reserved, remaining);
      }
      return moved;
    } catch (RuntimeException failure) {
      try {
        source.restoreReserved(reserved);
      } catch (RuntimeException rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    }
  }

  static int transferStorage(
      StorageCache source, StorageCache destination, String key, ItemStack sample, int amount) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    int planned = (int) Math.min(Math.max(0, amount), source.peekAmount(key));
    if (planned <= 0) {
      return 0;
    }
    StorageCache.PreparedAdd prepared = destination.prepareAdd(key, sample, planned);
    if (!prepared.accepted()) {
      return 0;
    }
    StorageCache.ReservedItem reserved = source.reserveItem(key, planned).orElse(null);
    if (reserved == null || reserved.amount() != planned) {
      source.restoreReserved(reserved);
      return 0;
    }
    try {
      StorageCache.AddResult added = destination.commitPrepared(prepared);
      if (!added.accepted()) {
        source.restoreReserved(reserved);
        return 0;
      }
      return planned;
    } catch (RuntimeException failure) {
      try {
        source.restoreReserved(reserved);
      } catch (RuntimeException rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    }
  }

  private static void rollbackSource(Runnable sourceRollback, RuntimeException primary) {
    try {
      sourceRollback.run();
    } catch (RuntimeException rollbackFailure) {
      if (primary != null) {
        primary.addSuppressed(rollbackFailure);
        return;
      }
      throw rollbackFailure;
    }
  }
}
