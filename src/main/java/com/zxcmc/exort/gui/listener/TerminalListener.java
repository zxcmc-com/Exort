package com.zxcmc.exort.gui.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.SessionManager;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class TerminalListener implements Listener {
  private final JavaPlugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final PlayerFeedback playerFeedback;
  private final Consumer<Block> terminalDisplayRefresh;
  private final BiFunction<String, String, CompletableFuture<Void>> storageTierWriter;
  private final StorageManager storageManager;
  private final SessionManager sessionManager;
  private final StorageKeys keys;
  private final int wireLimit;
  private final int wireHardCap;
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;
  private final Material terminalCarrier;

  public TerminalListener(
      JavaPlugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      PlayerFeedback playerFeedback,
      Consumer<Block> terminalDisplayRefresh,
      BiFunction<String, String, CompletableFuture<Void>> storageTierWriter,
      StorageManager storageManager,
      SessionManager sessionManager,
      StorageKeys keys,
      int wireLimit,
      int wireHardCap,
      int relayRangeChunks,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      Material terminalCarrier) {
    this.plugin = plugin;
    this.regionProtection = regionProtection;
    this.worldEditWandGuard = worldEditWandGuard;
    this.playerFeedback = playerFeedback;
    this.terminalDisplayRefresh = terminalDisplayRefresh;
    this.storageTierWriter = storageTierWriter;
    this.storageManager = storageManager;
    this.sessionManager = sessionManager;
    this.keys = keys;
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.relayCarrier = relayCarrier;
    this.terminalCarrier = terminalCarrier;
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    if (!isOurTerminal(block)) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (event.getPlayer().isSneaking()) {
      // Allow vanilla block placement with shift-right-click
      return;
    }
    if (!regionProtection.canUse(event.getPlayer(), block)) {
      event.setCancelled(true);
      feedbackError(event.getPlayer(), "message.no_permission");
      return;
    }
    terminalDisplayRefresh.accept(block);
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);

    var storageData =
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
    if (storageData.count() == 0) {
      feedbackError(event.getPlayer(), "message.no_storage_adjacent");
      event.setCancelled(true);
      return;
    }
    if (storageData.count() > 1) {
      feedbackError(event.getPlayer(), "message.multiple_storages_adjacent");
      event.setCancelled(true);
      return;
    }
    if (storageData.data() == null) {
      feedbackError(event.getPlayer(), "message.storage_missing_id");
      event.setCancelled(true);
      return;
    }

    Player player = event.getPlayer();
    event.setCancelled(true);
    String storageId = storageData.data().storageId();
    storageTierWriter
        .apply(storageId, storageData.data().tier().key())
        .whenComplete(
            (ignored, err) -> {
              if (err != null) {
                ExortLog.log(
                    plugin, Level.WARNING, "Failed to persist storage tier for " + storageId, err);
              }
            });
    storageManager
        .getOrLoad(storageId)
        .whenComplete(
            (cache, err) -> {
              if (err != null) {
                handleStorageLoadFailure(player, storageId, err);
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin, () -> completeOpen(player, block, storageId, cache));
            });
  }

  private void completeOpen(Player player, Block block, String storageId, StorageCache cache) {
    if (player == null || !player.isOnline()) return;
    if (!isOurTerminal(block)) return;
    if (!regionProtection.canUse(player, block)) {
      feedbackError(player, "message.no_permission");
      return;
    }
    var storageData =
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
    if (storageData.count() == 0) {
      feedbackError(player, "message.no_storage_adjacent");
      return;
    }
    if (storageData.count() > 1) {
      feedbackError(player, "message.multiple_storages_adjacent");
      return;
    }
    if (storageData.data() == null || !storageId.equals(storageData.data().storageId())) {
      feedbackError(player, "message.storage_missing_id");
      return;
    }
    if (!storageId.equals(cache.getStorageId())) return;

    cache.setDisplayName(storageData.data().displayName());
    Location storageLoc =
        storageData.data().block() != null ? storageData.data().block().getLocation() : null;
    TerminalKind kind = TerminalMarker.kind(plugin, block);
    if (kind == TerminalKind.CRAFTING) {
      sessionManager.openCraftingSession(
          player, cache, storageData.data().tier(), block, storageLoc);
    } else {
      sessionManager.openSession(player, cache, storageData.data().tier(), block, storageLoc);
    }
  }

  private void handleStorageLoadFailure(Player player, String storageId, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to load terminal storage " + storageId, err);
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

  private boolean isOurTerminal(Block block) {
    return Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block);
  }
}
