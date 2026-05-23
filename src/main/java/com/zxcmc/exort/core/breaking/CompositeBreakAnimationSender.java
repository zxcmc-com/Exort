package com.zxcmc.exort.core.breaking;

import java.util.List;
import org.bukkit.block.Block;

public final class CompositeBreakAnimationSender implements BreakAnimationSender {
  private final List<BreakAnimationSender> senders;

  private CompositeBreakAnimationSender(List<BreakAnimationSender> senders) {
    this.senders = List.copyOf(senders);
  }

  public static BreakAnimationSender of(List<BreakAnimationSender> senders) {
    List<BreakAnimationSender> active =
        senders.stream()
            .filter(sender -> sender != null && sender != BreakAnimationSender.NOOP)
            .toList();
    if (active.isEmpty()) {
      return BreakAnimationSender.NOOP;
    }
    if (active.size() == 1) {
      return active.get(0);
    }
    return new CompositeBreakAnimationSender(active);
  }

  @Override
  public void show(Block block, BreakType type, double progress) {
    for (BreakAnimationSender sender : senders) {
      sender.show(block, type, progress);
    }
  }

  @Override
  public void breakBlock(Block block, BreakType type) {
    for (BreakAnimationSender sender : senders) {
      sender.breakBlock(block, type);
    }
  }

  @Override
  public void clear(Block block) {
    for (BreakAnimationSender sender : senders) {
      sender.clear(block);
    }
  }
}
