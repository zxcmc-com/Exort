package com.zxcmc.exort.wireless.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.platform.BlockInteractUtil;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class WirelessListener implements Listener {
  private final JavaPlugin plugin;
  private final WirelessTerminalService service;
  private final StorageManager storageManager;
  private final CustomItems customItems;
  private final RegionProtection regionProtection;
  private final AuthenticationGate authenticationGate;
  private final BossBarManager bossBarManager;
  private final PlayerFeedback playerFeedback;
  private final SessionManager sessionManager;
  private final StorageKeys keys;
  private final int wireLimit;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;

  public WirelessListener(WirelessListenerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.service = dependencies.service();
    this.storageManager = dependencies.storageManager();
    this.customItems = dependencies.customItems();
    this.regionProtection = dependencies.regionProtection();
    this.authenticationGate = dependencies.authenticationGate();
    this.bossBarManager = dependencies.bossBarManager();
    this.playerFeedback = dependencies.playerFeedback();
    this.sessionManager = dependencies.sessionManager();
    this.keys = dependencies.keys();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.relayRangeChunks = dependencies.relayRangeChunks();
    this.wireMaterial = dependencies.wireMaterial();
    this.storageCarrier = dependencies.storageCarrier();
    this.relayCarrier = dependencies.relayCarrier();
  }

  @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) return;
    ItemStack stack = event.getItem();
    if (!service.isWireless(stack)) return;
    if (authenticationGate.blocks(event.getPlayer())) return;
    if (hasDeniedUse(event) && event.getAction() != Action.RIGHT_CLICK_AIR) return;
    PerfStats.measure(PerfStats.Area.WIRELESS, () -> onWirelessInteract(event, stack));
  }

  private static boolean hasDeniedUse(PlayerInteractEvent event) {
    return event.useInteractedBlock() == Result.DENY || event.useItemInHand() == Result.DENY;
  }

  private void onWirelessInteract(PlayerInteractEvent event, ItemStack stack) {
    Player player = event.getPlayer();
    if (event.getHand() == EquipmentSlot.OFF_HAND) {
      ItemStack main = player.getInventory().getItemInMainHand();
      if (main != null
          && main.hasItemMeta()
          && customItems != null
          && customItems.isCustomItem(main)) {
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
      if (!regionProtection.canUse(player, block)) {
        feedbackError(player, "message.no_permission");
        return true;
      }
      link =
          TerminalLinkFinder.find(
              block,
              keys,
              plugin,
              wireLimit,
              wireHardCap,
              wireMaterial,
              storageCarrier,
              relayCarrier,
              relayRangeChunks);
    } else if (StorageMarker.get(plugin, block).isPresent()) {
      if (!regionProtection.canUse(player, block)) {
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
    if (!regionProtection.canUse(player, link.data().block())) {
      feedbackError(player, "message.no_permission");
      return true;
    }
    StorageTier tier = link.data().tier();
    Location loc = link.data().block().getLocation();
    service.bind(player, stack, link.data().storageId(), tier, loc);
    updateHand(player, stack, hand);
    bossBarManager.remove(player);
    playerFeedback.success(player, "message.wireless.bound");
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
    if (!Carriers.matchesCarrier(anchorBlock, storageCarrier)
        || markerData.isEmpty()
        || !storageId.equals(markerData.get().storageId())) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    if (!service.inRange(anchor, player.getLocation())) {
      feedbackError(player, "message.wireless.out_of_range");
      return;
    }
    storageManager
        .getOrLoad(storageId)
        .thenApply(WirelessOpenData::new)
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
    if (!Carriers.matchesCarrier(anchorBlock, storageCarrier)
        || markerData.isEmpty()
        || !storageId.equals(markerData.get().storageId())) {
      feedbackError(player, "message.wireless.missing_storage");
      return;
    }
    if (!regionProtection.canUse(player, anchorBlock)) {
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

    boolean opened =
        sessionManager.openWirelessSession(
            player, openData.cache(), markerData.get().tier(), anchor);
    if (!opened) return;
    if (player.getGameMode() != GameMode.CREATIVE && !service.consumeCharge(current)) {
      feedbackError(player, "message.wireless.empty");
      sessionManager.forceCloseSession(player);
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
    playerFeedback.error(player, key);
  }

  private void updateHand(Player player, ItemStack stack, EquipmentSlot hand) {
    if (hand == EquipmentSlot.OFF_HAND) {
      player.getInventory().setItemInOffHand(stack);
    } else {
      player.getInventory().setItemInMainHand(stack);
    }
  }

  private record WirelessOpenData(StorageCache cache) {}
}
