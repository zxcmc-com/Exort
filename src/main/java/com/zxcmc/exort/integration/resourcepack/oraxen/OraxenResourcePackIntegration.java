package com.zxcmc.exort.integration.resourcepack.oraxen;

import com.zxcmc.exort.infra.logging.ExortLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class OraxenResourcePackIntegration {
  public static final String PLUGIN_NAME = "Oraxen";

  private OraxenResourcePackIntegration() {}

  public static boolean registerIfEnabled(JavaPlugin plugin, Plugin provider) {
    if (provider == null || !PLUGIN_NAME.equals(provider.getName()) || !provider.isEnabled()) {
      return false;
    }
    try {
      Bukkit.getPluginManager().registerEvents(new OraxenPackGeneratedListener(), plugin);
      ExortLog.success(
          "[Oraxen] Resource-pack integration enabled: Oraxen "
              + provider.getPluginMeta().getVersion()
              + ".");
      return true;
    } catch (LinkageError | RuntimeException error) {
      ExortLog.warn(
          "[Oraxen] Resource-pack integration unavailable: " + error.getClass().getSimpleName());
      return false;
    }
  }
}
