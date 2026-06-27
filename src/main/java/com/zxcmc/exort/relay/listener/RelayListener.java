package com.zxcmc.exort.relay.listener;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageDisplayName;
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

public final class RelayListener implements Listener {
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
  private final int relayRangeChunks;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material relayCarrier;
  private final Map<UUID, PendingRelay> pending = new ConcurrentHashMap<>();

  public RelayListener(
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
      int relayRangeChunks,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier) {
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
    this.relayRangeChunks = relayRangeChunks;
    this.wireMaterial = Objects.requireNonNull(wireMaterial, "wireMaterial");
    this.storageCarrier = Objects.requireNonNull(storageCarrier, "storageCarrier");
    this.relayCarrier = Objects.requireNonNull(relayCarrier, "relayCarrier");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    Block block = event.getClickedBlock();
    if (!isRelay(block)) return;
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
    if (RelayMarker.link(plugin, clicked).isPresent()) {
      pending.remove(player.getUniqueId());
      showStatus(player, clicked);
      return;
    }
    PendingRelay first = pending.get(player.getUniqueId());
    if (first == null || first.expired() || !isRelay(first.block())) {
      pending.put(player.getUniqueId(), new PendingRelay(clicked, System.currentTimeMillis()));
      playerFeedback.info(player, "message.relay_waiting");
      return;
    }
    if (sameBlock(first.block(), clicked)) {
      playerFeedback.warn(player, "message.relay_same");
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
      playerFeedback.error(player, "message.relay_cross_world");
      return;
    }
    if (RelayMarker.link(plugin, first).isPresent()
        || RelayMarker.link(plugin, second).isPresent()) {
      pending.remove(player.getUniqueId());
      playerFeedback.error(player, "message.relay_already_linked");
      return;
    }
    if (!NetworkGraphCache.inRelayRange(first, second, relayRangeChunks)) {
      playerFeedback.error(player, "message.relay_out_of_range", relayRangeChunks);
      return;
    }
    RelayMarker.link(plugin, first, second);
    pending.remove(player.getUniqueId());
    refreshEndpoint(first);
    refreshEndpoint(second);
    revalidateSessions.run();
    playerFeedback.success(player, "message.relay_linked");
  }

  private void unlink(Player player, Block block) {
    Block peer = RelayMarker.link(plugin, block).map(RelayMarker.Link::loadedBlock).orElse(null);
    RelayMarker.unlinkLoadedPair(plugin, block);
    pending.remove(player.getUniqueId());
    refreshEndpoint(block);
    if (peer != null && isRelay(peer)) {
      refreshEndpoint(peer);
    }
    revalidateSessions.run();
    playerFeedback.success(player, "message.relay_unlinked");
  }

  private void showStatus(Player player, Block block) {
    RelayMarker.Link link = RelayMarker.link(plugin, block).orElse(null);
    if (link == null) {
      playerFeedback.info(player, "message.relay_waiting");
      return;
    }
    String storageStatus = storageStatus(player, block);
    bossBarManager.showRelayStatus(coords(link), storageStatus, player, 120L);
  }

  private String storageStatus(Player player, Block relay) {
    TerminalLinkFinder.StorageSearchResult result =
        TerminalLinkFinder.find(
            relay,
            keys,
            plugin,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            relayRangeChunks);
    if (result.count() > 1) {
      return lang.tr(player, "relay.storage_multiple");
    }
    if (result.count() == 1 && result.data() != null) {
      String displayName = StorageDisplayName.normalize(result.data().displayName());
      String label = displayName != null ? displayName : tail(result.data().storageId());
      return lang.tr(player, "relay.storage_tail", label);
    }
    return lang.tr(player, "relay.storage_none");
  }

  private void refreshEndpoint(Block block) {
    NetworkGraphCache cache = networkGraphCache.get();
    if (cache != null) {
      cache.invalidateAround(block);
    }
    DisplayRefreshService refresh = displayRefreshService.get();
    if (refresh == null) return;
    refresh.refreshRelay(block);
    refresh.refreshBlockAndNeighbors(block);
    for (BlockFace face : FACES) {
      refresh.refreshBlockAndNeighbors(block.getRelative(face));
    }
    refresh.refreshNetworkFrom(block);
  }

  private boolean isRelay(Block block) {
    return block != null
        && Carriers.matchesCarrier(block, relayCarrier)
        && RelayMarker.isRelay(plugin, block);
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

  private String coords(RelayMarker.Link link) {
    return link.x() + " " + link.y() + " " + link.z();
  }

  private String tail(String id) {
    if (id == null || id.isBlank()) return "?";
    int start = Math.max(0, id.length() - 12);
    return id.substring(start);
  }

  private record PendingRelay(Block block, long createdMs) {
    boolean expired() {
      return System.currentTimeMillis() - createdMs > PENDING_TTL_MS;
    }
  }
}
