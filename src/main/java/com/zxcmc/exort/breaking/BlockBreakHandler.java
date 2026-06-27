package com.zxcmc.exort.breaking;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayTags;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class BlockBreakHandler {
  private final JavaPlugin plugin;
  private final CustomItems customItems;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Material relayCarrier;
  private final ItemHologramManager hologramManager;
  private final WireDisplayManager wireDisplayManager;
  private final DisplayRefreshService displayRefreshService;
  private final BreakAnimationSender breakAnimationSender;
  private final StorageManager storageManager;
  private final SessionManager sessionManager;
  private final Supplier<MonitorDisplayManager> monitorDisplayManager;
  private final Supplier<BusSessionManager> busSessionManager;
  private final Supplier<BusService> busService;
  private final Supplier<NetworkGraphCache> networkGraphCache;
  private final RegionProtection regionProtection;
  private final PlayerFeedback playerFeedback;

  public BlockBreakHandler(BlockBreakHandlerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.customItems = dependencies.customItems();
    this.wireMaterial = dependencies.wireMaterial();
    this.storageCarrier = dependencies.storageCarrier();
    this.terminalCarrier = dependencies.terminalCarrier();
    this.monitorCarrier = dependencies.monitorCarrier();
    this.busCarrier = dependencies.busCarrier();
    this.relayCarrier = dependencies.relayCarrier();
    this.hologramManager = dependencies.hologramManager();
    this.wireDisplayManager = dependencies.wireDisplayManager();
    this.displayRefreshService = dependencies.displayRefreshService();
    this.breakAnimationSender =
        dependencies.breakAnimationSender() == null
            ? BreakAnimationSender.NOOP
            : dependencies.breakAnimationSender();
    this.storageManager = dependencies.storageManager();
    this.sessionManager = dependencies.sessionManager();
    this.monitorDisplayManager = dependencies.monitorDisplayManager();
    this.busSessionManager = dependencies.busSessionManager();
    this.busService = dependencies.busService();
    this.networkGraphCache = dependencies.networkGraphCache();
    this.regionProtection = dependencies.regionProtection();
    this.playerFeedback = dependencies.playerFeedback();
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
    if (storageManager.getLoadedCache(storageId).isPresent()) {
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
      sessionManager.closeSessionsForTerminal(block);
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (displayRefreshService != null) {
          displayRefreshService.refreshBlockAndNeighbors(block);
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.removeTerminalDisplay(block);
      }
      TerminalMarker.clear(plugin, block);
      invalidateNetwork(block);
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
      var monitorDisplays = monitorDisplayManager.get();
      if (monitorDisplays != null) {
        monitorDisplays.unregisterMonitor(block);
      }
      MonitorMarker.clear(plugin, block);
      invalidateNetwork(block);
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (displayRefreshService != null) {
          displayRefreshService.refreshBlockAndNeighbors(block);
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.refreshBlockAndNeighbors(block);
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
      var busSessions = busSessionManager.get();
      if (busSessions != null) {
        busSessions.closeSessionsForBus(block);
      }
      var buses = busService.get();
      if (buses != null) {
        buses.unregisterBus(block);
      }
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (displayRefreshService != null) {
          displayRefreshService.refreshBlockAndNeighbors(block);
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.removeBusDisplay(block);
      }
      BusMarker.clear(plugin, block);
      invalidateNetwork(block);
      cleanupDisplays(block);
      return BreakResult.BROKEN;
    }

    if (RelayMarker.isRelay(plugin, block)) {
      if (!Carriers.matchesCarrier(block, relayCarrier)) {
        RelayMarker.unlinkLoadedPair(plugin, block);
        RelayMarker.clear(plugin, block);
        return BreakResult.BROKEN;
      }
      if (isRegionDenied(player, block, checkRegion)) {
        return BreakResult.DENIED;
      }
      Block peer = RelayMarker.link(plugin, block).map(RelayMarker.Link::loadedBlock).orElse(null);
      playBreakParticles(block, BreakType.RELAY);
      block.setType(Material.AIR);
      if (shouldDrop(player)) {
        dropItemSafe(block, customItems.relayItem());
      }
      RelayMarker.unlinkLoadedPair(plugin, block);
      RelayMarker.clear(plugin, block);
      invalidateNetwork(block);
      if (peer != null) {
        invalidateNetwork(peer);
      }
      sessionManager.revalidateSessions();
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        refreshWireNeighbors(block);
        if (peer != null) {
          refreshWireNeighbors(peer);
        }
        if (displayRefreshService != null) {
          displayRefreshService.refreshBlockAndNeighbors(block);
          if (peer != null) {
            displayRefreshService.refreshBlockAndNeighbors(peer);
          }
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.removeRelayDisplay(block);
        displayRefreshService.refreshBlockAndNeighbors(block);
        displayRefreshService.refreshNetworkFrom(block);
        if (peer != null) {
          displayRefreshService.refreshBlockAndNeighbors(peer);
          displayRefreshService.refreshNetworkFrom(peer);
        }
      }
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
      invalidateNetwork(block);
      sessionManager.revalidateSessions();
      if (wireDisplayManager != null) {
        wireDisplayManager.removeWire(block);
        if (wireDisplayManager.isEnabled() && displayRefreshService != null) {
          displayRefreshService.refreshBlockAndNeighbors(block);
        }
      }
      if (displayRefreshService != null) {
        displayRefreshService.refreshBlockAndNeighbors(block);
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
        displayRefreshService.refreshBlockAndNeighbors(block);
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
    String displayName = marker.get().displayName();
    StorageCache cache = storageManager.getLoadedCache(storageId).orElse(null);
    if (cache == null) {
      preloadStorageForBreak(player, storageId, true);
      return BreakResult.DENIED;
    }
    playBreakParticles(block, BreakType.STORAGE);
    block.setType(Material.AIR);
    long amount = cache.effectiveTotal();
    if (displayName == null) {
      displayName = cache.getDisplayName();
    }
    ItemStack drop = customItems.storageItem(tier, storageId, amount, displayName);
    dropItemSafe(block, drop);

    for (GuiSession session : sessionManager.sessionsForStorage(storageId).stream().toList()) {
      sessionManager.forceCloseSession(session.getViewer());
    }

    if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
      refreshWireNeighbors(block);
      if (displayRefreshService != null) {
        displayRefreshService.refreshBlockAndNeighbors(block);
      }
    }
    if (displayRefreshService != null) {
      displayRefreshService.refreshBlockAndNeighbors(block);
    }
    if (hologramManager != null) hologramManager.invalidateAll();
    if (hologramManager != null) hologramManager.unregisterStorage(block);
    if (displayRefreshService != null) {
      displayRefreshService.removeStorageDisplay(block);
    }
    StorageMarker.clear(plugin, block);
    invalidateNetwork(block);
    if (displayRefreshService != null) {
      displayRefreshService.refreshNetworkFrom(block);
    }
    cleanupDisplays(block);
    return BreakResult.BROKEN;
  }

  private void invalidateNetwork(Block block) {
    var cache = networkGraphCache.get();
    if (cache != null) {
      cache.invalidateAround(block);
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
    return checkRegion && (player == null || !regionProtection.canBreak(player, block));
  }

  private void preloadStorageForBreak(Player player, String storageId, boolean warnPlayer) {
    if (warnPlayer && player != null && player.isOnline()) {
      playerFeedback.warn(player, "message.storage_loading");
    }
    storageManager
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
                    playerFeedback.error(player, "message.storage_load_failed");
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
