package com.zxcmc.exort.integration.resourcepack.nexo;

import com.zxcmc.exort.infra.logging.ExortLog;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexoResourcePackIntegration {
  public static final String PLUGIN_NAME = "Nexo";

  private final AtomicBoolean registered = new AtomicBoolean();
  private final AtomicReference<File> apiHandoffRawPack = new AtomicReference<>();
  private final AtomicReference<Listener> listener = new AtomicReference<>();

  public synchronized boolean registerIfAvailable(JavaPlugin plugin, Plugin provider) {
    Objects.requireNonNull(plugin, "plugin");
    if (registered.get()) {
      return true;
    }
    if (provider == null || !PLUGIN_NAME.equals(provider.getName()) || !provider.isEnabled()) {
      return false;
    }
    try {
      Listener candidate = new NexoPostPackGenerateListener(this);
      Bukkit.getPluginManager().registerEvents(candidate, plugin);
      listener.set(candidate);
      registered.set(true);
      ExortLog.success("[Nexo] Resource-pack API integration registered" + versionSuffix(provider));
      return true;
    } catch (LinkageError | RuntimeException error) {
      ExortLog.warn(
          "[Nexo] Resource-pack API integration unavailable: " + error.getClass().getSimpleName());
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
    clearApiHandoff();
  }

  public boolean isApiHandoffAvailable() {
    return registered.get();
  }

  public boolean useApiHandoff(File rawPack) {
    if (!registered.get() || rawPack == null || !rawPack.isFile()) {
      clearApiHandoff();
      return false;
    }
    apiHandoffRawPack.set(rawPack);
    return true;
  }

  public void clearApiHandoff() {
    apiHandoffRawPack.set(null);
  }

  boolean addCurrentPack(NexoPackAdder adder) {
    File rawPack = apiHandoffRawPack.get();
    if (rawPack == null) {
      return false;
    }
    if (!rawPack.isFile()) {
      ExortLog.warn("[Nexo] Skipping API resource-pack handoff; raw Exort pack is missing.");
      clearApiHandoff();
      return false;
    }
    try {
      boolean added = adder.addResourcePack(rawPack);
      if (added) {
        ExortLog.info("[Nexo] Added Exort resource pack through post-generate API: " + rawPack);
      } else {
        ExortLog.warn("[Nexo] Nexo rejected Exort resource pack API handoff: " + rawPack);
      }
      return added;
    } catch (RuntimeException error) {
      ExortLog.warn(
          "[Nexo] Failed to add Exort resource pack through Nexo API: " + error.getMessage());
      return false;
    }
  }

  private static String versionSuffix(Plugin provider) {
    if (provider == null) {
      return ".";
    }
    return ": Nexo " + provider.getPluginMeta().getVersion() + ".";
  }

  @FunctionalInterface
  interface NexoPackAdder {
    boolean addResourcePack(File rawPack);
  }
}
