package com.zxcmc.exort.bus.listener;

import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.marker.BusMarker;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public class BusListener implements Listener {
  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final Consumer<Block> busDisplayRefresh;
  private final BusSessionManager busSessionManager;
  private final Material busCarrier;

  public BusListener(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      Consumer<Block> busDisplayRefresh,
      BusSessionManager busSessionManager,
      Material busCarrier) {
    this.plugin = plugin;
    this.regionProtection = regionProtection;
    this.worldEditWandGuard = worldEditWandGuard;
    this.busDisplayRefresh = busDisplayRefresh;
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
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (event.getPlayer().isSneaking()) {
      return;
    }
    if (!regionProtection.canUse(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }
    busDisplayRefresh.accept(block);
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);
    busSessionManager.openSession(event.getPlayer(), block);
  }

  private boolean isBus(Block block) {
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }
}
