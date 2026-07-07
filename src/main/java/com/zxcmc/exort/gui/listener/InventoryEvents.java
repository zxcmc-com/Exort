package com.zxcmc.exort.gui.listener;

import com.zxcmc.exort.bus.BusSession;
import com.zxcmc.exort.bus.BusSessionManager;
import com.zxcmc.exort.command.ExortGiveMenu;
import com.zxcmc.exort.gui.CraftingSession;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.gui.StorageSession;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.wireless.transmitter.TransmitterSession;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

public final class InventoryEvents implements Listener {
  private final SessionManager sessionManager;
  private final BusSessionManager busSessionManager;
  private final TransmitterSessionManager transmitterSessionManager;
  private final AuthenticationGate authenticationGate;

  public InventoryEvents(
      SessionManager sessionManager,
      BusSessionManager busSessionManager,
      TransmitterSessionManager transmitterSessionManager,
      AuthenticationGate authenticationGate) {
    this.sessionManager = sessionManager;
    this.busSessionManager = busSessionManager;
    this.transmitterSessionManager = transmitterSessionManager;
    this.authenticationGate = authenticationGate;
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (blockedByAuthentication(
        event.getWhoClicked() instanceof Player player ? player : null,
        event.getView().getTopInventory().getHolder())) {
      event.setCancelled(true);
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof ExortGiveMenu menu) {
      menu.handleClick(event);
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof StorageSession session) {
      if (event.getWhoClicked() instanceof Player player) {
        sessionManager.clearPendingSearchIfParent(player, session);
      }
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
      if (event.getWhoClicked() instanceof Player player) {
        sessionManager.clearPendingSearchIfParent(player, session);
      }
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
    if (event.getView().getTopInventory().getHolder() instanceof TransmitterSession session) {
      session.handleClick(event);
      return;
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onDrag(InventoryDragEvent event) {
    if (blockedByAuthentication(
        event.getWhoClicked() instanceof Player player ? player : null,
        event.getView().getTopInventory().getHolder())) {
      event.setCancelled(true);
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof ExortGiveMenu menu) {
      menu.handleDrag(event);
      return;
    }
    if (!(event.getView().getTopInventory().getHolder() instanceof StorageSession
        || event.getView().getTopInventory().getHolder() instanceof CraftingSession
        || event.getView().getTopInventory().getHolder() instanceof BusSession
        || event.getView().getTopInventory().getHolder() instanceof TransmitterSession)) {
      return;
    }
    if (event.getView().getTopInventory().getHolder() instanceof TransmitterSession session) {
      session.handleDrag(event);
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
    if (!(event.getPlayer() instanceof Player player)) {
      return;
    }
    if (event.getInventory().getHolder() instanceof StorageSession session) {
      if (sessionManager.isSearchCloseProtected(player, session)) {
        sessionManager.verifySearchProtectedClose(player, session);
        return;
      }
      sessionManager.closeSession(player, session);
      return;
    }
    if (event.getInventory().getHolder() instanceof CraftingSession session) {
      if (sessionManager.isSearchCloseProtected(player, session)) {
        sessionManager.verifySearchProtectedClose(player, session);
        return;
      }
      sessionManager.closeSession(player, session);
      return;
    }
    if (event.getInventory().getHolder() instanceof BusSession session) {
      busSessionManager.closeSession(player, session);
      return;
    }
    if (event.getInventory().getHolder() instanceof TransmitterSession session) {
      transmitterSessionManager.closeSession(player, session);
      return;
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    sessionManager.closeSession(event.getPlayer());
    busSessionManager.closeSession(event.getPlayer());
    transmitterSessionManager.closeSession(event.getPlayer(), null);
    sessionManager.bossBarManager().remove(event.getPlayer());
  }

  private boolean blockedByAuthentication(Player player, InventoryHolder holder) {
    if (player == null || !isExortInventory(holder) || !authenticationGate.blocks(player)) {
      return false;
    }
    sessionManager.closeSession(player);
    busSessionManager.closeSession(player);
    transmitterSessionManager.closeSession(player, null);
    sessionManager.bossBarManager().remove(player);
    player.closeInventory();
    return true;
  }

  private static boolean isExortInventory(InventoryHolder holder) {
    return holder instanceof ExortGiveMenu
        || holder instanceof GuiSession
        || holder instanceof BusSession
        || holder instanceof TransmitterSession;
  }
}
