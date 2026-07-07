package com.zxcmc.exort.wireless.transmitter;

import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.gui.GuiOverlayConfig;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.text.ExortText;
import com.zxcmc.exort.text.GuiOverlayGlyphs;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class TransmitterSessionManager {
  private static final long REFRESH_TICKS = 40L;

  private final Plugin plugin;
  private final WirelessTransmitterService transmitterService;
  private final WirelessTerminalService wirelessService;
  private final Lang lang;
  private final PlayerFeedback playerFeedback;
  private final RegionProtection regionProtection;
  private final BooleanSupplier resourceMode;
  private final Supplier<GuiOverlayConfig> overlayConfigSource;
  private final Map<UUID, TransmitterSession> byPlayer = new HashMap<>();
  private final Map<BlockKey, TransmitterSession> byBlock = new HashMap<>();
  private GuiOverlayConfig overlayConfig;
  private int refreshTaskId = -1;

  public TransmitterSessionManager(
      Plugin plugin,
      WirelessTransmitterService transmitterService,
      WirelessTerminalService wirelessService,
      Lang lang,
      PlayerFeedback playerFeedback,
      RegionProtection regionProtection,
      BooleanSupplier resourceMode,
      Supplier<GuiOverlayConfig> overlayConfigSource) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.transmitterService = Objects.requireNonNull(transmitterService, "transmitterService");
    this.wirelessService = Objects.requireNonNull(wirelessService, "wirelessService");
    this.lang = Objects.requireNonNull(lang, "lang");
    this.playerFeedback = Objects.requireNonNull(playerFeedback, "playerFeedback");
    this.regionProtection = Objects.requireNonNull(regionProtection, "regionProtection");
    this.resourceMode = Objects.requireNonNull(resourceMode, "resourceMode");
    this.overlayConfigSource = Objects.requireNonNull(overlayConfigSource, "overlayConfigSource");
    this.overlayConfig = overlayConfigSource.get();
  }

  public void reconfigure() {
    overlayConfig = overlayConfigSource.get();
    for (TransmitterSession session : new ArrayList<>(byPlayer.values())) {
      session.render();
    }
  }

  Plugin plugin() {
    return plugin;
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
  }

  public void dropStoredTerminal(Block transmitter) {
    if (transmitter == null) {
      return;
    }
    closeSessionsForTransmitter(transmitter);
    TransmitterStoredTerminal.take(
            plugin, transmitter, wirelessService::isWireless, message -> ExortLog.warn(message))
        .ifPresent(
            stored -> {
              ItemStack drop = wirelessService.extractFromStorage(stored);
              var dropped =
                  transmitter
                      .getWorld()
                      .dropItem(transmitter.getLocation().add(0.5, 0.5, 0.5), drop);
              dropped.setVelocity(new Vector(0, 0, 0));
            });
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
                  for (TransmitterSession session : new ArrayList<>(byPlayer.values())) {
                    if (!session.viewer().isOnline()) {
                      forceCloseSession(session.viewer());
                      continue;
                    }
                    session.render();
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

  private record BlockKey(UUID world, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }
}
