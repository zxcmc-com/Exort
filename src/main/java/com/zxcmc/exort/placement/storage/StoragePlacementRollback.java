package com.zxcmc.exort.placement.storage;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.StorageMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class StoragePlacementRollback {
  private StoragePlacementRollback() {}

  static boolean rollbackIfCurrent(
      StoragePlacementDependencies dependencies,
      Block block,
      String storageId,
      Player player,
      ItemStack refund,
      boolean shouldRefund) {
    if (block == null || storageId == null) return false;
    var plugin = dependencies.plugin();
    var marker = StorageMarker.get(plugin, block);
    if (marker.isEmpty() || !storageId.equals(marker.get().storageId())) {
      return false;
    }
    if (!Carriers.matchesCarrier(block, dependencies.storageCarrier())) {
      return false;
    }

    StorageMarker.clear(plugin, block);
    var refresh = dependencies.displayRefreshService().get();
    if (refresh != null) {
      refresh.removeStorageDisplay(block);
    }
    var holograms = dependencies.hologramManager().get();
    if (holograms != null) {
      holograms.unregisterStorage(block);
      holograms.invalidateAll();
    }
    block.setType(Material.AIR, false);

    if (shouldRefund) {
      refundPlacementItem(block, player, refund);
    }

    dependencies.revalidateSessions().run();
    dependencies.invalidateNetwork().accept(block);
    if (refresh != null) {
      refresh.refreshBlockAndNeighbors(block);
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
