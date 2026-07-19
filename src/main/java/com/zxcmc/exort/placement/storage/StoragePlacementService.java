package com.zxcmc.exort.placement.storage;

import com.zxcmc.exort.feedback.FeedbackReason;
import com.zxcmc.exort.storage.StorageClaimLocation;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Shared storage claim and persistence workflow used by both placement transports. */
public final class StoragePlacementService {
  private final StorageManager storageManager;
  private final StorageClaimRegistry claimRegistry;
  private final StoragePlacementFailureHandler failureHandler;
  private final StoragePlacementDependencies dependencies;

  public StoragePlacementService(
      StorageManager storageManager,
      StorageClaimRegistry claimRegistry,
      StoragePlacementDependencies dependencies) {
    this.storageManager = Objects.requireNonNull(storageManager, "storageManager");
    this.claimRegistry = Objects.requireNonNull(claimRegistry, "claimRegistry");
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    this.failureHandler = new StoragePlacementFailureHandler(dependencies);
  }

  public StorageClaimRegistry.ReservationResult reserve(
      Player player, String storageId, Block block) {
    StorageClaimLocation location = StorageClaimLocation.fromBlock(block);
    StorageClaimRegistry.ReservationResult result = claimRegistry.reserve(storageId, location);
    if (!result.allowed()) {
      warnDenied(player, storageId, location, result.denial());
    }
    return result;
  }

  public void preload(Player player, String storageId) {
    storageManager
        .getOrLoad(storageId)
        .whenComplete(
            (cache, error) -> {
              if (error != null) {
                failureHandler.reportStorageFailure(
                    player, "load placed storage " + storageId, error);
              }
            });
  }

  public void persist(
      Player player,
      Block block,
      String storageId,
      String tierKey,
      long tierMaxItems,
      String displayName,
      ItemStack refund,
      boolean shouldRefund,
      BlockState replacedState,
      StorageClaimRegistry.Reservation reservation) {
    claimRegistry
        .persist(reservation, tierKey, tierMaxItems, displayName)
        .whenComplete(
            (ignored, error) -> {
              if (error != null) {
                failureHandler.rollbackFailedPlacement(
                    player, block, storageId, refund, shouldRefund, replacedState, error);
              } else {
                storageManager.setCachedDisplayName(storageId, displayName);
              }
            });
  }

  private void warnDenied(
      Player player,
      String storageId,
      StorageClaimLocation location,
      StorageClaimRegistry.Denial denial) {
    dependencies
        .plugin()
        .getLogger()
        .warning(
            "Denied storage placement for "
                + storageId
                + " at "
                + location
                + ": physical claim "
                + denial);
    dependencies
        .playerFeedback()
        .respond(
            player,
            denial == StorageClaimRegistry.Denial.NOT_READY
                ? FeedbackReason.STORAGE_LOADING
                : FeedbackReason.STORAGE_CLAIM_CONFLICT,
            denial == StorageClaimRegistry.Denial.NOT_READY
                ? "message.storage_loading"
                : "message.storage_load_failed");
  }
}
