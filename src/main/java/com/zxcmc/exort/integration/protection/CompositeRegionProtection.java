package com.zxcmc.exort.integration.protection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class CompositeRegionProtection implements RegionProtection {
  private final List<Adapter> adapters;
  private final Logger logger;
  private final boolean failClosedOnError;
  private final Set<String> warnedFailures = ConcurrentHashMap.newKeySet();

  public CompositeRegionProtection(
      List<Adapter> adapters, Logger logger, boolean failClosedOnError) {
    this.adapters = List.copyOf(adapters);
    this.logger = logger;
    this.failClosedOnError = failClosedOnError;
  }

  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    return check("build", adapter -> adapter.protection().canBuild(player, location, material));
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    return check("break", adapter -> adapter.protection().canBreak(player, block));
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    return check("interact", adapter -> adapter.protection().canInteract(player, block));
  }

  @Override
  public boolean canUse(Player player, Block block) {
    return check("use", adapter -> adapter.protection().canUse(player, block));
  }

  private boolean check(String action, AdapterCheck check) {
    for (Adapter adapter : adapters) {
      try {
        if (!check.allowed(adapter)) {
          return false;
        }
      } catch (RuntimeException | LinkageError error) {
        warnFailure(adapter.name(), action, error);
        if (failClosedOnError) {
          return false;
        }
      }
    }
    return true;
  }

  private void warnFailure(String adapterName, String action, Throwable error) {
    if (!warnedFailures.add(adapterName + ":" + action)) {
      return;
    }
    if (logger == null) {
      return;
    }
    logger.log(
        Level.WARNING,
        adapterName
            + " protection "
            + action
            + " check failed; "
            + (failClosedOnError ? "denying Exort action." : "allowing Exort action."),
        error);
  }

  public List<String> adapterNames() {
    List<String> names = new ArrayList<>(adapters.size());
    for (Adapter adapter : adapters) {
      names.add(adapter.name());
    }
    return names;
  }

  public List<String> runtimeFailureKeys() {
    return warnedFailures.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }

  public record Adapter(String name, RegionProtection protection) {}

  private interface AdapterCheck {
    boolean allowed(Adapter adapter);
  }
}
