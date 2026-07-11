package com.zxcmc.exort.integration.protocol;

import org.bukkit.block.Block;

final class PacketTargetValidation {
  private PacketTargetValidation() {}

  static boolean shouldHandleCancelledPick(boolean cancelled, boolean itemsAdderEnabled) {
    return !cancelled || itemsAdderEnabled;
  }

  static boolean matchesPacketTarget(Block serverTarget, int x, int y, int z) {
    return serverTarget != null
        && serverTarget.getX() == x
        && serverTarget.getY() == y
        && serverTarget.getZ() == z;
  }
}
