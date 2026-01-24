package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.ChunkMarkerStore;
import com.zxcmc.exort.core.marker.DisplayMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.display.DisplayTags;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;

public final class DisplayCleanupService {
  private static final String[] DISPLAY_TYPES =
      new String[] {
        "wire", "storage", "terminal", "monitor", "bus", "monitor_item", "monitor_text"
      };
  private final ExortPlugin plugin;
  private final Material wireCarrier;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;

  public DisplayCleanupService(
      ExortPlugin plugin,
      Material wireCarrier,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier) {
    this.plugin = plugin;
    this.wireCarrier = wireCarrier;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
  }

  public void cleanupDisplays(Chunk chunk) {
    if (chunk.getEntities().length == 0) {
      return;
    }
    Set<UUID> monitorItems = null;
    Set<UUID> monitorTexts = null;
    for (var ent : chunk.getEntities()) {
      if (!(ent instanceof Display display)) continue;
      var tags = display.getScoreboardTags();
      boolean trackedDisplay =
          tags.contains(DisplayTags.DISPLAY_TAG) || tags.contains(DisplayTags.HOLOGRAM_TAG);
      if (!trackedDisplay) continue;
      if (tags.contains(DisplayTags.MONITOR_ITEM_TAG)) {
        if (monitorItems == null) {
          monitorItems = new HashSet<>();
          monitorTexts = new HashSet<>();
          loadMonitorDisplayIds(chunk, monitorItems, monitorTexts);
        }
        if (!monitorItems.contains(display.getUniqueId())) {
          display.remove();
        }
        continue;
      }
      if (tags.contains(DisplayTags.MONITOR_TEXT_TAG)) {
        if (monitorTexts == null) {
          monitorItems = new HashSet<>();
          monitorTexts = new HashSet<>();
          loadMonitorDisplayIds(chunk, monitorItems, monitorTexts);
        }
        if (!monitorTexts.contains(display.getUniqueId())) {
          display.remove();
        }
        continue;
      }
      Block block = ent.getLocation().getBlock();
      boolean keep =
          (Carriers.matchesCarrier(block, wireCarrier) && WireMarker.isWire(plugin, block))
              || (Carriers.matchesCarrier(block, storageCarrier)
                  && (StorageMarker.get(plugin, block).isPresent()
                      || StorageCoreMarker.isCore(plugin, block)))
              || (Carriers.matchesCarrier(block, terminalCarrier)
                  && TerminalMarker.isTerminal(plugin, block))
              || (Carriers.matchesCarrier(block, monitorCarrier)
                  && MonitorMarker.isMonitor(plugin, block))
              || (Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block));
      if (!keep) {
        display.remove();
      }
    }
  }

  private void loadMonitorDisplayIds(Chunk chunk, Set<UUID> monitorItems, Set<UUID> monitorTexts) {
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          DisplayMarker.get(plugin, "monitor_item", block).ifPresent(monitorItems::add);
          DisplayMarker.get(plugin, "monitor_text", block).ifPresent(monitorTexts::add);
        });
  }

  public void cleanupDisplayMarkers(Chunk chunk) {
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) return;
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          for (String type : DISPLAY_TYPES) {
            UUID uuid = DisplayMarker.get(plugin, type, block).orElse(null);
            if (uuid == null) continue;
            var ent = Bukkit.getEntity(uuid);
            if (!(ent instanceof Display) || ent.isDead()) {
              DisplayMarker.clear(plugin, type, block);
            }
          }
        });
  }
}
