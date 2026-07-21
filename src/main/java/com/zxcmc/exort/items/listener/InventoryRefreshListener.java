package com.zxcmc.exort.items.listener;

import com.zxcmc.exort.bus.BusSession;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.gui.GuiSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public final class InventoryRefreshListener implements Listener {
  static final int MAX_TRACKED_CONTAINERS = 8_192;

  private final Plugin plugin;
  private final IntSupplier refreshEpoch;
  private final Consumer<Player> refreshPlayerInventory;
  private final Consumer<Inventory> refreshContainerInventory;
  private final Map<String, Integer> refreshed;
  private final int maxTrackedContainers;
  private int lastEpoch = -1;

  public InventoryRefreshListener(
      Plugin plugin,
      IntSupplier refreshEpoch,
      Consumer<Player> refreshPlayerInventory,
      Consumer<Inventory> refreshContainerInventory) {
    this(
        plugin,
        refreshEpoch,
        refreshPlayerInventory,
        refreshContainerInventory,
        MAX_TRACKED_CONTAINERS);
  }

  InventoryRefreshListener(
      Plugin plugin,
      IntSupplier refreshEpoch,
      Consumer<Player> refreshPlayerInventory,
      Consumer<Inventory> refreshContainerInventory,
      int maxTrackedContainers) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.refreshEpoch = Objects.requireNonNull(refreshEpoch, "refreshEpoch");
    this.refreshPlayerInventory =
        Objects.requireNonNull(refreshPlayerInventory, "refreshPlayerInventory");
    this.refreshContainerInventory =
        Objects.requireNonNull(refreshContainerInventory, "refreshContainerInventory");
    if (maxTrackedContainers <= 0) {
      throw new IllegalArgumentException("maxTrackedContainers must be positive");
    }
    this.maxTrackedContainers = maxTrackedContainers;
    this.refreshed = new LinkedHashMap<>(128, 0.75f, true);
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> refreshPlayerInventory.accept(event.getPlayer()));
  }

  @EventHandler
  public void onInventoryOpen(InventoryOpenEvent event) {
    Inventory inventory = event.getInventory();
    if (inventory == null) return;
    InventoryHolder holder = inventory.getHolder();
    if (holder instanceof GuiSession) return;
    if (holder instanceof BusSession) return;
    if (inventory.getType() == InventoryType.PLAYER
        || inventory.getType() == InventoryType.CRAFTING) return;
    if (holder instanceof Player && inventory.getType() != InventoryType.ENDER_CHEST) return;
    int epoch = refreshEpoch.getAsInt();
    Player player = event.getPlayer() instanceof Player p ? p : null;
    String key = resolveKey(inventory, holder, player);
    if (key == null || key.isBlank()) return;
    if (!shouldRefresh(key, epoch)) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> refreshContainerInventory.accept(inventory));
  }

  boolean shouldRefresh(String key, int epoch) {
    if (key == null || key.isBlank()) {
      return false;
    }
    if (epoch != lastEpoch) {
      refreshed.clear();
      lastEpoch = epoch;
    }
    Integer seenEpoch = refreshed.get(key);
    if (seenEpoch != null && seenEpoch == epoch) {
      return false;
    }
    refreshed.put(key, epoch);
    if (refreshed.size() > maxTrackedContainers) {
      var iterator = refreshed.entrySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
        PerfStats.incrementCounter("inventoryRefresh.containerCacheEvictions");
      }
    }
    PerfStats.setGauge("inventoryRefresh.trackedContainers", refreshed.size());
    return true;
  }

  public void close() {
    refreshed.clear();
    lastEpoch = -1;
    PerfStats.setGauge("inventoryRefresh.trackedContainers", 0);
  }

  int trackedContainerCount() {
    return refreshed.size();
  }

  private String resolveKey(Inventory inventory, InventoryHolder holder, Player player) {
    if (inventory.getType() == InventoryType.ENDER_CHEST && player != null) {
      return "ender:" + player.getUniqueId();
    }
    if (holder instanceof BlockState blockState && blockState.getWorld() != null) {
      var loc = blockState.getLocation();
      return "block:"
          + blockState.getWorld().getUID()
          + ":"
          + loc.getBlockX()
          + ":"
          + loc.getBlockY()
          + ":"
          + loc.getBlockZ();
    }
    if (holder instanceof DoubleChest doubleChest) {
      var left = doubleChest.getLeftSide();
      if (left instanceof BlockState leftState && leftState.getWorld() != null) {
        var loc = leftState.getLocation();
        return "double:"
            + leftState.getWorld().getUID()
            + ":"
            + loc.getBlockX()
            + ":"
            + loc.getBlockY()
            + ":"
            + loc.getBlockZ();
      }
    }
    if (holder instanceof Entity entity) {
      return "entity:" + entity.getUniqueId();
    }
    return null;
  }
}
