package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class ChunkLoaderListener implements Listener {
  private static final String ADMIN_PERMISSION = "exort.storagenetwork.admin";

  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final PlayerFeedback playerFeedback;
  private final BossBarManager bossBarManager;
  private final ChunkLoaderService chunkLoaderService;
  private final Supplier<DisplayRefreshService> displayRefreshService;
  private final Material carrier;
  private final long statusDurationTicks;

  public ChunkLoaderListener(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      PlayerFeedback playerFeedback,
      BossBarManager bossBarManager,
      ChunkLoaderService chunkLoaderService,
      Supplier<DisplayRefreshService> displayRefreshService,
      Material carrier,
      long statusDurationTicks) {
    this.plugin = plugin;
    this.regionProtection = regionProtection;
    this.worldEditWandGuard = worldEditWandGuard;
    this.playerFeedback = playerFeedback;
    this.bossBarManager = bossBarManager;
    this.chunkLoaderService = chunkLoaderService;
    this.displayRefreshService = displayRefreshService;
    this.carrier = carrier;
    this.statusDurationTicks = statusDurationTicks;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    Optional<ChunkLoaderMarker.Data> marker = chunkLoader(block);
    if (marker.isEmpty()) return;
    Player player = event.getPlayer();
    if (worldEditWandGuard.isWorldEditWand(player, event.getItem())) return;
    if (!regionProtection.canUse(player, block)) {
      consume(event);
      playerFeedback.error(player, "message.no_permission");
      return;
    }
    ChunkLoaderMarker.Data data = marker.get();
    if (player.isSneaking()) {
      consume(event);
      toggle(player, block, data, false);
      return;
    }
    if (!data.enabled()) {
      consume(event);
      toggle(player, block, data, true);
      return;
    }
    consume(event);
    bossBarManager.showChunkLoaderStatus(data.type(), data.id(), player, statusDurationTicks);
  }

  private Optional<ChunkLoaderMarker.Data> chunkLoader(Block block) {
    if (block == null || !Carriers.matchesCarrier(block, carrier)) {
      return Optional.empty();
    }
    return ChunkLoaderMarker.get(plugin, block);
  }

  private void toggle(Player player, Block block, ChunkLoaderMarker.Data data, boolean enabled) {
    if (!canToggle(player, data)) {
      playerFeedback.error(player, "message.chunk_loader_toggle_denied");
      return;
    }
    ChunkLoaderService.ToggleResult result = chunkLoaderService.setEnabled(player, block, enabled);
    switch (result) {
      case ENABLED -> {
        playerFeedback.success(player, "message.chunk_loader_enabled");
        refresh(block);
      }
      case DISABLED -> {
        playerFeedback.success(player, "message.chunk_loader_disabled");
        refresh(block);
      }
      case ALREADY_ENABLED -> playerFeedback.info(player, "message.chunk_loader_already_enabled");
      case ALREADY_DISABLED -> playerFeedback.warn(player, "message.chunk_loader_already_disabled");
      case MISSING -> playerFeedback.error(player, "message.chunk_loader_toggle_failed");
    }
  }

  private boolean canToggle(Player player, ChunkLoaderMarker.Data data) {
    if (data.type() != ChunkLoaderType.PERSONAL_CHUNK_LOADER) {
      return true;
    }
    if (data.placedByUuid() != null && data.placedByUuid().equals(player.getUniqueId())) {
      return true;
    }
    return player.hasPermission(ADMIN_PERMISSION);
  }

  private void refresh(Block block) {
    DisplayRefreshService refresh =
        displayRefreshService == null ? null : displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshChunkLoader(block);
    }
  }

  private void consume(PlayerInteractEvent event) {
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);
  }
}
