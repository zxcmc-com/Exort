package com.zxcmc.exort.sanity;

import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.core.DisplayTags;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;

public final class MarkerSanityService {
  private final Plugin plugin;
  private final DisplayRefreshService displayRefreshService;
  private final Supplier<ItemHologramManager> hologramManager;
  private final Supplier<BusService> busService;
  private final Database database;
  private final Material wireCarrier;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Material relayCarrier;

  public MarkerSanityService(MarkerSanityDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.displayRefreshService = dependencies.displayRefreshService();
    this.hologramManager = dependencies.hologramManager();
    this.busService = dependencies.busService();
    this.database = dependencies.database();
    this.wireCarrier = dependencies.wireCarrier();
    this.storageCarrier = dependencies.storageCarrier();
    this.terminalCarrier = dependencies.terminalCarrier();
    this.monitorCarrier = dependencies.monitorCarrier();
    this.busCarrier = dependencies.busCarrier();
    this.relayCarrier = dependencies.relayCarrier();
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
          if (!state.validOrMigratableCarrier()) {
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
                unregisterBus(block);
              }
            } else {
              registerBusState(block);
            }
          }
          if (RelayMarker.isRelay(plugin, block)) {
            if (!Carriers.matchesCarrier(block, relayCarrier)) {
              if (Carriers.isBarrier(block)) {
                migrateRelayCarrier(block);
              } else {
                RelayMarker.unlinkLoadedPair(plugin, block);
                RelayMarker.clear(plugin, block);
                displayRefreshService.removeRelayDisplay(block);
              }
            } else {
              cleanupInvalidRelayLink(block);
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
              unregisterStorageHologram(block);
            }
            return;
          }
          String storageId = data.get().storageId();
          String tierKey = data.get().tier().key();
          long tierMaxItems = data.get().tierMaxItems();
          boolean refreshAfterSync = data.get().fallback();
          database
              .setStorageTier(storageId, tierKey, tierMaxItems)
              .whenComplete(
                  (ignored, err) -> {
                    if (err != null) {
                      plugin
                          .getLogger()
                          .log(
                              Level.WARNING,
                              "Failed to repair storage tier for " + storageId,
                              unwrap(err));
                      return;
                    }
                    if (!refreshAfterSync || !plugin.isEnabled()) return;
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
                                registerStorageHologram(block);
                              });
                    } catch (IllegalStateException handoffFailure) {
                      // Plugin is disabling between the async repair and the sync handoff.
                    }
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
    boolean hadRelay = RelayMarker.isRelay(plugin, block);
    boolean hadWire = WireMarker.isWire(plugin, block);
    boolean validOrMigratableCarrier = validOrMigratableCarrier(block, hadWire);
    return new MarkerRootState(
        hadStorage,
        hadStorageCore,
        hadTerminal,
        hadMonitor,
        hadBus,
        hadRelay,
        hadWire,
        validOrMigratableCarrier);
  }

  private void clearImpossibleMarkerRoot(Block block, MarkerRootState state) {
    removeTaggedDisplaysAt(block);
    if (state.hadStorage() || state.hadStorageCore()) {
      displayRefreshService.removeStorageDisplay(block);
      unregisterStorageHologram(block);
    }
    if (state.hadTerminal()) {
      displayRefreshService.removeTerminalDisplay(block);
      unregisterTerminalHologram(block);
    }
    if (state.hadMonitor()) {
      displayRefreshService.removeMonitorDisplay(block);
    }
    if (state.hadBus()) {
      displayRefreshService.removeBusDisplay(block);
      unregisterBus(block);
    }
    if (state.hadRelay()) {
      RelayMarker.unlinkLoadedPair(plugin, block);
      displayRefreshService.removeRelayDisplay(block);
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
        || Carriers.matchesCarrier(block, busCarrier)
        || Carriers.matchesCarrier(block, relayCarrier);
  }

  private boolean validOrMigratableCarrier(Block block, boolean hadWire) {
    return validOrMigratableCarrier(
        matchesAnyConfiguredCarrier(block),
        hadWire,
        Carriers.isBarrier(block),
        Carriers.isChorusCarrier(block));
  }

  static boolean validOrMigratableCarrier(
      boolean matchesAnyConfiguredCarrier,
      boolean hadWire,
      boolean isBarrier,
      boolean isFullChorus) {
    return MarkerCarrierSanity.validOrMigratableCarrier(
        matchesAnyConfiguredCarrier, hadWire, isBarrier, isFullChorus);
  }

  static boolean isMigratableWireCarrier(boolean hadWire, boolean isBarrier, boolean isFullChorus) {
    return MarkerCarrierSanity.isMigratableWireCarrier(hadWire, isBarrier, isFullChorus);
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
    boolean hadRelay = RelayMarker.isRelay(plugin, block);

    removeTaggedDisplaysAt(block);
    StorageMarker.clear(plugin, block);
    StorageCoreMarker.clear(plugin, block);
    TerminalMarker.clear(plugin, block);
    MonitorMarker.clear(plugin, block);
    BusMarker.clear(plugin, block);
    RelayMarker.unlinkLoadedPair(plugin, block);
    RelayMarker.clear(plugin, block);
    WireMarker.setWire(plugin, block);
    Carriers.applyCarrier(block, wireCarrier);

    if (hadStorageDisplay) {
      displayRefreshService.removeStorageDisplay(block);
      unregisterStorageHologram(block);
    }
    if (hadTerminal) {
      displayRefreshService.removeTerminalDisplay(block);
      unregisterTerminalHologram(block);
    }
    if (hadMonitor) {
      displayRefreshService.removeMonitorDisplay(block);
    }
    if (hadBus) {
      displayRefreshService.removeBusDisplay(block);
      unregisterBus(block);
    }
    if (hadRelay) {
      displayRefreshService.removeRelayDisplay(block);
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
    registerTerminalHologram(block);
  }

  private void migrateStorageCarrier(Block block) {
    Carriers.applyCarrier(block, storageCarrier);
    displayRefreshService.refreshStorage(block);
    registerStorageHologram(block);
  }

  private void migrateMonitorCarrier(Block block) {
    Carriers.applyCarrier(block, monitorCarrier);
    displayRefreshService.refreshMonitor(block);
  }

  private void migrateBusCarrier(Block block) {
    Carriers.applyCarrier(block, busCarrier);
    displayRefreshService.refreshBus(block);
    registerBusState(block);
  }

  private void migrateRelayCarrier(Block block) {
    Carriers.applyCarrier(block, relayCarrier);
    displayRefreshService.refreshRelay(block);
    displayRefreshService.refreshNetworkFrom(block);
  }

  private void cleanupInvalidRelayLink(Block block) {
    RelayMarker.Link link = RelayMarker.link(plugin, block).orElse(null);
    if (link == null) {
      return;
    }
    Block peer = link.loadedBlock();
    if (peer == null) {
      return;
    }
    boolean valid =
        Carriers.matchesCarrier(peer, relayCarrier)
            && RelayMarker.isRelay(plugin, peer)
            && RelayMarker.link(plugin, peer).filter(back -> back.sameBlock(block)).isPresent();
    if (valid) {
      return;
    }
    RelayMarker.clearLink(plugin, block);
    displayRefreshService.refreshRelay(block);
    displayRefreshService.refreshNetworkFrom(block);
  }

  private void unregisterStorageHologram(Block block) {
    ItemHologramManager holograms = hologramManager.get();
    if (holograms != null) {
      holograms.unregisterStorage(block);
    }
  }

  private void unregisterTerminalHologram(Block block) {
    ItemHologramManager holograms = hologramManager.get();
    if (holograms != null) {
      holograms.unregisterTerminal(block);
    }
  }

  private void registerStorageHologram(Block block) {
    ItemHologramManager holograms = hologramManager.get();
    if (holograms != null) {
      holograms.registerStorage(block);
      holograms.invalidateAll();
    }
  }

  private void registerTerminalHologram(Block block) {
    ItemHologramManager holograms = hologramManager.get();
    if (holograms != null) {
      holograms.registerTerminal(block);
      holograms.invalidateAll();
    }
  }

  private void unregisterBus(Block block) {
    BusService buses = busService.get();
    if (buses != null) {
      buses.unregisterBus(block);
    }
  }

  private void registerBusState(Block block) {
    BusService buses = busService.get();
    if (buses != null) {
      BusMarker.get(plugin, block)
          .ifPresent(data -> buses.getOrCreateState(BusPos.of(block), data, block));
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
      boolean hadRelay,
      boolean hadWire,
      boolean validOrMigratableCarrier) {
    boolean hasPrimaryMarker() {
      return hadStorage
          || hadStorageCore
          || hadTerminal
          || hadMonitor
          || hadBus
          || hadRelay
          || hadWire;
    }
  }
}
