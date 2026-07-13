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
    return localize(lang == null ? null : lang.pluginTextLanguage(player), entityId);
  }

  /** Packet-worker path backed only by safely published language and display-index state. */
  public String localize(String language, int entityId) {
    if (index == null || lang == null) {
      return null;
    }
    DisplayEntityIndex.Entry entry = index.findByNetworkId(entityId);
    if (entry == null || entry.localizationKey() == null || entry.localizationKey().isBlank()) {
      return null;
    }
    return lang.trLanguage(language, entry.localizationKey());
  }
}
