package com.zxcmc.exort.core.listeners;

import com.zxcmc.exort.bus.BusSession;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.gui.CraftingSession;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.gui.StorageSession;
import com.zxcmc.exort.storage.StorageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class InventoryEvents implements Listener {
  private final SessionManager sessionManager;
  private final StorageManager storageManager;
  private final BusSessionManager busSessionManager;

  public InventoryEvents(
      SessionManager sessionManager,
      StorageManager storageManager,
      BusSessionManager busSessionManager) {
    this.sessionManager = sessionManager;
    this.storageManager = storageManager;
    this.busSessionManager = busSessionManager;
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof StorageSession session) {
      ClickType click = event.getClick();
      if (click == ClickType.NUMBER_KEY
          || click == ClickType.SWAP_OFFHAND
          || click == ClickType.DOUBLE_CLICK) {
        event.setCancelled(true);
        return;
      }
      session.handleClick(event);
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof CraftingSession session) {
      ClickType click = event.getClick();
      if (click == ClickType.NUMBER_KEY
          || click == ClickType.SWAP_OFFHAND
          || click == ClickType.DOUBLE_CLICK
          || click == ClickType.DROP
          || click == ClickType.CONTROL_DROP) {
        event.setCancelled(true);
        return;
      }
      session.handleClick(event);
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof BusSession session) {
      ClickType click = event.getClick();
      if (click == ClickType.NUMBER_KEY
          || click == ClickType.SWAP_OFFHAND
          || click == ClickType.DOUBLE_CLICK
          || click == ClickType.DROP
          || click == ClickType.CONTROL_DROP) {
        event.setCancelled(true);
        return;
      }
      session.handleClick(event);
      return;
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof StorageSession
        || event.getView().getTopInventory().getHolder() instanceof CraftingSession
        || event.getView().getTopInventory().getHolder() instanceof BusSession)) {
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof CraftingSession) {
      event.setCancelled(true);
      return;
    }
    // Cancel if any slot in the top inventory is affected
    for (int rawSlot : event.getRawSlots()) {
      if (rawSlot < event.getView().getTopInventory().getSize()) {
        event.setCancelled(true);
        break;
      }
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (event.getInventory().getHolder() instanceof StorageSession session) {
      if (sessionManager.isSwitchingToSearch((Player) event.getPlayer())) {
        return;
      }
      sessionManager.closeSession((Player) event.getPlayer());
      if (session.getCache().isDirty() && !session.getCache().hasViewers()) {
        storageManager.flush(session.getCache());
      }
      return;
    }
    if (event.getInventory().getHolder() instanceof CraftingSession session) {
      if (sessionManager.isSwitchingToSearch((Player) event.getPlayer())) {
        return;
      }
      sessionManager.closeSession((Player) event.getPlayer());
      if (session.getCache().isDirty() && !session.getCache().hasViewers()) {
        storageManager.flush(session.getCache());
      }
      return;
    }
    if (event.getInventory().getHolder() instanceof BusSession) {
      busSessionManager.closeSession((Player) event.getPlayer());
      return;
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    sessionManager.closeSession(event.getPlayer());
    busSessionManager.closeSession(event.getPlayer());
  }
}
