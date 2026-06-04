package com.zxcmc.exort.integration.protection;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.flags.type.RoleFlag;
import me.angeschossen.lands.api.land.LandWorld;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class LandsProtection implements RegionProtection {
  private final LandsIntegration lands;

  public LandsProtection(Plugin plugin) {
    this.lands = LandsIntegration.of(plugin);
  }

  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    return hasFlag(player, location, material, Flags.BLOCK_PLACE);
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    if (block == null) return true;
    return hasFlag(player, block.getLocation(), block.getType(), Flags.BLOCK_BREAK);
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    if (block == null) return true;
    return hasFlag(player, block.getLocation(), block.getType(), Flags.INTERACT_GENERAL);
  }

  @Override
  public boolean canUse(Player player, Block block) {
    if (block == null) return true;
    return hasFlag(player, block.getLocation(), block.getType(), Flags.INTERACT_CONTAINER);
  }

  private boolean hasFlag(Player player, Location location, Material material, RoleFlag flag) {
    if (player == null || location == null || flag == null) return true;
    World bukkitWorld = location.getWorld();
    if (bukkitWorld == null) return true;
    LandWorld landWorld = lands.getWorld(bukkitWorld);
    if (landWorld == null) return true;
    LandPlayer landPlayer = lands.getLandPlayer(player.getUniqueId());
    return landWorld.hasRoleFlag(landPlayer, location, flag, material, false);
  }
}
