package com.zxcmc.exort.display;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class DisplayRefreshService {
  private static final BlockFace[] FACES =
      new BlockFace[] {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
      };

  private final ExortPlugin plugin;
  private final WireDisplayManager wireDisplayManager;
  private final StorageDisplayManager storageDisplayManager;
  private final TerminalDisplayManager terminalDisplayManager;
  private final MonitorDisplayManager monitorDisplayManager;
  private final BusDisplayManager busDisplayManager;

  public DisplayRefreshService(
      ExortPlugin plugin,
      WireDisplayManager wireDisplayManager,
      StorageDisplayManager storageDisplayManager,
      TerminalDisplayManager terminalDisplayManager,
      MonitorDisplayManager monitorDisplayManager,
      BusDisplayManager busDisplayManager) {
    this.plugin = plugin;
    this.wireDisplayManager = wireDisplayManager;
    this.storageDisplayManager = storageDisplayManager;
    this.terminalDisplayManager = terminalDisplayManager;
    this.monitorDisplayManager = monitorDisplayManager;
    this.busDisplayManager = busDisplayManager;
  }

  public void refreshChunk(Chunk chunk) {
    var keys = chunk.getPersistentDataContainer().getKeys();
    if (keys.isEmpty()) return;
    boolean hasWire = false;
    boolean hasStorage = false;
    boolean hasTerminal = false;
    boolean hasMonitor = false;
    boolean hasBus = false;
    String ns = plugin.getName().toLowerCase();
    for (NamespacedKey key : keys) {
      if (!key.getNamespace().equals(ns)) continue;
      String raw = key.getKey();
      if (!hasWire && raw.startsWith("wire_")) hasWire = true;
      if (!hasStorage && raw.startsWith("storage_")) hasStorage = true;
      if (!hasTerminal && raw.startsWith("terminal_")) hasTerminal = true;
      if (!hasMonitor && raw.startsWith("monitor_")) hasMonitor = true;
      if (!hasBus && raw.startsWith("bus_")) hasBus = true;
      if (hasWire && hasStorage && hasTerminal && hasMonitor && hasBus) {
        break;
      }
    }
    if (hasWire && wireDisplayManager != null) {
      wireDisplayManager.refreshChunk(chunk);
    }
    if (hasStorage && storageDisplayManager != null) {
      storageDisplayManager.refreshChunk(chunk);
    }
    if (hasTerminal && terminalDisplayManager != null) {
      terminalDisplayManager.refreshChunk(chunk);
    }
    if (hasMonitor && monitorDisplayManager != null) {
      monitorDisplayManager.refreshChunk(chunk);
    }
    if (hasBus && busDisplayManager != null) {
      busDisplayManager.refreshChunk(chunk);
    }
  }

  public void refreshWireAndNeighbors(Block block) {
    if (wireDisplayManager != null) {
      wireDisplayManager.updateWireAndNeighbors(block);
    }
  }

  public void refreshNetworkFrom(Block block) {
    if (block == null || block.getWorld() == null) return;
    var wireMaterial = plugin.getWireMaterial();
    if (wireMaterial == null) return;
    int hardCap = Math.max(0, plugin.getWireHardCap());
    if (hardCap == 0) return;
    boolean isWire =
        Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block);
    if (isWire) {
      refreshFromWire(block, hardCap, wireMaterial);
      return;
    }
    for (var face : FACES) {
      Block neighbor = block.getRelative(face);
      if (!isChunkLoaded(neighbor)) continue;
      if (Carriers.matchesCarrier(neighbor, wireMaterial) && WireMarker.isWire(plugin, neighbor)) {
        refreshFromWire(neighbor, hardCap, wireMaterial);
      }
    }
  }

  private void refreshFromWire(Block start, int hardCap, Material wireMaterial) {
    Queue<Block> queue = new ArrayDeque<>();
    Set<Block> visited = new HashSet<>();
    Set<Block> terminals = new HashSet<>();
    Set<Block> monitors = new HashSet<>();
    Set<Block> buses = new HashSet<>();
    queue.add(start);
    visited.add(start);
    while (!queue.isEmpty() && visited.size() <= hardCap) {
      Block current = queue.poll();
      for (var face : FACES) {
        Block next = current.getRelative(face);
        if (visited.contains(next)) continue;
        if (!isChunkLoaded(next)) continue;
        if (Carriers.matchesCarrier(next, wireMaterial) && WireMarker.isWire(plugin, next)) {
          visited.add(next);
          queue.add(next);
          continue;
        }
        if (Carriers.matchesCarrier(next, plugin.getTerminalCarrier())
            && TerminalMarker.isTerminal(plugin, next)) {
          terminals.add(next);
        } else if (Carriers.matchesCarrier(next, plugin.getMonitorCarrier())
            && MonitorMarker.isMonitor(plugin, next)) {
          monitors.add(next);
        } else if (Carriers.matchesCarrier(next, plugin.getBusCarrier())
            && BusMarker.isBus(plugin, next)) {
          buses.add(next);
        } else if (Carriers.matchesCarrier(next, plugin.getStorageCarrier())
            && StorageMarker.get(plugin, next).isPresent()) {
          if (storageDisplayManager != null) {
            storageDisplayManager.refresh(next);
          }
        }
      }
    }
    for (Block terminal : terminals) {
      refreshTerminal(terminal);
    }
    for (Block monitor : monitors) {
      refreshMonitor(monitor);
    }
    for (Block bus : buses) {
      refreshBus(bus);
    }
  }

  private boolean isChunkLoaded(Block block) {
    if (block == null || block.getWorld() == null) return false;
    return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  public void refreshStorage(Block block) {
    if (storageDisplayManager != null) {
      storageDisplayManager.refresh(block);
    }
  }

  public void refreshTerminal(Block block) {
    if (terminalDisplayManager != null) {
      terminalDisplayManager.refresh(block);
    }
  }

  public void refreshMonitor(Block block) {
    if (monitorDisplayManager != null) {
      monitorDisplayManager.refresh(block);
    }
  }

  public void refreshBus(Block block) {
    if (busDisplayManager != null) {
      busDisplayManager.refresh(block);
    }
  }

  public void removeStorageDisplay(Block block) {
    if (storageDisplayManager != null) {
      storageDisplayManager.removeDisplay(block);
    }
  }

  public void removeTerminalDisplay(Block block) {
    if (terminalDisplayManager != null) {
      terminalDisplayManager.removeDisplay(block);
    }
  }

  public void removeMonitorDisplay(Block block) {
    if (monitorDisplayManager != null) {
      monitorDisplayManager.removeDisplay(block);
    }
  }

  public void removeBusDisplay(Block block) {
    if (busDisplayManager != null) {
      busDisplayManager.removeDisplay(block);
    }
  }
}
