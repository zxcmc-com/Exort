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
import com.zxcmc.exort.display.DisplayTags;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;

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

  public int repairFullChorusWires(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) return 0;
    int repaired = 0;
    int minY = chunk.getWorld().getMinHeight();
    int maxY = chunk.getWorld().getMaxHeight();
    int baseX = chunk.getX() << 4;
    int baseZ = chunk.getZ() << 4;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        for (int y = minY; y < maxY; y++) {
          Block block = chunk.getWorld().getBlockAt(baseX + x, y, baseZ + z);
          if (!Carriers.isFullChorus(block) || WireMarker.isWire(plugin, block)) {
            continue;
          }
          repairFullChorusWire(block);
          repaired++;
        }
      }
    }
    return repaired;
  }

  public CleanupResult cleanupStaleMarkers(Chunk chunk) {
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) return CleanupResult.empty();
    int[] acceptedRoots = {0};
    int[] skippedRoots = {0};
    int[] clearedRoots = {0};
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          MarkerRootState state = markerRootState(block);
          if (!state.hasPrimaryMarker()) {
            skippedRoots[0]++;
            return;
          }
          if (!state.matchesAnyConfiguredCarrier()) {
            clearImpossibleMarkerRoot(block, state);
            clearedRoots[0]++;
            return;
          }
          acceptedRoots[0]++;
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
    return new CleanupResult(acceptedRoots[0], skippedRoots[0], clearedRoots[0]);
  }

  private MarkerRootState markerRootState(Block block) {
    boolean hadStorage = StorageMarker.get(plugin, block).isPresent();
    boolean hadStorageCore = StorageCoreMarker.isCore(plugin, block);
    boolean hadTerminal = TerminalMarker.isTerminal(plugin, block);
    boolean hadMonitor = MonitorMarker.isMonitor(plugin, block);
    boolean hadBus = BusMarker.isBus(plugin, block);
    boolean hadWire = WireMarker.isWire(plugin, block);
    boolean matchesCarrier = matchesAnyConfiguredCarrier(block);
    return new MarkerRootState(
        hadStorage, hadStorageCore, hadTerminal, hadMonitor, hadBus, hadWire, matchesCarrier);
  }

  private void clearImpossibleMarkerRoot(Block block, MarkerRootState state) {
    removeTaggedDisplaysAt(block);
    if (state.hadStorage() || state.hadStorageCore()) {
      displayRefreshService.removeStorageDisplay(block);
      if (plugin.getHologramManager() != null) {
        plugin.getHologramManager().unregisterStorage(block);
      }
    }
    if (state.hadTerminal()) {
      displayRefreshService.removeTerminalDisplay(block);
      if (plugin.getHologramManager() != null) {
        plugin.getHologramManager().unregisterTerminal(block);
      }
    }
    if (state.hadMonitor()) {
      displayRefreshService.removeMonitorDisplay(block);
    }
    if (state.hadBus()) {
      displayRefreshService.removeBusDisplay(block);
      if (plugin.getBusService() != null) {
        plugin.getBusService().unregisterBus(block);
      }
    }
    if (state.hadWire()) {
      displayRefreshService.refreshWireAndNeighbors(block);
    }
    ChunkMarkerStore.clearBlock(plugin, block);
  }

  private boolean matchesAnyConfiguredCarrier(Block block) {
    return Carriers.matchesCarrier(block, wireCarrier)
        || Carriers.matchesCarrier(block, storageCarrier)
        || Carriers.matchesCarrier(block, terminalCarrier)
        || Carriers.matchesCarrier(block, monitorCarrier)
        || Carriers.matchesCarrier(block, busCarrier);
  }

  private Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }

  private void repairFullChorusWire(Block block) {
    boolean hadStorage = StorageMarker.get(plugin, block).isPresent();
    boolean hadStorageDisplay = hadStorage || StorageCoreMarker.isCore(plugin, block);
    boolean hadTerminal = TerminalMarker.isTerminal(plugin, block);
    boolean hadMonitor = MonitorMarker.isMonitor(plugin, block);
    boolean hadBus = BusMarker.isBus(plugin, block);

    removeTaggedDisplaysAt(block);
    StorageMarker.clear(plugin, block);
    StorageCoreMarker.clear(plugin, block);
    TerminalMarker.clear(plugin, block);
    MonitorMarker.clear(plugin, block);
    BusMarker.clear(plugin, block);
    WireMarker.setWire(plugin, block);
    Carriers.applyCarrier(block, wireCarrier);

    if (hadStorageDisplay) {
      displayRefreshService.removeStorageDisplay(block);
      if (plugin.getHologramManager() != null) {
        plugin.getHologramManager().unregisterStorage(block);
      }
    }
    if (hadTerminal) {
      displayRefreshService.removeTerminalDisplay(block);
      if (plugin.getHologramManager() != null) {
        plugin.getHologramManager().unregisterTerminal(block);
      }
    }
    if (hadMonitor) {
      displayRefreshService.removeMonitorDisplay(block);
    }
    if (hadBus) {
      displayRefreshService.removeBusDisplay(block);
      if (plugin.getBusService() != null) {
        plugin.getBusService().unregisterBus(block);
      }
    }
    displayRefreshService.refreshWireAndNeighbors(block);
    displayRefreshService.refreshNetworkFrom(block);
  }

  private void removeTaggedDisplaysAt(Block block) {
    if (block == null || block.getWorld() == null) return;
    var center = block.getLocation().add(0.5, 0.5, 0.5);
    for (var entity : block.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
      if (!(entity instanceof Display display)) continue;
      if (!display.getLocation().getBlock().equals(block)) continue;
      var tags = display.getScoreboardTags();
      if (tags.contains(DisplayTags.DISPLAY_TAG) || tags.contains(DisplayTags.HOLOGRAM_TAG)) {
        display.remove();
      }
    }
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

  public record CleanupResult(int acceptedRoots, int skippedRoots, int clearedRoots) {
    public static CleanupResult empty() {
      return new CleanupResult(0, 0, 0);
    }

    public boolean changed() {
      return clearedRoots > 0;
    }

    public boolean hasAnyRoot() {
      return acceptedRoots > 0 || skippedRoots > 0 || clearedRoots > 0;
    }
  }

  private record MarkerRootState(
      boolean hadStorage,
      boolean hadStorageCore,
      boolean hadTerminal,
      boolean hadMonitor,
      boolean hadBus,
      boolean hadWire,
      boolean matchesAnyConfiguredCarrier) {
    boolean hasPrimaryMarker() {
      return hadStorage || hadStorageCore || hadTerminal || hadMonitor || hadBus || hadWire;
    }
  }
}
