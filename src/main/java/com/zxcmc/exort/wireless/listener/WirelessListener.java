package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.core.task.PluginTasks;
import com.zxcmc.exort.core.util.BlockInteractUtil;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Optional;
import java.util.logging.Level;
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
      feedbackError(player, "message.wireless.disabled");
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
        feedbackError(player, "message.no_permission");
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
        feedbackError(player, "message.no_permission");
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
      feedbackError(player, "message.wireless.missing_storage");
      return true;
    }
    if (!plugin.getRegionProtection().canUse(player, link.data().block())) {
      feedbackError(player, "message.no_permission");
      return true;
    }
    StorageTier tier = link.data().tier();
    Location loc = link.data().block().getLocation();
    service.bind(player, stack, link.data().storageId(), tier, loc);
    updateHand(player, stack, hand);
    plugin.getBossBarManager().remove(player);
    plugin.getPlayerFeedback().success(player, "message.wireless.bound");
    return true;
  }

  private void tryOpen(Player player, ItemStack stack, EquipmentSlot hand) {
    if (!service.isLinked(stack)) {
      feedbackError(player, "message.wireless.not_linked");
      return;
    }
    if (service.currentCharge(stack) <= 0) {
      feedbackError(player, "message.wireless.empty");
      return;
    }
    if (!service.isOwner(player, stack)) {
      service.unbind(stack);
      feedbackError(player, "message.wireless.wrong_owner");
      updateHand(player, stack, hand);
      return;
    }
    String storageId = service.storageId(stack);
    Location anchor = service.storageLocation(stack);
    if (storageId == null || anchor == null) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    if (!anchor.isWorldLoaded()) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    Block anchorBlock = anchor.getBlock();
    Optional<StorageMarker.Data> markerData = StorageMarker.get(plugin, anchorBlock);
    if (!Carriers.matchesCarrier(anchorBlock, plugin.getStorageCarrier())
        || markerData.isEmpty()
        || !storageId.equals(markerData.get().storageId())) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    if (!service.inRange(anchor, player.getLocation())) {
      feedbackError(player, "message.wireless.out_of_range");
      return;
    }
    plugin
        .getDatabase()
        .getStorageTier(storageId)
        .thenCompose(
            optTier ->
                storageManager
                    .getOrLoad(storageId)
                    .thenApply(cache -> new WirelessOpenData(optTier, cache)))
        .whenComplete(
            (data, err) -> {
              if (err != null) {
                handleStorageLoadFailure(player, storageId, err);
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin, () -> completeWirelessOpen(player, hand, storageId, anchor, data));
            });
  }

  private void completeWirelessOpen(
      Player player,
      EquipmentSlot hand,
      String expectedStorageId,
      Location expectedAnchor,
      WirelessOpenData openData) {
    if (player == null || !player.isOnline()) return;
    ItemStack current = currentHandItem(player, hand);
    if (!service.isWireless(current)) return;
    if (!service.isEnabled()) {
      feedbackError(player, "message.wireless.disabled");
      return;
    }
    if (!service.isLinked(current)) {
      feedbackError(player, "message.wireless.not_linked");
      return;
    }
    if (service.currentCharge(current) <= 0) {
      feedbackError(player, "message.wireless.empty");
      return;
    }
    if (!service.isOwner(player, current)) {
      service.unbind(current);
      feedbackError(player, "message.wireless.wrong_owner");
      updateHand(player, current, hand);
      return;
    }

    String storageId = service.storageId(current);
    Location anchor = service.storageLocation(current);
    if (!expectedStorageId.equals(storageId) || !sameBlock(expectedAnchor, anchor)) {
      return;
    }
    if (storageId == null || anchor == null || !anchor.isWorldLoaded()) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    Block anchorBlock = anchor.getBlock();
    Optional<StorageMarker.Data> markerData = StorageMarker.get(plugin, anchorBlock);
    if (!Carriers.matchesCarrier(anchorBlock, plugin.getStorageCarrier())
        || markerData.isEmpty()
        || !storageId.equals(markerData.get().storageId())) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    if (!plugin.getRegionProtection().canUse(player, anchorBlock)) {
      feedbackError(player, "message.no_permission");
      return;
    }
    if (!service.inRange(anchor, player.getLocation())) {
      feedbackError(player, "message.wireless.out_of_range");
      return;
    }
    if (!storageId.equals(openData.cache().getStorageId())) {
      return;
    }

    StorageTier tier =
        openData.optTier().flatMap(StorageTier::fromString).orElse(markerData.get().tier());
    boolean opened =
        plugin.getSessionManager().openWirelessSession(player, openData.cache(), tier, anchor);
    if (!opened) return;
    if (player.getGameMode() != GameMode.CREATIVE && !service.consumeCharge(current)) {
      feedbackError(player, "message.wireless.empty");
      plugin.getSessionManager().forceCloseSession(player);
    }
    updateHand(player, current, hand);
  }

  private ItemStack currentHandItem(Player player, EquipmentSlot hand) {
    return hand == EquipmentSlot.OFF_HAND
        ? player.getInventory().getItemInOffHand()
        : player.getInventory().getItemInMainHand();
  }

  private boolean sameBlock(Location a, Location b) {
    if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
    return a.getWorld().getUID().equals(b.getWorld().getUID())
        && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY()
        && a.getBlockZ() == b.getBlockZ();
  }

  private void handleStorageLoadFailure(Player player, String storageId, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to load wireless storage " + storageId, err);
    PluginTasks.runSyncIfEnabled(
        plugin,
        () -> {
          if (player == null || !player.isOnline()) return;
          feedbackError(player, "message.storage_load_failed");
        });
  }

  private void feedbackError(Player player, String key) {
    plugin.getPlayerFeedback().error(player, key);
  }

  private void updateHand(Player player, ItemStack stack, EquipmentSlot hand) {
    if (hand == EquipmentSlot.OFF_HAND) {
      player.getInventory().setItemInOffHand(stack);
    } else {
      player.getInventory().setItemInMainHand(stack);
    }
  }

  private record WirelessOpenData(Optional<String> optTier, StorageCache cache) {}
}
