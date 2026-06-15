package com.zxcmc.exort.bridge;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BridgeMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class BridgeListener implements Listener {
  private static final long PENDING_TTL_MS = 60_000L;
  private static final BlockFace[] FACES = {
    BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
  };

  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final PlayerFeedback playerFeedback;
  private final BossBarManager bossBarManager;
  private final Lang lang;
  private final StorageKeys keys;
  private final Supplier<DisplayRefreshService> displayRefreshService;
  private final Supplier<NetworkGraphCache> networkGraphCache;
  private final Runnable revalidateSessions;
  private final int wireLimit;
  private final int wireHardCap;
  private final int bridgeRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material bridgeCarrier;
  private final Map<UUID, PendingBridge> pending = new ConcurrentHashMap<>();

  public BridgeListener(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      PlayerFeedback playerFeedback,
      BossBarManager bossBarManager,
      Lang lang,
      StorageKeys keys,
      Supplier<DisplayRefreshService> displayRefreshService,
      Supplier<NetworkGraphCache> networkGraphCache,
      Runnable revalidateSessions,
      int wireLimit,
      int wireHardCap,
      int bridgeRangeChunks,
      Material wireMaterial,
      Material storageCarrier,
      Material bridgeCarrier) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.regionProtection = Objects.requireNonNull(regionProtection, "regionProtection");
    this.worldEditWandGuard = Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    this.playerFeedback = Objects.requireNonNull(playerFeedback, "playerFeedback");
    this.bossBarManager = Objects.requireNonNull(bossBarManager, "bossBarManager");
    this.lang = Objects.requireNonNull(lang, "lang");
    this.keys = Objects.requireNonNull(keys, "keys");
    this.displayRefreshService =
        Objects.requireNonNull(displayRefreshService, "displayRefreshService");
    this.networkGraphCache = Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    this.revalidateSessions = Objects.requireNonNull(revalidateSessions, "revalidateSessions");
    this.wireLimit = wireLimit;
    this.wireHardCap = wireHardCap;
    this.bridgeRangeChunks = bridgeRangeChunks;
    this.wireMaterial = Objects.requireNonNull(wireMaterial, "wireMaterial");
    this.storageCarrier = Objects.requireNonNull(storageCarrier, "storageCarrier");
    this.bridgeCarrier = Objects.requireNonNull(bridgeCarrier, "bridgeCarrier");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (!isBridge(block)) return;
    Player player = event.getPlayer();
    if (worldEditWandGuard.isWorldEditWand(player, event.getItem())) return;
    if (!regionProtection.canUse(player, block)) {
      event.setCancelled(true);
      playerFeedback.error(player, "message.no_permission");
      return;
    }
    if (player.isSneaking()) {
      if (isEmptyHand(event.getItem())) {
        consume(event);
        unlink(player, block);
      }
      return;
    }
    consume(event);
    handleNormalClick(player, block);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    pending.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onWorldChange(PlayerChangedWorldEvent event) {
    pending.remove(event.getPlayer().getUniqueId());
  }

  private void handleNormalClick(Player player, Block clicked) {
    if (BridgeMarker.link(plugin, clicked).isPresent()) {
      pending.remove(player.getUniqueId());
      showStatus(player, clicked);
      return;
    }
    PendingBridge first = pending.get(player.getUniqueId());
    if (first == null || first.expired() || !isBridge(first.block())) {
      pending.put(player.getUniqueId(), new PendingBridge(clicked, System.currentTimeMillis()));
      playerFeedback.info(player, "message.bridge_waiting");
      return;
    }
    if (sameBlock(first.block(), clicked)) {
      playerFeedback.warn(player, "message.bridge_same");
      return;
    }
    if (!regionProtection.canUse(player, first.block())) {
      pending.remove(player.getUniqueId());
      playerFeedback.error(player, "message.no_permission");
      return;
    }
    link(player, first.block(), clicked);
  }

  private void link(Player player, Block first, Block second) {
    if (!sameWorld(first, second)) {
      playerFeedback.error(player, "message.bridge_cross_world");
      return;
    }
    if (BridgeMarker.link(plugin, first).isPresent()
        || BridgeMarker.link(plugin, second).isPresent()) {
      pending.remove(player.getUniqueId());
      playerFeedback.error(player, "message.bridge_already_linked");
      return;
    }
    if (!NetworkGraphCache.inBridgeRange(first, second, bridgeRangeChunks)) {
      playerFeedback.error(player, "message.bridge_out_of_range", bridgeRangeChunks);
      return;
    }
    BridgeMarker.link(plugin, first, second);
    pending.remove(player.getUniqueId());
    refreshEndpoint(first);
    refreshEndpoint(second);
    revalidateSessions.run();
    playerFeedback.success(player, "message.bridge_linked");
  }

  private void unlink(Player player, Block block) {
    Block peer = BridgeMarker.link(plugin, block).map(BridgeMarker.Link::loadedBlock).orElse(null);
    BridgeMarker.unlinkLoadedPair(plugin, block);
    pending.remove(player.getUniqueId());
    refreshEndpoint(block);
    if (peer != null && isBridge(peer)) {
      refreshEndpoint(peer);
    }
    revalidateSessions.run();
    playerFeedback.success(player, "message.bridge_unlinked");
  }

  private void showStatus(Player player, Block block) {
    BridgeMarker.Link link = BridgeMarker.link(plugin, block).orElse(null);
    if (link == null) {
      playerFeedback.info(player, "message.bridge_waiting");
      return;
    }
    String storageStatus = storageStatus(player, block);
    bossBarManager.showBridgeStatus(coords(link), storageStatus, player, 120L);
  }

  private String storageStatus(Player player, Block bridge) {
    TerminalLinkFinder.StorageSearchResult result =
        TerminalLinkFinder.find(
            bridge,
            keys,
            plugin,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            bridgeCarrier,
            bridgeRangeChunks);
    if (result.count() > 1) {
      return lang.tr(player, "bridge.storage_multiple");
    }
    if (result.count() == 1 && result.data() != null) {
      return lang.tr(player, "bridge.storage_tail", tail(result.data().storageId()));
    }
    return lang.tr(player, "bridge.storage_none");
  }

  private void refreshEndpoint(Block block) {
    NetworkGraphCache cache = networkGraphCache.get();
    if (cache != null) {
      cache.invalidateAround(block);
    }
    DisplayRefreshService refresh = displayRefreshService.get();
    if (refresh == null) return;
    refresh.refreshBridge(block);
    refresh.refreshBlockAndNeighbors(block);
    for (BlockFace face : FACES) {
      refresh.refreshBlockAndNeighbors(block.getRelative(face));
    }
    refresh.refreshNetworkFrom(block);
  }

  private boolean isBridge(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, bridgeCarrier)
        && BridgeMarker.isBridge(plugin, block);
  }

  private boolean isEmptyHand(ItemStack item) {
    return item == null || item.getType() == Material.AIR;
  }

  private void consume(PlayerInteractEvent event) {
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    event.setCancelled(true);
  }

  private boolean sameBlock(Block first, Block second) {
    return first != null
        && second != null
        && sameWorld(first, second)
        && first.getX() == second.getX()
        && first.getY() == second.getY()
        && first.getZ() == second.getZ();
  }

  private boolean sameWorld(Block first, Block second) {
    return first != null
        && second != null
        && first.getWorld() != null
        && second.getWorld() != null
        && first.getWorld().getUID().equals(second.getWorld().getUID());
  }

  private String coords(BridgeMarker.Link link) {
    return link.x() + " " + link.y() + " " + link.z();
  }

  private String tail(String id) {
    if (id == null || id.isBlank()) return "?";
    int start = Math.max(0, id.length() - 12);
    return id.substring(start);
  }

  private record PendingBridge(Block block, long createdMs) {
    boolean expired() {
      return System.currentTimeMillis() - createdMs > PENDING_TTL_MS;
    }
  }
}
