package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.lang.reflect.InvocationTargetException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class WorldEditIntegration {
  private static final String BRIDGE_CLASS =
      "com.zxcmc.exort.integration.worldedit.WorldEditBridge";
  private static final String WORLDEDIT_CLASS = "com.sk89q.worldedit.WorldEdit";
  private static final String EXTENT_CLASS = "com.sk89q.worldedit.extent.Extent";

  private final Object bridge;

  private WorldEditIntegration(Object bridge) {
    this.bridge = bridge;
  }

  public static WorldEditIntegration tryRegister(WorldEditBridgeDependencies dependencies) {
    if (dependencies == null) return null;
    Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
    Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
    if (worldEdit == null && fawe == null) return null;
    if (!isClassAvailable(WORLDEDIT_CLASS) || !isClassAvailable(EXTENT_CLASS)) {
      ExortLog.warn("[WorldEdit] Integration disabled: missing classes.");
      return null;
    }
    try {
      Class<?> bridgeClass =
          Class.forName(BRIDGE_CLASS, true, WorldEditIntegration.class.getClassLoader());
      Object bridgeInstance =
          bridgeClass
              .getMethod("tryRegister", WorldEditBridgeDependencies.class)
              .invoke(null, dependencies);
      return bridgeInstance == null ? null : new WorldEditIntegration(bridgeInstance);
    } catch (InvocationTargetException e) {
      ExortLog.warn("[WorldEdit] Integration disabled: " + message(e.getCause()));
      return null;
    } catch (ReflectiveOperationException | LinkageError e) {
      ExortLog.warn("[WorldEdit] Integration disabled: " + message(e));
      return null;
    }
  }

  public void shutdown() {
    try {
      bridge.getClass().getMethod("shutdown").invoke(bridge);
    } catch (ReflectiveOperationException | LinkageError ignored) {
    }
  }

  private static boolean isClassAvailable(String className) {
    try {
      Class.forName(className, false, WorldEditIntegration.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError ignored) {
      return false;
    }
  }

  private static String message(Throwable throwable) {
    if (throwable == null) return "unknown error";
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable.getClass().getSimpleName();
    }
    return throwable.getClass().getSimpleName() + ": " + message;
  }
}
