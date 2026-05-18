package com.zxcmc.exort.bus;

import com.zxcmc.exort.bus.resolver.BusTargetResolver;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageTier;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BusSessionManager {
  private final ExortPlugin plugin;
  private final BusService busService;
  private final Lang lang;
  private final Map<UUID, BusSession> byPlayer = new HashMap<>();
  private final Map<BusPos, Set<BusSession>> byBus = new HashMap<>();
  private int deviceTaskId = -1;

  public BusSessionManager(ExortPlugin plugin, BusService busService, Lang lang) {
    this.plugin = plugin;
    this.busService = busService;
    this.lang = lang;
  }

  public void reconfigure() {
    restartDeviceWatcher();
  }

  public void shutdown() {
    stopDeviceWatcher();
    for (BusSession session : new ArrayList<>(byPlayer.values())) {
      forceCloseSession(session.getViewer());
    }
  }

  private void startDeviceWatcher() {
    if (deviceTaskId != -1) return;
    long intervalTicks =
        Math.max(1L, plugin.getConfig().getLong("session.deviceCheckIntervalTicks", 5L));
    deviceTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> {
                  Material busCarrier = plugin.getBusCarrier();
                  double maxDistanceSquared = maxDeviceDistanceSquared();
                  List<Player> toClose = new ArrayList<>();
                  for (BusSession session : new ArrayList<>(byPlayer.values())) {
                    if (shouldCloseSession(session, busCarrier, maxDistanceSquared)) {
                      toClose.add(session.getViewer());
                    }
                  }
                  for (Player player : toClose) {
                    forceCloseSession(player);
                  }
                },
                intervalTicks,
                intervalTicks);
  }

  private void stopDeviceWatcher() {
    if (deviceTaskId != -1) {
      Bukkit.getScheduler().cancelTask(deviceTaskId);
      deviceTaskId = -1;
    }
  }

  private void restartDeviceWatcher() {
    stopDeviceWatcher();
    startDeviceWatcher();
  }

  public boolean openSession(Player player, Block busBlock) {
    if (busBlock == null) return false;
    var dataOpt = BusMarker.get(plugin, busBlock);
    if (dataOpt.isEmpty()) return false;
    BusPos pos = BusPos.of(busBlock);
    BusState state = busService.getOrCreateState(pos, dataOpt.get(), busBlock);
    if (state == null) return false;
    BusSession existing = byPlayer.get(player.getUniqueId());
    if (existing != null) {
      closeSession(player);
    }
    Component title = titleFor(state.type());
    BusSession session =
        new BusSession(
            player,
            this,
            state,
            lang,
            plugin.getBossBarManager(),
            !plugin.isResourceMode(),
            title,
            busBlock);
    byPlayer.put(player.getUniqueId(), session);
    byBus.computeIfAbsent(pos, k -> new HashSet<>()).add(session);
    state.viewerOpened();
    plugin.getBossBarManager().remove(player);
    player.openInventory(session.getInventory());
    session.render();
    return true;
  }

  private Component titleFor(BusType type) {
    boolean resourceMode = plugin.isResourceMode();
    String prefixKey = "resourceMode.bus.gui.prefix";
    String fontKey = "resourceMode.bus.gui.font";
    String nameKey = type == BusType.EXPORT ? "gui.bus.export_title" : "gui.bus.import_title";
    String prefix = resourceMode ? plugin.getConfig().getString(prefixKey, "§fἢ") : "";
    String font =
        resourceMode ? plugin.getConfig().getString(fontKey, "exort:default") : "minecraft:default";
    Component name = Component.text(lang.tr(nameKey));
    if (prefix == null || prefix.isEmpty()) {
      return name;
    }
    Component prefixComponent = Component.text(prefix);
    try {
      prefixComponent = prefixComponent.font(net.kyori.adventure.key.Key.key(font));
    } catch (IllegalArgumentException ignored) {
      // Ignore invalid font id and use default.
    }
    return prefixComponent.append(name);
  }

  public void closeSession(Player player) {
    closeSessionState(player, null);
  }

  public void closeSession(Player player, BusSession expectedSession) {
    closeSessionState(player, expectedSession);
  }

  public void forceCloseSession(Player player) {
    if (player == null) return;
    closeSessionState(player, null);
    player.closeInventory();
  }

  private boolean closeSessionState(Player player, BusSession expectedSession) {
    if (player == null) return false;
    if (expectedSession != null && byPlayer.get(player.getUniqueId()) != expectedSession) {
      return false;
    }
    BusSession session = byPlayer.remove(player.getUniqueId());
    if (session == null) return false;
    BusPos pos = session.getState().pos();
    Set<BusSession> set = byBus.get(pos);
    if (set != null) {
      set.remove(session);
      if (set.isEmpty()) {
        byBus.remove(pos);
      }
    }
    session.getState().viewerClosed();
    session.onClose();
    return true;
  }

  public void closeSessionsForBus(Block busBlock) {
    if (busBlock == null) return;
    BusPos pos = BusPos.of(busBlock);
    Set<BusSession> sessions = byBus.getOrDefault(pos, Collections.emptySet());
    for (BusSession session : new ArrayList<>(sessions)) {
      forceCloseSession(session.getViewer());
    }
  }

  public BusSession sessionFor(Player player) {
    return byPlayer.get(player.getUniqueId());
  }

  public void saveSettings(BusState state) {
    if (state == null) return;
    busService.saveSettings(state);
  }

  public BusLinkStatus resolveStatus(BusState state) {
    if (state == null) {
      return new BusLinkStatus(StorageState.NONE, null, null, null, false);
    }
    Block busBlock = state.pos().block();
    if (busBlock == null) {
      return new BusLinkStatus(StorageState.NONE, null, null, null, false);
    }
    var link =
        TerminalLinkFinder.find(
            busBlock,
            plugin.getKeys(),
            plugin,
            plugin.getWireLimit(),
            plugin.getWireHardCap(),
            plugin.getWireMaterial(),
            plugin.getStorageCarrier());
    boolean loop = busService.isLoopDisabled(state.pos());
    if (link.count() == 0 || link.data() == null) {
      return new BusLinkStatus(StorageState.NONE, null, null, null, loop);
    }
    if (link.count() > 1) {
      return new BusLinkStatus(StorageState.MULTIPLE, null, null, null, loop);
    }
    String storageId = link.data().storageId();
    StorageTier tier = link.data().tier();
    String storageName = tier == null ? storageId : tier.displayName();
    var targetOpt = busService.resolveTarget(busBlock, state.facing());
    String invName = null;
    if (targetOpt.isPresent()) {
      BusTargetResolver.BusTarget target = targetOpt.get();
      if (target instanceof BusTargetResolver.InventoryTarget inv) {
        invName = inv.block().getType().name();
      } else if (target instanceof BusTargetResolver.StorageTarget storageTarget) {
        StorageTier targetTier = storageTarget.tier();
        invName = targetTier != null ? targetTier.displayName() : "Exort Storage";
      }
    }
    return new BusLinkStatus(StorageState.OK, storageId, storageName, invName, loop);
  }

  public enum StorageState {
    OK,
    NONE,
    MULTIPLE
  }

  public record BusLinkStatus(
      StorageState storageState,
      String storageId,
      String storageName,
      String inventoryName,
      boolean loopDisabled) {}

  private boolean shouldCloseSession(
      BusSession session, Material busCarrier, double maxDistanceSquared) {
    Player player = session.getViewer();
    if (player == null || !player.isOnline()) return true;
    Block busBlock = session.getBusBlock();
    if (busBlock == null) return true;
    if (busCarrier == null) return false;
    if (!isBlockChunkLoaded(busBlock)) return true;
    if (!Carriers.matchesCarrier(busBlock, busCarrier)) return true;
    if (BusMarker.get(plugin, busBlock).isEmpty()) return true;
    return isOutOfDeviceRange(player, busBlock, maxDistanceSquared);
  }

  private boolean isOutOfDeviceRange(Player player, Block block, double maxDistanceSquared) {
    if (player.getWorld() == null || block.getWorld() == null) return true;
    if (!player.getWorld().getUID().equals(block.getWorld().getUID())) return true;
    Location playerLocation = player.getLocation();
    double dx = playerLocation.getX() - (block.getX() + 0.5D);
    double dy = playerLocation.getY() - (block.getY() + 0.5D);
    double dz = playerLocation.getZ() - (block.getZ() + 0.5D);
    return dx * dx + dy * dy + dz * dz > maxDistanceSquared;
  }

  private boolean isBlockChunkLoaded(Block block) {
    return block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private double maxDeviceDistanceSquared() {
    double distance =
        Math.max(1.0D, plugin.getConfig().getDouble("session.maxDeviceDistanceBlocks", 8.0D));
    return distance * distance;
  }
}
