package com.zxcmc.exort.core.breaking;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.core.task.PluginTasks;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.DisplayTags;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.WireDisplayManager;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class BlockBreakHandler {
  private final ExortPlugin plugin;
  private final CustomItems customItems;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final ItemHologramManager hologramManager;
  private final WireDisplayManager wireDisplayManager;
  private final DisplayRefreshService displayRefreshService;
  private final BreakAnimationSender breakAnimationSender;

  public BlockBreakHandler(
      ExortPlugin plugin,
      CustomItems customItems,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      ItemHologramManager hologramManager,
      WireDisplayManager wireDisplayManager,
      DisplayRefreshService displayRefreshService,
      BreakAnimationSender breakAnimationSender) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.hologramManager = hologramManager;
    this.wireDisplayManager = wireDisplayManager;
    this.displayRefreshService = displayRefreshService;
    this.breakAnimationSender =
        breakAnimationSender == null ? BreakAnimationSender.NOOP : breakAnimationSender;
  }

  public enum BreakResult {
    BROKEN,
    DENIED,
    IGNORED
  }

  public void preloadStorageForBreakStart(Player player, Block block) {
    if (block == null) return;
    var marker = StorageMarker.get(plugin, block);
    if (marker.isEmpty() || !Carriers.matchesCarrier(block, storageCarrier)) {
      return;
    }
    String storageId = marker.get().storageId();
    if (plugin.getStorageManager().getLoadedCache(storageId).isPresent()) {
      return;
    }
    preloadStorageForBreak(player, storageId, false);
  }

  public BreakResult handleBreak(Player player, Block block, boolean checkRegion) {
    if (block == null) return BreakResult.IGNORED;

    if (TerminalMarker.isTerminal(plugin, block)) {
      if (!Carriers.matchesCarrier(block, terminalCarrier)) {
        TerminalMarker.clear(plugin, block);
        return BreakResult.BROKEN;
      }
      if (isRegionDenied(player, block, checkRegion)) {
        return BreakResult.DENIED;
      }
      TerminalKind kind = TerminalMarker.kind(plugin, block);
      playBreakParticles(block, BreakType.TERMINAL);
      block.setType(Material.AIR);
      if (shouldDrop(player)) {
        dropItemSafe(
            block,
            kind == TerminalKind.CRAFTING
                ? customItems.craftingTerminalItem()
                : customItems.terminalItem());
      }
      if (hologramManager != null) hologramManager.unregisterTerminal(block);
      if (hologramManager != null) hologramManager.invalidateAll();
      plugin.getSessionManager().closeSessionsForTerminal(block);
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (displayRefreshService != null) {
          displayRefreshService.refreshChunk(block.getChunk());
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.removeTerminalDisplay(block);
      }
      TerminalMarker.clear(plugin, block);
      invalidateNetwork();
      cleanupDisplays(block);
      return BreakResult.BROKEN;
    }

    if (MonitorMarker.isMonitor(plugin, block)) {
      if (!Carriers.matchesCarrier(block, monitorCarrier)) {
        MonitorMarker.clear(plugin, block);
        return BreakResult.BROKEN;
      }
      if (isRegionDenied(player, block, checkRegion)) {
        return BreakResult.DENIED;
      }
      playBreakParticles(block, BreakType.MONITOR);
      block.setType(Material.AIR);
      if (shouldDrop(player)) {
        dropItemSafe(block, customItems.monitorItem());
      }
      if (plugin.getMonitorDisplayManager() != null) {
        plugin.getMonitorDisplayManager().unregisterMonitor(block);
      }
      MonitorMarker.clear(plugin, block);
      invalidateNetwork();
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (displayRefreshService != null) {
          displayRefreshService.refreshChunk(block.getChunk());
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.refreshChunk(block.getChunk());
      }
      cleanupDisplays(block);
      return BreakResult.BROKEN;
    }

    if (BusMarker.isBus(plugin, block)) {
      if (!Carriers.matchesCarrier(block, busCarrier)) {
        BusMarker.clear(plugin, block);
        return BreakResult.BROKEN;
      }
      if (isRegionDenied(player, block, checkRegion)) {
        return BreakResult.DENIED;
      }
      var data = BusMarker.get(plugin, block).orElse(null);
      playBreakParticles(block, BreakType.BUS);
      block.setType(Material.AIR);
      if (shouldDrop(player)) {
        if (data != null && data.type() == BusType.EXPORT) {
          dropItemSafe(block, customItems.exportBusItem());
        } else {
          dropItemSafe(block, customItems.importBusItem());
        }
      }
      if (plugin.getBusSessionManager() != null) {
        plugin.getBusSessionManager().closeSessionsForBus(block);
      }
      if (plugin.getBusService() != null) {
        plugin.getBusService().unregisterBus(block);
      }
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (displayRefreshService != null) {
          displayRefreshService.refreshChunk(block.getChunk());
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.removeBusDisplay(block);
      }
      BusMarker.clear(plugin, block);
      invalidateNetwork();
      cleanupDisplays(block);
      return BreakResult.BROKEN;
    }

    if (WireMarker.isWire(plugin, block)) {
      if (!Carriers.matchesCarrier(block, wireMaterial)) {
        WireMarker.clearWire(plugin, block);
        return BreakResult.BROKEN;
      }
      if (isRegionDenied(player, block, checkRegion)) {
        return BreakResult.DENIED;
      }
      playBreakParticles(block, BreakType.WIRE);
      block.setType(Material.AIR);
      if (shouldDrop(player)) {
        dropItemSafe(block, customItems.wireItem());
      }
      WireMarker.clearWire(plugin, block);
      invalidateNetwork();
      plugin.getSessionManager().revalidateSessions();
      if (wireDisplayManager != null) {
        wireDisplayManager.removeWire(block);
        if (wireDisplayManager.isEnabled() && displayRefreshService != null) {
          displayRefreshService.refreshChunk(block.getChunk());
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.refreshChunk(block.getChunk());
      }
      if (hologramManager != null) hologramManager.invalidateAll();
      if (displayRefreshService != null) {
        displayRefreshService.refreshNetworkFrom(block);
      }
      cleanupDisplays(block);
      return BreakResult.BROKEN;
    }

    if (StorageCoreMarker.isCore(plugin, block)
        && !Carriers.matchesCarrier(block, storageCarrier)) {
      StorageCoreMarker.clear(plugin, block);
      return BreakResult.BROKEN;
    }
    if (StorageCoreMarker.isCore(plugin, block) && Carriers.matchesCarrier(block, storageCarrier)) {
      if (isRegionDenied(player, block, checkRegion)) {
        return BreakResult.DENIED;
      }
      playBreakParticles(block, BreakType.STORAGE);
      block.setType(Material.AIR);
      if (shouldDrop(player)) {
        dropItemSafe(block, customItems.storageCoreItem());
      }
      StorageCoreMarker.clear(plugin, block);
      if (displayRefreshService != null) {
        displayRefreshService.removeStorageDisplay(block);
      }
      if (displayRefreshService != null) {
        displayRefreshService.refreshChunk(block.getChunk());
      }
      cleanupDisplays(block);
      return BreakResult.BROKEN;
    }

    var marker = StorageMarker.get(plugin, block);
    if (marker.isPresent() && !Carriers.matchesCarrier(block, storageCarrier)) {
      StorageMarker.clear(plugin, block);
      return BreakResult.BROKEN;
    }
    if (marker.isEmpty()) return BreakResult.IGNORED;
    if (isRegionDenied(player, block, checkRegion)) {
      return BreakResult.DENIED;
    }
    String storageId = marker.get().storageId();
    StorageTier tier = marker.get().tier();
    StorageCache cache = plugin.getStorageManager().getLoadedCache(storageId).orElse(null);
    if (cache == null) {
      preloadStorageForBreak(player, storageId, true);
      return BreakResult.DENIED;
    }
    playBreakParticles(block, BreakType.STORAGE);
    block.setType(Material.AIR);
    long amount = cache.effectiveTotal();
    ItemStack drop = customItems.storageItem(tier, storageId, amount);
    dropItemSafe(block, drop);

    for (GuiSession session :
        plugin.getSessionManager().sessionsForStorage(storageId).stream().toList()) {
      plugin.getSessionManager().forceCloseSession(session.getViewer());
    }

    if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
      refreshWireNeighbors(block);
      if (displayRefreshService != null) {
        displayRefreshService.refreshChunk(block.getChunk());
      }
    }
    if (displayRefreshService != null) {
      displayRefreshService.refreshChunk(block.getChunk());
    }
    if (hologramManager != null) hologramManager.invalidateAll();
    if (hologramManager != null) hologramManager.unregisterStorage(block);
    if (displayRefreshService != null) {
      displayRefreshService.removeStorageDisplay(block);
    }
    StorageMarker.clear(plugin, block);
    invalidateNetwork();
    if (displayRefreshService != null) {
      displayRefreshService.refreshNetworkFrom(block);
    }
    cleanupDisplays(block);
    return BreakResult.BROKEN;
  }

  private void invalidateNetwork() {
    var cache = plugin.getNetworkGraphCache();
    if (cache != null) {
      cache.invalidateAll();
    }
  }

  private void playBreakParticles(Block block, BreakType type) {
    breakAnimationSender.breakBlock(block, type);
  }

  private void refreshWireNeighbors(Block block) {
    for (var face :
        new BlockFace[] {
          BlockFace.UP,
          BlockFace.DOWN,
          BlockFace.NORTH,
          BlockFace.SOUTH,
          BlockFace.EAST,
          BlockFace.WEST
        }) {
      Block neighbor = block.getRelative(face);
      if (Carriers.matchesCarrier(neighbor, wireMaterial) && WireMarker.isWire(plugin, neighbor)) {
        wireDisplayManager.updateWireAndNeighbors(neighbor);
      }
    }
  }

  private void cleanupDisplays(Block block) {
    var loc = block.getLocation().add(0.5, 0.5, 0.5);
    for (var ent : block.getWorld().getNearbyEntities(loc, 0.75, 0.75, 0.75)) {
      if (!(ent instanceof Display display)) continue;
      var tags = display.getScoreboardTags();
      if (tags.contains(DisplayTags.DISPLAY_TAG) || tags.contains(DisplayTags.HOLOGRAM_TAG)) {
        display.remove();
      }
    }
  }

  private boolean shouldDrop(Player player) {
    return player == null || player.getGameMode() != GameMode.CREATIVE;
  }

  private boolean isRegionDenied(Player player, Block block, boolean checkRegion) {
    return checkRegion && (player == null || !plugin.getRegionProtection().canBreak(player, block));
  }

  private void preloadStorageForBreak(Player player, String storageId, boolean warnPlayer) {
    if (warnPlayer && player != null && player.isOnline()) {
      plugin.getPlayerFeedback().warn(player, "message.storage_loading");
    }
    plugin
        .getStorageManager()
        .getOrLoad(storageId)
        .whenComplete(
            (cache, err) -> {
              if (err == null) return;
              ExortLog.log(
                  plugin, Level.WARNING, "Failed to load storage before break " + storageId, err);
              PluginTasks.runSyncIfEnabled(
                  plugin,
                  () -> {
                    if (player == null || !player.isOnline()) return;
                    plugin.getPlayerFeedback().error(player, "message.storage_load_failed");
                  });
            });
  }

  private void dropItemSafe(Block block, ItemStack item) {
    var world = block.getWorld();
    var loc = block.getLocation().add(0.5, 0.5, 0.5);
    var dropped = world.dropItem(loc, item);
    dropped.setVelocity(new Vector(0, 0, 0));
  }
}
