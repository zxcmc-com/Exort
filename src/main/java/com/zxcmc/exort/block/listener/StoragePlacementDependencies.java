package com.zxcmc.exort.block.listener;

import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

record StoragePlacementDependencies(
    JavaPlugin plugin,
    PlayerFeedback playerFeedback,
    Material storageCarrier,
    Supplier<DisplayRefreshService> displayRefreshService,
    Supplier<ItemHologramManager> hologramManager,
    Runnable revalidateSessions,
    Runnable invalidateNetwork) {
  StoragePlacementDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    Objects.requireNonNull(hologramManager, "hologramManager");
    Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    Objects.requireNonNull(invalidateNetwork, "invalidateNetwork");
  }
}
