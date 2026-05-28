package com.zxcmc.exort.integration.worldedit;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public final class WorldEditRuntimeBootstrap {
  private WorldEditRuntimeBootstrap() {}

  public static WorldEditIntegration register(
      WorldEditBridgeDependencies dependencies,
      Function<WorldEditBridgeDependencies, WorldEditIntegration> registrar,
      Consumer<WorldEditIntegration> integrationSink) {
    Objects.requireNonNull(dependencies, "dependencies");
    Objects.requireNonNull(registrar, "registrar");
    Objects.requireNonNull(integrationSink, "integrationSink");

    WorldEditIntegration integration = registrar.apply(dependencies);
    integrationSink.accept(integration);
    if (integration != null) {
      return integration;
    }
    Plugin plugin = dependencies.plugin();
    Bukkit.getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onPluginEnable(PluginEnableEvent event) {
                String name = event.getPlugin().getName();
                if (!"WorldEdit".equals(name) && !"FastAsyncWorldEdit".equals(name)) {
                  return;
                }
                WorldEditIntegration registered = registrar.apply(dependencies);
                if (registered != null) {
                  integrationSink.accept(registered);
                  HandlerList.unregisterAll(this);
                }
              }
            },
            plugin);
    return null;
  }
}
