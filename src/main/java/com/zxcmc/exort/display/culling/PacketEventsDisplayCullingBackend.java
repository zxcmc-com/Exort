package com.zxcmc.exort.display.culling;

import com.zxcmc.exort.display.core.DisplayMetadataNormalizer;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

final class PacketEventsDisplayCullingBackend implements DisplayCullingBackend {
  private final PacketEnhancements.DisplayCullingPackets packets;

  PacketEventsDisplayCullingBackend(PacketEnhancements.DisplayCullingPackets packets) {
    this.packets = packets;
  }

  @Override
  public String name() {
    return "packetevents";
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
