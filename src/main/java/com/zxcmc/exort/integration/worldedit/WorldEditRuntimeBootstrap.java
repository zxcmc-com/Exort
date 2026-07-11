package com.zxcmc.exort.integration.worldedit;

import com.zxcmc.exort.integration.OptionalProviderLifecycle;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public final class WorldEditRuntimeBootstrap {
  private WorldEditRuntimeBootstrap() {}

  public static Registration register(
      WorldEditBridgeDependencies dependencies,
      Function<WorldEditBridgeDependencies, WorldEditIntegration> registrar,
      Consumer<WorldEditIntegration> integrationSink) {
    Objects.requireNonNull(dependencies, "dependencies");
    Objects.requireNonNull(registrar, "registrar");
    Objects.requireNonNull(integrationSink, "integrationSink");

    OptionalProviderLifecycle<WorldEditIntegration> lifecycle =
        new OptionalProviderLifecycle<>(integrationSink, WorldEditIntegration::shutdown);
    lifecycle.enable(() -> registrar.apply(dependencies));
    Plugin plugin = dependencies.plugin();
    Listener watcher =
        new Listener() {
          @EventHandler
          public void onPluginEnable(PluginEnableEvent event) {
            String name = event.getPlugin().getName();
            if (!isWorldEditProvider(name)) {
              return;
            }
            lifecycle.refresh(() -> registrar.apply(dependencies));
          }

          @EventHandler
          public void onPluginDisable(PluginDisableEvent event) {
            String name = event.getPlugin().getName();
            if (!isWorldEditProvider(name)) {
              return;
            }
            lifecycle.disable();
            if (otherProviderIsEnabled(name)) {
              lifecycle.enable(() -> registrar.apply(dependencies));
            }
          }
        };
    Bukkit.getPluginManager().registerEvents(watcher, plugin);
    return new Registration(watcher, lifecycle);
  }

  private static boolean isWorldEditProvider(String pluginName) {
    return "WorldEdit".equals(pluginName) || "FastAsyncWorldEdit".equals(pluginName);
  }

  private static boolean otherProviderIsEnabled(String disabledProvider) {
    String other = "WorldEdit".equals(disabledProvider) ? "FastAsyncWorldEdit" : "WorldEdit";
    Plugin plugin = Bukkit.getPluginManager().getPlugin(other);
    return plugin != null && plugin.isEnabled();
  }

  public static final class Registration implements AutoCloseable {
    private final Listener watcher;
    private final OptionalProviderLifecycle<WorldEditIntegration> lifecycle;
    private boolean closed;

    private Registration(
        Listener watcher, OptionalProviderLifecycle<WorldEditIntegration> lifecycle) {
      this.watcher = watcher;
      this.lifecycle = lifecycle;
    }

    public WorldEditIntegration current() {
      return lifecycle.current();
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      HandlerList.unregisterAll(watcher);
      lifecycle.close();
    }
  }
}
