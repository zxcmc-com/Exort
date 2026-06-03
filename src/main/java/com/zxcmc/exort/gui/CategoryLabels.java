package com.zxcmc.exort.gui;

import com.zxcmc.exort.i18n.Lang;
import org.bukkit.entity.Player;

final class CategoryLabels {
  private CategoryLabels() {}

  static String labelForIndex(int index, Lang lang) {
    return labelForIndex(index, lang, null);
  }

  static String labelForIndex(int index, Lang lang, Player viewer) {
    CreativeTabOrder order = CreativeTabOrder.get();
    if (order != null) {
      String tabId = order.tabId(index);
      if (tabId != null) {
        return labelForTabId(tabId, lang, viewer);
      }
    }
    return labelForFallbackIndex(index, lang, viewer);
  }

  private static String labelForTabId(String tabId, Lang lang, Player viewer) {
    String id = tabId;
    int sep = id.indexOf(':');
    if (sep >= 0) {
      id = id.substring(sep + 1);
    }
    return switch (id) {
      case "building_blocks" -> lang.tr(viewer, "gui.category.building_blocks");
      case "colored_blocks" -> lang.tr(viewer, "gui.category.colored_blocks");
      case "natural_blocks" -> lang.tr(viewer, "gui.category.natural_blocks");
      case "functional_blocks" -> lang.tr(viewer, "gui.category.functional_blocks");
      case "redstone_blocks" -> lang.tr(viewer, "gui.category.redstone_blocks");
      case "tools_and_utilities" -> lang.tr(viewer, "gui.category.tools_and_utilities");
      case "combat" -> lang.tr(viewer, "gui.category.combat");
      case "food_and_drinks" -> lang.tr(viewer, "gui.category.food_and_drinks");
      case "ingredients" -> lang.tr(viewer, "gui.category.ingredients");
      case "spawn_eggs" -> lang.tr(viewer, "gui.category.spawn_eggs");
      case "operator", "op_blocks" -> lang.tr(viewer, "gui.category.operator");
      case "custom" -> lang.tr(viewer, "gui.category.custom");
      default -> lang.tr(viewer, "gui.category.other");
    };
  }

  private static String labelForFallbackIndex(int index, Lang lang, Player viewer) {
    return switch (index) {
      case 0 -> lang.tr(viewer, "gui.category.building_blocks");
      case 1 -> lang.tr(viewer, "gui.category.colored_blocks");
      case 2 -> lang.tr(viewer, "gui.category.natural_blocks");
      case 3 -> lang.tr(viewer, "gui.category.functional_blocks");
      case 4 -> lang.tr(viewer, "gui.category.redstone_blocks");
      case 5 -> lang.tr(viewer, "gui.category.tools_and_utilities");
      case 6 -> lang.tr(viewer, "gui.category.combat");
      case 7 -> lang.tr(viewer, "gui.category.food_and_drinks");
      case 8 -> lang.tr(viewer, "gui.category.ingredients");
      case 9 -> lang.tr(viewer, "gui.category.spawn_eggs");
      case 10 -> lang.tr(viewer, "gui.category.operator");
      default -> lang.tr(viewer, "gui.category.other");
    };
  }
}
