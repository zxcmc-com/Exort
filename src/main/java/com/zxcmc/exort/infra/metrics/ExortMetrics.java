package com.zxcmc.exort.infra.metrics;

import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.integration.protection.WorldGuardProtectionConfig;
import com.zxcmc.exort.recipes.RecipeRuntimeConfig;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortMetrics {
  private static final int BSTATS_PLUGIN_ID = 28841;
  private static final Pattern LANGUAGE_CODE_PATTERN =
      Pattern.compile("[a-z]{2,3}(?:_[a-z0-9]{2,4})?");

  private ExortMetrics() {}

  public static Metrics create(JavaPlugin plugin) {
    Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
    registerCharts(plugin, metrics);
    return metrics;
  }

  private static void registerCharts(JavaPlugin plugin, Metrics metrics) {
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
            () -> languageChartValue(plugin.getConfig().getString("language", "en_us"))));
    metrics.addCustomChart(
        new SimplePie(
            "wireless_enabled",
            () ->
                WirelessRuntimeConfig.fromConfig(plugin.getConfig()).enabled()
                    ? "enabled"
                    : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "recipes_enabled",
            () ->
                RecipeRuntimeConfig.fromConfig(plugin.getConfig()).enabled()
                    ? "enabled"
                    : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "worldguard_state",
            () -> {
              WorldGuardProtectionConfig config =
                  WorldGuardProtectionConfig.fromConfig(plugin.getConfig());
              if (!config.enabled()) return "disabled";
              boolean present = plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
              return present ? "active" : "missing";
            }));
    metrics.addCustomChart(
        new SimplePie(
            "bus_storage_targets",
            () ->
                BusRuntimeConfig.fromConfig(plugin.getConfig()).allowStorageTargets()
                    ? "enabled"
                    : "disabled"));
    metrics.addCustomChart(
        new SimplePie(
            "default_sort_mode",
            () ->
                StorageRuntimeConfig.fromConfig(plugin.getConfig())
                    .defaultSortModeName()
                    .toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "bus_default_import",
            () ->
                BusRuntimeConfig.fromConfig(plugin.getConfig())
                    .defaultImportMode()
                    .name()
                    .toLowerCase(Locale.ROOT)));
    metrics.addCustomChart(
        new SimplePie(
            "bus_default_export",
            () ->
                BusRuntimeConfig.fromConfig(plugin.getConfig())
                    .defaultExportMode()
                    .name()
                    .toLowerCase(Locale.ROOT)));
  }

  static String languageChartValue(String rawLanguage) {
    String normalized =
        rawLanguage == null
            ? "en_us"
            : rawLanguage.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    return LANGUAGE_CODE_PATTERN.matcher(normalized).matches() ? normalized : "custom_or_invalid";
  }
}
