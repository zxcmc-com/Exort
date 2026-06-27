package com.zxcmc.exort.display.culling;

import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

interface DisplayCullingBackend {
  String name();

  boolean supportsPerPlayerViewRange();

  boolean hide(Player player, Display display, float effectiveViewRange);

  boolean show(Player player, Display display, float effectiveViewRange);
}
