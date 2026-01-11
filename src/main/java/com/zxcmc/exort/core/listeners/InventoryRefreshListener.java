package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.ExortPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryRefreshListener implements Listener {
    private final ExortPlugin plugin;
    private final Map<String, Integer> refreshed = new ConcurrentHashMap<>();
    private volatile int lastEpoch = -1;

    public InventoryRefreshListener(ExortPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.refreshPlayerInventory(event.getPlayer()));
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) return;
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof com.zxcmc.exort.gui.GuiSession) return;
        if (holder instanceof com.zxcmc.exort.bus.BusSession) return;
        if (inventory.getType() == InventoryType.PLAYER || inventory.getType() == InventoryType.CRAFTING) return;
        if (holder instanceof Player && inventory.getType() != InventoryType.ENDER_CHEST) return;
        int epoch = plugin.getInventoryRefreshEpoch();
        if (epoch != lastEpoch) {
            refreshed.clear();
            lastEpoch = epoch;
        }
        Player player = event.getPlayer() instanceof Player p ? p : null;
        String key = resolveKey(inventory, holder, player);
        if (key == null || key.isBlank()) return;
        Integer seenEpoch = refreshed.get(key);
        if (seenEpoch != null && seenEpoch == epoch) {
            return;
        }
        refreshed.put(key, epoch);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.refreshContainerInventory(inventory));
    }

    private String resolveKey(Inventory inventory, InventoryHolder holder, Player player) {
        if (inventory.getType() == InventoryType.ENDER_CHEST && player != null) {
            return "ender:" + player.getUniqueId();
        }
        if (holder instanceof BlockState blockState && blockState.getWorld() != null) {
            var loc = blockState.getLocation();
            return "block:" + blockState.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        }
        if (holder instanceof DoubleChest doubleChest) {
            var left = doubleChest.getLeftSide();
            if (left instanceof BlockState leftState && leftState.getWorld() != null) {
                var loc = leftState.getLocation();
                return "double:" + leftState.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
            }
        }
        if (holder instanceof Entity entity) {
            return "entity:" + entity.getUniqueId();
        }
        return null;
    }
}
