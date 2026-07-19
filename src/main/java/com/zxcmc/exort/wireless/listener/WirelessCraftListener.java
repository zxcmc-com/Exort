package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.WirelessUnbindPolicy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

public class WirelessCraftListener implements Listener {
  private final WirelessUnbindPolicy unbindPolicy;

  public WirelessCraftListener(WirelessTerminalService service) {
    this.unbindPolicy = new WirelessUnbindPolicy(service);
  }

  @EventHandler
  public void onPrepare(PrepareItemCraftEvent event) {
    CraftingInventory inv = event.getInventory();
    ItemStack[] matrix = inv.getMatrix();
    if (matrix == null) return;
    unbindPolicy.plan(matrix).ifPresent(plan -> inv.setResult(plan.result()));
  }
}
