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
  private static final String TYPE_WIRE = "wire";
  private static final String TYPE_STORAGE = "storage";
  private static final String TYPE_TERMINAL = "terminal";
  private static final String TYPE_MONITOR = "monitor";
  private static final String TYPE_BUS = "bus";
  private static final String TYPE_MONITOR_ITEM = "monitor_item";
  private static final String TYPE_MONITOR_TEXT = "monitor_text";
  private static final String[] DISPLAY_TYPES =
      new String[] {
        TYPE_WIRE,
        TYPE_STORAGE,
        TYPE_TERMINAL,
        TYPE_MONITOR,
        TYPE_BUS,
        TYPE_MONITOR_ITEM,
        TYPE_MONITOR_TEXT
      };
  private static final Set<String> MARKER_BACKED_DISPLAY_TYPES =
      Set.of(TYPE_STORAGE, TYPE_TERMINAL, TYPE_MONITOR, TYPE_BUS);
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
      if (isTaggedWireDisplay(tags)) {
        if (!isValidWireBlock(block)) {
          display.remove();
        }
        continue;
      }
      String markerBackedType = markerBackedDisplayType(tags);
      if (markerBackedType != null) {
        if (!isCurrentMarkerBackedDisplay(display, block, markerBackedType)) {
          display.remove();
        }
        continue;
      }
      if (isUntypedDisplay(tags) && !isCurrentLegacyWireDisplay(display, block)) {
        display.remove();
        continue;
      }
      boolean keep = isValidAnyDisplayBlock(block);
      if (!keep) {
        display.remove();
      }
    }
  }

  private boolean isTaggedWireDisplay(Set<String> tags) {
    return tags.contains(DisplayTags.WIRE_CENTER_TAG)
        || tags.stream().anyMatch(tag -> tag.startsWith(DisplayTags.WIRE_CONNECTION_PREFIX));
  }

  private boolean isUntypedDisplay(Set<String> tags) {
    return tags.contains(DisplayTags.DISPLAY_TAG) && !tags.contains(DisplayTags.HOLOGRAM_TAG);
  }

  private boolean isCurrentLegacyWireDisplay(Display display, Block block) {
    if (!isValidWireBlock(block)) {
      return false;
    }
    UUID expected = DisplayMarker.get(plugin, TYPE_WIRE, block).orElse(null);
    return expected != null && expected.equals(display.getUniqueId());
  }

  private String markerBackedDisplayType(Set<String> tags) {
    for (String type : MARKER_BACKED_DISPLAY_TYPES) {
      if (tags.contains(type)) {
        return type;
      }
    }
    return null;
  }

  private boolean isCurrentMarkerBackedDisplay(Display display, Block block, String type) {
    if (!isValidMarkerBackedBlock(block, type)) {
      return false;
    }
    UUID expected = DisplayMarker.get(plugin, type, block).orElse(null);
    return expected != null && expected.equals(display.getUniqueId());
  }

  private boolean isValidWireBlock(Block block) {
    return Carriers.matchesCarrier(block, wireCarrier) && WireMarker.isWire(plugin, block);
  }

  private boolean isValidMarkerBackedBlock(Block block, String type) {
    return switch (type) {
      case TYPE_STORAGE ->
          Carriers.matchesCarrier(block, storageCarrier)
              && (StorageMarker.get(plugin, block).isPresent()
                  || StorageCoreMarker.isCore(plugin, block));
      case TYPE_TERMINAL ->
          Carriers.matchesCarrier(block, terminalCarrier)
              && TerminalMarker.isTerminal(plugin, block);
      case TYPE_MONITOR ->
          Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
      case TYPE_BUS -> Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
      default -> false;
    };
  }

  private boolean isValidAnyDisplayBlock(Block block) {
    return isValidWireBlock(block)
        || isValidMarkerBackedBlock(block, TYPE_STORAGE)
        || isValidMarkerBackedBlock(block, TYPE_TERMINAL)
        || isValidMarkerBackedBlock(block, TYPE_MONITOR)
        || isValidMarkerBackedBlock(block, TYPE_BUS);
  }

  private void loadMonitorDisplayIds(Chunk chunk, Set<UUID> monitorItems, Set<UUID> monitorTexts) {
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          DisplayMarker.get(plugin, TYPE_MONITOR_ITEM, block).ifPresent(monitorItems::add);
          DisplayMarker.get(plugin, TYPE_MONITOR_TEXT, block).ifPresent(monitorTexts::add);
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
