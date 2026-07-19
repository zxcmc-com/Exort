package com.zxcmc.exort.bus;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

public record BusSettings(BusPos pos, BusType type, BusMode mode, ItemStack[] filters) {
  public BusSettings {
    filters = copyFilters(filters);
  }

  @Override
  public ItemStack[] filters() {
    return copyFilters(filters);
  }

  private static ItemStack[] copyFilters(ItemStack[] source) {
    if (source == null || source.length == 0) {
      return new ItemStack[0];
    }
    ItemStack[] copy = Arrays.copyOf(source, source.length);
    for (int index = 0; index < copy.length; index++) {
      copy[index] = copy[index] == null ? null : copy[index].clone();
    }
    return copy;
  }
}
