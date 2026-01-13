package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.display.DisplayRefreshService;
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
    var keys = chunk.getPersistentDataContainer().getKeys();
    if (keys.isEmpty()) return;
    for (var key : keys) {
      if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;
      String raw = key.getKey();
      if (raw.startsWith("wire_display_")
          || raw.startsWith("storage_display_")
          || raw.startsWith("terminal_display_")
          || raw.startsWith("monitor_display_")
          || raw.startsWith("bus_display_")
          || raw.startsWith("monitor_item_")
          || raw.startsWith("monitor_item_blob_")
          || raw.startsWith("monitor_text_display_")) {
        continue;
      }
      if (raw.startsWith("wire_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("wire_".length()));
        if (xyz == null) continue;
        Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
        if (!Carriers.matchesCarrier(block, wireCarrier)) {
          if (Carriers.isBarrier(block) || Carriers.isChorusCarrier(block)) {
            migrateWireCarrier(block);
          } else {
            WireMarker.clearWire(plugin, block);
          }
        }
        continue;
      }
      if (raw.startsWith("terminal_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("terminal_".length()));
        if (xyz == null) continue;
        Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
        if (!Carriers.matchesCarrier(block, terminalCarrier)) {
          if (Carriers.isBarrier(block)) {
            migrateTerminalCarrier(block);
          } else {
            TerminalMarker.clear(plugin, block);
          }
        }
        continue;
      }
      if (raw.startsWith("monitor_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("monitor_".length()));
        if (xyz == null) continue;
        Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
        if (!Carriers.matchesCarrier(block, monitorCarrier)) {
          if (Carriers.isBarrier(block)) {
            migrateMonitorCarrier(block);
          } else {
            MonitorMarker.clear(plugin, block);
            displayRefreshService.removeMonitorDisplay(block);
          }
        }
        continue;
      }
      if (raw.startsWith("bus_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("bus_".length()));
        if (xyz == null) continue;
        Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
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
                  data -> plugin.getBusService().getOrCreateState(BusPos.of(block), data, block));
        }
        continue;
      }
      if (raw.startsWith("storage_core_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("storage_core_".length()));
        if (xyz == null) continue;
        Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
        if (!Carriers.matchesCarrier(block, storageCarrier)) {
          if (Carriers.isBarrier(block)) {
            Carriers.applyCarrier(block, storageCarrier);
          } else {
            StorageCoreMarker.clear(plugin, block);
          }
        }
        continue;
      }
      if (raw.startsWith("storage_")) {
        int[] xyz = MarkerCoords.parseXYZ(raw.substring("storage_".length()));
        if (xyz == null) continue;
        Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
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
          continue;
        }
        var data = StorageMarker.get(plugin, block);
        if (data.isEmpty()) {
          StorageMarker.clear(plugin, block);
          block.setType(Material.AIR, false);
          displayRefreshService.removeStorageDisplay(block);
          if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().unregisterStorage(block);
          }
          continue;
        }
        String storageId = data.get().storageId();
        String tierKey = data.get().tier().key();
        if (plugin.getStorageManager().peekLoadedCache(storageId).isPresent()) {
          continue;
        }
        plugin
            .getDatabase()
            .storageExists(storageId)
            .thenAccept(
                exists -> {
                  if (exists) return;
                  plugin.getDatabase().setStorageTier(storageId, tierKey);
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> {
                            displayRefreshService.refreshStorage(block);
                            if (plugin.getHologramManager() != null) {
                              plugin.getHologramManager().registerStorage(block);
                              plugin.getHologramManager().invalidateAll();
                            }
                          });
                });
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
}
