package com.zxcmc.exort.bus.loop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusLoopGuardTest {
  @Test
  void sideSensitiveAllImportAndAllExportDoNotConflictBySlotSeparation() {
    assertFalse(
        BusLoopGuard.filtersIntersect(
            BusMode.ALL,
            Set.of(),
            BusType.IMPORT,
            true,
            BusMode.ALL,
            Set.of(),
            BusType.EXPORT,
            false));
  }

  @Test
  void sideSensitiveWhitelistStillBlocksMatchingPingPongFilters() {
    assertTrue(
        BusLoopGuard.filtersIntersect(
            BusMode.WHITELIST,
            Set.of("minecraft:iron_ingot"),
            BusType.IMPORT,
            true,
            BusMode.WHITELIST,
            Set.of("minecraft:iron_ingot"),
            BusType.EXPORT,
            false));
  }
}
