package com.zxcmc.exort.runtime;

import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

public record RuntimeBusServicesDependencies(
    JavaPlugin plugin,
    StorageKeys keys,
    BossBarManager bossBarManager,
    StorageManager storageManager,
    Database database,
    WirelessTerminalService wirelessService,
    Lang lang,
    ItemNameService itemNameService,
    RuntimeMaterials materials,
    int wireLimit,
    int wireHardCap,
    BusRuntimeConfig busRuntime,
    BooleanSupplier resourceMode,
    Supplier<GuiRuntimeConfig> guiRuntimeConfig,
    Supplier<GuiOverlayConfig> guiOverlayConfig,
    Consumer<String> renderStorage) {
  public RuntimeBusServicesDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(wirelessService, "wirelessService");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(itemNameService, "itemNameService");
    Objects.requireNonNull(materials, "materials");
    Objects.requireNonNull(busRuntime, "busRuntime");
    Objects.requireNonNull(resourceMode, "resourceMode");
    Objects.requireNonNull(guiRuntimeConfig, "guiRuntimeConfig");
    Objects.requireNonNull(guiOverlayConfig, "guiOverlayConfig");
    Objects.requireNonNull(renderStorage, "renderStorage");
  }
}
