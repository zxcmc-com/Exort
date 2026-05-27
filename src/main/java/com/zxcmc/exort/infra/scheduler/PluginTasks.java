package com.zxcmc.exort.infra.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PluginTasks {
  private PluginTasks() {}

  public static void runSyncIfEnabled(Plugin plugin, Runnable task) {
    if (plugin == null || task == null || !plugin.isEnabled()) return;
    try {
      Bukkit.getScheduler()
          .runTask(
              plugin,
              () -> {
                if (plugin.isEnabled()) {
                  task.run();
                }
              });
    } catch (RuntimeException ignored) {
      // The plugin may be disabling while an async callback completes.
    }
  }
}
