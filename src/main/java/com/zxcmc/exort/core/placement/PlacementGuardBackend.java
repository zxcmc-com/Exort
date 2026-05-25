package com.zxcmc.exort.core.placement;

import org.bukkit.entity.Player;

public interface PlacementGuardBackend {
  String name();

  boolean usesServerEntities();

  PlacementGuardHandle createGuard(Player player, GuardTarget target);
}
