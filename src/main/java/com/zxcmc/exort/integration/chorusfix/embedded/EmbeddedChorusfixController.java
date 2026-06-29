package com.zxcmc.exort.integration.chorusfix.embedded;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.chorusfix.ChorusfixIntegration;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmbeddedChorusfixController {
  public static final String CHORUSFIX_DOWNLOAD_URL =
      "https://github.com/zxcmc-com/Chorusfix/releases/latest";
  public static final List<String> KNOWN_CHORUS_PROVIDER_PLUGINS =
      List.of("Nexo", "ItemsAdder", "Oraxen");
  private static final String EMBEDDED_ENABLED_MESSAGE =
      "[Chorusfix] Embedded Exort chorus handling enabled.";

  private final JavaPlugin plugin;
  private final Supplier<EmbeddedChorusfixConfig> config;
  private final BooleanSupplier paperChorusUpdatesDisabled;
  private final Supplier<Material> wireMaterial;
  private final Predicate<Block> exortChorusCarrier;
  private final BooleanSupplier externalChorusfixEnabled;
  private final Supplier<List<String>> enabledKnownProviders;
  private ChorusUpdateService updates;
  private ChorusUpdateListener listener;
  private EmbeddedChorusfixStatus status = EmbeddedChorusfixStatus.INACTIVE;
  private List<String> blockedProviders = List.of();
  private final BlockedProviderWarningState blockedProviderWarningState =
      new BlockedProviderWarningState();
  private boolean embeddedEnabledLogged;

  public EmbeddedChorusfixController(
      JavaPlugin plugin,
      Supplier<EmbeddedChorusfixConfig> config,
      BooleanSupplier paperChorusUpdatesDisabled,
      Supplier<Material> wireMaterial,
      Predicate<Block> exortChorusCarrier) {
    this(
        plugin,
        config,
        paperChorusUpdatesDisabled,
        wireMaterial,
        exortChorusCarrier,
        () -> Bukkit.getPluginManager().isPluginEnabled(ChorusfixIntegration.PLUGIN_NAME),
        EmbeddedChorusfixController::enabledKnownProvidersFromBukkit);
  }

  EmbeddedChorusfixController(
      JavaPlugin plugin,
      Supplier<EmbeddedChorusfixConfig> config,
      BooleanSupplier paperChorusUpdatesDisabled,
      Supplier<Material> wireMaterial,
      Predicate<Block> exortChorusCarrier,
      BooleanSupplier externalChorusfixEnabled,
      Supplier<List<String>> enabledKnownProviders) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.config = Objects.requireNonNull(config, "config");
    this.paperChorusUpdatesDisabled =
        Objects.requireNonNull(paperChorusUpdatesDisabled, "paperChorusUpdatesDisabled");
    this.wireMaterial = Objects.requireNonNull(wireMaterial, "wireMaterial");
    this.exortChorusCarrier = Objects.requireNonNull(exortChorusCarrier, "exortChorusCarrier");
    this.externalChorusfixEnabled =
        Objects.requireNonNull(externalChorusfixEnabled, "externalChorusfixEnabled");
    this.enabledKnownProviders =
        Objects.requireNonNull(enabledKnownProviders, "enabledKnownProviders");
  }

  public EmbeddedChorusfixStatus refresh() {
    stop();
    EmbeddedChorusfixConfig settings = config.get();
    List<String> providers = enabledKnownProviders.get();
    EmbeddedChorusfixStatus next =
        evaluate(
            settings,
            externalChorusfixEnabled.getAsBoolean(),
            paperChorusUpdatesDisabled.getAsBoolean(),
            wireMaterial.get(),
            providers);
    blockedProviders =
        next == EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER ? List.copyOf(providers) : List.of();
    status = next;
    if (next != EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER) {
      blockedProviderWarningState.reset();
    }
    if (next != EmbeddedChorusfixStatus.EMBEDDED) {
      embeddedEnabledLogged = false;
    }
    if (next == EmbeddedChorusfixStatus.EMBEDDED) {
      start(settings);
    }
    return status;
  }

  public void stop() {
    if (updates != null) {
      updates.shutdown();
      updates = null;
    }
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
  }

  public EmbeddedChorusfixStatus status() {
    return status;
  }

  public List<String> blockedProviders() {
    return blockedProviders;
  }

  public boolean announceEmbeddedIfActive() {
    return announceEmbeddedIfActive(ExortLog::success);
  }

  boolean announceEmbeddedIfActive(Consumer<String> successConsumer) {
    Objects.requireNonNull(successConsumer, "successConsumer");
    if (status != EmbeddedChorusfixStatus.EMBEDDED || embeddedEnabledLogged) {
      return false;
    }
    embeddedEnabledLogged = true;
    successConsumer.accept(EMBEDDED_ENABLED_MESSAGE);
    return true;
  }

  public boolean warnIfBlockedByProvider() {
    return warnIfBlockedByProvider(ExortLog::warn);
  }

  boolean warnIfBlockedByProvider(Consumer<String> warningConsumer) {
    Objects.requireNonNull(warningConsumer, "warningConsumer");
    if (!blockedProviderWarningState.shouldWarn(status, blockedProviders)) {
      return false;
    }
    warningConsumer.accept(blockedByProviderWarning(blockedProviders));
    return true;
  }

  public boolean isKnownProvider(String pluginName) {
    return KNOWN_CHORUS_PROVIDER_PLUGINS.contains(pluginName);
  }

  private void start(EmbeddedChorusfixConfig settings) {
    ExortChorusCarrierDetector detector = (block, mask) -> exortChorusCarrier.test(block);
    updates =
        new ChorusUpdateService(
            plugin, settings, detector, new EmbeddedChorusfixRuntimeState(true));
    listener = new ChorusUpdateListener(updates);
    Bukkit.getPluginManager().registerEvents(listener, plugin);
  }

  static EmbeddedChorusfixStatus evaluate(
      EmbeddedChorusfixConfig settings,
      boolean externalChorusfixEnabled,
      boolean paperChorusUpdatesDisabled,
      Material wireMaterial,
      List<String> providers) {
    if (externalChorusfixEnabled) {
      return EmbeddedChorusfixStatus.EXTERNAL;
    }
    if (!settings.enabled()) {
      return EmbeddedChorusfixStatus.DISABLED;
    }
    if (!paperChorusUpdatesDisabled || wireMaterial != Carriers.CHORUS_MATERIAL) {
      return EmbeddedChorusfixStatus.INACTIVE;
    }
    if (!providers.isEmpty()) {
      return EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER;
    }
    return EmbeddedChorusfixStatus.EMBEDDED;
  }

  static String blockedByProviderWarning(List<String> providers) {
    String providerList = String.join(", ", providers);
    String verb = providers.size() == 1 ? "is" : "are";
    return "[Chorusfix] Embedded Exort chorus handling disabled because "
        + providerList
        + " "
        + verb
        + " enabled without the external Chorusfix plugin. Download Chorusfix here: "
        + CHORUSFIX_DOWNLOAD_URL;
  }

  private static List<String> enabledKnownProvidersFromBukkit() {
    PluginManager manager = Bukkit.getPluginManager();
    return KNOWN_CHORUS_PROVIDER_PLUGINS.stream().filter(manager::isPluginEnabled).toList();
  }

  static final class BlockedProviderWarningState {
    private List<String> warnedProviders = List.of();

    boolean shouldWarn(EmbeddedChorusfixStatus status, List<String> providers) {
      if (status != EmbeddedChorusfixStatus.BLOCKED_BY_PROVIDER) {
        reset();
        return false;
      }
      List<String> currentProviders = List.copyOf(providers);
      if (currentProviders.isEmpty() || currentProviders.equals(warnedProviders)) {
        return false;
      }
      warnedProviders = currentProviders;
      return true;
    }

    void reset() {
      warnedProviders = List.of();
    }
  }
}
