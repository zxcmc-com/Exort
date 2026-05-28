package com.zxcmc.exort.items;

import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryRefreshService {
  private final Supplier<CustomItems> customItems;
  private final Supplier<WirelessTerminalService> wirelessService;
  private final AtomicInteger epoch = new AtomicInteger();

  public InventoryRefreshService(
      Supplier<CustomItems> customItems, Supplier<WirelessTerminalService> wirelessService) {
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.wirelessService = Objects.requireNonNull(wirelessService, "wirelessService");
  }

  public int epoch() {
    return epoch.get();
  }

  public void bumpEpoch() {
    epoch.incrementAndGet();
  }

  public void refreshPlayerInventory(Player player) {
    if (player == null) return;
    refreshInventory(player.getInventory(), false);
    refreshInventory(player.getEnderChest(), false);
  }

  public void refreshContainerInventory(Inventory inventory) {
    refreshInventory(inventory, false);
  }

  private int refreshInventory(Inventory inventory, boolean inStorage) {
    CustomItems items = customItems.get();
    if (inventory == null || items == null) return 0;
    int changed = 0;
    int size = inventory.getSize();
    for (int i = 0; i < size; i++) {
      ItemStack stack = inventory.getItem(i);
      if (stack == null || stack.getType() == Material.AIR) continue;
      if (!items.isCustomItem(stack)) continue;
      if (items.refreshItem(stack, wirelessService.get(), inStorage)) {
        inventory.setItem(i, stack);
        changed++;
      }
    }
    return changed;
  }
}
