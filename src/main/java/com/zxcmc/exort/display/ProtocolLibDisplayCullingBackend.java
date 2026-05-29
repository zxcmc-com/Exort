package com.zxcmc.exort.display;

import com.zxcmc.exort.integration.protocol.ProtocolLibEnhancements;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

final class ProtocolLibDisplayCullingBackend implements DisplayCullingBackend {
  private final ProtocolLibEnhancements.DisplayCullingPackets packets;

  ProtocolLibDisplayCullingBackend(ProtocolLibEnhancements.DisplayCullingPackets packets) {
    this.packets = packets;
  }

  @Override
  public String name() {
    return "protocollib";
  }

  @Override
  public boolean supportsPerPlayerViewRange() {
    return true;
  }

  @Override
  public boolean hide(Player player, Display display, float effectiveViewRange) {
    if (player == null || display == null || !display.isValid()) {
      return false;
    }
    return packets.sendViewRange(player, display.getEntityId(), 0.0f);
  }

  @Override
  public boolean show(Player player, Display display, float effectiveViewRange) {
    if (player == null || display == null || !display.isValid()) {
      return false;
    }
    float viewRange =
        effectiveViewRange <= 0.0f ? DisplayMetadataNormalizer.BASE_VIEW_RANGE : effectiveViewRange;
    return packets.sendViewRange(player, display.getEntityId(), viewRange);
  }
}
