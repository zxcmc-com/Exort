package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class ChunkLoaderListener implements Listener {
  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final BossBarManager bossBarManager;
  private final Material carrier;
  private final long statusDurationTicks;

  public ChunkLoaderListener(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      BossBarManager bossBarManager,
      Material carrier,
      long statusDurationTicks) {
    this.plugin = plugin;
    this.regionProtection = regionProtection;
    this.worldEditWandGuard = worldEditWandGuard;
    this.bossBarManager = bossBarManager;
    this.carrier = carrier;
    this.statusDurationTicks = statusDurationTicks;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (!isChunkLoader(block)) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (event.getPlayer().isSneaking()) {
      return;
    }
    if (!regionProtection.canUse(event.getPlayer(), block)) {
      event.setCancelled(true);
      return;
    }
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);
    ChunkLoaderMarker.get(plugin, block)
        .ifPresent(
            data ->
                bossBarManager.showChunkLoaderStatus(
                    data.id(), event.getPlayer(), statusDurationTicks));
  }

  private boolean isChunkLoader(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, carrier)
        && ChunkLoaderMarker.isChunkLoader(plugin, block);
  }
}
