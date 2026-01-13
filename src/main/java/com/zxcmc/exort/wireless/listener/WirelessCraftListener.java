package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.wireless.WirelessTerminalService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class WirelessCraftListener implements Listener {
  private final WirelessTerminalService service;

  public WirelessCraftListener(WirelessTerminalService service) {
    this.service = service;
  }

  @EventHandler
  public void onPrepare(PrepareItemCraftEvent event) {
    CraftingInventory inv = event.getInventory();
    ItemStack[] matrix = inv.getMatrix();
    if (matrix == null) return;
    ItemStack found = null;
    for (ItemStack stack : matrix) {
      if (stack == null || stack.getType().isAir()) continue;
      if (!service.isWireless(stack)) {
        return; // other item present, abort
      }
      if (found != null) {
        return; // more than one wireless
      }
      found = stack;
    }
    if (found == null) return;
    inv.setResult(service.resetLinkViaCraft(found));
  }
}
