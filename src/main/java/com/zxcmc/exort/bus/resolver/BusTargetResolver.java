package com.zxcmc.exort.bus.resolver;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;
import java.util.UUID;

public final class BusTargetResolver {
    private final ExortPlugin plugin;
    private final boolean allowStorageTargets;

    public BusTargetResolver(ExortPlugin plugin, boolean allowStorageTargets) {
        this.plugin = plugin;
        this.allowStorageTargets = allowStorageTargets;
    }

    public Optional<BusTarget> resolve(Block busBlock, BlockFace facing) {
        if (busBlock == null || facing == null) return Optional.empty();
        Block target = busBlock.getRelative(facing);
        if (!busBlock.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) {
            return Optional.empty();
        }
        var storage = StorageMarker.get(plugin, target);
        if (storage.isPresent()) {
            if (!allowStorageTargets) {
                return Optional.empty();
            }
            StorageMarker.Data data = storage.get();
            return Optional.of(new StorageTarget(target, data.storageId(), data.tier()));
        }
        if (TerminalMarker.isTerminal(plugin, target)
                || MonitorMarker.isMonitor(plugin, target)
                || WireMarker.isWire(plugin, target)
                || BusMarker.isBus(plugin, target)) {
            return Optional.empty();
        }
        BlockState state = target.getState();
        if (!(state instanceof InventoryHolder holder)) return Optional.empty();
        Inventory inv = holder.getInventory();
        if (inv == null) return Optional.empty();
        return Optional.of(new InventoryTarget(target, inv, state, facing.getOppositeFace()));
    }

    public InvKey inventoryKey(InventoryTarget target) {
        if (target == null) return null;
        Inventory inv = target.inventory();
        if (inv != null) {
            InventoryHolder holder = inv.getHolder();
            if (holder instanceof DoubleChest doubleChest) {
                Location left = locationOfHolder(doubleChest.getLeftSide());
                Location right = locationOfHolder(doubleChest.getRightSide());
                if (left != null && right != null && left.getWorld() != null && left.getWorld().equals(right.getWorld())) {
                    Location min = compareLocation(left, right) <= 0 ? left : right;
                    return new InvKey(min.getWorld().getUID(), min.getBlockX(), min.getBlockY(), min.getBlockZ());
                }
                Location center = doubleChest.getLocation();
                if (center != null && center.getWorld() != null) {
                    return new InvKey(center.getWorld().getUID(), center.getBlockX(), center.getBlockY(), center.getBlockZ());
                }
            }
        }
        Block block = target.block();
        if (block == null || block.getWorld() == null) return null;
        return new InvKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private Location locationOfHolder(InventoryHolder holder) {
        if (holder instanceof BlockState state) {
            return state.getLocation();
        }
        if (holder instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }

    private int compareLocation(Location a, Location b) {
        int wx = Integer.compare(a.getBlockX(), b.getBlockX());
        if (wx != 0) return wx;
        int wy = Integer.compare(a.getBlockY(), b.getBlockY());
        if (wy != 0) return wy;
        return Integer.compare(a.getBlockZ(), b.getBlockZ());
    }

    public interface BusTarget {
    }

    public record InventoryTarget(Block block, Inventory inventory, BlockState state, BlockFace side) implements BusTarget {
    }

    public record StorageTarget(Block block, String storageId, StorageTier tier) implements BusTarget {
    }

    public record InvKey(UUID world, int x, int y, int z) {
    }
}
