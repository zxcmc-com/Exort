package com.zxcmc.exort.command;

import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.LoadTestService;
import com.zxcmc.exort.debug.PickDebugService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.culling.DisplayCullingService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.resourcepack.hosting.ResourcePackService;
import com.zxcmc.exort.integration.chorusfix.embedded.EmbeddedChorusfixStatus;
import com.zxcmc.exort.integration.protection.ProtectionStatus;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.platform.PaperChorusPlantUpdates;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record ExortBrigadierDependencies(
    JavaPlugin plugin,
    Lang lang,
    CommandRuntimeAccess runtimeAccess,
    StorageKeys keys,
    StorageManager storageManager,
    Database database,
    SessionManager sessionManager,
    CacheDebugService cacheDebugService,
    WorldEditDebugService worldEditDebugService,
    PickDebugService pickDebugService,
    Supplier<DisplayCullingService> displayCullingService,
    LoadTestService loadTestService,
    ItemNameService itemNameService,
    Supplier<ResourcePackService> resourcePackService,
    Runnable resourcePackReloader,
    Supplier<CompletableFuture<ItemNameService.Status>> runtimeReloader,
    Supplier<String> configuredLanguage,
    Consumer<String> configuredLanguageSaver,
    Consumer<String> configuredModeSaver,
    Supplier<String> configuredMode,
    Supplier<String> effectiveMode,
    BooleanSupplier resourceWireCarrierFallback,
    Supplier<String> pluginVersion,
    Supplier<PaperChorusPlantUpdates.FixResult> chorusPlantUpdateDisabler,
    Supplier<EmbeddedChorusfixStatus> chorusfixStatus,
    LongSupplier cacheIdleUnloadSeconds,
    IntSupplier wireLimit,
    IntSupplier wireHardCap,
    IntSupplier relayRangeChunks,
    Supplier<Material> wireMaterial,
    Supplier<Material> storageCarrier,
    Supplier<Material> relayCarrier,
    Supplier<ProtectionStatus> protectionStatus) {
  public ExortBrigadierDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(runtimeAccess, "runtimeAccess");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(cacheDebugService, "cacheDebugService");
    Objects.requireNonNull(worldEditDebugService, "worldEditDebugService");
    Objects.requireNonNull(pickDebugService, "pickDebugService");
    Objects.requireNonNull(displayCullingService, "displayCullingService");
    Objects.requireNonNull(loadTestService, "loadTestService");
    Objects.requireNonNull(itemNameService, "itemNameService");
    Objects.requireNonNull(resourcePackService, "resourcePackService");
    Objects.requireNonNull(resourcePackReloader, "resourcePackReloader");
    Objects.requireNonNull(runtimeReloader, "runtimeReloader");
    Objects.requireNonNull(configuredLanguage, "configuredLanguage");
    Objects.requireNonNull(configuredLanguageSaver, "configuredLanguageSaver");
    Objects.requireNonNull(configuredModeSaver, "configuredModeSaver");
    Objects.requireNonNull(configuredMode, "configuredMode");
    Objects.requireNonNull(effectiveMode, "effectiveMode");
    Objects.requireNonNull(resourceWireCarrierFallback, "resourceWireCarrierFallback");
    Objects.requireNonNull(pluginVersion, "pluginVersion");
    Objects.requireNonNull(chorusPlantUpdateDisabler, "chorusPlantUpdateDisabler");
    Objects.requireNonNull(chorusfixStatus, "chorusfixStatus");
    Objects.requireNonNull(cacheIdleUnloadSeconds, "cacheIdleUnloadSeconds");
    Objects.requireNonNull(wireLimit, "wireLimit");
    Objects.requireNonNull(wireHardCap, "wireHardCap");
    Objects.requireNonNull(relayRangeChunks, "relayRangeChunks");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
    Objects.requireNonNull(protectionStatus, "protectionStatus");
  }

  public CustomItems customItems() {
    return runtimeAccess.customItems();
  }

  public WirelessTerminalService wirelessService() {
    return runtimeAccess.wirelessService();
  }
}
