package com.zxcmc.exort.recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class RecipeDiscoveryListener implements Listener {
  private final Plugin plugin;
  private final RecipeService recipeService;
  private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

  public RecipeDiscoveryListener(Plugin plugin, RecipeService recipeService) {
    this.plugin = plugin;
    this.recipeService = recipeService;
  }

  public void discoverForOnlinePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      schedule(player);
    }
  }

  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    schedule(event.getPlayer());
  }

  @EventHandler
  public void onPickup(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player) {
      schedule(player);
    }
  }

  @EventHandler
  public void onCraft(CraftItemEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      schedule(player);
    }
  }

  @EventHandler
  public void onSmith(SmithItemEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      schedule(player);
    }
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      schedule(player);
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      schedule(player);
    }
  }

  private void schedule(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }
    UUID playerId = player.getUniqueId();
    if (!pending.add(playerId)) {
      return;
    }
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              pending.remove(playerId);
              discover(player);
            });
  }

  void discover(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }
    List<RecipeDiscoveryEntry> entries = recipeService.discoveryEntries();
    if (entries.isEmpty()) {
      return;
    }
    ItemStack[] inventory = scanStacks(player);
    List<NamespacedKey> keys = new ArrayList<>();
    for (RecipeDiscoveryEntry entry : entries) {
      if (!player.hasDiscoveredRecipe(entry.key()) && entry.matchesAny(inventory)) {
        keys.add(entry.key());
      }
    }
    if (!keys.isEmpty()) {
      player.discoverRecipes(keys);
    }
  }

  private ItemStack[] scanStacks(Player player) {
    List<ItemStack> stacks = new ArrayList<>();
    ItemStack[] contents = player.getInventory().getContents();
    if (contents != null) {
      for (ItemStack stack : contents) {
        if (stack != null) {
          stacks.add(stack);
        }
      }
    }
    ItemStack cursor = player.getItemOnCursor();
    if (cursor != null) {
      stacks.add(cursor);
    }
    return stacks.toArray(new ItemStack[0]);
  }
}
