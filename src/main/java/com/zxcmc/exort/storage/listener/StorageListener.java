package com.zxcmc.exort.storage.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.marker.StorageMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

public class StorageListener implements Listener {
  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final BossBarManager bossBarManager;
  private final long peekDurationTicks;
  private final Material storageCarrier;

  public StorageListener(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      BossBarManager bossBarManager,
      long peekDurationTicks,
      Material storageCarrier) {
    this.plugin = plugin;
    this.regionProtection = regionProtection;
    this.worldEditWandGuard = worldEditWandGuard;
    this.bossBarManager = bossBarManager;
    this.peekDurationTicks = peekDurationTicks;
    this.storageCarrier = storageCarrier;
  }

  private boolean isOurStorage(Block block) {
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent();
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    Block block = event.getClickedBlock();
    if (block == null) return;
    if (!event.getAction().isRightClick()) return;
    if (!isOurStorage(block)) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (event.getPlayer().isSneaking()) {
      // allow vanilla placement
      return;
    }
    if (!regionProtection.canUse(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);
    var data = StorageMarker.get(plugin, block).orElse(null);
    if (data == null) return;
    bossBarManager.showPeek(
        data.storageId(), data.tier(), data.displayName(), event.getPlayer(), peekDurationTicks);
  }
}
