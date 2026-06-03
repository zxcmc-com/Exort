package com.zxcmc.exort.i18n;

import com.zxcmc.exort.bus.BusSession;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SessionManager;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerLocaleService implements Listener {
  private final JavaPlugin plugin;
  private final Lang lang;
  private final ItemNameService itemNames;
  private final Supplier<SessionManager> sessionManager;
  private final Supplier<BusSessionManager> busSessionManager;

  public PlayerLocaleService(
      JavaPlugin plugin,
      Lang lang,
      ItemNameService itemNames,
      Supplier<SessionManager> sessionManager,
      Supplier<BusSessionManager> busSessionManager) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.lang = Objects.requireNonNull(lang, "lang");
    this.itemNames = Objects.requireNonNull(itemNames, "itemNames");
    this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    this.busSessionManager = Objects.requireNonNull(busSessionManager, "busSessionManager");
  }

  public String pluginTextLanguage(Player player) {
    return lang.pluginTextLanguage(player);
  }

  public String itemDictionaryLanguage(Player player) {
    if (player == null) {
      return itemNames.dictionaryLanguage(lang.configuredLanguage(), lang.configuredLanguage());
    }
    return itemNames.dictionaryLanguage(player.locale().toString(), lang.configuredLanguage());
  }

  public void preloadItemDictionary(Player player) {
    if (player == null) {
      return;
    }
    String requested = itemNames.normalizeLanguage(player.locale().toString());
    if (!itemNames.canUseDictionaryLanguage(requested)) {
      return;
    }
    itemNames
        .preloadDictionary(requested)
        .thenAccept(
            loaded -> {
              if (loaded) {
                rerenderSession(player);
              }
            });
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    preloadItemDictionary(event.getPlayer());
  }

  @EventHandler
  public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
    Player player = event.getPlayer();
    String requested = itemNames.normalizeLanguage(event.locale().toString());
    if (itemNames.canUseDictionaryLanguage(requested)) {
      itemNames.preloadDictionary(requested).thenAccept(loaded -> rerenderSession(player));
      return;
    }
    rerenderSession(player);
  }

  private void rerenderSession(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              SessionManager manager = sessionManager.get();
              if (manager == null || !player.isOnline()) {
                return;
              }
              GuiSession session = manager.sessionFor(player);
              if (session != null) {
                session.render();
              }
              BusSessionManager busManager = busSessionManager.get();
              if (busManager != null) {
                BusSession busSession = busManager.sessionFor(player);
                if (busSession != null) {
                  busSession.render();
                }
              }
            });
  }
}
