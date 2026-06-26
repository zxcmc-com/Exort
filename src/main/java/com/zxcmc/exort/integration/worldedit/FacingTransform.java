package com.zxcmc.exort.integration.worldedit;

import org.bukkit.block.BlockFace;

@FunctionalInterface
interface FacingTransform {
  BlockFace apply(BlockFace face);
}
