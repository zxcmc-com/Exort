package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.core.util.BlockInteractUtil;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class WirelessListener implements Listener {
  private final ExortPlugin plugin;
  private final WirelessTerminalService service;
  private final StorageManager storageManager;

  public WirelessListener(
      ExortPlugin plugin, WirelessTerminalService service, StorageManager storageManager) {
    this.plugin = plugin;
    this.service = service;
    this.storageManager = storageManager;
  }

  @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
    ItemStack stack = event.getItem();
    if (!service.isWireless(stack)) return;
    Player player = event.getPlayer();
    if (event.getHand() == EquipmentSlot.OFF_HAND) {
      ItemStack main = player.getInventory().getItemInMainHand();
      if (main != null
          && main.hasItemMeta()
          && plugin.getCustomItems() != null
          && plugin.getCustomItems().isCustomItem(main)) {
        return;
      }
    }
    if (!service.isEnabled()) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.disabled"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      Block block = event.getClickedBlock();
      if (block != null) {
        if (BlockInteractUtil.isInteractable(block)
            && !TerminalMarker.isTerminal(plugin, block)
            && StorageMarker.get(plugin, block).isEmpty()) {
          return;
        }
        if (tryBind(player, block, stack, event.getHand())) {
          event.setCancelled(true);
          event.setUseItemInHand(Result.DENY);
          return;
        }
      }
    }
    if (event.getAction() == Action.RIGHT_CLICK_AIR
        || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      event.setCancelled(true);
      event.setUseItemInHand(Result.DENY);
      tryOpen(player, stack, event.getHand());
    }
  }

  private boolean tryBind(Player player, Block block, ItemStack stack, EquipmentSlot hand) {
    TerminalLinkFinder.StorageSearchResult link;
    if (TerminalMarker.isTerminal(plugin, block)) {
      if (!plugin.getRegionProtection().canUse(player, block)) {
        plugin
            .getBossBarManager()
            .showError(
                player, plugin.getLang().tr("message.no_permission"), plugin.getStoragePeekTicks());
        return true;
      }
      link =
          TerminalLinkFinder.find(
              block,
              plugin.getKeys(),
              plugin,
              plugin.getWireLimit(),
              plugin.getWireHardCap(),
              plugin.getWireMaterial(),
              plugin.getStorageCarrier());
    } else if (StorageMarker.get(plugin, block).isPresent()) {
      if (!plugin.getRegionProtection().canUse(player, block)) {
        plugin
            .getBossBarManager()
            .showError(
                player, plugin.getLang().tr("message.no_permission"), plugin.getStoragePeekTicks());
        return true;
      }
      StorageMarker.Data data = StorageMarker.get(plugin, block).get();
      link =
          new TerminalLinkFinder.StorageSearchResult(
              1, new TerminalLinkFinder.StorageBlockInfo(block, data.storageId(), data.tier()));
    } else {
      return false;
    }
    if (link.count() != 1 || link.data() == null || link.data().block() == null) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.missing_storage"),
              plugin.getStoragePeekTicks());
      return true;
    }
    if (!plugin.getRegionProtection().canUse(player, link.data().block())) {
      plugin
          .getBossBarManager()
          .showError(
              player, plugin.getLang().tr("message.no_permission"), plugin.getStoragePeekTicks());
      return true;
    }
    StorageTier tier = link.data().tier();
    Location loc = link.data().block().getLocation();
    service.bind(player, stack, link.data().storageId(), tier, loc);
    updateHand(player, stack, hand);
    plugin.getBossBarManager().remove(player);
    return true;
  }

  private void tryOpen(Player player, ItemStack stack, EquipmentSlot hand) {
    if (!service.isLinked(stack)) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.not_linked"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (service.currentCharge(stack) <= 0) {
      plugin
          .getBossBarManager()
          .showError(
              player, plugin.getLang().tr("message.wireless.empty"), plugin.getStoragePeekTicks());
      return;
    }
    if (!service.isOwner(player, stack)) {
      service.unbind(stack);
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.wrong_owner"),
              plugin.getStoragePeekTicks());
      updateHand(player, stack, hand);
      return;
    }
    String storageId = service.storageId(stack);
    Location anchor = service.storageLocation(stack);
    if (storageId == null || anchor == null) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.missing_storage"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (!anchor.isWorldLoaded()) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.missing_storage"),
              plugin.getStoragePeekTicks());
      return;
    }
    Block anchorBlock = anchor.getBlock();
    if (!Carriers.matchesCarrier(anchorBlock, plugin.getStorageCarrier())
        || StorageMarker.get(plugin, anchorBlock).isEmpty()) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.missing_storage"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (!service.inRange(anchor, player.getLocation())) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.out_of_range"),
              plugin.getStoragePeekTicks());
      return;
    }
    plugin
        .getDatabase()
        .getStorageTier(storageId)
        .thenAccept(
            optTier ->
                storageManager
                    .getOrLoad(storageId)
                    .thenAccept(
                        cache ->
                            Bukkit.getScheduler()
                                .runTask(
                                    plugin,
                                    () -> {
                                      StorageTier tier =
                                          optTier
                                              .flatMap(StorageTier::fromString)
                                              .orElseGet(
                                                  () ->
                                                      StorageTier.allTiers().stream()
                                                          .findFirst()
                                                          .orElse(null));
                                      if (tier == null) {
                                        plugin
                                            .getBossBarManager()
                                            .showError(
                                                player,
                                                plugin
                                                    .getLang()
                                                    .tr("message.wireless.missing_storage"),
                                                plugin.getStoragePeekTicks());
                                        return;
                                      }
                                      boolean opened =
                                          plugin
                                              .getSessionManager()
                                              .openWirelessSession(player, cache, tier, anchor);
                                      if (opened) {
                                        if (player.getGameMode() != GameMode.CREATIVE
                                            && !service.consumeCharge(stack)) {
                                          plugin
                                              .getBossBarManager()
                                              .showError(
                                                  player,
                                                  plugin.getLang().tr("message.wireless.empty"),
                                                  plugin.getStoragePeekTicks());
                                          player.closeInventory();
                                        }
                                        updateHand(player, stack, hand);
                                      }
                                    })));
  }

  private void updateHand(Player player, ItemStack stack, EquipmentSlot hand) {
    if (hand == EquipmentSlot.OFF_HAND) {
      player.getInventory().setItemInOffHand(stack);
    } else {
      player.getInventory().setItemInMainHand(stack);
    }
  }
}
