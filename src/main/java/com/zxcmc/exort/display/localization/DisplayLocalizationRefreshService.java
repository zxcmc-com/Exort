package com.zxcmc.exort.display.localization;

import com.zxcmc.exort.display.core.DisplayEntityIndex;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.plugin.Plugin;

public final class DisplayLocalizationRefreshService implements Listener {
  private final Plugin plugin;
  private final DisplayEntityIndex index;
  private final DisplayMetadataService metadataService;
  private final double maxDistance;

  public DisplayLocalizationRefreshService(
      Plugin plugin,
      DisplayEntityIndex index,
      DisplayMetadataService metadataService,
      double maxDistance) {
    this.plugin = plugin;
    this.index = index;
    this.metadataService = metadataService;
    this.maxDistance = Math.max(1.0, maxDistance);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    scheduleRefresh(event.getPlayer());
  }

  @EventHandler
  public void onPlayerLocaleChange(PlayerLocaleChangeEvent event) {
    scheduleRefresh(event.getPlayer());
  }

  private void scheduleRefresh(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> refreshNearby(player));
  }

  private void refreshNearby(Player player) {
    if (player == null || !player.isOnline() || index == null || metadataService == null) {
      return;
    }
    List<DisplayEntityIndex.Entry> entries = index.query(player.getLocation(), maxDistance);
    for (DisplayEntityIndex.Entry entry : entries) {
      if (entry.localizationKey() == null || entry.localizationKey().isBlank()) {
        continue;
      }
      Display display = index.resolve(entry.entityUuid());
      if (display != null) {
        metadataService.resync(display);
      }
    }
  }
}
