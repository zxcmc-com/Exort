package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record WirelessListenerDependencies(
    JavaPlugin plugin,
    WirelessTerminalService service,
    StorageManager storageManager,
    CustomItems customItems,
    RegionProtection regionProtection,
    BossBarManager bossBarManager,
    PlayerFeedback playerFeedback,
    Database database,
    SessionManager sessionManager,
    StorageKeys keys,
    int wireLimit,
    int wireHardCap,
    Material wireMaterial,
    Material storageCarrier) {
  public WirelessListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(keys, "keys");
  }
}
