package com.zxcmc.exort.integration.protocol;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

final class PacketItemLocalizer {
  private PacketItemLocalizer() {}

  static ItemStack localizeSlot(
      String language, ItemStack item, PacketEnhancements.ItemLocalizer localizer) {
    if (item == null || localizer == null) {
      return item;
    }
    ItemStack localized = localizer.localize(language, item);
    return localized == null ? item : localized;
  }

  static List<ItemStack> localizeItems(
      String language, List<ItemStack> items, PacketEnhancements.ItemLocalizer localizer) {
    if (items == null || items.isEmpty() || localizer == null) {
      return items;
    }
    List<ItemStack> localized = null;
    for (int i = 0; i < items.size(); i++) {
      ItemStack original = items.get(i);
      ItemStack next = localizeSlot(language, original, localizer);
      if (next != original && localized == null) {
        localized = new ArrayList<>(items);
      }
      if (localized != null) {
        localized.set(i, next);
      }
    }
    return localized == null ? items : localized;
  }
}
