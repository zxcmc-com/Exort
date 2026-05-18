package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.core.util.BlockInteractUtil;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
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
    Optional<StorageMarker.Data> markerData = StorageMarker.get(plugin, anchorBlock);
    if (!Carriers.matchesCarrier(anchorBlock, plugin.getStorageCarrier())
        || markerData.isEmpty()
        || !storageId.equals(markerData.get().storageId())) {
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
              Bukkit.getScheduler()
                  .runTask(
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
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.disabled"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (!service.isLinked(current)) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.not_linked"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (service.currentCharge(current) <= 0) {
      plugin
          .getBossBarManager()
          .showError(
              player, plugin.getLang().tr("message.wireless.empty"), plugin.getStoragePeekTicks());
      return;
    }
    if (!service.isOwner(player, current)) {
      service.unbind(current);
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.wrong_owner"),
              plugin.getStoragePeekTicks());
      updateHand(player, current, hand);
      return;
    }

    String storageId = service.storageId(current);
    Location anchor = service.storageLocation(current);
    if (!expectedStorageId.equals(storageId) || !sameBlock(expectedAnchor, anchor)) {
      return;
    }
    if (storageId == null || anchor == null || !anchor.isWorldLoaded()) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.missing_storage"),
              plugin.getStoragePeekTicks());
      return;
    }
    Block anchorBlock = anchor.getBlock();
    Optional<StorageMarker.Data> markerData = StorageMarker.get(plugin, anchorBlock);
    if (!Carriers.matchesCarrier(anchorBlock, plugin.getStorageCarrier())
        || markerData.isEmpty()
        || !storageId.equals(markerData.get().storageId())) {
      plugin
          .getBossBarManager()
          .showError(
              player,
              plugin.getLang().tr("message.wireless.missing_storage"),
              plugin.getStoragePeekTicks());
      return;
    }
    if (!plugin.getRegionProtection().canUse(player, anchorBlock)) {
      plugin
          .getBossBarManager()
          .showError(
              player, plugin.getLang().tr("message.no_permission"), plugin.getStoragePeekTicks());
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
    if (!storageId.equals(openData.cache().getStorageId())) {
      return;
    }

    StorageTier tier =
        openData.optTier().flatMap(StorageTier::fromString).orElse(markerData.get().tier());
    boolean opened =
        plugin.getSessionManager().openWirelessSession(player, openData.cache(), tier, anchor);
    if (!opened) return;
    if (player.getGameMode() != GameMode.CREATIVE && !service.consumeCharge(current)) {
      plugin
          .getBossBarManager()
          .showError(
              player, plugin.getLang().tr("message.wireless.empty"), plugin.getStoragePeekTicks());
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
    plugin
        .getLogger()
        .log(Level.WARNING, "Failed to load wireless storage " + storageId, unwrap(err));
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (player == null || !player.isOnline()) return;
              plugin
                  .getBossBarManager()
                  .showError(
                      player,
                      plugin.getLang().tr("message.storage_load_failed"),
                      plugin.getStoragePeekTicks());
            });
  }

  private Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
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
