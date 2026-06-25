package com.zxcmc.exort.integration.chorusfix.embedded;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public final class ChorusUpdateListener implements Listener {
  private final ChorusUpdateService updates;

  public ChorusUpdateListener(ChorusUpdateService updates) {
    this.updates = updates;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    updates.enqueueAfterBlockBreakNextTick(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(BlockPlaceEvent event) {
    updates.enqueueAfterBlockPlaceNextTick(event.getBlockPlaced());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPhysics(BlockPhysicsEvent event) {
    updates.enqueueIfChorusRelatedNextTick(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockGrow(BlockGrowEvent event) {
    updates.enqueueVanillaMutationNeighborhoodNextTick(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockSpread(BlockSpreadEvent event) {
    updates.enqueueVanillaMutationNeighborhoodNextTick(event.getBlock());
    updates.enqueueVanillaMutationNeighborhoodNextTick(event.getSource());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockFade(BlockFadeEvent event) {
    updates.enqueueNeighborhoodNextTick(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityChangeBlock(EntityChangeBlockEvent event) {
    updates.enqueueNeighborhoodNextTick(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    updates.enqueueNeighborhoodNextTick(event.getBlock());
    for (Block block : event.getBlocks()) {
      updates.enqueueNeighborhoodNextTick(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    updates.enqueueNeighborhoodNextTick(event.getBlock());
    for (Block block : event.getBlocks()) {
      updates.enqueueNeighborhoodNextTick(block);
    }
  }
}
