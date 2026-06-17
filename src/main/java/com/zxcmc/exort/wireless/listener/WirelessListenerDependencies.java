package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
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
    AuthenticationGate authenticationGate,
    BossBarManager bossBarManager,
    PlayerFeedback playerFeedback,
    SessionManager sessionManager,
    StorageKeys keys,
    int wireLimit,
    int wireHardCap,
    int relayRangeChunks,
    Material wireMaterial,
    Material storageCarrier,
    Material relayCarrier) {
  public WirelessListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(authenticationGate, "authenticationGate");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(sessionManager, "sessionManager");
    Objects.requireNonNull(keys, "keys");
  }
}
