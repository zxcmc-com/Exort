package com.zxcmc.exort.integration.resourcepack.oraxen;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class OraxenResourcePackIntegration {
  public static final String PLUGIN_NAME = "Oraxen";

  private final AtomicBoolean registered = new AtomicBoolean();
  private final AtomicReference<Listener> listener = new AtomicReference<>();

  public synchronized boolean registerIfEnabled(JavaPlugin plugin, Plugin provider) {
    Objects.requireNonNull(plugin, "plugin");
    if (registered.get()) {
      return true;
    }
    if (provider == null || !PLUGIN_NAME.equals(provider.getName()) || !provider.isEnabled()) {
      return false;
    }
    try {
      Listener candidate = new OraxenPackGeneratedListener();
      Bukkit.getPluginManager().registerEvents(candidate, plugin);
      listener.set(candidate);
      registered.set(true);
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

  public boolean isRegistered() {
    return registered.get();
  }

  public synchronized void clearRegistration() {
    registered.set(false);
    Listener registeredListener = listener.getAndSet(null);
    if (registeredListener != null) {
      HandlerList.unregisterAll(registeredListener);
    }
  }
}
