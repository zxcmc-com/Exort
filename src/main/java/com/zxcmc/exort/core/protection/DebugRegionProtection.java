package com.zxcmc.exort.core.protection;

import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class DebugRegionProtection implements RegionProtection {
  private final RegionProtection delegate;
  private final Logger logger;
  private final boolean enabled;

  public DebugRegionProtection(RegionProtection delegate, Logger logger, boolean enabled) {
    this.delegate = delegate;
    this.logger = logger;
    this.enabled = enabled;
  }

  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    boolean allowed = delegate.canBuild(player, location, material);
    if (!allowed) {
      log("build", player, location, material);
    }
    return allowed;
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    boolean allowed = delegate.canBreak(player, block);
    if (!allowed) {
      log("break", player, block.getLocation(), block.getType());
    }
    return allowed;
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    boolean allowed = delegate.canInteract(player, block);
    if (!allowed) {
      log("interact", player, block.getLocation(), block.getType());
    }
    return allowed;
  }

  @Override
  public boolean canUse(Player player, Block block) {
    boolean allowed = delegate.canUse(player, block);
    if (!allowed) {
      log("use", player, block.getLocation(), block.getType());
    }
    return allowed;
  }

  private void log(String action, Player player, Location location, Material material) {
    if (!enabled || logger == null) return;
    if (player == null || location == null) return;
    logger.info(
        "[WorldGuard] Denied "
            + action
            + " for "
            + player.getName()
            + " at "
            + format(location)
            + " ("
            + material
            + ")");
  }

  private String format(Location location) {
    return location.getWorld().getName()
        + " "
        + location.getBlockX()
        + ","
        + location.getBlockY()
        + ","
        + location.getBlockZ();
  }
}
