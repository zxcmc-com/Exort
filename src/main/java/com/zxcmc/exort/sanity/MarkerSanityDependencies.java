package com.zxcmc.exort.sanity;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.infra.db.Database;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public record MarkerSanityDependencies(
    Plugin plugin,
    DisplayRefreshService displayRefreshService,
    Supplier<ItemHologramManager> hologramManager,
    Supplier<BusService> busService,
    Database database,
    Material wireCarrier,
    Material storageCarrier,
    Material terminalCarrier,
    Material monitorCarrier,
    Material busCarrier,
    Material relayCarrier) {
  public MarkerSanityDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(hologramManager, "hologramManager");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(wireCarrier, "wireCarrier");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(terminalCarrier, "terminalCarrier");
    Objects.requireNonNull(monitorCarrier, "monitorCarrier");
    Objects.requireNonNull(busCarrier, "busCarrier");
    Objects.requireNonNull(relayCarrier, "relayCarrier");
  }
}
