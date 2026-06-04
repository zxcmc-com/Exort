package com.zxcmc.exort.integration.protection;

import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ResidenceProtection implements RegionProtection {
  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    return hasFlag(player, location, Flags.build);
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    if (block == null) return true;
    return hasFlag(player, block.getLocation(), Flags.destroy);
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    if (block == null) return true;
    return hasFlag(player, block.getLocation(), Flags.use);
  }

  @Override
  public boolean canUse(Player player, Block block) {
    if (block == null) return true;
    return hasFlag(player, block.getLocation(), Flags.container);
  }

  private boolean hasFlag(Player player, Location location, Flags flag) {
    if (player == null || location == null || flag == null) return true;
    return FlagPermissions.getPerms(location).playerHas(player, flag, true);
  }
}
