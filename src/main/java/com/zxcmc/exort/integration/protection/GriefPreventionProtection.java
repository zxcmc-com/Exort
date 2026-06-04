package com.zxcmc.exort.integration.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

@SuppressWarnings("deprecation")
public final class GriefPreventionProtection implements RegionProtection {
  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    if (player == null || location == null || material == null) return true;
    Claim claim = claimAt(location);
    return claim == null || claim.allowBuild(player, material) == null;
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    if (player == null || block == null) return true;
    Claim claim = claimAt(block.getLocation());
    return claim == null || claim.allowBreak(player, block.getType()) == null;
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    if (player == null || block == null) return true;
    Claim claim = claimAt(block.getLocation());
    return claim == null || claim.allowAccess(player) == null;
  }

  @Override
  public boolean canUse(Player player, Block block) {
    if (player == null || block == null) return true;
    Claim claim = claimAt(block.getLocation());
    return claim == null || claim.allowContainers(player) == null;
  }

  private Claim claimAt(Location location) {
    GriefPrevention plugin = GriefPrevention.instance;
    if (plugin == null || plugin.dataStore == null) return null;
    return plugin.dataStore.getClaimAt(location, false, null);
  }
}
