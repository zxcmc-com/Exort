package com.zxcmc.exort.placement.storage;

import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class StoragePlacementFailureHandler {
  private final StoragePlacementDependencies dependencies;

  public StoragePlacementFailureHandler(StoragePlacementDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  public void rollbackFailedPlacement(
      Player player,
      Block block,
      String storageId,
      ItemStack refund,
      boolean shouldRefund,
      Throwable err) {
    var plugin = dependencies.plugin();
    ExortLog.log(plugin, Level.WARNING, "Failed to persist storage tier for " + storageId, err);
    PluginTasks.runSyncIfEnabled(
        plugin,
        () -> {
          boolean rolledBack =
              StoragePlacementRollback.rollbackIfCurrent(
                  dependencies, block, storageId, player, refund, shouldRefund);
          if (!rolledBack) {
            plugin
                .getLogger()
                .warning(
                    "Could not roll back failed storage placement for "
                        + storageId
                        + "; block marker changed before the database failure was handled.");
          }
          showStorageFailure(player);
        });
  }

  public void reportStorageFailure(Player player, String action, Throwable err) {
    var plugin = dependencies.plugin();
    ExortLog.log(plugin, Level.WARNING, "Failed to " + action, err);
    PluginTasks.runSyncIfEnabled(plugin, () -> showStorageFailure(player));
  }

  private void showStorageFailure(Player player) {
    if (player == null || !player.isOnline()) return;
    dependencies.playerFeedback().error(player, "message.storage_load_failed");
  }
}
