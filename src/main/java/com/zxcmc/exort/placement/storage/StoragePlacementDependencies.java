package com.zxcmc.exort.placement.storage;

import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.feedback.PlayerFeedback;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public record StoragePlacementDependencies(
    JavaPlugin plugin,
    PlayerFeedback playerFeedback,
    Material storageCarrier,
    Supplier<DisplayRefreshService> displayRefreshService,
    Supplier<ItemHologramManager> hologramManager,
    Runnable revalidateSessions,
    Consumer<Block> invalidateNetwork) {
  public StoragePlacementDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(hologramManager, "hologramManager");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(invalidateNetwork, "invalidateNetwork");
  }
}
