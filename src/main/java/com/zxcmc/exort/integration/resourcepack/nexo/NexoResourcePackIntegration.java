package com.zxcmc.exort.integration.resourcepack.nexo;

import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackHosting;
import com.zxcmc.exort.integration.resourcepack.ResourcePackProviderBridge;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class NexoResourcePackIntegration {
  public static final String PLUGIN_NAME = "Nexo";

  private final AtomicBoolean registered = new AtomicBoolean();
  private final AtomicReference<File> apiHandoffRawPack = new AtomicReference<>();

  public boolean registerIfAvailable(JavaPlugin plugin, Plugin provider) {
    Objects.requireNonNull(plugin, "plugin");
    if (registered.get()) {
      return true;
    }
    if (provider != null && !PLUGIN_NAME.equals(provider.getName())) {
      return false;
    }
    if (!ResourcePackProviderBridge.isProviderInstalled(plugin, ResourcePackHosting.NEXO)) {
      return false;
    }
    try {
      Bukkit.getPluginManager().registerEvents(new NexoPostPackGenerateListener(this), plugin);
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

  public void clearRegistration() {
    registered.set(false);
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
