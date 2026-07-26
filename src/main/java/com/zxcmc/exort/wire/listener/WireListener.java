package com.zxcmc.exort.wire.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.FeedbackReason;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

public class WireListener implements Listener {
  private final int wireLimit;
  private final int wireHardCap;
  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final BossBarManager bossBarManager;
  private final PlayerFeedback playerFeedback;
  private final StorageKeys keys;
  private final Supplier<NetworkGraphCache> networkGraphCache;
  private final long peekDurationTicks;

  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;
  private final int relayRangeChunks;

  public WireListener(WireListenerDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.regionProtection = dependencies.regionProtection();
    this.worldEditWandGuard = dependencies.worldEditWandGuard();
    this.bossBarManager = dependencies.bossBarManager();
    this.playerFeedback = dependencies.playerFeedback();
    this.keys = dependencies.keys();
    this.networkGraphCache = dependencies.networkGraphCache();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.wireMaterial = dependencies.wireMaterial();
    this.peekDurationTicks = dependencies.peekDurationTicks();
    this.storageCarrier = dependencies.storageCarrier();
    this.relayCarrier = dependencies.relayCarrier();
    this.relayRangeChunks = dependencies.relayRangeChunks();
  }

  @EventHandler(ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    Block block = event.getClickedBlock();
    if (block == null || !Carriers.matchesCarrier(block, wireMaterial)) return;
    if (!WireMarker.isWire(plugin, block)) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (!regionProtection.canInteract(event.getPlayer(), block)) {
      playerFeedback.respond(
          event.getPlayer(), FeedbackReason.INTERACTION_DENIED, "message.no_permission");
      return;
    }

    NetworkGraphCache.Inspection info = inspectNetwork(block);
    if (info.storage().status() == TerminalLinkFinder.StorageSearchStatus.HARD_CAP) {
      playerFeedback.respond(
          event.getPlayer(),
          FeedbackReason.NETWORK_TRAVERSAL_LIMIT,
          "message.wire.hard_cap",
          info.nodes(),
          wireHardCap);
      return;
    }

    boolean tooLong = info.wires() > wireLimit;
    bossBarManager.showWireStatus(
        info.wires(),
        wireLimit,
        tooLong,
        info.storage().connected(),
        event.getPlayer(),
        peekDurationTicks);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onWaterFlow(BlockFromToEvent event) {
    if (wireMaterial != Material.CHORUS_PLANT) return;
    Block target = event.getToBlock();
    if (target.getType() != wireMaterial) return;
    if (WireMarker.isWire(plugin, target)) {
      event.setCancelled(true);
    }
  }

  private NetworkGraphCache.Inspection inspectNetwork(Block start) {
    NetworkGraphCache current = networkGraphCache.get();
    if (current != null) {
      return current.inspect(
          start,
          keys,
          plugin,
          wireLimit,
          wireHardCap,
          wireMaterial,
          storageCarrier,
          relayCarrier,
          relayRangeChunks);
    }
    return NetworkGraphCache.inspectLoaded(
        start,
        keys,
        plugin,
        wireLimit,
        wireHardCap,
        wireMaterial,
        storageCarrier,
        relayCarrier,
        relayRangeChunks);
  }
}
