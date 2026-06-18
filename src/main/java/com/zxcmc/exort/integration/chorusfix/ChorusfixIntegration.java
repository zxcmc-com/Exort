package com.zxcmc.exort.integration.chorusfix;

import org.bukkit.plugin.Plugin;

public final class ChorusfixIntegration {
  public static final String PLUGIN_NAME = "Chorusfix";

  private ChorusfixIntegration() {}

  public static String enabledMessage(Plugin plugin) {
    String version = plugin == null ? "unknown" : plugin.getPluginMeta().getVersion();
    return "[Chorusfix] Integration enabled: Chorusfix " + version + ".";
  }
}
