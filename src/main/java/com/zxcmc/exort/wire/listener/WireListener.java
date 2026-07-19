package com.zxcmc.exort.wire.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.FeedbackReason;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.items.CustomItemClassifier;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class WireListener implements Listener {
  private final int wireLimit;
  private final int wireHardCap;
  private final JavaPlugin plugin;
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
    if (event.getPlayer().isSneaking()) return;
    Block block = event.getClickedBlock();
    if (block == null || !Carriers.matchesCarrier(block, wireMaterial)) return;
    if (!WireMarker.isWire(plugin, block)) return;
    if (worldEditWandGuard.isWorldEditWand(event.getPlayer(), event.getItem())) return;
    if (!regionProtection.canInteract(event.getPlayer(), block)) {
      event.setCancelled(true);
      playerFeedback.respond(
          event.getPlayer(), FeedbackReason.INTERACTION_DENIED, "message.no_permission");
      return;
    }

    NetworkGraphCache.Inspection info = inspectNetwork(block);
    if (info.storage().status() == TerminalLinkFinder.StorageSearchStatus.HARD_CAP) {
      event.setCancelled(true);
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
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    if (!allowPlacement(event.getItem())) {
      event.setCancelled(true);
      playerFeedback.respond(
          event.getPlayer(), FeedbackReason.INTERACTION_DENIED, "message.wire.item_not_placeable");
    }
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

  private boolean allowPlacement(ItemStack item) {
    return CustomItemClassifier.isType(keys, item, "wire")
        || CustomItemClassifier.isType(keys, item, "terminal")
        || CustomItemClassifier.isType(keys, item, "crafting_terminal")
        || CustomItemClassifier.isType(keys, item, "storage")
        || CustomItemClassifier.isType(keys, item, "monitor")
        || CustomItemClassifier.isType(keys, item, "import_bus")
        || CustomItemClassifier.isType(keys, item, "export_bus")
        || CustomItemClassifier.isType(keys, item, "relay")
        || CustomItemClassifier.isType(keys, item, "transmitter");
  }
}
