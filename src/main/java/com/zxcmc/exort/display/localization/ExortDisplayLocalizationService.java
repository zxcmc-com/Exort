package com.zxcmc.exort.display.localization;

import com.zxcmc.exort.display.core.DisplayEntityIndex;
import com.zxcmc.exort.i18n.Lang;
import org.bukkit.entity.Player;

public final class ExortDisplayLocalizationService {
  private final DisplayEntityIndex index;
  private final Lang lang;

  public ExortDisplayLocalizationService(DisplayEntityIndex index, Lang lang) {
    this.index = index;
    this.lang = lang;
  }

  public String localize(Player player, int entityId) {
    if (index == null || lang == null) {
      return null;
    }
    DisplayEntityIndex.Entry entry = index.findByNetworkId(entityId);
    if (entry == null || entry.localizationKey() == null || entry.localizationKey().isBlank()) {
      return null;
    }
    return lang.trLanguage(lang.pluginTextLanguage(player), entry.localizationKey());
  }
}
