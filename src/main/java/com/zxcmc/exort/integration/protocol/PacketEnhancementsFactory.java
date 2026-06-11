package com.zxcmc.exort.integration.protocol;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PacketEnhancementsFactory {
  private static final String PACKET_EVENTS_PLUGIN = "packetevents";
  private static final Set<String> LOGGED_FALLBACKS = ConcurrentHashMap.newKeySet();

  private PacketEnhancementsFactory() {}

  public static PacketEnhancements tryCreate(JavaPlugin plugin, Consumer<String> pickDebugSink) {
    if (!plugin.getConfig().getBoolean("packetEvents.enabled", true)) {
      return null;
    }
    Plugin packetEvents = Bukkit.getPluginManager().getPlugin(PACKET_EVENTS_PLUGIN);
    if (packetEvents == null || !packetEvents.isEnabled()) {
      logUnavailableFallback(packetEvents);
      return null;
    }
    try {
      return PacketEventsEnhancements.tryCreate(plugin, packetEvents, pickDebugSink);
    } catch (LinkageError | RuntimeException e) {
      ExortLog.warn("[PacketEvents] Integration failed to initialize: " + describeError(e));
      return null;
    }
  }

  private static String describeError(Throwable error) {
    String message = error.getMessage();
    return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
  }

  private static void logUnavailableFallback(Plugin packetEvents) {
    String message = unavailableFallbackMessage(packetEvents == null, pluginVersion(packetEvents));
    if (LOGGED_FALLBACKS.add(message)) {
      ExortLog.info(message);
    }
  }

  static String unavailableFallbackMessage(boolean missing, String version) {
    if (missing) {
      return "[PacketEvents] Plugin not found; using Paper fallbacks for optional packet features.";
    }
    String suffix = version == null || version.isBlank() ? "" : " (" + version + ")";
    return "[PacketEvents] Plugin is installed"
        + suffix
        + " but not enabled; using Paper fallbacks for optional packet features.";
  }

  private static String pluginVersion(Plugin plugin) {
    return plugin == null ? null : plugin.getPluginMeta().getVersion();
  }
}
