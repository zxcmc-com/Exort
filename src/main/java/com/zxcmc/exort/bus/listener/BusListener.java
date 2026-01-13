package com.zxcmc.exort.bus.listener;

import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class BusListener implements Listener {
  private final ExortPlugin plugin;
  private final BusSessionManager busSessionManager;
  private final Material busCarrier;

  public BusListener(ExortPlugin plugin, BusSessionManager busSessionManager, Material busCarrier) {
    this.plugin = plugin;
    this.busSessionManager = busSessionManager;
    this.busCarrier = busCarrier;
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    if (!isBus(block)) return;
    if (event.getPlayer().isSneaking()) {
      return;
    }
    if (!plugin.getRegionProtection().canUse(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }
    if (plugin.getBusDisplayManager() != null) {
      plugin.getBusDisplayManager().refresh(block);
    }
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);
    busSessionManager.openSession(event.getPlayer(), block);
  }

  private boolean isBus(Block block) {
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }
}
