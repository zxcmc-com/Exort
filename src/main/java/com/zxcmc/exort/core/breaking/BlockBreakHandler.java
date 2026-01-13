package com.zxcmc.exort.core.breaking;

import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.DisplayTags;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.WireDisplayManager;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
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
      DisplayRefreshService displayRefreshService) {
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
  }

  public boolean handleBreak(Player player, Block block, boolean checkRegion) {
    if (block == null) return false;

    if (TerminalMarker.isTerminal(plugin, block)) {
      if (!Carriers.matchesCarrier(block, terminalCarrier)) {
        TerminalMarker.clear(plugin, block);
        return true;
      }
      if (checkRegion && !plugin.getRegionProtection().canBreak(player, block)) {
        return true;
      }
      block.setType(Material.AIR);
      if (player.getGameMode() != GameMode.CREATIVE) {
        TerminalKind kind = TerminalMarker.kind(plugin, block);
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
      return true;
    }

    if (MonitorMarker.isMonitor(plugin, block)) {
      if (!Carriers.matchesCarrier(block, monitorCarrier)) {
        MonitorMarker.clear(plugin, block);
        return true;
      }
      if (checkRegion && !plugin.getRegionProtection().canBreak(player, block)) {
        return true;
      }
      block.setType(Material.AIR);
      if (player.getGameMode() != GameMode.CREATIVE) {
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
      return true;
    }

    if (BusMarker.isBus(plugin, block)) {
      if (!Carriers.matchesCarrier(block, busCarrier)) {
        BusMarker.clear(plugin, block);
        return true;
      }
      if (checkRegion && !plugin.getRegionProtection().canBreak(player, block)) {
        return true;
      }
      var data = BusMarker.get(plugin, block).orElse(null);
      block.setType(Material.AIR);
      if (player.getGameMode() != GameMode.CREATIVE) {
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
      return true;
    }

    if (WireMarker.isWire(plugin, block)) {
      if (!Carriers.matchesCarrier(block, wireMaterial)) {
        WireMarker.clearWire(plugin, block);
        return true;
      }
      if (checkRegion && !plugin.getRegionProtection().canBreak(player, block)) {
        return true;
      }
      block.setType(Material.AIR);
      if (player.getGameMode() != GameMode.CREATIVE) {
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
      return true;
    }

    if (StorageCoreMarker.isCore(plugin, block)
        && !Carriers.matchesCarrier(block, storageCarrier)) {
      StorageCoreMarker.clear(plugin, block);
      return true;
    }
    if (StorageCoreMarker.isCore(plugin, block) && Carriers.matchesCarrier(block, storageCarrier)) {
      if (checkRegion && !plugin.getRegionProtection().canBreak(player, block)) {
        return true;
      }
      block.setType(Material.AIR);
      if (player.getGameMode() != GameMode.CREATIVE) {
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
      return true;
    }

    var marker = StorageMarker.get(plugin, block);
    if (marker.isPresent() && !Carriers.matchesCarrier(block, storageCarrier)) {
      StorageMarker.clear(plugin, block);
      return true;
    }
    if (marker.isEmpty()) return false;
    if (checkRegion && !plugin.getRegionProtection().canBreak(player, block)) {
      return true;
    }
    String storageId = marker.get().storageId();
    StorageTier tier = marker.get().tier();
    block.setType(Material.AIR);
    long amount =
        plugin
            .getStorageManager()
            .getLoadedCache(storageId)
            .map(StorageCache::effectiveTotal)
            .orElse(0L);
    ItemStack drop = customItems.storageItem(tier, storageId, amount);
    dropItemSafe(block, drop);

    for (GuiSession session :
        plugin.getSessionManager().sessionsForStorage(storageId).stream().toList()) {
      session.getViewer().closeInventory();
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
    return true;
  }

  private void invalidateNetwork() {
    var cache = plugin.getNetworkGraphCache();
    if (cache != null) {
      cache.invalidateAll();
    }
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

  private void dropItemSafe(Block block, ItemStack item) {
    var world = block.getWorld();
    var loc = block.getLocation().add(0.5, 0.5, 0.5);
    var dropped = world.dropItem(loc, item);
    dropped.setVelocity(new Vector(0, 0, 0));
  }
}
