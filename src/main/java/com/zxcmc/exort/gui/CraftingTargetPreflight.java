package com.zxcmc.exort.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CraftingTargetPreflight {
  private CraftingTargetPreflight() {}

  static List<OutputStack> merge(List<OutputStack> primary, List<OutputStack> secondary) {
    Map<String, OutputStack> merged = new LinkedHashMap<>();
    addAll(merged, primary);
    addAll(merged, secondary);
    return new ArrayList<>(merged.values());
  }

  private static void addAll(Map<String, OutputStack> target, List<OutputStack> additions) {
    if (additions == null || additions.isEmpty()) {
      return;
    }
    for (OutputStack addition : additions) {
      if (addition == null || addition.key() == null || addition.amount() <= 0) {
        continue;
      }
      OutputStack existing = target.get(addition.key());
      if (existing == null) {
        target.put(addition.key(), addition);
      } else {
        target.put(
            addition.key(),
            new OutputStack(
                addition.key(),
                existing.sample(),
                saturatingAdd(existing.amount(), addition.amount())));
      }
    }
  }

  private static long saturatingAdd(long left, long right) {
    if (right <= 0) return left;
    if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }
}
