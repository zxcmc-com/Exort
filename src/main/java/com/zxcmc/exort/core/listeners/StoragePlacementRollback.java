package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.StorageMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class StoragePlacementRollback {
  private StoragePlacementRollback() {}

  static boolean rollbackIfCurrent(
      ExortPlugin plugin,
      Block block,
      Material storageCarrier,
      String storageId,
      Player player,
      ItemStack refund,
      boolean shouldRefund) {
    if (block == null || storageId == null) return false;
    var marker = StorageMarker.get(plugin, block);
    if (marker.isEmpty() || !storageId.equals(marker.get().storageId())) {
      return false;
    }
    if (!Carriers.matchesCarrier(block, storageCarrier)) {
      return false;
    }

    StorageMarker.clear(plugin, block);
    var refresh = plugin.getDisplayRefreshService();
    if (refresh != null) {
      refresh.removeStorageDisplay(block);
    }
    if (plugin.getHologramManager() != null) {
      plugin.getHologramManager().unregisterStorage(block);
      plugin.getHologramManager().invalidateAll();
    }
    block.setType(Material.AIR, false);

    if (shouldRefund) {
      refundPlacementItem(block, player, refund);
    }

    if (plugin.getSessionManager() != null) {
      plugin.getSessionManager().revalidateSessions();
    }
    var cache = plugin.getNetworkGraphCache();
    if (cache != null) {
      cache.invalidateAll();
    }
    if (refresh != null) {
      refresh.refreshChunk(block.getChunk());
      refresh.refreshNetworkFrom(block);
    }
    return true;
  }

  private static void refundPlacementItem(Block block, Player player, ItemStack refund) {
    if (refund == null || refund.getType() == Material.AIR || refund.getAmount() <= 0) return;
    ItemStack one = refund.clone();
    one.setAmount(1);
    if (player != null && player.isOnline()) {
      var leftovers = player.getInventory().addItem(one);
      if (leftovers.isEmpty()) {
        player.updateInventory();
        return;
      }
      leftovers
          .values()
          .forEach(item -> block.getWorld().dropItemNaturally(block.getLocation(), item));
      player.updateInventory();
      return;
    }
    block.getWorld().dropItemNaturally(block.getLocation(), one);
  }
}
