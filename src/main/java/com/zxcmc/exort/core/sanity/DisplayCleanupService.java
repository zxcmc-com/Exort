package com.zxcmc.exort.core.sanity;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.DisplayMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
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
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;

public final class DisplayCleanupService {
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
    var pdc = chunk.getPersistentDataContainer();
    for (NamespacedKey key : pdc.getKeys()) {
      if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;
      String rawKey = key.getKey();
      if (rawKey.startsWith("monitor_item_display_")) {
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw == null) continue;
        try {
          monitorItems.add(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
          // Ignore invalid UUIDs; will be cleaned up in marker pass
        }
        continue;
      }
      if (rawKey.startsWith("monitor_text_display_")) {
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw == null) continue;
        try {
          monitorTexts.add(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
          // Ignore invalid UUIDs; will be cleaned up in marker pass
        }
      }
    }
  }

  public void cleanupDisplayMarkers(Chunk chunk) {
    var pdc = chunk.getPersistentDataContainer();
    var keys = pdc.getKeys();
    if (keys.isEmpty()) return;
    for (var key : keys) {
      if (!key.getNamespace().equals(plugin.getName().toLowerCase())) continue;
      String raw = key.getKey();
      if (raw.startsWith("wire_display_")) {
        cleanupDisplayMarker(chunk, raw, "wire");
      } else if (raw.startsWith("storage_display_")) {
        cleanupDisplayMarker(chunk, raw, "storage");
      } else if (raw.startsWith("terminal_display_")) {
        cleanupDisplayMarker(chunk, raw, "terminal");
      } else if (raw.startsWith("monitor_display_")) {
        cleanupDisplayMarker(chunk, raw, "monitor");
      } else if (raw.startsWith("bus_display_")) {
        cleanupDisplayMarker(chunk, raw, "bus");
      } else if (raw.startsWith("monitor_item_display_")) {
        cleanupDisplayMarker(chunk, raw, "monitor_item");
      } else if (raw.startsWith("monitor_text_display_")) {
        cleanupDisplayMarker(chunk, raw, "monitor_text");
      }
    }
  }

  private void cleanupDisplayMarker(Chunk chunk, String rawKey, String type) {
    String suffix = rawKey.substring((type + "_display_").length());
    int[] xyz = MarkerCoords.parseXYZ(suffix);
    if (xyz == null) return;
    Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
    var uuid = DisplayMarker.get(plugin, type, block).orElse(null);
    if (uuid == null) {
      DisplayMarker.clear(plugin, type, block);
      return;
    }
    var ent = Bukkit.getEntity(uuid);
    if (!(ent instanceof Display) || ent.isDead()) {
      DisplayMarker.clear(plugin, type, block);
    }
  }
}
