package com.zxcmc.exort.runtime;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SearchDialogService;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.items.InventoryRefreshService;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageManager;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/** Stable services shared by one runtime generation. */
public record CoreServices(
    JavaPlugin plugin,
    Lang lang,
    ItemNameService itemNameService,
    SearchDialogService searchDialogService,
    StorageKeys keys,
    StorageManager storageManager,
    Database database,
    SessionManager sessionManager,
    BossBarManager bossBarManager,
    PlayerFeedback playerFeedback,
    InventoryRefreshService inventoryRefreshService,
    MaintenanceScheduler maintenanceScheduler) {
  public CoreServices {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(lang, "lang");
    Objects.requireNonNull(itemNameService, "itemNameService");
    Objects.requireNonNull(searchDialogService, "searchDialogService");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(inventoryRefreshService, "inventoryRefreshService");
    Objects.requireNonNull(maintenanceScheduler, "maintenanceScheduler");
  }
}
