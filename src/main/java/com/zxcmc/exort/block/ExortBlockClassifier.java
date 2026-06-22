package com.zxcmc.exort.block;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.runtime.RuntimeMaterials;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class ExortBlockClassifier {
  private final Plugin plugin;
  private final RuntimeMaterials materials;

  public ExortBlockClassifier(Plugin plugin, RuntimeMaterials materials) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.materials = Objects.requireNonNull(materials, "materials");
  }

  public boolean isExortBlock(Block block) {
    if (block == null) return false;
    if (Carriers.matchesCarrier(block, materials.terminalCarrier())
        && TerminalMarker.isTerminal(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.wire()) && WireMarker.isWire(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.monitorCarrier())
        && MonitorMarker.isMonitor(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.busCarrier()) && BusMarker.isBus(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.relayCarrier())
        && RelayMarker.isRelay(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageMarker.isMarkedStorage(plugin, block)) {
      return true;
    }
    return Carriers.matchesCarrier(block, materials.storageCarrier())
        && StorageCoreMarker.isCore(plugin, block);
  }

  public boolean isExortChorusCarrier(Block block) {
    return block != null && block.getType() == Material.CHORUS_PLANT && isExortBlock(block);
  }
}
