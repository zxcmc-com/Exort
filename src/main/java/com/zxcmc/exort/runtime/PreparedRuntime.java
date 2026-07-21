package com.zxcmc.exort.runtime;

import com.zxcmc.exort.breaking.BreakConfig;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.chunkloader.ChunkLoaderConfig;
import com.zxcmc.exort.gui.GuiRuntimeConfig;
import com.zxcmc.exort.integration.worldedit.WorldEditBulkConfig;
import com.zxcmc.exort.placement.PlacementGuardConfig;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.storage.StorageRuntimeConfig;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.wireless.WirelessRuntimeConfig;
import java.util.Objects;

/** Immutable preflight output consumed by exactly one runtime activation attempt. */
public record PreparedRuntime(
    RuntimeItemModelConfig itemModels,
    RuntimeNetworkConfig network,
    WirelessRuntimeConfig wireless,
    BusRuntimeConfig bus,
    StorageRuntimeConfig storage,
    GuiRuntimeConfig gui,
    ChunkLoaderConfig chunkLoader,
    BreakConfig breaking,
    PlacementGuardConfig placementGuard,
    WorldEditBulkConfig worldEdit,
    StorageTierCatalog storageTierCatalog,
    RecipeService.Activation recipeActivation) {
  public PreparedRuntime {
    Objects.requireNonNull(itemModels, "itemModels");
    Objects.requireNonNull(network, "network");
    Objects.requireNonNull(wireless, "wireless");
    Objects.requireNonNull(bus, "bus");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(gui, "gui");
    Objects.requireNonNull(chunkLoader, "chunkLoader");
    Objects.requireNonNull(breaking, "breaking");
    Objects.requireNonNull(placementGuard, "placementGuard");
    Objects.requireNonNull(worldEdit, "worldEdit");
    Objects.requireNonNull(storageTierCatalog, "storageTierCatalog");
    Objects.requireNonNull(recipeActivation, "recipeActivation");
  }

  public PreparedRuntime withRecipeActivation(RecipeService.Activation recipes) {
    return new PreparedRuntime(
        itemModels,
        network,
        wireless,
        bus,
        storage,
        gui,
        chunkLoader,
        breaking,
        placementGuard,
        worldEdit,
        storageTierCatalog,
        recipes);
  }
}
