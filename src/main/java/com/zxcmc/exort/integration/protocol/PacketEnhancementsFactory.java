package com.zxcmc.exort.integration.protocol;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PacketEnhancementsFactory {
  private static final String PACKET_EVENTS_PLUGIN = "packetevents";

  private PacketEnhancementsFactory() {}

  public static PacketEnhancements tryCreate(JavaPlugin plugin, Consumer<String> pickDebugSink) {
    if (!plugin.getConfig().getBoolean("packetEvents.enabled", true)) {
      return null;
    }
    Plugin packetEvents = Bukkit.getPluginManager().getPlugin(PACKET_EVENTS_PLUGIN);
    if (packetEvents == null || !packetEvents.isEnabled()) {
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
}
