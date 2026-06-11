package com.zxcmc.exort.integration.protection;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class MutableRegionProtection implements RegionProtection {
  private volatile RegionProtection delegate;

  public MutableRegionProtection(RegionProtection delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  public void setDelegate(RegionProtection delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  public RegionProtection delegate() {
    return delegate;
  }

  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    return delegate.canBuild(player, location, material);
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    return delegate.canBreak(player, block);
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    return delegate.canInteract(player, block);
  }

  @Override
  public boolean canUse(Player player, Block block) {
    return delegate.canUse(player, block);
  }
}
