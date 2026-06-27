package com.zxcmc.exort.display.wire;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

final class WireConnectionModelResolver {
  private WireConnectionModelResolver() {}

  static int connectionsMask(
      Plugin plugin,
      Block wire,
      Material wireCarrierMaterial,
      Material terminalMaterial,
      Material storageCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier) {
    int mask = 0;
    for (BlockFace face : WireModelKeys.CONNECTION_FACES) {
      if (isConnected(
          plugin,
          wire,
          face,
          wireCarrierMaterial,
          terminalMaterial,
          storageCarrier,
          monitorCarrier,
          busCarrier,
          relayCarrier)) {
        mask |= WireModelKeys.bit(face);
      }
    }
    return mask;
  }

  private static boolean isConnected(
      Plugin plugin,
      Block wire,
      BlockFace face,
      Material wireCarrierMaterial,
      Material terminalMaterial,
      Material storageCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier) {
    Block neighbor = wire.getRelative(face);
    if (neighbor == null) return false;
    if (Carriers.matchesCarrier(neighbor, wireCarrierMaterial)
        && WireMarker.isWire(plugin, neighbor)) return true;
    if (isTerminal(plugin, neighbor, terminalMaterial)) {
      return !isFrontFace(
          plugin, neighbor, face.getOppositeFace(), terminalMaterial, monitorCarrier, busCarrier);
    }
    if (isStorage(plugin, neighbor, storageCarrier)) return true;
    if (isMonitor(plugin, neighbor, monitorCarrier)) {
      return !isFrontFace(
          plugin, neighbor, face.getOppositeFace(), terminalMaterial, monitorCarrier, busCarrier);
    }
    if (isBus(plugin, neighbor, busCarrier)) {
      return !isFrontFace(
          plugin, neighbor, face.getOppositeFace(), terminalMaterial, monitorCarrier, busCarrier);
    }
    if (isRelay(plugin, neighbor, relayCarrier)) return true;
    return false;
  }

  private static boolean isTerminal(Plugin plugin, Block block, Material terminalMaterial) {
    return Carriers.matchesCarrier(block, terminalMaterial)
        && TerminalMarker.isTerminal(plugin, block);
  }

  private static boolean isStorage(Plugin plugin, Block block, Material storageCarrier) {
    return Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent();
  }

  private static boolean isMonitor(Plugin plugin, Block block, Material monitorCarrier) {
    return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
  }

  private static boolean isBus(Plugin plugin, Block block, Material busCarrier) {
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }

  private static boolean isRelay(Plugin plugin, Block block, Material relayCarrier) {
    return Carriers.matchesCarrier(block, relayCarrier) && RelayMarker.isRelay(plugin, block);
  }

  private static boolean isFrontFace(
      Plugin plugin,
      Block block,
      BlockFace towardWire,
      Material terminalMaterial,
      Material monitorCarrier,
      Material busCarrier) {
    if (towardWire == null) return false;
    if (isTerminal(plugin, block, terminalMaterial)) {
      return TerminalMarker.facing(plugin, block).map(towardWire::equals).orElse(false);
    }
    if (isMonitor(plugin, block, monitorCarrier)) {
      return MonitorMarker.facing(plugin, block).map(towardWire::equals).orElse(false);
    }
    if (isBus(plugin, block, busCarrier)) {
      return BusMarker.get(plugin, block)
          .map(BusMarker.Data::facing)
          .map(towardWire::equals)
          .orElse(false);
    }
    return false;
  }
}
