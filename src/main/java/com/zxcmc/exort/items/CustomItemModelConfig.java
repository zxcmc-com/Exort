package com.zxcmc.exort.items;

import com.zxcmc.exort.wireless.booster.WirelessBoosterTier;
import java.util.Map;

/** Immutable item-model configuration consumed by the custom item factory/facade. */
public record CustomItemModelConfig(
    String wire,
    String storage,
    String terminal,
    String craftingTerminal,
    String monitor,
    String importBus,
    String exportBus,
    String relay,
    String transmitter,
    String chunkLoader,
    String personalChunkLoader,
    String dormantChunkLoader,
    String wireless,
    String wirelessDisabled,
    String wirelessVanilla,
    Map<WirelessBoosterTier, String> wirelessBoosters) {
  public CustomItemModelConfig {
    wire = safe(wire);
    storage = safe(storage);
    terminal = safe(terminal);
    craftingTerminal = safe(craftingTerminal);
    monitor = safe(monitor);
    importBus = safe(importBus);
    exportBus = safe(exportBus);
    relay = safe(relay);
    transmitter = safe(transmitter);
    chunkLoader = safe(chunkLoader);
    personalChunkLoader = safe(personalChunkLoader);
    dormantChunkLoader = safe(dormantChunkLoader);
    wireless = safe(wireless);
    wirelessDisabled = safe(wirelessDisabled);
    wirelessVanilla = safe(wirelessVanilla);
    wirelessBoosters = wirelessBoosters == null ? Map.of() : Map.copyOf(wirelessBoosters);
  }

  public static CustomItemModelConfig empty() {
    return new CustomItemModelConfig(
        "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", Map.of());
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
