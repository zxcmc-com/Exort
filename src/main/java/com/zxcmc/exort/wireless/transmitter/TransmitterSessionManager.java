package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.text.GuiOverlayGlyphs;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

public final class TransmitterSessionManager {
  private static final long REFRESH_TICKS = 40L;
  private static final long REFRESH_END_TOLERANCE_MS = 1_000L;

  public enum VisualState {
    DEFAULT,
    ENABLED,
    CHARGING
  }

  private final Plugin plugin;
  private final WirelessTransmitterService transmitterService;
  private final WirelessTerminalService wirelessService;
  private final CustomItems customItems;
  private final Lang lang;
  private final PlayerFeedback playerFeedback;
  private final RegionProtection regionProtection;
  private final BooleanSupplier resourceMode;
  private final Supplier<GuiOverlayConfig> overlayConfigSource;
  private final Consumer<Block> transmitterRefresh;
  private final Map<UUID, TransmitterSession> byPlayer = new HashMap<>();
  private final Map<BlockKey, TransmitterSession> byBlock = new HashMap<>();
  private final Map<BlockKey, ScheduledRefresh> chargingRefreshTasks = new HashMap<>();
  private GuiOverlayConfig overlayConfig;
  private int refreshTaskId = -1;

  public TransmitterSessionManager(
      Plugin plugin,
      WirelessTransmitterService transmitterService,
      WirelessTerminalService wirelessService,
      CustomItems customItems,
      Lang lang,
      PlayerFeedback playerFeedback,
      RegionProtection regionProtection,
      BooleanSupplier resourceMode,
      Supplier<GuiOverlayConfig> overlayConfigSource,
      Consumer<Block> transmitterRefresh) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.transmitterService = Objects.requireNonNull(transmitterService, "transmitterService");
    this.wirelessService = Objects.requireNonNull(wirelessService, "wirelessService");
    this.customItems = Objects.requireNonNull(customItems, "customItems");
    this.lang = Objects.requireNonNull(lang, "lang");
    this.playerFeedback = Objects.requireNonNull(playerFeedback, "playerFeedback");
    this.regionProtection = Objects.requireNonNull(regionProtection, "regionProtection");
    this.resourceMode = Objects.requireNonNull(resourceMode, "resourceMode");
    this.overlayConfigSource = Objects.requireNonNull(overlayConfigSource, "overlayConfigSource");
    this.transmitterRefresh = Objects.requireNonNull(transmitterRefresh, "transmitterRefresh");
    this.overlayConfig = overlayConfigSource.get();
  }

  public void reconfigure() {
    overlayConfig = overlayConfigSource.get();
    for (TransmitterSession session : new ArrayList<>(byPlayer.values())) {
      session.render();
    }
  }

  public void rerender(Player player) {
    if (player == null) {
      return;
    }
    TransmitterSession session = byPlayer.get(player.getUniqueId());
    if (session != null) {
      session.render();
    }
  }

  Plugin plugin() {
    return plugin;
  }

  CustomItems customItems() {
    return customItems;
  }

  public void open(Player player, Block transmitter) {
    if (player == null || transmitter == null) {
      return;
    }
    closeSession(player, null);
    TransmitterSession existing = byBlock.get(BlockKey.of(transmitter));
    if (existing != null) {
      forceCloseSession(existing.viewer());
    }
    boolean useFillers = !resourceMode.getAsBoolean();
    TransmitterSession session =
        new TransmitterSession(
            this,
            player,
            transmitter,
            transmitterService,
            wirelessService,
            lang,
            playerFeedback,
            regionProtection,
            useFillers,
            titleFor(player));
    byPlayer.put(player.getUniqueId(), session);
    byBlock.put(BlockKey.of(transmitter), session);
    player.openInventory(session.getInventory());
    session.render();
    startRefreshTask();
  }

  public void closeSession(Player player, TransmitterSession expected) {
    if (player == null) {
      return;
    }
    TransmitterSession session = byPlayer.get(player.getUniqueId());
    if (session == null || (expected != null && session != expected)) {
      return;
    }
    byPlayer.remove(player.getUniqueId());
    byBlock.remove(BlockKey.of(session.transmitter()));
    session.onClose();
    stopRefreshTaskIfIdle();
  }

  public void forceCloseSession(Player player) {
    if (player == null) {
      return;
    }
    closeSession(player, null);
    player.closeInventory();
  }

  public void closeSessionsForTransmitter(Block transmitter) {
    if (transmitter == null) {
      return;
    }
    TransmitterSession session = byBlock.get(BlockKey.of(transmitter));
    if (session != null) {
      forceCloseSession(session.viewer());
    }
  }

  public void shutdown() {
    for (TransmitterSession session : new ArrayList<>(byPlayer.values())) {
      forceCloseSession(session.viewer());
    }
    stopRefreshTask();
    cancelChargingRefreshTasks();
  }

  public void dropStoredItems(Block transmitter) {
    if (transmitter == null) {
      return;
    }
    closeSessionsForTransmitter(transmitter);
    cancelChargingRefresh(transmitter);
    TransmitterStoredTerminal.TakeReservation terminal =
        TransmitterStoredTerminal.reserveTake(
                plugin, transmitter, wirelessService::isWireless, message -> ExortLog.warn(message))
            .orElse(null);
    dropReservation(
        transmitter, terminal, wirelessService::extractFromStorage, "Wireless Terminal");
    TransmitterStoredTerminal.TakeReservation booster =
        TransmitterStoredBooster.reserveTake(
                plugin,
                transmitter,
                customItems::isWirelessBooster,
                message -> ExortLog.warn(message))
            .orElse(null);
    dropReservation(transmitter, booster, Function.identity(), "Wireless Signal Booster");
  }

  private void dropReservation(
      Block transmitter,
      TransmitterStoredTerminal.TakeReservation reservation,
      Function<ItemStack, ItemStack> prepareDrop,
      String itemName) {
    if (reservation == null || !reservation.commit()) {
      return;
    }
    boolean spawned = false;
    try {
      ItemStack drop = prepareDrop.apply(reservation.item());
      var dropped =
          transmitter.getWorld().dropItem(transmitter.getLocation().add(0.5, 0.5, 0.5), drop);
      spawned = true;
      dropped.setVelocity(new Vector(0, 0, 0));
    } catch (RuntimeException failure) {
      boolean restored = false;
      if (!spawned) {
        try {
          restored = reservation.rollback();
        } catch (RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
      }
      if (!spawned && !restored) {
        ExortLog.error(
            "Failed to restore "
                + itemName
                + " after transmitter drop error: "
                + failure.getMessage());
      } else {
        ExortLog.warn("Wireless Transmitter item drop failed: " + failure.getMessage());
      }
    }
  }

  public VisualState visualState(Block transmitter) {
    return visualState(transmitter, transmitterService.status(transmitter));
  }

  VisualState visualState(Block transmitter, WirelessTransmitterService.Status status) {
    if (transmitter == null || transmitter.getWorld() == null) {
      return VisualState.DEFAULT;
    }
    WirelessTransmitterService.Status safeStatus =
        status == null ? transmitterService.status(transmitter) : status;
    boolean active = safeStatus.active();
    var stored =
        TransmitterStoredTerminal.get(
            plugin, transmitter, wirelessService::isWireless, message -> ExortLog.warn(message));
    if (stored.isEmpty()) {
      cancelChargingRefresh(transmitter);
      return active ? VisualState.ENABLED : VisualState.DEFAULT;
    }
    WirelessTerminalService.StoredChargeState charge =
        wirelessService.reconcileStoredCharge(stored.get(), active);
    if (!TransmitterStoredTerminal.set(
        plugin, transmitter, charge.stack(), wirelessService::isWireless)) {
      cancelChargingRefresh(transmitter);
      return active ? VisualState.ENABLED : VisualState.DEFAULT;
    }
    if (!active) {
      cancelChargingRefresh(transmitter);
      return VisualState.DEFAULT;
    }
    if (charge.charging()) {
      scheduleChargingRefresh(transmitter, charge.chargingEndsAtMillis());
      return VisualState.CHARGING;
    }
    cancelChargingRefresh(transmitter);
    return VisualState.ENABLED;
  }

  void reconcileCharging(Block transmitter, WirelessTransmitterService.Status status) {
    visualState(transmitter, status);
  }

  boolean prepareStoredTerminal(Block transmitter, ItemStack stack) {
    if (!wirelessService.isWireless(stack)) {
      return false;
    }
    WirelessTransmitterService.Status status = transmitterService.status(transmitter);
    WirelessTerminalService.StoredChargeState charge =
        wirelessService.reconcileStoredCharge(stack, status.active());
    return charge.stack() != null;
  }

  void refreshTransmitterDisplay(Block transmitter) {
    if (transmitter != null) {
      transmitterRefresh.accept(transmitter);
    }
  }

  public void cancelChargingRefresh(Block transmitter) {
    if (transmitter == null || transmitter.getWorld() == null) {
      return;
    }
    cancelChargingRefresh(BlockKey.of(transmitter));
  }

  private Component titleFor(Player viewer) {
    Component name = ExortText.plain(lang.tr(viewer, "gui.transmitter.title"));
    if (!resourceMode.getAsBoolean()) {
      return name;
    }
    GuiOverlayConfig config = overlayConfig == null ? GuiOverlayConfig.defaults() : overlayConfig;
    return GuiOverlayGlyphs.overlay(
            GuiOverlayConfig.TRANSMITTER_KEY, config.transmitter(), ExortLog::warn)
        .map(overlay -> ExortText.withPrefix(overlay, name))
        .orElse(name);
  }

  private void startRefreshTask() {
    if (refreshTaskId != -1) {
      return;
    }
    refreshTaskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin,
                () -> {
                  List<Player> toClose = null;
                  for (TransmitterSession session : byPlayer.values()) {
                    if (!session.viewer().isOnline()) {
                      if (toClose == null) {
                        toClose = new ArrayList<>();
                      }
                      toClose.add(session.viewer());
                      continue;
                    }
                    session.render();
                  }
                  if (toClose != null) {
                    for (Player player : toClose) {
                      forceCloseSession(player);
                    }
                  }
                  stopRefreshTaskIfIdle();
                },
                REFRESH_TICKS,
                REFRESH_TICKS);
  }

  private void stopRefreshTaskIfIdle() {
    if (!byPlayer.isEmpty()) {
      return;
    }
    stopRefreshTask();
  }

  private void stopRefreshTask() {
    if (refreshTaskId == -1) {
      return;
    }
    Bukkit.getScheduler().cancelTask(refreshTaskId);
    refreshTaskId = -1;
  }

  private void scheduleChargingRefresh(Block transmitter, long endsAtMillis) {
    if (transmitter == null || transmitter.getWorld() == null || endsAtMillis <= 0L) {
      return;
    }
    BlockKey key = BlockKey.of(transmitter);
    ScheduledRefresh existing = chargingRefreshTasks.get(key);
    if (existing != null
        && Math.abs(existing.endsAtMillis() - endsAtMillis) <= REFRESH_END_TOLERANCE_MS) {
      return;
    }
    cancelChargingRefresh(key);
    BukkitScheduler scheduler = Bukkit.getScheduler();
    if (scheduler == null) {
      return;
    }
    long delayMs = Math.max(50L, endsAtMillis - System.currentTimeMillis() + 50L);
    long delayTicks = Math.max(1L, (delayMs + 49L) / 50L);
    int taskId =
        scheduler.scheduleSyncDelayedTask(
            plugin,
            () -> {
              chargingRefreshTasks.remove(key);
              Block block = loadedBlock(key);
              if (block == null) {
                return;
              }
              visualState(block);
              transmitterRefresh.accept(block);
            },
            Math.min(delayTicks, Integer.MAX_VALUE));
    if (taskId != -1) {
      chargingRefreshTasks.put(key, new ScheduledRefresh(taskId, endsAtMillis));
    }
  }

  private void cancelChargingRefresh(BlockKey key) {
    ScheduledRefresh existing = chargingRefreshTasks.remove(key);
    if (existing == null) {
      return;
    }
    BukkitScheduler scheduler = Bukkit.getScheduler();
    if (scheduler != null) {
      scheduler.cancelTask(existing.taskId());
    }
  }

  private void cancelChargingRefreshTasks() {
    for (ScheduledRefresh refresh : new ArrayList<>(chargingRefreshTasks.values())) {
      BukkitScheduler scheduler = Bukkit.getScheduler();
      if (scheduler != null) {
        scheduler.cancelTask(refresh.taskId());
      }
    }
    chargingRefreshTasks.clear();
  }

  private Block loadedBlock(BlockKey key) {
    World world = Bukkit.getWorld(key.world());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      return null;
    }
    return world.getBlockAt(key.x(), key.y(), key.z());
  }

  private record BlockKey(UUID world, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  private record ScheduledRefresh(int taskId, long endsAtMillis) {}
}
