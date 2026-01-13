package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.core.recipes.CraftingRules;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.StonecutterInventory;
import org.bukkit.inventory.StonecuttingRecipe;

public final class CraftBlockerListener implements Listener {
  private final CraftingRules rules;

  public CraftBlockerListener(CraftingRules rules) {
    this.rules = rules;
  }

  @EventHandler
  public void onPrepareCraft(PrepareItemCraftEvent event) {
    CraftingInventory inv = event.getInventory();
    if (rules.shouldBlock(inv.getMatrix(), event.getRecipe())) {
      inv.setResult(null);
    }
  }

  @EventHandler
  public void onCraft(CraftItemEvent event) {
    CraftingInventory inv = event.getInventory();
    if (rules.shouldBlock(inv.getMatrix(), event.getRecipe())) {
      event.setCancelled(true);
      inv.setResult(null);
    }
  }

  @EventHandler
  public void onPrepareSmithing(PrepareSmithingEvent event) {
    SmithingInventory inv = event.getInventory();
    if (rules.shouldBlock(inv.getContents(), inv.getRecipe())) {
      event.setResult(null);
    }
  }

  @EventHandler
  public void onSmith(SmithItemEvent event) {
    SmithingInventory inv = event.getInventory();
    if (rules.shouldBlock(inv.getContents(), inv.getRecipe())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onStonecutterSelect(PlayerStonecutterRecipeSelectEvent event) {
    StonecutterInventory inv = event.getStonecutterInventory();
    ItemStack input = inv.getInputItem();
    if (!rules.isCustomItem(input)) return;
    StonecuttingRecipe recipe = event.getStonecuttingRecipe();
    if (rules.shouldBlock(new ItemStack[] {input}, recipe)) {
      event.setCancelled(true);
      inv.setResult(null);
    }
  }

  @EventHandler
  public void onCook(BlockCookEvent event) {
    ItemStack source = event.getSource();
    if (!rules.isCustomItem(source)) return;
    if (rules.shouldBlock(new ItemStack[] {source}, event.getRecipe())) {
      event.setCancelled(true);
    }
  }
}
