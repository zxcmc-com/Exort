package com.zxcmc.exort.core.protection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public final class WorldGuardProtection implements RegionProtection {
  private final Method causeCreate;
  private final Method fireAndTest;
  private final Constructor<?> placeCtor;
  private final Constructor<?> breakCtor;
  private final Constructor<?> useCtor;

  public WorldGuardProtection() {
    try {
      ClassLoader loader = WorldGuardProtection.class.getClassLoader();
      Class<?> causeClass = Class.forName("com.sk89q.worldguard.bukkit.cause.Cause", false, loader);
      Class<?> eventsClass =
          Class.forName("com.sk89q.worldguard.bukkit.util.Events", false, loader);
      Class<?> placeClass =
          Class.forName("com.sk89q.worldguard.bukkit.event.block.PlaceBlockEvent", false, loader);
      Class<?> breakClass =
          Class.forName("com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent", false, loader);
      Class<?> useClass =
          Class.forName("com.sk89q.worldguard.bukkit.event.block.UseBlockEvent", false, loader);

      causeCreate = causeClass.getMethod("create", Object[].class);
      fireAndTest = eventsClass.getMethod("fireAndTestCancel", Event.class);
      placeCtor =
          placeClass.getConstructor(Event.class, causeClass, Location.class, Material.class);
      breakCtor = breakClass.getConstructor(Event.class, causeClass, Block.class);
      useCtor = useClass.getConstructor(Event.class, causeClass, Block.class);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("WorldGuard classes not available", e);
    }
  }

  @Override
  public boolean canBuild(Player player, Location location, Material material) {
    if (player == null || location == null || material == null) return true;
    return fire(placeCtor, player, location, material);
  }

  @Override
  public boolean canBreak(Player player, Block block) {
    if (player == null || block == null) return true;
    return fire(breakCtor, player, block);
  }

  @Override
  public boolean canInteract(Player player, Block block) {
    if (player == null || block == null) return true;
    return fire(useCtor, player, block);
  }

  @Override
  public boolean canUse(Player player, Block block) {
    if (player == null || block == null) return true;
    return fire(useCtor, player, block);
  }

  private Object cause(Player player) throws ReflectiveOperationException {
    return causeCreate.invoke(null, new Object[] {new Object[] {player}});
  }

  private boolean fire(Constructor<?> ctor, Object... args) {
    try {
      Object[] callArgs = new Object[args.length + 1];
      callArgs[0] = null;
      callArgs[1] = cause((Player) args[0]);
      System.arraycopy(args, 1, callArgs, 2, args.length - 1);
      Object event = ctor.newInstance(callArgs);
      boolean cancelled = (Boolean) fireAndTest.invoke(null, event);
      return !cancelled;
    } catch (ReflectiveOperationException e) {
      return true;
    }
  }
}
