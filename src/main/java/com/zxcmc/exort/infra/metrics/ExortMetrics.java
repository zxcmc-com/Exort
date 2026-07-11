package com.zxcmc.exort.infra.metrics;

import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.i18n.LocalizationFiles;
import com.zxcmc.exort.integration.protection.ProtectionRuntimeConfig;
import com.zxcmc.exort.recipes.RecipeRuntimeConfig;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortMetrics {
  private static final int BSTATS_PLUGIN_ID = 28841;
  private static final String OTHER_LANGUAGE = "other";
  private static final Set<String> BUNDLED_LANGUAGES = loadBundledLanguages();

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
            "protection_state",
            () ->
                protectionStateChartValue(
                    ProtectionRuntimeConfig.fromConfig(plugin.getConfig()),
                    plugin.getServer().getPluginManager()::isPluginEnabled)));
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
    if (rawLanguage != null && rawLanguage.isBlank()) {
      return OTHER_LANGUAGE;
    }
    String normalized = LocalizationFiles.normalizeLanguage(rawLanguage);
    return BUNDLED_LANGUAGES.contains(normalized) ? normalized : OTHER_LANGUAGE;
  }

  static Set<String> bundledLanguageChartValues() {
    return BUNDLED_LANGUAGES;
  }

  private static Set<String> loadBundledLanguages() {
    try (InputStream input =
        ExortMetrics.class.getClassLoader().getResourceAsStream(LocalizationFiles.LANG_INDEX)) {
      if (input == null) {
        return Set.of(LocalizationFiles.DEFAULT_LANGUAGE);
      }
      LinkedHashSet<String> languages =
          new LinkedHashSet<>(LocalizationFiles.readLanguageIndex(input));
      languages.add(LocalizationFiles.DEFAULT_LANGUAGE);
      return Set.copyOf(languages);
    } catch (Exception ignored) {
      return Set.of(LocalizationFiles.DEFAULT_LANGUAGE);
    }
  }

  static String protectionStateChartValue(
      ProtectionRuntimeConfig config, PluginPresence pluginPresence) {
    if (!config.enabled()) return "disabled";
    ProtectionRuntimeConfig.Adapters adapters = config.adapters();
    int active = 0;
    String activeName = "";
    if (adapters.worldGuard() && pluginPresence.isEnabled("WorldGuard")) {
      active++;
      activeName = "worldguard";
    }
    if (adapters.griefPrevention() && pluginPresence.isEnabled("GriefPrevention")) {
      active++;
      activeName = "griefprevention";
    }
    if (adapters.towny() && pluginPresence.isEnabled("Towny")) {
      active++;
      activeName = "towny";
    }
    if (adapters.lands() && pluginPresence.isEnabled("Lands")) {
      active++;
      activeName = "lands";
    }
    if (adapters.residence() && pluginPresence.isEnabled("Residence")) {
      active++;
      activeName = "residence";
    }
    if (active == 0) return "missing";
    return active == 1 ? activeName : "multiple";
  }

  interface PluginPresence {
    boolean isEnabled(String pluginName);
  }
}
