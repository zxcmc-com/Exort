package com.zxcmc.exort.core.placement;

import java.util.UUID;
import org.bukkit.entity.Player;

public interface PlacementGuardHandle {
  boolean isValid();

  void move(Player player, GuardTarget target);

  void remove();

  UUID bukkitEntityUuid();
}
