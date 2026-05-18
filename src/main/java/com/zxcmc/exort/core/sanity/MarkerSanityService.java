package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.ChunkMarkerStore;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.display.DisplayRefreshService;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class MarkerSanityService {
  private final ExortPlugin plugin;
  private final DisplayRefreshService displayRefreshService;
  private final Material wireCarrier;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;

  public MarkerSanityService(
      ExortPlugin plugin,
      DisplayRefreshService displayRefreshService,
      Material wireCarrier,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier) {
    this.plugin = plugin;
    this.displayRefreshService = displayRefreshService;
    this.wireCarrier = wireCarrier;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
  }

  public void cleanupStaleMarkers(Chunk chunk) {
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) return;
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (WireMarker.isWire(plugin, block) && !Carriers.matchesCarrier(block, wireCarrier)) {
            if (Carriers.isBarrier(block) || Carriers.isChorusCarrier(block)) {
              migrateWireCarrier(block);
            } else {
              WireMarker.clearWire(plugin, block);
            }
          }
          if (TerminalMarker.isTerminal(plugin, block)
              && !Carriers.matchesCarrier(block, terminalCarrier)) {
            if (Carriers.isBarrier(block)) {
              migrateTerminalCarrier(block);
            } else {
              TerminalMarker.clear(plugin, block);
            }
          }
          if (MonitorMarker.isMonitor(plugin, block)
              && !Carriers.matchesCarrier(block, monitorCarrier)) {
            if (Carriers.isBarrier(block)) {
              migrateMonitorCarrier(block);
            } else {
              MonitorMarker.clear(plugin, block);
              displayRefreshService.removeMonitorDisplay(block);
            }
          }
          if (BusMarker.isBus(plugin, block)) {
            if (!Carriers.matchesCarrier(block, busCarrier)) {
              if (Carriers.isBarrier(block)) {
                migrateBusCarrier(block);
              } else {
                BusMarker.clear(plugin, block);
                displayRefreshService.removeBusDisplay(block);
                if (plugin.getBusService() != null) {
                  plugin.getBusService().unregisterBus(block);
                }
              }
            } else if (plugin.getBusService() != null) {
              BusMarker.get(plugin, block)
                  .ifPresent(
                      data ->
                          plugin.getBusService().getOrCreateState(BusPos.of(block), data, block));
            }
          }
          if (StorageCoreMarker.isCore(plugin, block)
              && !Carriers.matchesCarrier(block, storageCarrier)) {
            if (Carriers.isBarrier(block)) {
              Carriers.applyCarrier(block, storageCarrier);
            } else {
              StorageCoreMarker.clear(plugin, block);
            }
          }
          var data = StorageMarker.get(plugin, block);
          if (data.isEmpty()) {
            return;
          }
          if (!Carriers.matchesCarrier(block, storageCarrier)) {
            if (Carriers.isBarrier(block)) {
              migrateStorageCarrier(block);
            } else {
              StorageMarker.clear(plugin, block);
              displayRefreshService.removeStorageDisplay(block);
              if (plugin.getHologramManager() != null) {
                plugin.getHologramManager().unregisterStorage(block);
              }
            }
            return;
          }
          String storageId = data.get().storageId();
          String tierKey = data.get().tier().key();
          if (plugin.getStorageManager().peekLoadedCache(storageId).isPresent()) {
            return;
          }
          plugin
              .getDatabase()
              .storageExists(storageId)
              .whenComplete(
                  (exists, err) -> {
                    if (err != null) {
                      plugin
                          .getLogger()
                          .log(
                              Level.WARNING,
                              "Failed to check storage marker " + storageId,
                              unwrap(err));
                      return;
                    }
                    if (exists) return;
                    plugin
                        .getDatabase()
                        .setStorageTier(storageId, tierKey)
                        .whenComplete(
                            (ignored, tierErr) -> {
                              if (tierErr != null) {
                                plugin
                                    .getLogger()
                                    .log(
                                        Level.WARNING,
                                        "Failed to repair storage tier for " + storageId,
                                        unwrap(tierErr));
                                return;
                              }
                              if (!plugin.isEnabled()) return;
                              try {
                                Bukkit.getScheduler()
                                    .runTask(
                                        plugin,
                                        () -> {
                                          if (!plugin.isEnabled()) return;
                                          var current = StorageMarker.get(plugin, block);
                                          if (current.isEmpty()
                                              || !storageId.equals(current.get().storageId())
                                              || !Carriers.matchesCarrier(block, storageCarrier)) {
                                            return;
                                          }
                                          displayRefreshService.refreshStorage(block);
                                          if (plugin.getHologramManager() != null) {
                                            plugin.getHologramManager().registerStorage(block);
                                            plugin.getHologramManager().invalidateAll();
                                          }
                                        });
                              } catch (IllegalStateException handoffFailure) {
                                // Plugin is disabling between the async repair and the sync
                                // handoff.
                              }
                            });
                  });
        });
  }

  private Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }

  private void migrateWireCarrier(Block block) {
    Carriers.applyCarrier(block, wireCarrier);
    displayRefreshService.refreshWireAndNeighbors(block);
  }

  private void migrateTerminalCarrier(Block block) {
    Carriers.applyCarrier(block, terminalCarrier);
    displayRefreshService.refreshTerminal(block);
    if (plugin.getHologramManager() != null) {
      plugin.getHologramManager().registerTerminal(block);
      plugin.getHologramManager().invalidateAll();
    }
  }

  private void migrateStorageCarrier(Block block) {
    Carriers.applyCarrier(block, storageCarrier);
    displayRefreshService.refreshStorage(block);
    if (plugin.getHologramManager() != null) {
      plugin.getHologramManager().registerStorage(block);
      plugin.getHologramManager().invalidateAll();
    }
  }

  private void migrateMonitorCarrier(Block block) {
    Carriers.applyCarrier(block, monitorCarrier);
    displayRefreshService.refreshMonitor(block);
  }

  private void migrateBusCarrier(Block block) {
    Carriers.applyCarrier(block, busCarrier);
    displayRefreshService.refreshBus(block);
    if (plugin.getBusService() != null) {
      BusMarker.get(plugin, block)
          .ifPresent(
              data -> plugin.getBusService().getOrCreateState(BusPos.of(block), data, block));
    }
  }
}
