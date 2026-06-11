package com.zxcmc.exort.bus;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.keys.StorageKeys;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record BusSessionDependencies(
    JavaPlugin plugin,
    StorageKeys keys,
    BossBarManager bossBarManager,
    BooleanSupplier resourceMode,
    Supplier<Material> busCarrier,
    int wireLimit,
    int wireHardCap,
    Material wireMaterial,
    Material storageCarrier,
    Supplier<GuiRuntimeConfig> runtimeConfig,
    Supplier<GuiOverlayConfig> overlayConfig,
    ItemNameService itemNameService) {
  public BusSessionDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(resourceMode, "resourceMode");
    Objects.requireNonNull(busCarrier, "busCarrier");
    Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    Objects.requireNonNull(overlayConfig, "overlayConfig");
    Objects.requireNonNull(itemNameService, "itemNameService");
  }
}
