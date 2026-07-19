package com.zxcmc.exort.placement.bridge;

import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.network.NetworkGraphCache;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.block.Block;

/** Placement side effects that refresh runtime views after a successful mutation. */
public record PlacementRefreshContext(
    Supplier<DisplayRefreshService> displayRefreshService,
    Supplier<ItemHologramManager> hologramManager,
    Supplier<MonitorDisplayManager> monitorDisplayManager,
    Supplier<BusService> busService,
    Supplier<NetworkGraphCache> networkGraphCache,
    Runnable revalidateSessions,
    Consumer<Block> monitorPlacedRecorder,
    Consumer<Block> transmitterPlacedRecorder) {
  public PlacementRefreshContext {
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(hologramManager, "hologramManager");
    Objects.requireNonNull(monitorDisplayManager, "monitorDisplayManager");
    Objects.requireNonNull(busService, "busService");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(monitorPlacedRecorder, "monitorPlacedRecorder");
    Objects.requireNonNull(transmitterPlacedRecorder, "transmitterPlacedRecorder");
  }
}
