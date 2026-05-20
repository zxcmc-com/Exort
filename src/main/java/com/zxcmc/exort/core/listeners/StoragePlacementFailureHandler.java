package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.core.task.PluginTasks;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class StoragePlacementFailureHandler {
  private final ExortPlugin plugin;
  private final Material storageCarrier;

  StoragePlacementFailureHandler(ExortPlugin plugin, Material storageCarrier) {
    this.plugin = plugin;
    this.storageCarrier = storageCarrier;
  }

  void rollbackFailedPlacement(
      Player player,
      Block block,
      String storageId,
      ItemStack refund,
      boolean shouldRefund,
      Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to persist storage tier for " + storageId, err);
    PluginTasks.runSyncIfEnabled(
        plugin,
        () -> {
          boolean rolledBack =
              StoragePlacementRollback.rollbackIfCurrent(
                  plugin, block, storageCarrier, storageId, player, refund, shouldRefund);
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

  void reportStorageFailure(Player player, String action, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to " + action, err);
    PluginTasks.runSyncIfEnabled(plugin, () -> showStorageFailure(player));
  }

  private void showStorageFailure(Player player) {
    if (player == null || !player.isOnline()) return;
    plugin.getPlayerFeedback().error(player, "message.storage_load_failed");
  }
}
