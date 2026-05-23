package com.zxcmc.exort.core.breaking;

import org.bukkit.block.Block;

public interface BreakAnimationSender {
  BreakAnimationSender NOOP =
      new BreakAnimationSender() {
        @Override
        public void show(Block block, BreakType type, double progress) {}

        @Override
        public void clear(Block block) {}
      };

  void show(Block block, BreakType type, double progress);

  default void breakBlock(Block block, BreakType type) {}

  void clear(Block block);
}
