package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.TerminalKind;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import com.zxcmc.exort.core.carrier.Carriers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import com.zxcmc.exort.core.util.BlockInteractUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * Allows placement of plugin items even if their material is not placeable in the current mode
 * (e.g. switching RESOURCE -> VANILLA leaves PAPER items). Places the correct block for the active mode.
 */
public class ItemPlaceBridgeListener implements Listener {
    private final ExortPlugin plugin;
    private final StorageManager storageManager;
    private final CustomItems customItems;
    private final StorageKeys keys;
    private final Material wireMaterial;
    private final Material storageCarrier;
    private final Material terminalCarrier;
    private final Material monitorCarrier;
    private final Material busCarrier;

    public ItemPlaceBridgeListener(ExortPlugin plugin,
                                   StorageManager storageManager,
                                   CustomItems customItems,
                                   StorageKeys keys,
                                   Material wireMaterial,
                                   Material storageCarrier,
                                   Material terminalCarrier,
                                   Material monitorCarrier,
                                   Material busCarrier) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.customItems = customItems;
        this.keys = keys;
        this.wireMaterial = wireMaterial;
        this.storageCarrier = storageCarrier;
        this.terminalCarrier = terminalCarrier;
        this.monitorCarrier = monitorCarrier;
        this.busCarrier = busCarrier;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
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
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), wireMaterial)) return;
            placeWire(event, target);
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.WIRE);
            return;
        }

        // Storage
        Optional<StorageTier> tierOpt = customItems.tierFromItem(stack);
        if (tierOpt.isPresent()) {
            event.setCancelled(true);
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), storageCarrier)) return;
            placeStorage(event, target, tierOpt.get(), customItems.storageId(stack).orElse(UUID.randomUUID().toString()));
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.STORAGE);
            return;
        }
        if (customItems.isStorageCore(stack)) {
            event.setCancelled(true);
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), storageCarrier)) return;
            placeStorageCore(event, target);
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.STORAGE);
            return;
        }

        // Terminal
        if (customItems.isTerminal(stack)) {
            event.setCancelled(true);
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), terminalCarrier)) return;
            placeTerminal(event, target, TerminalKind.TERMINAL);
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.TERMINAL);
            return;
        }

        // Crafting terminal
        if (customItems.isCraftingTerminal(stack)) {
            event.setCancelled(true);
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), terminalCarrier)) return;
            placeTerminal(event, target, TerminalKind.CRAFTING);
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.TERMINAL);
            return;
        }

        // Monitor
        if (customItems.isMonitor(stack)) {
            event.setCancelled(true);
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), monitorCarrier)) return;
            placeMonitor(event, target);
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.MONITOR);
            return;
        }

        // Import/Export bus
        if (customItems.isImportBus(stack) || customItems.isExportBus(stack)) {
            event.setCancelled(true);
            if (!plugin.getRegionProtection().canBuild(event.getPlayer(), target.getLocation(), busCarrier)) return;
            placeBus(event, target, customItems.isExportBus(stack));
            consume(event);
            playPlaceSound(target, com.zxcmc.exort.core.breaking.BreakType.BUS);
        }
    }

    private void placeWire(PlayerInteractEvent event, Block target) {
        Carriers.applyCarrier(target, wireMaterial);
        WireMarker.setWire(plugin, target);
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
            refresh.refreshWireAndNeighbors(target);
            refresh.refreshChunk(target.getChunk());
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().invalidateAll();
        }
        plugin.getSessionManager().revalidateSessions();
        invalidateNetwork();
        if (refresh != null) {
            refresh.refreshNetworkFrom(target);
        }
    }

    private void placeStorage(PlayerInteractEvent event, Block target, StorageTier tier, String storageId) {
        Carriers.applyCarrier(target, storageCarrier);
        org.bukkit.block.BlockFace face = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
        StorageMarker.set(plugin, target, storageId, tier, face);
        plugin.getDatabase().setStorageTier(storageId, tier.key());
        storageManager.getOrLoad(storageId);
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
            refresh.refreshStorage(target);
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().registerStorage(target);
            plugin.getHologramManager().invalidateAll();
        }
        if (refresh != null) {
            refresh.refreshChunk(target.getChunk());
        }
        invalidateNetwork();
        if (refresh != null) {
            refresh.refreshNetworkFrom(target);
        }
    }

    private void placeStorageCore(PlayerInteractEvent event, Block target) {
        Carriers.applyCarrier(target, storageCarrier);
        StorageCoreMarker.set(plugin, target);
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
            refresh.refreshStorage(target);
            refresh.refreshChunk(target.getChunk());
        }
    }

    private void placeTerminal(PlayerInteractEvent event, Block target, TerminalKind kind) {
        org.bukkit.block.BlockFace face = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
        Carriers.applyCarrier(target, terminalCarrier);
        TerminalMarker.set(plugin, target, kind, face);
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
            refresh.refreshTerminal(target);
        }
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().registerTerminal(target);
            plugin.getHologramManager().invalidateAll();
        }
        if (refresh != null) {
            refresh.refreshChunk(target.getChunk());
        }
        invalidateNetwork();
    }

    private void placeMonitor(PlayerInteractEvent event, Block target) {
        org.bukkit.block.BlockFace face = horizontalFacing(event.getPlayer().getFacing().getOppositeFace());
        Carriers.applyCarrier(target, monitorCarrier);
        MonitorMarker.set(plugin, target, face);
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
            refresh.refreshChunk(target.getChunk());
        }
        if (plugin.getMonitorDisplayManager() != null) {
            plugin.getMonitorDisplayManager().registerMonitor(target);
        }
        plugin.markMonitorPlaced(target);
        invalidateNetwork();
    }

    private void placeBus(PlayerInteractEvent event, Block target, boolean exportBus) {
        Block clicked = event.getClickedBlock();
        org.bukkit.block.BlockFace face = resolveBusFacing(event, clicked);
        Carriers.applyCarrier(target, busCarrier);
        com.zxcmc.exort.core.marker.BusMarker.set(plugin, target,
                exportBus ? com.zxcmc.exort.bus.BusType.EXPORT : com.zxcmc.exort.bus.BusType.IMPORT,
                face,
                defaultBusMode(exportBus));
        var refresh = plugin.getDisplayRefreshService();
        if (refresh != null) {
            refresh.refreshBus(target);
            refresh.refreshChunk(target.getChunk());
        }
        if (plugin.getBusService() != null) {
            plugin.getBusService().getOrCreateState(com.zxcmc.exort.bus.BusPos.of(target),
                    com.zxcmc.exort.core.marker.BusMarker.get(plugin, target).orElse(null),
                    target);
        }
        invalidateNetwork();
    }

    private void invalidateNetwork() {
        var cache = plugin.getNetworkGraphCache();
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    private void playPlaceSound(Block block, com.zxcmc.exort.core.breaking.BreakType type) {
        var soundConfig = plugin.getBreakSoundConfig();
        if (soundConfig == null || !soundConfig.enabled()) return;
        com.zxcmc.exort.core.breaking.BreakSoundPlayer.play(
                block.getWorld(),
                block.getLocation().add(0.5, 0.5, 0.5),
                soundConfig.placeKey(type),
                soundConfig.range(),
                soundConfig.volume(),
                soundConfig.pitch()
        );
    }

    private boolean isWire(ItemStack stack) {
        if (!stack.hasItemMeta()) return false;
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(keys.type(), PersistentDataType.STRING);
        return "wire".equalsIgnoreCase(type);
    }

    private boolean isTerminal(Block block) {
        return Carriers.matchesCarrier(block, terminalCarrier) && com.zxcmc.exort.core.marker.TerminalMarker.isTerminal(plugin, block);
    }

    private boolean isMonitor(Block block) {
        return Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block);
    }

    private boolean isBus(Block block) {
        return Carriers.matchesCarrier(block, busCarrier) && com.zxcmc.exort.core.marker.BusMarker.isBus(plugin, block);
    }


    private boolean isInventoryBlock(Block block) {
        return block != null && block.getState() instanceof InventoryHolder;
    }

    private boolean isExortStorage(Block block) {
        return block != null
                && Carriers.matchesCarrier(block, storageCarrier)
                && StorageMarker.get(plugin, block).isPresent();
    }

    private Block resolveTarget(Block clicked, org.bukkit.block.BlockFace face) {
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
            BoundingBox box = new BoundingBox(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    target.getX() + 1,
                    target.getY() + 1,
                    target.getZ() + 1
            );
            for (var entity : target.getWorld().getNearbyEntities(box)) {
                if (entity instanceof org.bukkit.entity.LivingEntity) {
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

    private com.zxcmc.exort.bus.BusMode defaultBusMode(boolean exportBus) {
        String path = exportBus ? "bus.defaultMode.export" : "bus.defaultMode.import";
        String raw = plugin.getConfig().getString(path, "WHITELIST");
        com.zxcmc.exort.bus.BusMode mode = com.zxcmc.exort.bus.BusMode.fromString(raw);
        return mode == null ? com.zxcmc.exort.bus.BusMode.WHITELIST : mode;
    }

    private void consume(PlayerInteractEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (hand == null) return;
        boolean initialized = hand.hasItemMeta() && hand.getItemMeta().getPersistentDataContainer().has(keys.storageId(), PersistentDataType.STRING);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && !initialized) return;
        hand.setAmount(hand.getAmount() - 1);
        event.getPlayer().getInventory().setItem(event.getHand(), hand.getAmount() > 0 ? hand : null);
        event.getPlayer().updateInventory();
    }

    private org.bukkit.block.BlockFace horizontalFacing(org.bukkit.block.BlockFace face) {
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> face;
            case NORTH_NORTH_EAST, EAST_NORTH_EAST, NORTH_EAST -> org.bukkit.block.BlockFace.NORTH;
            case SOUTH_SOUTH_EAST, SOUTH_EAST, EAST_SOUTH_EAST -> org.bukkit.block.BlockFace.SOUTH;
            case SOUTH_SOUTH_WEST, SOUTH_WEST, WEST_SOUTH_WEST -> org.bukkit.block.BlockFace.SOUTH;
            case NORTH_NORTH_WEST, NORTH_WEST, WEST_NORTH_WEST -> org.bukkit.block.BlockFace.NORTH;
            case UP, DOWN -> org.bukkit.block.BlockFace.SOUTH;
            default -> org.bukkit.block.BlockFace.SOUTH;
        };
    }

    private org.bukkit.block.BlockFace resolveBusFacing(PlayerInteractEvent event, Block clicked) {
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

    private org.bukkit.block.BlockFace faceTowardPlayer(org.bukkit.entity.Player player) {
        org.bukkit.util.Vector dir = player.getLocation().getDirection();
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());
        if (ay >= ax && ay >= az) {
            return dir.getY() < 0 ? org.bukkit.block.BlockFace.UP : org.bukkit.block.BlockFace.DOWN;
        }
        if (ax >= az) {
            return dir.getX() < 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST;
        }
        return dir.getZ() < 0 ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
    }
}
