package com.zxcmc.exort.placement;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ExortBlockTargetResolver {
  private final Plugin plugin;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;

  public ExortBlockTargetResolver(
      Plugin plugin,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier) {
    this.plugin = plugin;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
  }

  public boolean isExortBlock(Block block) {
    if (block == null) return false;
    if (Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent()) {
      return true;
    }
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageCoreMarker.isCore(plugin, block);
  }
}
