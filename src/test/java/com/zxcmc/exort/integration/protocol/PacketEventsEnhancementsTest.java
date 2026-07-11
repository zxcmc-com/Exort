package com.zxcmc.exort.integration.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.testsupport.BukkitTestDoubles;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class PacketEventsEnhancementsTest {
  @Test
  void cancelledPickRequiresItemsAdderCompatibilityContext() {
    assertFalse(PacketTargetValidation.shouldHandleCancelledPick(true, false));
    assertTrue(PacketTargetValidation.shouldHandleCancelledPick(true, true));
    assertTrue(PacketTargetValidation.shouldHandleCancelledPick(false, false));
  }

  @Test
  void packetCoordinatesMustMatchServerTargetExactly() {
    Block target =
        BukkitTestDoubles.proxy(
            Block.class,
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getX" -> 12;
                  case "getY" -> 64;
                  case "getZ" -> -9;
                  default -> BukkitTestDoubles.defaultValue(method.getReturnType());
                });

    assertTrue(PacketTargetValidation.matchesPacketTarget(target, 12, 64, -9));
    assertFalse(PacketTargetValidation.matchesPacketTarget(target, 13, 64, -9));
    assertFalse(PacketTargetValidation.matchesPacketTarget(target, 12, 65, -9));
    assertFalse(PacketTargetValidation.matchesPacketTarget(target, 12, 64, -8));
    assertFalse(PacketTargetValidation.matchesPacketTarget(null, 12, 64, -9));
  }
}
