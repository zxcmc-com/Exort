package com.zxcmc.exort.block.listener;

import com.zxcmc.exort.breaking.BreakSoundConfig;
import com.zxcmc.exort.breaking.BreakSoundPlayer;
import com.zxcmc.exort.breaking.BreakType;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.MonitorDisplayManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.platform.BlockInteractUtil;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wire.WirePlacementLimitGuard;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Allows placement of plugin items even if their material is not placeable in the current mode
 * (e.g. switching RESOURCE -> VANILLA leaves PAPER items). Places the correct block for the active
 * mode.
 */
public class ItemPlaceBridgeListener implements Listener {
  private final JavaPlugin plugin;
  private final StorageManager storageManager;
  private final CustomItems customItems;
  private final StorageKeys keys;
  private final Material wireMaterial;
  private final WirePlacementLimitGuard wirePlacementLimitGuard;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final StoragePlacementFailureHandler placementFailureHandler;
  private final RegionProtection regionProtection;
  private final Supplier<DisplayRefreshService> displayRefreshService;
  private final Supplier<ItemHologramManager> hologramManager;
  private final Supplier<MonitorDisplayManager> monitorDisplayManager;
  private final Supplier<BusService> busService;
  private final Supplier<NetworkGraphCache> networkGraphCache;
  private final Runnable revalidateSessions;
  private final Consumer<Block> monitorPlacedRecorder;
  private final BiFunction<String, String, CompletableFuture<Void>> storageTierSaver;
  private final Supplier<BreakSoundConfig> breakSoundConfig;
  private final Supplier<BusRuntimeConfig> busRuntimeConfig;

  public ItemPlaceBridgeListener(ItemPlaceBridgeDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.storageManager = dependencies.storageManager();
    this.customItems = dependencies.customItems();
    this.keys = dependencies.keys();
    this.wireMaterial = dependencies.wireMaterial();
    this.wirePlacementLimitGuard =
        new WirePlacementLimitGuard(
            plugin, wireMaterial, dependencies.wireHardCap(), dependencies.playerFeedback());
    this.storageCarrier = dependencies.storageCarrier();
    this.terminalCarrier = dependencies.terminalCarrier();
    this.monitorCarrier = dependencies.monitorCarrier();
    this.busCarrier = dependencies.busCarrier();
    this.regionProtection = dependencies.regionProtection();
    this.displayRefreshService = dependencies.displayRefreshService();
    this.hologramManager = dependencies.hologramManager();
    this.monitorDisplayManager = dependencies.monitorDisplayManager();
    this.busService = dependencies.busService();
    this.networkGraphCache = dependencies.networkGraphCache();
    this.revalidateSessions = dependencies.revalidateSessions();
    this.monitorPlacedRecorder = dependencies.monitorPlacedRecorder();
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
                hologramManager,
                revalidateSessions,
                this::invalidateNetwork));
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() == null) return;
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() == EquipmentSlot.OFF_HAND && !shouldUseOffhand(event)) return;
    ItemStack stack = event.getItem();
    if (stack == null || !stack.hasItemMeta()) return;
    Block clicked = event.getClickedBlock();
    if (clicked == null) return;
    if (!event.getPlayer().isSneaking()) {
      if (isTerminal(clicked) || isMonitor(clicked) || isBus(clicked)) {
        return;
      }
    }
    boolean isBusItem = customItems.isImportBus(stack) || customItems.isExportBus(stack);
    boolean isWirelessItem = customItems.isWirelessTerminal(stack);
    if (BlockInteractUtil.isInteractable(clicked)) {
      if (isWirelessItem) {
        return;
      }
      if (!event.getPlayer().isSneaking()) {
        if (!isBusItem || !isInventoryBlock(clicked)) {
          return;
        }
      }
    }
    Block target = resolveTarget(clicked, event.getBlockFace());
    if (target == null || !isReplaceable(target)) return;
    if (!hasPlacementSpace(target)) return;

    // Wire
    if (isWire(stack)) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), wireMaterial)) return;
      if (!wirePlacementLimitGuard.canPlace(event.getPlayer(), target)) return;
      Material replacedType = target.getType();
      placeWire(target);
      WireWaterFlowRefresh.refreshAfterWirePlacement(target, replacedType);
      finishPlacement(event, target, BreakType.WIRE);
      refreshWirePlacement(target);
      return;
    }

    // Storage
    Optional<StorageTier> tierOpt = customItems.tierFromItem(stack);
    if (tierOpt.isPresent()) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), storageCarrier))
        return;
      ItemStack placedItem = stack.clone();
      placedItem.setAmount(1);
      StorageTier tier = tierOpt.get();
      String storageId = customItems.storageId(stack).orElse(UUID.randomUUID().toString());
      boolean shouldRefund = shouldRefundPlacementItem(event.getPlayer(), placedItem);
      placeStorage(event, target, tier, storageId);
      finishPlacement(event, target, BreakType.STORAGE);
      persistStorageTier(
          event.getPlayer(), target, storageId, tier.key(), placedItem, shouldRefund);
      preloadStorage(event.getPlayer(), storageId);
      refreshStoragePlacement(target);
      return;
    }
    if (customItems.isStorageCore(stack)) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), storageCarrier))
        return;
      placeStorageCore(target);
      finishPlacement(event, target, BreakType.STORAGE);
      refreshStorageCorePlacement(target);
      return;
    }

    // Terminal
    if (customItems.isTerminal(stack)) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), terminalCarrier))
        return;
      placeTerminal(event, target, TerminalKind.TERMINAL);
      finishPlacement(event, target, BreakType.TERMINAL);
      refreshTerminalPlacement(target);
      return;
    }

    // Crafting terminal
    if (customItems.isCraftingTerminal(stack)) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), terminalCarrier))
        return;
      placeTerminal(event, target, TerminalKind.CRAFTING);
      finishPlacement(event, target, BreakType.TERMINAL);
      refreshTerminalPlacement(target);
      return;
    }

    // Monitor
    if (customItems.isMonitor(stack)) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), monitorCarrier))
        return;
      placeMonitor(event, target);
      finishPlacement(event, target, BreakType.MONITOR);
      monitorPlacedRecorder.accept(target);
      refreshMonitorPlacement(target);
      return;
    }

    // Import/Export bus
    if (customItems.isImportBus(stack) || customItems.isExportBus(stack)) {
      event.setCancelled(true);
      if (!regionProtection.canBuild(event.getPlayer(), target.getLocation(), busCarrier)) return;
      placeBus(event, target, customItems.isExportBus(stack));
      finishPlacement(event, target, BreakType.BUS);
      refreshBusPlacement(target);
    }
  }

  private void placeWire(Block target) {
    Carriers.applyCarrier(target, wireMaterial);
    WireMarker.setWire(plugin, target);
  }

  private void refreshWirePlacement(Block target) {
    var refresh = displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshWireAndNeighbors(target);
      refresh.refreshBlockAndNeighbors(target);
    }
    var holograms = hologramManager.get();
    if (holograms != null) {
      holograms.invalidateAll();
    }
    revalidateSessions.run();
    invalidateNetwork(target);
    if (refresh != null) {
      refresh.refreshNetworkFrom(target);
    }
  }

  private void placeStorage(
      PlayerInteractEvent event, Block target, StorageTier tier, String storageId) {
    Carriers.applyCarrier(target, storageCarrier);
    BlockFace face = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
    StorageMarker.set(plugin, target, storageId, tier, face);
  }

  private void refreshStoragePlacement(Block target) {
    var refresh = displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshStorage(target);
    }
    var holograms = hologramManager.get();
    if (holograms != null) {
      holograms.registerStorage(target);
      holograms.invalidateAll();
    }
    if (refresh != null) {
      refresh.refreshBlockAndNeighbors(target);
    }
    invalidateNetwork(target);
    if (refresh != null) {
      refresh.refreshNetworkFrom(target);
    }
  }

  private void placeStorageCore(Block target) {
    Carriers.applyCarrier(target, storageCarrier);
    StorageCoreMarker.set(plugin, target);
  }

  private void refreshStorageCorePlacement(Block target) {
    var refresh = displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshStorage(target);
      refresh.refreshBlockAndNeighbors(target);
    }
  }

  private void placeTerminal(PlayerInteractEvent event, Block target, TerminalKind kind) {
    BlockFace face = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
    Carriers.applyCarrier(target, terminalCarrier);
    TerminalMarker.set(plugin, target, kind, face);
  }

  private void refreshTerminalPlacement(Block target) {
    invalidateNetwork(target);
    var refresh = displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshTerminal(target);
    }
    var holograms = hologramManager.get();
    if (holograms != null) {
      holograms.registerTerminal(target);
      holograms.invalidateAll();
    }
    if (refresh != null) {
      refresh.refreshBlockAndNeighbors(target);
      refresh.refreshNetworkFrom(target);
    }
  }

  private void placeMonitor(PlayerInteractEvent event, Block target) {
    BlockFace face = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
    Carriers.applyCarrier(target, monitorCarrier);
    MonitorMarker.set(plugin, target, face);
  }

  private void refreshMonitorPlacement(Block target) {
    invalidateNetwork(target);
    var refresh = displayRefreshService.get();
    var monitorDisplays = monitorDisplayManager.get();
    if (monitorDisplays != null) {
      monitorDisplays.registerMonitor(target);
    }
    if (refresh != null) {
      refresh.refreshBlockAndNeighbors(target);
      refresh.refreshNetworkFrom(target);
    }
  }

  private void placeBus(PlayerInteractEvent event, Block target, boolean exportBus) {
    Block clicked = event.getClickedBlock();
    BlockFace face = resolveBusFacing(event, clicked);
    Carriers.applyCarrier(target, busCarrier);
    BusMarker.set(
        plugin,
        target,
        exportBus ? BusType.EXPORT : BusType.IMPORT,
        face,
        defaultBusMode(exportBus));
  }

  private void refreshBusPlacement(Block target) {
    invalidateNetwork(target);
    var refresh = displayRefreshService.get();
    if (refresh != null) {
      refresh.refreshBus(target);
      refresh.refreshBlockAndNeighbors(target);
    }
    var buses = busService.get();
    if (buses != null) {
      buses.getOrCreateState(BusPos.of(target), BusMarker.get(plugin, target).orElse(null), target);
    }
    if (refresh != null) {
      refresh.refreshNetworkFrom(target);
    }
  }

  private void invalidateNetwork(Block block) {
    var cache = networkGraphCache.get();
    if (cache != null) {
      cache.invalidateAround(block);
    }
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

  private void finishPlacement(PlayerInteractEvent event, Block target, BreakType type) {
    consume(event);
    event.getPlayer().swingHand(event.getHand());
    playPlaceSound(target, type);
  }

  private boolean isWire(ItemStack stack) {
    if (!stack.hasItemMeta()) return false;
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    String type = pdc.get(keys.type(), PersistentDataType.STRING);
    return "wire".equalsIgnoreCase(type);
  }

  private boolean isTerminal(Block block) {
    return Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block);
  }

  private boolean isMonitor(Block block) {
    return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
  }

  private boolean isBus(Block block) {
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }

  private boolean isInventoryBlock(Block block) {
    return block != null && block.getState() instanceof InventoryHolder;
  }

  private boolean isExortStorage(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent();
  }

  private Block resolveTarget(Block clicked, BlockFace face) {
    if (clicked == null) return null;
    if (isReplaceable(clicked)) {
      return clicked;
    }
    return clicked.getRelative(face);
  }

  private boolean isReplaceable(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    return type.isAir() || block.isReplaceable();
  }

  private boolean hasPlacementSpace(Block target) {
    try {
      BoundingBox box =
          new BoundingBox(
              target.getX(),
              target.getY(),
              target.getZ(),
              target.getX() + 1,
              target.getY() + 1,
              target.getZ() + 1);
      for (var entity : target.getWorld().getNearbyEntities(box)) {
        if (entity instanceof LivingEntity) {
          return false;
        }
      }
    } catch (NoSuchMethodError ignored) {
      // If bounding boxes aren't available, skip the entity check.
    }
    return true;
  }

  private boolean shouldUseOffhand(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.OFF_HAND) return true;
    ItemStack main = event.getPlayer().getInventory().getItemInMainHand();
    if (main == null || main.getType() == Material.AIR) return true;
    if (main.hasItemMeta() && customItems.isCustomItem(main)) return false;
    Material type = main.getType();
    if (type.isEdible() && event.getPlayer().getFoodLevel() >= 20) return true;
    if (type.isBlock()) return false;
    if (type.isEdible()) return false;
    return true;
  }

  private BusMode defaultBusMode(boolean exportBus) {
    return busRuntimeConfig.get().defaultMode(exportBus);
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
      ItemStack refund,
      boolean shouldRefund) {
    storageTierSaver
        .apply(storageId, tierKey)
        .whenComplete(
            (ignored, err) -> {
              if (err != null) {
                placementFailureHandler.rollbackFailedPlacement(
                    player, block, storageId, refund, shouldRefund, err);
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

  private void consume(PlayerInteractEvent event) {
    ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
    if (hand == null) return;
    boolean initialized =
        hand.hasItemMeta()
            && hand.getItemMeta()
                .getPersistentDataContainer()
                .has(keys.storageId(), PersistentDataType.STRING);
    if (event.getPlayer().getGameMode() == GameMode.CREATIVE && !initialized) return;
    hand.setAmount(hand.getAmount() - 1);
    event.getPlayer().getInventory().setItem(event.getHand(), hand.getAmount() > 0 ? hand : null);
    event.getPlayer().updateInventory();
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

  private BlockFace resolveBusFacing(PlayerInteractEvent event, Block clicked) {
    if (clicked != null) {
      if (isInventoryBlock(clicked) || isExortStorage(clicked)) {
        return event.getBlockFace().getOppositeFace();
      }
      if (Carriers.matchesCarrier(clicked, wireMaterial) && WireMarker.isWire(plugin, clicked)) {
        return event.getBlockFace();
      }
    }
    return faceTowardPlayer(event.getPlayer());
  }

  private BlockFace faceTowardPlayer(Player player) {
    Vector dir = player.getLocation().getDirection();
    double ax = Math.abs(dir.getX());
    double ay = Math.abs(dir.getY());
    double az = Math.abs(dir.getZ());
    if (ay >= ax && ay >= az) {
      return dir.getY() < 0 ? BlockFace.UP : BlockFace.DOWN;
    }
    if (ax >= az) {
      return dir.getX() < 0 ? BlockFace.EAST : BlockFace.WEST;
    }
    return dir.getZ() < 0 ? BlockFace.SOUTH : BlockFace.NORTH;
  }
}
