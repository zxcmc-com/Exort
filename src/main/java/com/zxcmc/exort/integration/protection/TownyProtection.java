package com.zxcmc.exort.integration.protection;

import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class TownyProtection implements RegionProtection {
  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    if (player == null || location == null || material == null) return true;
    return PlayerCacheUtil.getCachePermission(player, location, material, ActionType.BUILD);
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    if (player == null || block == null) return true;
    return PlayerCacheUtil.getCachePermission(
        player, block.getLocation(), block.getType(), ActionType.DESTROY);
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    if (player == null || block == null) return true;
    return PlayerCacheUtil.getCachePermission(
        player, block.getLocation(), block.getType(), ActionType.SWITCH);
  }

  @Override
  public boolean canUse(Player player, Block block) {
    return canInteract(player, block);
  }
}
