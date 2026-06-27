package com.zxcmc.exort.block.listener;

import com.zxcmc.exort.breaking.BlockBreakHandler;
import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.BreakSoundPlayer;
import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.device.MonitorDisplayManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.display.wire.WireDisplayManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.placement.storage.StoragePlacementDependencies;
import com.zxcmc.exort.placement.storage.StoragePlacementFailureHandler;
import com.zxcmc.exort.placement.storage.StorageTierSaver;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wire.placement.WirePlacementLimitGuard;
import com.zxcmc.exort.wire.placement.WireWaterFlowRefresh;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockListener implements Listener {
  private final JavaPlugin plugin;
  private final StorageManager storageManager;
  private final StorageKeys keys;
  private final CustomItems customItems;
  private final Material wireMaterial;
  private final WirePlacementLimitGuard wirePlacementLimitGuard;
  private final ItemHologramManager hologramManager;
  private final WireDisplayManager wireDisplayManager;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Material relayCarrier;
  private final BlockBreakHandler breakHandler;
  private final StoragePlacementFailureHandler placementFailureHandler;
  private final RegionProtection regionProtection;
  private final Supplier<DisplayRefreshService> displayRefreshService;
  private final Supplier<MonitorDisplayManager> monitorDisplayManager;
  private final Supplier<BusService> busService;
  private final Supplier<NetworkGraphCache> networkGraphCache;
  private final StorageTierSaver storageTierSaver;
  private final Supplier<BreakSoundConfig> breakSoundConfig;
  private final Supplier<BusRuntimeConfig> busRuntimeConfig;

  public BlockListener(BlockListenerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.storageManager = dependencies.storageManager();
    this.keys = dependencies.keys();
    this.customItems = dependencies.customItems();
    this.wireMaterial = dependencies.wireMaterial();
    this.wirePlacementLimitGuard =
        new WirePlacementLimitGuard(
            plugin, wireMaterial, dependencies.wireHardCap(), dependencies.playerFeedback());
    this.hologramManager = dependencies.hologramManager();
    this.wireDisplayManager = dependencies.wireDisplayManager();
    this.storageCarrier = dependencies.storageCarrier();
    this.terminalCarrier = dependencies.terminalCarrier();
    this.monitorCarrier = dependencies.monitorCarrier();
    this.busCarrier = dependencies.busCarrier();
    this.relayCarrier = dependencies.relayCarrier();
    this.breakHandler = dependencies.breakHandler();
    this.regionProtection = dependencies.regionProtection();
    this.displayRefreshService = dependencies.displayRefreshService();
    this.monitorDisplayManager = dependencies.monitorDisplayManager();
    this.busService = dependencies.busService();
    this.networkGraphCache = dependencies.networkGraphCache();
    this.storageTierSaver = dependencies.storageTierSaver();
    this.breakSoundConfig = dependencies.breakSoundConfig();
    this.busRuntimeConfig = dependencies.busRuntimeConfig();
    this.placementFailureHandler =
        new StoragePlacementFailureHandler(
            new StoragePlacementDependencies(
                plugin,
                dependencies.playerFeedback(),
                storageCarrier,
                displayRefreshService,
                dependencies.hologramManagerSource(),
                dependencies.revalidateSessions(),
                this::invalidateNetwork));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onPlace(BlockPlaceEvent event) {
    Block block = event.getBlockPlaced();
    if (customItems.isMonitor(event.getItemInHand())
        && Carriers.matchesCarrier(block, monitorCarrier)) {
      if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
        event.setCancelled(true);
        return;
      }
      consumeIfInitialized(event);
      BlockFace monitorFace = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
      BlockData data = block.getBlockData();
      if (data instanceof Directional directional) {
        directional.setFacing(monitorFace);
        block.setBlockData(directional, false);
      }
      MonitorMarker.set(plugin, block, monitorFace);
      invalidateNetwork(block);
      var refresh = displayRefreshService.get();
      playPlaceSound(block, BreakType.MONITOR);
      var monitorDisplays = monitorDisplayManager.get();
      if (monitorDisplays != null) {
        monitorDisplays.registerMonitor(block);
      }
      if (refresh != null) {
        refresh.refreshBlockAndNeighbors(block);
        refresh.refreshNetworkFrom(block);
      }
      return;
    }

    if (Carriers.matchesCarrier(block, busCarrier)) {
      boolean isImport = customItems.isImportBus(event.getItemInHand());
      boolean isExport = customItems.isExportBus(event.getItemInHand());
      if (!isImport && !isExport) {
        // not a bus
      } else {
        if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
          event.setCancelled(true);
          return;
        }
        consumeIfInitialized(event);
        BlockFace face = null;
        Block against = event.getBlockAgainst();
        if (against != null) {
          BlockFace raw = against.getFace(block);
          if (raw != null) {
            boolean onWire =
                Carriers.matchesCarrier(against, wireMaterial)
                    && WireMarker.isWire(plugin, against);
            face = onWire ? raw : raw.getOppositeFace();
          }
        }
        if (face == null) {
          face = event.getPlayer().getFacing().getOppositeFace();
        }
        BusMarker.set(
            plugin,
            block,
            isExport ? BusType.EXPORT : BusType.IMPORT,
            face,
            defaultBusMode(isExport));
        invalidateNetwork(block);
        var refresh = displayRefreshService.get();
        if (refresh != null) {
          refresh.refreshBus(block);
          refresh.refreshBlockAndNeighbors(block);
        }
        var buses = busService.get();
        if (buses != null) {
          buses.getOrCreateState(
              BusPos.of(block), BusMarker.get(plugin, block).orElse(null), block);
        }
        playPlaceSound(block, BreakType.BUS);
        if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
          for (var dir :
              new BlockFace[] {
                BlockFace.UP,
                BlockFace.DOWN,
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST
              }) {
            Block neighbor = block.getRelative(dir);
            if (Carriers.matchesCarrier(neighbor, wireMaterial)
                && WireMarker.isWire(plugin, neighbor)) {
              wireDisplayManager.updateWireAndNeighbors(neighbor);
            }
          }
        }
        if (refresh != null) {
          refresh.refreshNetworkFrom(block);
        }
        return;
      }
    }

    if (Carriers.matchesCarrier(block, terminalCarrier)) {
      boolean isTerminal = customItems.isTerminal(event.getItemInHand());
      boolean isCrafting = customItems.isCraftingTerminal(event.getItemInHand());
      if (!isTerminal && !isCrafting) return;
      if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
        event.setCancelled(true);
        return;
      }
      consumeIfInitialized(event);
      BlockFace terminalFace = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
      // orient to player facing if directional
      BlockData data = block.getBlockData();
      if (data instanceof Directional directional) {
        directional.setFacing(terminalFace);
        block.setBlockData(directional, false);
      }
      TerminalMarker.set(
          plugin, block, isCrafting ? TerminalKind.CRAFTING : TerminalKind.TERMINAL, terminalFace);
      invalidateNetwork(block);
      var refresh = displayRefreshService.get();
      if (refresh != null) {
        refresh.refreshTerminal(block);
      }
      playPlaceSound(block, BreakType.TERMINAL);
      if (hologramManager != null) hologramManager.registerTerminal(block);
      if (hologramManager != null) hologramManager.invalidateAll();
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        for (var dir :
            new BlockFace[] {
              BlockFace.UP,
              BlockFace.DOWN,
              BlockFace.NORTH,
              BlockFace.SOUTH,
              BlockFace.EAST,
              BlockFace.WEST
            }) {
          Block neighbor = block.getRelative(dir);
          if (Carriers.matchesCarrier(neighbor, wireMaterial)
              && WireMarker.isWire(plugin, neighbor)) {
            wireDisplayManager.updateWireAndNeighbors(neighbor);
          }
        }
      }
      if (refresh != null) {
        refresh.refreshBlockAndNeighbors(block);
        refresh.refreshNetworkFrom(block);
      }
      return;
    }

    if (Carriers.matchesCarrier(block, wireMaterial)) {
      Material replacedType = event.getBlockReplacedState().getType();
      ItemStack inHand = event.getItemInHand();
      if (inHand == null || !inHand.hasItemMeta()) return;
      var itemPdc = inHand.getItemMeta().getPersistentDataContainer();
      String type = itemPdc.get(keys.type(), PersistentDataType.STRING);
      if (!"wire".equalsIgnoreCase(type)) return;
      if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
        event.setCancelled(true);
        return;
      }
      if (!wirePlacementLimitGuard.canPlace(event.getPlayer(), block)) {
        event.setCancelled(true);
        return;
      }
      Carriers.applyCarrier(block, wireMaterial);
      WireMarker.setWire(plugin, block);
      WireWaterFlowRefresh.refreshAfterWirePlacement(block, replacedType);
      consumeIfInitialized(event); // consumes initialized storage blocks; for wire mostly no-op
      if (wireDisplayManager != null) {
        wireDisplayManager.updateWireAndNeighbors(block);
      }
      var refresh = displayRefreshService.get();
      if (refresh != null) {
        refresh.refreshBlockAndNeighbors(block);
      }
      playPlaceSound(block, BreakType.WIRE);
      if (hologramManager != null) hologramManager.invalidateAll();
      invalidateNetwork(block);
      if (refresh != null) {
        refresh.refreshNetworkFrom(block);
      }
      return;
    }

    if (customItems.isRelay(event.getItemInHand())
        && Carriers.matchesCarrier(block, relayCarrier)) {
      if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
        event.setCancelled(true);
        return;
      }
      RelayMarker.set(plugin, block);
      consumeIfInitialized(event);
      invalidateNetwork(block);
      var refresh = displayRefreshService.get();
      if (refresh != null) {
        refresh.refreshRelay(block);
        refresh.refreshBlockAndNeighbors(block);
        refresh.refreshNetworkFrom(block);
      }
      playPlaceSound(block, BreakType.RELAY);
      if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
        for (var dir :
            new BlockFace[] {
              BlockFace.UP,
              BlockFace.DOWN,
              BlockFace.NORTH,
              BlockFace.SOUTH,
              BlockFace.EAST,
              BlockFace.WEST
            }) {
          Block neighbor = block.getRelative(dir);
          if (Carriers.matchesCarrier(neighbor, wireMaterial)
              && WireMarker.isWire(plugin, neighbor)) {
            wireDisplayManager.updateWireAndNeighbors(neighbor);
          }
        }
      }
      return;
    }

    Optional<StorageTier> tierOpt = customItems.tierFromItem(event.getItemInHand());
    if (tierOpt.isEmpty()) {
      if (customItems.isStorageCore(event.getItemInHand())) {
        if (!Carriers.matchesCarrier(block, storageCarrier)) return;
        if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
          event.setCancelled(true);
          return;
        }
        consumeIfInitialized(event);
        StorageCoreMarker.set(plugin, block);
        var refresh = displayRefreshService.get();
        if (refresh != null) {
          refresh.refreshStorage(block);
          refresh.refreshBlockAndNeighbors(block);
        }
        playPlaceSound(block, BreakType.STORAGE);
      }
      return;
    }
    StorageTier tier = tierOpt.get();
    if (!Carriers.matchesCarrier(block, storageCarrier)) return;
    if (!regionProtection.canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
      event.setCancelled(true);
      return;
    }

    String storageId =
        customItems.storageId(event.getItemInHand()).orElse(UUID.randomUUID().toString());
    String displayName = customItems.storageDisplayName(event.getItemInHand()).orElse(null);
    ItemStack placedItem = event.getItemInHand().clone();
    placedItem.setAmount(1);
    boolean refundOnPersistFailure = shouldRefundPlacementItem(event.getPlayer(), placedItem);
    consumeIfInitialized(event);
    // Apply facing for vault like vanilla placement
    BlockData data = block.getBlockData();
    if (data instanceof Directional directional) {
      BlockFace storageFace = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
      directional.setFacing(storageFace);
      block.setBlockData(directional, false);
    }
    BlockFace storageFace = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
    StorageMarker.set(plugin, block, storageId, tier, storageFace, displayName);
    preloadStorage(event.getPlayer(), storageId);
    persistStorageTier(
        event.getPlayer(),
        block,
        storageId,
        tier.key(),
        tier.maxItems(),
        displayName,
        placedItem,
        refundOnPersistFailure);
    var refresh = displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshStorage(block);
    }
    playPlaceSound(block, BreakType.STORAGE);
    if (hologramManager != null) hologramManager.registerStorage(block);
    if (hologramManager != null) hologramManager.invalidateAll();
    if (wireDisplayManager != null && wireDisplayManager.isEnabled()) {
      for (var dir :
          new BlockFace[] {
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
          }) {
        Block neighbor = block.getRelative(dir);
        if (Carriers.matchesCarrier(neighbor, wireMaterial)
            && WireMarker.isWire(plugin, neighbor)) {
          wireDisplayManager.updateWireAndNeighbors(neighbor);
        }
      }
    }
    if (refresh != null) {
      refresh.refreshBlockAndNeighbors(block);
    }
    invalidateNetwork(block);
    if (refresh != null) {
      refresh.refreshNetworkFrom(block);
    }
  }

  private void invalidateNetwork(Block block) {
    var cache = networkGraphCache.get();
    if (cache != null) {
      cache.invalidateAround(block);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    BlockBreakHandler.BreakResult result = breakHandler.handleBreak(event.getPlayer(), block, true);
    switch (result) {
      case BROKEN -> event.setDropItems(false);
      case DENIED -> {
        event.setCancelled(true);
        event.setDropItems(false);
      }
      case IGNORED -> {
        // Let vanilla and other plugins handle non-Exort blocks.
      }
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onPistonExtend(BlockPistonExtendEvent event) {
    if (containsPluginBlock(event.getBlocks())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onPistonRetract(BlockPistonRetractEvent event) {
    if (containsPluginBlock(event.getBlocks())) {
      event.setCancelled(true);
    }
  }

  private boolean containsPluginBlock(List<Block> blocks) {
    for (Block b : blocks) {
      if (TerminalMarker.isTerminal(plugin, b)) {
        return true;
      }
      if (StorageMarker.get(plugin, b).isPresent()) {
        return true;
      }
      if (StorageCoreMarker.isCore(plugin, b)) {
        return true;
      }
      if (WireMarker.isWire(plugin, b)) {
        return true;
      }
      if (MonitorMarker.isMonitor(plugin, b)) {
        return true;
      }
      if (BusMarker.isBus(plugin, b)) {
        return true;
      }
      if (RelayMarker.isRelay(plugin, b)) {
        return true;
      }
    }
    return false;
  }

  private void consumeIfInitialized(BlockPlaceEvent event) {
    ItemStack inHand = event.getItemInHand();
    if (inHand == null || !inHand.hasItemMeta()) return;
    if (event.getPlayer().getGameMode() != GameMode.CREATIVE) return;
    var pdc = inHand.getItemMeta().getPersistentDataContainer();
    if (pdc.has(keys.storageId(), PersistentDataType.STRING)) {
      inHand.setAmount(inHand.getAmount() - 1);
      event.getPlayer().getInventory().setItem(event.getHand(), inHand);
    }
  }

  private BlockFace horizontalFacing(BlockFace face) {
    return switch (face) {
      case NORTH, SOUTH, EAST, WEST -> face;
      case NORTH_NORTH_EAST, EAST_NORTH_EAST, NORTH_EAST -> BlockFace.NORTH;
      case SOUTH_SOUTH_EAST, SOUTH_EAST, EAST_SOUTH_EAST -> BlockFace.SOUTH;
      case SOUTH_SOUTH_WEST, SOUTH_WEST, WEST_SOUTH_WEST -> BlockFace.SOUTH;
      case NORTH_NORTH_WEST, NORTH_WEST, WEST_NORTH_WEST -> BlockFace.NORTH;
      case UP, DOWN -> BlockFace.SOUTH;
      default -> BlockFace.SOUTH;
    };
  }

  private void playPlaceSound(Block block, BreakType type) {
    var soundConfig = breakSoundConfig.get();
    if (soundConfig == null || !soundConfig.enabled()) return;
    BreakSoundPlayer.play(
        block.getWorld(),
        block.getLocation().add(0.5, 0.5, 0.5),
        soundConfig.placeKey(type),
        soundConfig.range(),
        soundConfig.volume(),
        soundConfig.pitch());
  }

  private void preloadStorage(Player player, String storageId) {
    storageManager
        .getOrLoad(storageId)
        .whenComplete(
            (cache, err) -> {
              if (err != null) {
                placementFailureHandler.reportStorageFailure(
                    player, "load placed storage " + storageId, err);
              }
            });
  }

  private void persistStorageTier(
      Player player,
      Block block,
      String storageId,
      String tierKey,
      long tierMaxItems,
      String displayName,
      ItemStack refund,
      boolean shouldRefund) {
    storageTierSaver
        .save(storageId, tierKey, tierMaxItems, displayName)
        .whenComplete(
            (ignored, err) -> {
              if (err != null) {
                placementFailureHandler.rollbackFailedPlacement(
                    player, block, storageId, refund, shouldRefund, err);
              } else {
                storageManager.setCachedDisplayName(storageId, displayName);
              }
            });
  }

  private boolean shouldRefundPlacementItem(Player player, ItemStack item) {
    if (item == null || item.getType() == Material.AIR) return false;
    if (player == null || player.getGameMode() != GameMode.CREATIVE) return true;
    if (!item.hasItemMeta()) return false;
    return item.getItemMeta()
        .getPersistentDataContainer()
        .has(keys.storageId(), PersistentDataType.STRING);
  }

  private BusMode defaultBusMode(boolean exportBus) {
    return busRuntimeConfig.get().defaultMode(exportBus);
  }
}
