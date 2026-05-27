package com.zxcmc.exort.gui;

import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.text.ExortText;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

final class StorageGuiControls {
  private static final DecimalFormat FORMAT_NUMBER = new DecimalFormat("#,###");
  private static final DecimalFormat FORMAT_PERCENT = new DecimalFormat("0.0");

  private StorageGuiControls() {}

  static ItemStack sortButton(Lang lang, SortMode sortMode, boolean useFillers) {
    String title =
        switch (sortMode) {
          case AMOUNT -> lang.tr("gui.sort.amount");
          case NAME -> lang.tr("gui.sort.name");
          case ID -> lang.tr("gui.sort.id");
          case CATEGORY -> lang.tr("gui.sort.category");
        };
    return GuiItems.sortButton(text(title), List.of(text(lang.tr("gui.sort.hint"))), useFillers);
  }

  static ItemStack searchButton(
      Lang lang, boolean hasSearch, String searchQuery, boolean useFillers) {
    String title =
        hasSearch
            ? lang.tr("gui.search.button") + ": " + searchQuery
            : lang.tr("gui.search.button");
    return GuiItems.searchButton(
        text(title),
        List.of(text(lang.tr("gui.search.hint")), text(lang.tr("gui.search.hint_clear"))),
        useFillers);
  }

  static ItemStack infoButton(
      Lang lang,
      StorageCache cache,
      StorageTier tier,
      boolean showStorageId,
      boolean readOnly,
      boolean infoErrorActive,
      String infoErrorMessage,
      int infoConfirmRemaining,
      boolean infoBlocked,
      boolean useFillers) {
    long current = cache.effectiveTotal();
    long max = Math.max(1, tier.maxItems());
    double filled = Math.min(1.0, Math.max(0.0, (double) current / (double) max));
    double free = 1.0 - filled;
    Component title =
        text(lang.tr("gui.info.used") + " " + formatNumber(current) + "/" + formatNumber(max) + " ")
            .append(
                Component.text(
                    "(" + FORMAT_PERCENT.format(filled * 100.0) + "%)", freeColor(free)));

    List<Component> lore = new ArrayList<>();
    lore.add(text(tier.displayName()));
    if (showStorageId) {
      lore.add(text(lang.tr("gui.info.storage_id", cache.getStorageId())));
    }
    if (infoErrorActive && infoErrorMessage != null && !infoErrorMessage.isBlank()) {
      lore.add(redText(infoErrorMessage));
    }
    if (readOnly) {
      lore.add(text(lang.tr("gui.info.force_hint")));
      if (infoConfirmRemaining > 0) {
        lore.add(redText(lang.tr("gui.info.force_warning")));
        lore.add(redText(lang.tr("gui.info.force_confirm", infoConfirmRemaining)));
      } else if (infoBlocked) {
        lore.add(redText(lang.tr("gui.info.force_blocked")));
      }
    }
    return infoErrorActive
        ? GuiItems.infoErrorButton(title, lore)
        : GuiItems.infoButton(title, lore, useFillers);
  }

  private static Component text(String value) {
    return ExortText.itemText(value);
  }

  private static Component redText(String value) {
    return text(value).color(NamedTextColor.RED);
  }

  private static String formatNumber(long value) {
    return FORMAT_NUMBER.format(value);
  }

  private static NamedTextColor freeColor(double freeRatio) {
    if (freeRatio <= 0.05) {
      return NamedTextColor.RED;
    }
    if (freeRatio <= 0.30) {
      return NamedTextColor.GOLD;
    }
    return NamedTextColor.GREEN;
  }
}
