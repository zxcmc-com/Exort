package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.i18n.Lang;

final class CategoryLabels {
    private CategoryLabels() {
    }

    static String labelForIndex(int index, Lang lang) {
        CreativeTabOrder order = CreativeTabOrder.get();
        if (order != null) {
            String tabId = order.tabId(index);
            if (tabId != null) {
                return labelForTabId(tabId, lang);
            }
        }
        return labelForFallbackIndex(index, lang);
    }

    private static String labelForTabId(String tabId, Lang lang) {
        String id = tabId;
        int sep = id.indexOf(':');
        if (sep >= 0) {
            id = id.substring(sep + 1);
        }
        return switch (id) {
            case "building_blocks" -> lang.tr("gui.category.building_blocks");
            case "colored_blocks" -> lang.tr("gui.category.colored_blocks");
            case "natural_blocks" -> lang.tr("gui.category.natural_blocks");
            case "functional_blocks" -> lang.tr("gui.category.functional_blocks");
            case "redstone_blocks" -> lang.tr("gui.category.redstone_blocks");
            case "tools_and_utilities" -> lang.tr("gui.category.tools_and_utilities");
            case "combat" -> lang.tr("gui.category.combat");
            case "food_and_drinks" -> lang.tr("gui.category.food_and_drinks");
            case "ingredients" -> lang.tr("gui.category.ingredients");
            case "spawn_eggs" -> lang.tr("gui.category.spawn_eggs");
            case "operator" -> lang.tr("gui.category.operator");
            case "custom" -> lang.tr("gui.category.custom");
            default -> lang.tr("gui.category.other");
        };
    }

    private static String labelForFallbackIndex(int index, Lang lang) {
        return switch (index) {
            case 0 -> lang.tr("gui.category.building_blocks");
            case 1 -> lang.tr("gui.category.colored_blocks");
            case 2 -> lang.tr("gui.category.natural_blocks");
            case 3 -> lang.tr("gui.category.functional_blocks");
            case 4 -> lang.tr("gui.category.redstone_blocks");
            case 5 -> lang.tr("gui.category.tools_and_utilities");
            case 6 -> lang.tr("gui.category.combat");
            case 7 -> lang.tr("gui.category.food_and_drinks");
            case 8 -> lang.tr("gui.category.ingredients");
            case 9 -> lang.tr("gui.category.spawn_eggs");
            case 10 -> lang.tr("gui.category.operator");
            default -> lang.tr("gui.category.other");
        };
    }
}
