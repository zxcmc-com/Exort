package com.zxcmc.exort.core.breaking;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.StorageCoreMarker;
import com.zxcmc.exort.core.marker.StorageMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.marker.WireMarker;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CustomBlockBreaker implements Listener, Runnable {
  private static final int MAX_SWING_TICKS = 5;
  private static final double DEFAULT_REACH = 5.0;

  private final ExortPlugin plugin;
  private final BlockBreakHandler breakHandler;
  private final BreakConfig breakConfig;
  private final BreakSoundService soundService;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material busCarrier;
  private final BreakSessionManager sessionManager = new BreakSessionManager();
  private int taskId = -1;
  private long tick = 0;

  public CustomBlockBreaker(
      ExortPlugin plugin,
      BlockBreakHandler breakHandler,
      BreakConfig breakConfig,
      BreakSoundConfig soundConfig,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material busCarrier) {
    this.plugin = plugin;
    this.breakHandler = breakHandler;
    this.breakConfig = breakConfig;
    this.soundService = new BreakSoundService(soundConfig);
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.busCarrier = busCarrier;
  }

  public void start() {
    if (taskId != -1) return;
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 1L, 1L);
  }

  public void shutdown() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    sessionManager.clear();
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onBlockDamage(BlockDamageEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    BreakType type = resolveType(block);
    if (type == BreakType.NONE) return;
    if (!plugin.getRegionProtection().canBreak(player, block)) {
      event.setCancelled(true);
      return;
    }
    event.setCancelled(true);

    if (player.getGameMode() == GameMode.CREATIVE) {
      stopBreaking(player);
      soundService.playBreak(block, type);
      breakHandler.handleBreak(player, block, false);
      return;
    }

    BreakSettings settings = settingsFor(type);
    startBreaking(player, block, type, settings);
  }

  @EventHandler(ignoreCancelled = true)
  public void onSwing(PlayerAnimationEvent event) {
    Player player = event.getPlayer();
    BreakSessionManager.BlockKey key = sessionManager.getPlayerSession(player.getUniqueId());
    Block target = player.getTargetBlockExact((int) DEFAULT_REACH, FluidCollisionMode.NEVER);
    if (target == null) {
      stopBreaking(player);
      return;
    }
    if (key == null) {
      return;
    }
    BreakSessionManager.BreakSession session = sessionManager.getSession(key);
    if (session == null) return;
    if (target.getWorld() != session.block.getWorld()
        || target.getX() != session.block.getX()
        || target.getY() != session.block.getY()
        || target.getZ() != session.block.getZ()) {
      stopBreaking(player);
      return;
    }
    session.touch(player.getUniqueId(), tick);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    stopBreaking(event.getPlayer());
  }

  @Override
  public void run() {
    tick++;
    if (sessionManager.sessions().isEmpty()) return;

    Iterator<Map.Entry<BreakSessionManager.BlockKey, BreakSessionManager.BreakSession>> it =
        sessionManager.sessions().entrySet().iterator();
    while (it.hasNext()) {
      BreakSessionManager.BreakSession session = it.next().getValue();
      Block block = session.block;
      if (block == null || resolveType(block) != session.type) {
        sessionManager.clearSession(session);
        it.remove();
        continue;
      }
      boolean removed = false;
      double totalDamage = 0.0;
      Iterator<BreakSessionManager.PlayerState> pit = session.players.iterator();
      while (pit.hasNext()) {
        BreakSessionManager.PlayerState state = pit.next();
        Player player = Bukkit.getPlayer(state.playerId);
        if (player == null || !player.isOnline()) {
          sessionManager.clearPlayerMapping(state.playerId);
          pit.remove();
          continue;
        }
        if (!isStillBreaking(player, block, state.lastSwingTick)) {
          sessionManager.clearPlayerMapping(player.getUniqueId());
          pit.remove();
          continue;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
          breakHandler.handleBreak(player, block, false);
          sessionManager.clearSession(session);
          it.remove();
          removed = true;
          break;
        }
        totalDamage += BreakProgressCalculator.computeDamage(player, session.settings);
      }
      if (removed) continue;
      if (session.players.isEmpty()) {
        sessionManager.clearSession(session);
        it.remove();
        continue;
      }
      if (totalDamage <= 0.0) continue;
      session.progress += totalDamage;
      soundService.handleTick(
          block, session.type, session.soundTracker, tick, session.progress, totalDamage);
      if (session.progress >= 1.0) {
        Player breaker =
            session.players.isEmpty() ? null : Bukkit.getPlayer(session.players.get(0).playerId);
        if (!session.soundTracker.isBreakPlayed()) {
          soundService.playBreak(block, session.type);
          session.soundTracker.markBreakPlayed();
        }
        if (breaker != null) {
          breakHandler.handleBreak(breaker, block, false);
        }
        sessionManager.clearSession(session);
        it.remove();
      }
    }
  }

  private void startBreaking(Player player, Block block, BreakType type, BreakSettings settings) {
    BreakSessionManager.BlockKey key = BreakSessionManager.BlockKey.from(block);
    BreakSessionManager.BlockKey current = sessionManager.getPlayerSession(player.getUniqueId());
    if (current != null && current.equals(key)) {
      BreakSessionManager.BreakSession session = sessionManager.getSession(key);
      if (session != null) {
        session.touch(player.getUniqueId(), tick);
        return;
      }
    }
    stopBreaking(player);
    BreakSessionManager.BreakSession session = sessionManager.getOrCreate(block, type, settings);
    boolean firstPlayer = session.players.isEmpty();
    sessionManager.attachPlayer(key, player.getUniqueId(), tick);
    if (firstPlayer && soundService.enabled()) {
      soundService.playHit(block, type);
      session.soundTracker.markHitAt(tick);
    }
  }

  private void stopBreaking(Player player) {
    sessionManager.detachPlayer(player.getUniqueId());
  }

  private boolean isStillBreaking(Player player, Block block, long lastSwingTick) {
    if (tick - lastSwingTick > MAX_SWING_TICKS) return false;
    Block target = player.getTargetBlockExact((int) DEFAULT_REACH, FluidCollisionMode.NEVER);
    if (target == null) return false;
    if (target.getWorld() != block.getWorld()) return false;
    if (target.getX() != block.getX()
        || target.getY() != block.getY()
        || target.getZ() != block.getZ()) return false;
    return true;
  }

  private BreakType resolveType(Block block) {
    if (block == null) return BreakType.NONE;
    if (MonitorMarker.isMonitor(plugin, block) && Carriers.matchesCarrier(block, terminalCarrier)) {
      return BreakType.MONITOR;
    }
    if (TerminalMarker.isTerminal(plugin, block)
        && Carriers.matchesCarrier(block, terminalCarrier)) {
      return BreakType.TERMINAL;
    }
    if (BusMarker.isBus(plugin, block) && Carriers.matchesCarrier(block, busCarrier)) {
      return BreakType.BUS;
    }
    if (StorageMarker.get(plugin, block).isPresent()
        && Carriers.matchesCarrier(block, storageCarrier)) {
      return BreakType.STORAGE;
    }
    if (StorageCoreMarker.isCore(plugin, block) && Carriers.matchesCarrier(block, storageCarrier)) {
      return BreakType.STORAGE;
    }
    if (WireMarker.isWire(plugin, block) && Carriers.matchesCarrier(block, wireMaterial)) {
      if (wireMaterial == Carriers.CARRIER_BARRIER) {
        return BreakType.WIRE;
      }
      return BreakType.NONE;
    }
    return BreakType.NONE;
  }

  private BreakSettings settingsFor(BreakType type) {
    return switch (type) {
      case STORAGE -> breakConfig.storage();
      case TERMINAL -> breakConfig.terminal();
      case MONITOR -> breakConfig.monitor();
      case BUS -> breakConfig.bus();
      case WIRE -> breakConfig.wire();
      default -> breakConfig.storage();
    };
  }
}
