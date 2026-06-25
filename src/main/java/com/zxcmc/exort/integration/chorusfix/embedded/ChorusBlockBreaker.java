package com.zxcmc.exort.integration.chorusfix.embedded;

import org.bukkit.block.Block;

@FunctionalInterface
interface ChorusBlockBreaker {
  boolean breakNaturallyWithFeedback(Block block);
}
