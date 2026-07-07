package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.wireless.transmitter.WirelessTransmitterService;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record WirelessListenerDependencies(
    JavaPlugin plugin,
    WirelessTerminalService service,
    WirelessTransmitterService transmitterService,
    StorageManager storageManager,
    CustomItems customItems,
    RegionProtection regionProtection,
    AuthenticationGate authenticationGate,
    PlayerFeedback playerFeedback,
    SessionManager sessionManager,
    Material storageCarrier,
    Material transmitterCarrier) {
  public WirelessListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(transmitterService, "transmitterService");
    Objects.requireNonNull(storageManager, "storageManager");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(authenticationGate, "authenticationGate");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(sessionManager, "sessionManager");
  }
}
