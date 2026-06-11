package com.zxcmc.exort.runtime;

import com.zxcmc.exort.block.listener.BlockListener;
import com.zxcmc.exort.block.listener.BlockListenerDependencies;
import com.zxcmc.exort.block.listener.ItemPlaceBridgeDependencies;
import com.zxcmc.exort.block.listener.ItemPlaceBridgeListener;
import com.zxcmc.exort.bus.listener.BusListener;
import com.zxcmc.exort.gui.listener.InventoryEvents;
import com.zxcmc.exort.gui.listener.SearchDialogListener;
import com.zxcmc.exort.gui.listener.TerminalListener;
import com.zxcmc.exort.items.listener.InventoryRefreshListener;
import com.zxcmc.exort.items.listener.PickListener;
import com.zxcmc.exort.monitor.listener.MonitorListener;
import com.zxcmc.exort.monitor.listener.MonitorListenerDependencies;
import com.zxcmc.exort.placement.ExortBlockTargetResolver;
import com.zxcmc.exort.placement.FailoverPlacementGuardBackend;
import com.zxcmc.exort.placement.PacketPlacementGuardBackend;
import com.zxcmc.exort.placement.PaperEntityPlacementGuardBackend;
import com.zxcmc.exort.placement.PlacementGuardBackend;
import com.zxcmc.exort.placement.PlacementGuardConfig;
import com.zxcmc.exort.placement.RightClickPlacementGuard;
import com.zxcmc.exort.recipes.CraftingRules;
import com.zxcmc.exort.recipes.RecipeService;
import com.zxcmc.exort.recipes.listener.CraftBlockerListener;
import com.zxcmc.exort.storage.listener.StorageListener;
import com.zxcmc.exort.wire.listener.WireListener;
import com.zxcmc.exort.wire.listener.WireListenerDependencies;
import com.zxcmc.exort.wireless.listener.WirelessCraftListener;
import com.zxcmc.exort.wireless.listener.WirelessListener;
import com.zxcmc.exort.wireless.listener.WirelessListenerDependencies;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public final class RuntimeListenerRegistrar {
  private RuntimeListenerRegistrar() {}

  public static RuntimeListenerRegistration register(
      RuntimeListenerDependencies deps, PlacementGuardConfig placementConfig) {
    deps.customBlockBreaker().start();
    register(deps, deps.customBlockBreaker());
    registerBlockListeners(deps);
    registerGuiListeners(deps);
    registerStorageAndWireListeners(deps);
    registerPickListener(deps);
    registerItemPlaceBridge(deps);
    registerMonitorListener(deps);
    RightClickPlacementGuard placementGuard = registerPlacementGuard(deps, placementConfig);
    registerInventoryRefreshListener(deps);
    RecipeRegistration recipes = registerRecipes(deps);
    registerWirelessListeners(deps);
    return new RuntimeListenerRegistration(
        placementGuard, recipes.craftingRules(), recipes.recipeService());
  }

  private static void registerBlockListeners(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    register(
        deps,
        new BlockListener(
            new BlockListenerDependencies(
                deps.plugin(),
                deps.storageManager(),
                deps.keys(),
                deps.customItems(),
                materials.wire(),
                deps.wireHardCap(),
                deps.hologramManager(),
                deps.hologramManagerSource(),
                deps.wireDisplayManager(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier(),
                deps.breakHandler(),
                deps.regionProtection(),
                deps.playerFeedback(),
                deps.displayRefreshServiceSource(),
                deps.monitorDisplayManagerSource(),
                deps.busServiceSource(),
                deps.networkGraphCacheSource(),
                deps.revalidateSessions(),
                deps.database()::setStorageTier,
                () -> deps.breakSoundConfig(),
                () -> deps.busRuntimeConfig())));
  }

  private static void registerGuiListeners(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    register(
        deps,
        new TerminalListener(
            deps.plugin(),
            deps.regionProtection(),
            deps.worldEditWandGuard(),
            deps.playerFeedback(),
            block -> {
              if (deps.terminalDisplayManager() != null) {
                deps.terminalDisplayManager().refresh(block);
              }
            },
            deps.database()::setStorageTier,
            deps.storageManager(),
            deps.sessionManager(),
            deps.keys(),
            deps.wireLimit(),
            deps.wireHardCap(),
            materials.wire(),
            materials.storageCarrier(),
            materials.terminalCarrier()));
    register(
        deps,
        new BusListener(
            deps.plugin(),
            deps.regionProtection(),
            deps.worldEditWandGuard(),
            block -> {
              if (deps.busDisplayManager() != null) {
                deps.busDisplayManager().refresh(block);
              }
            },
            deps.busSessionManager(),
            materials.busCarrier()));
    register(
        deps,
        new InventoryEvents(
            deps.sessionManager(), deps.busSessionManager(), deps.authenticationGate()));
    register(
        deps,
        new SearchDialogListener(deps.sessionManager(), deps.searchDialogService(), deps.plugin()));
  }

  private static void registerStorageAndWireListeners(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    register(
        deps,
        new StorageListener(
            deps.plugin(),
            deps.regionProtection(),
            deps.worldEditWandGuard(),
            deps.bossBarManager(),
            deps.storagePeekTicks(),
            materials.storageCarrier()));
    register(
        deps,
        new WireListener(
            new WireListenerDependencies(
                deps.plugin(),
                deps.regionProtection(),
                deps.worldEditWandGuard(),
                deps.bossBarManager(),
                deps.keys(),
                deps.wireLimit(),
                deps.wireHardCap(),
                materials.wire(),
                deps.wirePeekTicks(),
                materials.storageCarrier())));
  }

  private static void registerPickListener(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    var pickListener =
        new PickListener(
            deps.plugin(),
            deps.customItems(),
            deps.keys(),
            deps.pickDebugSink(),
            materials.wire(),
            materials.storageCarrier(),
            materials.terminalCarrier(),
            materials.monitorCarrier(),
            materials.busCarrier());
    register(deps, pickListener);
    if (deps.packetEnhancements() != null) {
      deps.packetEnhancements().registerPickBridge(pickListener);
    }
  }

  private static void registerItemPlaceBridge(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    register(
        deps,
        new ItemPlaceBridgeListener(
            new ItemPlaceBridgeDependencies(
                deps.plugin(),
                deps.storageManager(),
                deps.customItems(),
                deps.keys(),
                materials.wire(),
                deps.wireHardCap(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier(),
                deps.regionProtection(),
                deps.playerFeedback(),
                deps.displayRefreshServiceSource(),
                deps.hologramManagerSource(),
                deps.monitorDisplayManagerSource(),
                deps.busServiceSource(),
                deps.networkGraphCacheSource(),
                deps.revalidateSessions(),
                deps.monitorPlacedRecorder(),
                deps.database()::setStorageTier,
                () -> deps.breakSoundConfig(),
                () -> deps.busRuntimeConfig())));
  }

  private static void registerMonitorListener(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    register(
        deps,
        new MonitorListener(
            new MonitorListenerDependencies(
                deps.plugin(),
                deps.regionProtection(),
                deps.keys(),
                deps.bossBarManager(),
                deps.itemNameService(),
                deps.authenticationGate(),
                deps.worldEditWandGuard(),
                materials.monitorCarrier(),
                materials.wire(),
                materials.storageCarrier(),
                deps::wireLimit,
                deps::wireHardCap,
                deps::storagePeekTicks,
                deps.monitorRecentlyPlaced(),
                block -> {
                  if (deps.monitorDisplayManager() != null) {
                    deps.monitorDisplayManager().refresh(block);
                  }
                })));
  }

  private static RightClickPlacementGuard registerPlacementGuard(
      RuntimeListenerDependencies deps, PlacementGuardConfig placementConfig) {
    if (!placementConfig.enabled()) {
      if (deps.packetEnhancements() != null) {
        deps.packetEnhancements().markPlacementGuardDisabledByConfig();
      }
      return null;
    }

    RuntimeMaterials materials = deps.materials();
    PlacementGuardBackend placementGuardBackend =
        createPlacementGuardBackend(deps, placementConfig);
    RightClickPlacementGuard placementGuard =
        new RightClickPlacementGuard(
            deps.plugin(),
            deps.customItems(),
            deps.customBlockBreaker(),
            new ExortBlockTargetResolver(
                deps.plugin(),
                materials.wire(),
                materials.storageCarrier(),
                materials.terminalCarrier(),
                materials.monitorCarrier(),
                materials.busCarrier()),
            placementGuardBackend,
            placementConfig.pollIntervalTicks(),
            placementConfig.targetRangeBlocks(),
            placementConfig.guardScale(),
            placementConfig.cornerInset());
    register(deps, placementGuard);
    placementGuard.start();
    return placementGuard;
  }

  private static PlacementGuardBackend createPlacementGuardBackend(
      RuntimeListenerDependencies deps, PlacementGuardConfig config) {
    PaperEntityPlacementGuardBackend paperBackend =
        new PaperEntityPlacementGuardBackend(deps.plugin(), config.guardScale());
    if (!config.packetEventsGuardEnabled()) {
      if (deps.packetEnhancements() != null) {
        deps.packetEnhancements().markPlacementGuardDisabledByConfig();
      }
      return paperBackend;
    }
    if (deps.packetEnhancements() != null) {
      var packets = deps.packetEnhancements().tryCreatePlacementGuardPackets(config.guardScale());
      if (packets != null) {
        final FailoverPlacementGuardBackend[] holder = new FailoverPlacementGuardBackend[1];
        PacketPlacementGuardBackend packetBackend =
            new PacketPlacementGuardBackend(
                packets,
                reason -> {
                  deps.packetEnhancements().markPlacementGuardRuntimeFallback(reason);
                  holder[0].switchToPaperFallback(reason);
                });
        FailoverPlacementGuardBackend failoverBackend =
            new FailoverPlacementGuardBackend(packetBackend, paperBackend);
        holder[0] = failoverBackend;
        return failoverBackend;
      }
    }
    return paperBackend;
  }

  private static void registerInventoryRefreshListener(RuntimeListenerDependencies deps) {
    register(
        deps,
        new InventoryRefreshListener(
            deps.plugin(),
            deps.inventoryRefreshService()::epoch,
            deps.inventoryRefreshService()::refreshPlayerInventory,
            deps.inventoryRefreshService()::refreshContainerInventory));
  }

  private static RecipeRegistration registerRecipes(RuntimeListenerDependencies deps) {
    RecipeService previousRecipeService = deps.previousRecipeService();
    if (previousRecipeService != null) {
      previousRecipeService.unregisterAll();
    }
    CraftingRules craftingRules =
        new CraftingRules(
            deps.keys(),
            deps.craftingConfig().blockVanilla(),
            deps.craftingConfig().allowExternal());
    register(deps, new CraftBlockerListener(craftingRules));
    RecipeService recipeService =
        new RecipeService(deps.plugin(), deps.customItems(), deps.wirelessService());
    recipeService.reload();
    return new RecipeRegistration(craftingRules, recipeService);
  }

  private static void registerWirelessListeners(RuntimeListenerDependencies deps) {
    RuntimeMaterials materials = deps.materials();
    register(
        deps,
        new WirelessListener(
            new WirelessListenerDependencies(
                deps.plugin(),
                deps.wirelessService(),
                deps.storageManager(),
                deps.customItems(),
                deps.regionProtection(),
                deps.authenticationGate(),
                deps.bossBarManager(),
                deps.playerFeedback(),
                deps.database(),
                deps.sessionManager(),
                deps.keys(),
                deps.wireLimit(),
                deps.wireHardCap(),
                materials.wire(),
                materials.storageCarrier())));
    register(deps, new WirelessCraftListener(deps.wirelessService()));
  }

  private static void register(RuntimeListenerDependencies deps, Listener listener) {
    Bukkit.getPluginManager().registerEvents(listener, deps.plugin());
  }

  private record RecipeRegistration(CraftingRules craftingRules, RecipeService recipeService) {}
}
