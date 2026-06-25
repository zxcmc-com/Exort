package com.zxcmc.exort.integration.chorusfix.embedded;

import org.bukkit.block.Block;

public interface ExortChorusCarrierDetector {
  boolean isCustom(Block block, ChorusFaceMask mask);

  default boolean isHardCustom(Block block, ChorusFaceMask mask) {
    return isCustom(block, mask);
  }
}
