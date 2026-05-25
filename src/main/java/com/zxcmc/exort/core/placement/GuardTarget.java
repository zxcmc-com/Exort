package com.zxcmc.exort.core.placement;

import org.bukkit.Location;

public record GuardTarget(BlockKey exortBlock, BlockKey placementBlock, Location location) {
  GuardKey key() {
    return new GuardKey(exortBlock, placementBlock);
  }
}
