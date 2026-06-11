package com.zxcmc.exort.integration.protocol;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PacketItemLocalizer {
  @FunctionalInterface
  interface ItemLocalizer {
    ItemStack localize(Player player, ItemStack item);
  }

  private PacketItemLocalizer() {}

  static ItemStack localizeSlot(Player player, ItemStack item, ItemLocalizer localizer) {
    if (item == null || localizer == null) {
      return item;
    }
    ItemStack localized = localizer.localize(player, item);
    return localized == null ? item : localized;
  }

  static List<ItemStack> localizeItems(
      Player player, List<ItemStack> items, ItemLocalizer localizer) {
    if (items == null || items.isEmpty() || localizer == null) {
      return items;
    }
    List<ItemStack> localized = null;
    for (int i = 0; i < items.size(); i++) {
      ItemStack original = items.get(i);
      ItemStack next = localizeSlot(player, original, localizer);
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
