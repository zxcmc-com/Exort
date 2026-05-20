package com.zxcmc.exort.core.metrics;

import com.zxcmc.exort.core.ExortPlugin;
import java.util.Locale;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

public final class ExortMetrics {
  private static final int BSTATS_PLUGIN_ID = 28841;

  private ExortMetrics() {}

  public static Metrics create(ExortPlugin plugin) {
    Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
    registerCharts(plugin, metrics);
    return metrics;
  }

  private static void registerCharts(ExortPlugin plugin, Metrics metrics) {
    metrics.addCustomChart(
        new SimplePie(
            "mode",
            () ->
                "RESOURCE".equalsIgnoreCase(plugin.getConfig().getString("mode", "RESOURCE"))
                    ? "resource"
                    : "vanilla"));
    metrics.addCustomChart(
        new SimplePie(
            "language",
            () -> plugin.getConfig().getString("language", "en_us").toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "wireless_enabled",
            () ->
                plugin.getConfig().getBoolean("wireless.enabled", true) ? "enabled" : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "recipes_enabled",
            () -> plugin.getConfig().getBoolean("recipes.enabled", true) ? "enabled" : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "worldguard_state",
            () -> {
              boolean enabled = plugin.getConfig().getBoolean("worldguard.enabled", true);
              if (!enabled) return "disabled";
              boolean present = plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
              return present ? "active" : "missing";
            }));
    metrics.addCustomChart(
        new SimplePie(
            "bus_storage_targets",
            () ->
                plugin.getConfig().getBoolean("bus.allowStorageTargets", true)
                    ? "enabled"
                    : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "default_sort_mode",
            () ->
                plugin
                    .getConfig()
                    .getString("defaultSortMode", "AMOUNT")
                    .toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "bus_default_import",
            () ->
                plugin
                    .getConfig()
                    .getString("bus.defaultMode.import", "WHITELIST")
                    .toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "bus_default_export",
            () ->
                plugin
                    .getConfig()
                    .getString("bus.defaultMode.export", "WHITELIST")
                    .toLowerCase(Locale.ROOT)));
  }
}
