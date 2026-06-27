package com.zxcmc.exort.display.culling;

import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class PaperDisplayCullingBackend implements DisplayCullingBackend {
  private final Plugin plugin;

  PaperDisplayCullingBackend(Plugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String name() {
    return "paper";
  }

  @Override
  public boolean supportsPerPlayerViewRange() {
    return false;
  }

  @Override
  public boolean hide(Player player, Display display, float effectiveViewRange) {
    if (player == null || display == null || !display.isValid()) {
      return false;
    }
    try {
      player.hideEntity(plugin, display);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  @Override
  public boolean show(Player player, Display display, float effectiveViewRange) {
    if (player == null || display == null || !display.isValid()) {
      return false;
    }
    try {
      player.showEntity(plugin, display);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }
}
