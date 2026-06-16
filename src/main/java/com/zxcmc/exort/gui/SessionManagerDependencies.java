package com.zxcmc.exort.gui;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record SessionManagerDependencies(
    JavaPlugin plugin,
    Database database,
    StorageManager storageManager,
    StorageKeys keys,
    Lang lang,
    ItemNameService itemNameService,
    SearchDialogService searchDialogService,
    Supplier<BossBarManager> bossBarManager,
    Supplier<PlayerFeedback> playerFeedback,
    Supplier<WirelessTerminalService> wirelessService,
    Supplier<BusService> busService,
    Supplier<CraftingRules> craftingRules,
    BooleanSupplier resourceMode,
    BooleanSupplier dialogSupported,
    IntSupplier wireLimit,
    IntSupplier wireHardCap,
    IntSupplier relayRangeChunks,
    Supplier<Material> wireMaterial,
    Supplier<Material> storageCarrier,
    Supplier<Material> relayCarrier,
    Supplier<Material> terminalCarrier,
    Supplier<GuiRuntimeConfig> runtimeConfig,
    Supplier<GuiOverlayConfig> overlayConfig,
    Consumer<String> storageChangeListener) {
  public SessionManagerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(itemNameService, "itemNameService");
    Objects.requireNonNull(searchDialogService, "searchDialogService");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(wirelessService, "wirelessService");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(craftingRules, "craftingRules");
    Objects.requireNonNull(resourceMode, "resourceMode");
    Objects.requireNonNull(dialogSupported, "dialogSupported");
    Objects.requireNonNull(wireLimit, "wireLimit");
    Objects.requireNonNull(wireHardCap, "wireHardCap");
    Objects.requireNonNull(relayRangeChunks, "relayRangeChunks");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    Objects.requireNonNull(overlayConfig, "overlayConfig");
    Objects.requireNonNull(storageChangeListener, "storageChangeListener");
  }
}
