package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.breaking.BlockBreakHandler;
import com.zxcmc.exort.core.breaking.BreakSoundPlayer;
import com.zxcmc.exort.core.breaking.BreakType;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.display.WireDisplayManager;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class BlockListener implements Listener {
  private final ExortPlugin plugin;
  private final StorageManager storageManager;
  private final StorageKeys keys;
  private final CustomItems customItems;
  private final Material wireMaterial;
  private final ItemHologramManager hologramManager;
  private final WireDisplayManager wireDisplayManager;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final BlockBreakHandler breakHandler;

  public BlockListener(
      ExortPlugin plugin,
      StorageManager storageManager,
      StorageKeys keys,
      CustomItems customItems,
      Material wireMaterial,
      ItemHologramManager hologramManager,
      WireDisplayManager wireDisplayManager,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      BlockBreakHandler breakHandler) {
    this.plugin = plugin;
    this.storageManager = storageManager;
    this.keys = keys;
    this.customItems = customItems;
    this.wireMaterial = wireMaterial;
    this.hologramManager = hologramManager;
    this.wireDisplayManager = wireDisplayManager;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.breakHandler = breakHandler;
  }

  @EventHandler(ignoreCancelled = true)
  public void onPlace(BlockPlaceEvent event) {
    Block block = event.getBlockPlaced();
    if (customItems.isMonitor(event.getItemInHand())
        && Carriers.matchesCarrier(block, monitorCarrier)) {
      if (!plugin
          .getRegionProtection()
          .canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
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
      var refresh = plugin.getDisplayRefreshService();
      if (refresh != null) {
        refresh.refreshChunk(block.getChunk());
      }
      playPlaceSound(block, BreakType.MONITOR);
      if (plugin.getMonitorDisplayManager() != null) {
        plugin.getMonitorDisplayManager().registerMonitor(block);
      }
      invalidateNetwork();
      return;
    }

    if (Carriers.matchesCarrier(block, busCarrier)) {
      boolean isImport = customItems.isImportBus(event.getItemInHand());
      boolean isExport = customItems.isExportBus(event.getItemInHand());
      if (!isImport && !isExport) {
        // not a bus
      } else {
        if (!plugin
            .getRegionProtection()
            .canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
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
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
          refresh.refreshBus(block);
          refresh.refreshChunk(block.getChunk());
        }
        if (plugin.getBusService() != null) {
          plugin
              .getBusService()
              .getOrCreateState(BusPos.of(block), BusMarker.get(plugin, block).orElse(null), block);
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
        invalidateNetwork();
        return;
      }
    }

    if (Carriers.matchesCarrier(block, terminalCarrier)) {
      boolean isTerminal = customItems.isTerminal(event.getItemInHand());
      boolean isCrafting = customItems.isCraftingTerminal(event.getItemInHand());
      if (!isTerminal && !isCrafting) return;
      if (!plugin
          .getRegionProtection()
          .canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
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
      var refresh = plugin.getDisplayRefreshService();
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
        refresh.refreshChunk(block.getChunk());
      }
      invalidateNetwork();
      return;
    }

    if (Carriers.matchesCarrier(block, wireMaterial)) {
      ItemStack inHand = event.getItemInHand();
      if (inHand == null || !inHand.hasItemMeta()) return;
      var itemPdc = inHand.getItemMeta().getPersistentDataContainer();
      String type = itemPdc.get(keys.type(), PersistentDataType.STRING);
      if (!"wire".equalsIgnoreCase(type)) return;
      if (!plugin
          .getRegionProtection()
          .canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
        event.setCancelled(true);
        return;
      }
      Carriers.applyCarrier(block, wireMaterial);
      WireMarker.setWire(plugin, block);
      consumeIfInitialized(event); // consumes initialized storage blocks; for wire mostly no-op
      if (wireDisplayManager != null) {
        wireDisplayManager.updateWireAndNeighbors(block);
      }
      var refresh = plugin.getDisplayRefreshService();
      if (refresh != null) {
        refresh.refreshChunk(block.getChunk());
      }
      playPlaceSound(block, BreakType.WIRE);
      if (hologramManager != null) hologramManager.invalidateAll();
      invalidateNetwork();
      if (refresh != null) {
        refresh.refreshNetworkFrom(block);
      }
      return;
    }

    Optional<StorageTier> tierOpt = customItems.tierFromItem(event.getItemInHand());
    if (tierOpt.isEmpty()) {
      if (customItems.isStorageCore(event.getItemInHand())) {
        if (!Carriers.matchesCarrier(block, storageCarrier)) return;
        if (!plugin
            .getRegionProtection()
            .canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
          event.setCancelled(true);
          return;
        }
        consumeIfInitialized(event);
        StorageCoreMarker.set(plugin, block);
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
          refresh.refreshStorage(block);
          refresh.refreshChunk(block.getChunk());
        }
        playPlaceSound(block, BreakType.STORAGE);
      }
      return;
    }
    StorageTier tier = tierOpt.get();
    if (!Carriers.matchesCarrier(block, storageCarrier)) return;
    if (!plugin
        .getRegionProtection()
        .canBuild(event.getPlayer(), block.getLocation(), block.getType())) {
      event.setCancelled(true);
      return;
    }

    String storageId =
        customItems.storageId(event.getItemInHand()).orElse(UUID.randomUUID().toString());
    consumeIfInitialized(event);
    // Apply facing for vault like vanilla placement
    BlockData data = block.getBlockData();
    if (data instanceof Directional directional) {
      BlockFace storageFace = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
      directional.setFacing(storageFace);
      block.setBlockData(directional, false);
    }
    BlockFace storageFace = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
    StorageMarker.set(plugin, block, storageId, tier, storageFace);
    storageManager.getOrLoad(storageId);
    plugin.getDatabase().setStorageTier(storageId, tier.key());
    var refresh = plugin.getDisplayRefreshService();
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
      refresh.refreshChunk(block.getChunk());
    }
    invalidateNetwork();
    if (refresh != null) {
      refresh.refreshNetworkFrom(block);
    }
  }

  private void invalidateNetwork() {
    var cache = plugin.getNetworkGraphCache();
    if (cache != null) {
      cache.invalidateAll();
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (breakHandler.handleBreak(event.getPlayer(), block, true)) {
      event.setDropItems(false);
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
    var soundConfig = plugin.getBreakSoundConfig();
    if (soundConfig == null || !soundConfig.enabled()) return;
    BreakSoundPlayer.play(
        block.getWorld(),
        block.getLocation().add(0.5, 0.5, 0.5),
        soundConfig.placeKey(type),
        soundConfig.range(),
        soundConfig.volume(),
        soundConfig.pitch());
  }

  private BusMode defaultBusMode(boolean exportBus) {
    String path = exportBus ? "bus.defaultMode.export" : "bus.defaultMode.import";
    String raw = plugin.getConfig().getString(path, "WHITELIST");
    BusMode mode = BusMode.fromString(raw);
    return mode == null ? BusMode.WHITELIST : mode;
  }
}
