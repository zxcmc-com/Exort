package com.zxcmc.exort.integration.chorusfix.embedded;

import org.bukkit.block.Block;

public final class ChorusBreakExecutor implements ChorusBlockBreaker {
  @Override
  public boolean breakNaturallyWithFeedback(Block block) {
    return block.breakNaturally(true, false);
  }
}
