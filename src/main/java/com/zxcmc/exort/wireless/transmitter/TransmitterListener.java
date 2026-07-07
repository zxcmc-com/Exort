package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public final class TransmitterListener implements Listener {
  private final Plugin plugin;
  private final WirelessTransmitterService transmitterService;
  private final WirelessTerminalService wirelessService;
  private final TransmitterSessionManager transmitterSessionManager;
  private final Material transmitterCarrier;
  private final RegionProtection regionProtection;
  private final AuthenticationGate authenticationGate;
  private final PlayerFeedback playerFeedback;

  public TransmitterListener(
      Plugin plugin,
      WirelessTransmitterService transmitterService,
      WirelessTerminalService wirelessService,
      TransmitterSessionManager transmitterSessionManager,
      Material transmitterCarrier,
      RegionProtection regionProtection,
      AuthenticationGate authenticationGate,
      PlayerFeedback playerFeedback) {
    this.plugin = plugin;
    this.transmitterService = transmitterService;
    this.wirelessService = wirelessService;
    this.transmitterSessionManager = transmitterSessionManager;
    this.transmitterCarrier = transmitterCarrier;
    this.regionProtection = regionProtection;
    this.authenticationGate = authenticationGate;
    this.playerFeedback = playerFeedback;
  }

  @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    Block block = event.getClickedBlock();
    if (!isTransmitter(block)) {
      return;
    }
    Player player = event.getPlayer();
    if (authenticationGate.blocks(player)) {
      return;
    }
    if (player.isSneaking()) {
      return;
    }
    event.setCancelled(true);
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    if (!wirelessService.isEnabled() || !transmitterService.isEnabled()) {
      playerFeedback.error(player, "message.wireless.disabled");
      return;
    }
    if (!regionProtection.canUse(player, block)) {
      playerFeedback.error(player, "message.no_permission");
      return;
    }
    transmitterSessionManager.open(player, block);
  }

  private boolean isTransmitter(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, transmitterCarrier)
        && TransmitterMarker.isTransmitter(plugin, block);
  }
}
