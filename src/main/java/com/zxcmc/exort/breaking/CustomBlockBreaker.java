package com.zxcmc.exort.breaking;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CustomBlockBreaker
    implements Listener, Runnable, PacketEnhancements.CustomBreakingController {
  private static final int MAX_SWING_TICKS = 5;
  private static final double DEFAULT_REACH = 5.0;

  private final Plugin plugin;
  private final RegionProtection regionProtection;
  private final WorldEditWandGuard worldEditWandGuard;
  private final BlockBreakHandler breakHandler;
  private final BreakConfig breakConfig;
  private final BreakSoundService soundService;
  private final BreakAnimationSender breakAnimationSender;
  private final ClientBreakSpeedSuppressor clientBreakSpeedSuppressor;
  private final Material wireMaterial;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final Material relayCarrier;
  private final BreakSessionManager sessionManager = new BreakSessionManager();
  private final Map<UUID, DamageIntent> vanillaDamageIntents = new HashMap<>();
  private final Map<UUID, Long> rightClickTicks = new HashMap<>();
  private final Map<UUID, Long> pendingSwingTicks = new HashMap<>();
  private int taskId = -1;
  private long tick = 0;

  public CustomBlockBreaker(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      BlockBreakHandler breakHandler,
      BreakConfig breakConfig,
      BreakSoundConfig soundConfig,
      BreakAnimationSender breakAnimationSender,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier) {
    this(
        plugin,
        regionProtection,
        worldEditWandGuard,
        breakHandler,
        breakConfig,
        soundConfig,
        breakAnimationSender,
        wireMaterial,
        storageCarrier,
        terminalCarrier,
        monitorCarrier,
        busCarrier,
        relayCarrier,
        new ClientBreakSpeedSuppressor(plugin));
  }

  CustomBlockBreaker(
      Plugin plugin,
      RegionProtection regionProtection,
      WorldEditWandGuard worldEditWandGuard,
      BlockBreakHandler breakHandler,
      BreakConfig breakConfig,
      BreakSoundConfig soundConfig,
      BreakAnimationSender breakAnimationSender,
      Material wireMaterial,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier,
      Material relayCarrier,
      ClientBreakSpeedSuppressor clientBreakSpeedSuppressor) {
    this.plugin = plugin;
    this.regionProtection = regionProtection;
    this.worldEditWandGuard = worldEditWandGuard;
    this.breakHandler = breakHandler;
    this.breakConfig = breakConfig;
    this.soundService = new BreakSoundService(soundConfig);
    this.breakAnimationSender =
        breakAnimationSender == null ? BreakAnimationSender.NOOP : breakAnimationSender;
    this.clientBreakSpeedSuppressor = clientBreakSpeedSuppressor;
    this.wireMaterial = wireMaterial;
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.relayCarrier = relayCarrier;
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
    clearAllBreakAnimations();
    clientBreakSpeedSuppressor.clearAll();
    sessionManager.clear();
    vanillaDamageIntents.clear();
    rightClickTicks.clear();
    pendingSwingTicks.clear();
  }

  public void handlePlacementGuardAttack(Player player, Block block) {
    if (player == null || block == null) return;
    if (isWorldEditWand(player, player.getInventory().getItemInMainHand())) {
      stopBreaking(player);
      return;
    }
    BreakType type = resolveType(block);
    if (type == BreakType.NONE) {
      stopBreaking(player);
      return;
    }
    tryStartOrContinueBreaking(player, block, type);
  }

  @Override
  public boolean handleDestroyStart(Player player, Block block, int sequence) {
    if (player == null || block == null) {
      return false;
    }
    BreakType type = resolveType(block);
    if (type == BreakType.NONE) {
      stopBreaking(player);
      return false;
    }
    if (isCurrentSession(player, block)) {
      clearVanillaDamageIntent(player);
      updateClientBreakSpeedSuppression(player, type);
      return true;
    }
    tryStartOrContinueBreaking(player, block, type);
    return true;
  }

  @Override
  public boolean handleDestroyAbort(Player player, Block block, int sequence) {
    if (player == null || block == null) {
      return false;
    }
    boolean currentSession = isCurrentSession(player, block);
    if (!currentSession && resolveType(block) == BreakType.NONE) {
      return false;
    }
    clearVanillaDamageIntent(player);
    stopBreaking(player);
    return true;
  }

  @Override
  public boolean handleDestroyFinish(Player player, Block block, int sequence) {
    if (player == null || block == null) {
      return false;
    }
    return isCurrentSession(player, block) || resolveType(block) != BreakType.NONE;
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onBlockDamage(BlockDamageEvent event) {
    Block block = event.getBlock();
    BreakType type = resolveType(block);
    if (type == BreakType.NONE) {
      rememberVanillaDamageIntent(event.getPlayer(), block);
      return;
    }
    if (isWorldEditWand(event.getPlayer(), event.getItemInHand())) {
      stopBreaking(event.getPlayer());
      return;
    }
    clearVanillaDamageIntent(event.getPlayer());
    event.setInstaBreak(false);
    event.setCancelled(true);
    tryStartOrContinueBreaking(event.getPlayer(), block, type);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBlockDamageAbort(BlockDamageAbortEvent event) {
    handleDestroyAbort(event.getPlayer(), event.getBlock(), 0);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onCreativeBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (player.getGameMode() != GameMode.CREATIVE) {
      return;
    }
    Block block = event.getBlock();
    BreakType type = resolveType(block);
    if (type == BreakType.NONE) {
      return;
    }
    if (isWorldEditWand(player, player.getInventory().getItemInMainHand())) {
      stopBreaking(player);
      return;
    }
    clearVanillaDamageIntentIfSource(player, block);
    stopBreaking(player);
    event.setDropItems(false);
    event.setExpToDrop(0);
    event.setCancelled(true);
    breakCreative(player, block, type);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void onCreativeLeftClick(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
    Player player = event.getPlayer();
    if (player.getGameMode() != GameMode.CREATIVE) return;
    Block block = event.getClickedBlock();
    BreakType type = resolveType(block);
    if (type == BreakType.NONE) return;
    if (isWorldEditWand(player, player.getInventory().getItemInMainHand())) {
      stopBreaking(player);
      return;
    }
    event.setCancelled(true);
    event.setUseInteractedBlock(Result.DENY);
    event.setUseItemInHand(Result.DENY);
    breakCreative(player, block, type);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onBlockBreak(BlockBreakEvent event) {
    clearVanillaDamageIntentIfSource(event.getPlayer(), event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (!event.getAction().isRightClick()) return;
    rememberRightClick(event.getPlayer());
    clearVanillaDamageIntent(event.getPlayer());
    stopBreaking(event.getPlayer());
  }

  @EventHandler(ignoreCancelled = true)
  public void onSwing(PlayerArmSwingEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    pendingSwingTicks.put(event.getPlayer().getUniqueId(), tick);
  }

  private boolean tryStartOrContinueBreaking(Player player) {
    if (isWorldEditWand(player, player.getInventory().getItemInMainHand())) {
      stopBreaking(player);
      return false;
    }
    Block target = player.getTargetBlockExact((int) DEFAULT_REACH, FluidCollisionMode.NEVER);
    if (target == null) {
      stopBreaking(player);
      return false;
    }
    BreakType type = resolveType(target);
    if (type == BreakType.NONE) {
      stopBreaking(player);
      return false;
    }
    return tryStartOrContinueBreaking(player, target, type);
  }

  private boolean tryStartOrContinueBreaking(Player player, Block block, BreakType type) {
    if (isWorldEditWand(player, player.getInventory().getItemInMainHand())) {
      stopBreaking(player);
      return false;
    }
    clearVanillaDamageIntent(player);
    if (!regionProtection.canBreak(player, block)) {
      stopBreaking(player);
      return false;
    }
    if (player.getGameMode() == GameMode.CREATIVE) {
      breakCreative(player, block, type);
      return true;
    }
    startBreaking(player, block, type, settingsFor(type));
    return true;
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    stopBreaking(event.getPlayer());
    clearVanillaDamageIntent(event.getPlayer());
    rightClickTicks.remove(event.getPlayer().getUniqueId());
    pendingSwingTicks.remove(event.getPlayer().getUniqueId());
  }

  @Override
  public void run() {
    tick++;
    expireTransientPlayerState();
    processPendingSwings();
    if (sessionManager.sessions().isEmpty()) return;

    Iterator<Map.Entry<BreakSessionManager.BlockKey, BreakSessionManager.BreakSession>> it =
        sessionManager.sessions().entrySet().iterator();
    while (it.hasNext()) {
      BreakSessionManager.BreakSession session = it.next().getValue();
      Block block = session.block;
      if (block == null || resolveType(block) != session.type) {
        clearBreakAnimation(session);
        clearSession(session);
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
          clientBreakSpeedSuppressor.forget(state.playerId);
          sessionManager.clearPlayerMapping(state.playerId);
          pit.remove();
          continue;
        }
        if (!isStillBreaking(player, block, state.lastSwingTick)) {
          clientBreakSpeedSuppressor.clear(player);
          sessionManager.clearPlayerMapping(player.getUniqueId());
          pit.remove();
          continue;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
          breakHandler.handleBreak(player, block, false);
          clearBreakAnimation(session);
          clearSession(session);
          it.remove();
          removed = true;
          break;
        }
        double damage = BreakProgressCalculator.computeDamage(player, session.settings);
        if (BreakTimingPolicy.canApplyDamage(tick, state.startedTick, damage)) {
          totalDamage += damage;
        }
      }
      if (removed) continue;
      if (session.players.isEmpty()) {
        clearBreakAnimation(session);
        clearSession(session);
        it.remove();
        continue;
      }
      if (totalDamage <= 0.0) continue;
      double nextProgress = session.progress + totalDamage;
      if (nextProgress >= 1.0) {
        session.progress = nextProgress;
        Player breaker =
            session.players.isEmpty() ? null : Bukkit.getPlayer(session.players.get(0).playerId);
        BlockBreakHandler.BreakResult result =
            breaker == null
                ? BlockBreakHandler.BreakResult.IGNORED
                : breakHandler.handleBreak(breaker, block, false);
        if (result == BlockBreakHandler.BreakResult.BROKEN
            && !session.soundTracker.isBreakPlayed()) {
          soundService.playBreak(block, session.type);
          session.soundTracker.markBreakPlayed();
        }
        clearBreakAnimation(session);
        clearSession(session);
        it.remove();
        continue;
      }
      session.progress = nextProgress;
      if (updateBreakAnimation(session) && shouldPlayHitSound(session)) {
        soundService.playHit(block, session.type);
        markHitSoundPlayed(session);
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
        updateClientBreakSpeedSuppression(player, type);
        return;
      }
    }
    stopBreaking(player);
    boolean newSession = sessionManager.getSession(key) == null;
    sessionManager.getOrCreate(block, type, settings);
    if (newSession && type == BreakType.STORAGE) {
      breakHandler.preloadStorageForBreakStart(player, block);
    }
    sessionManager.attachPlayer(key, player.getUniqueId(), tick);
    updateClientBreakSpeedSuppression(player, type);
  }

  private void stopBreaking(Player player) {
    clientBreakSpeedSuppressor.clear(player);
    BreakSessionManager.BreakSession removed = sessionManager.detachPlayer(player.getUniqueId());
    if (removed != null) {
      clearBreakAnimation(removed);
    }
  }

  private void breakCreative(Player player, Block block, BreakType type) {
    clearVanillaDamageIntent(player);
    stopBreaking(player);
    Sound carrierBreakSound = CarrierBreakSoundSuppressor.breakSound(block);
    CarrierBreakSoundSuppressor.stop(player, carrierBreakSound);
    if (!regionProtection.canBreak(player, block)) {
      return;
    }
    if (breakHandler.handleBreak(player, block, false) == BlockBreakHandler.BreakResult.BROKEN) {
      playCreativeBreakSound(player, block, type, carrierBreakSound);
    }
  }

  private void playCreativeBreakSound(
      Player player, Block block, BreakType type, Sound carrierBreakSound) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (player != null && player.isOnline()) {
                CarrierBreakSoundSuppressor.stop(player, carrierBreakSound);
              }
              soundService.playBreak(block, type);
            });
  }

  private void clearSession(BreakSessionManager.BreakSession session) {
    if (session == null) return;
    for (BreakSessionManager.PlayerState state : session.players) {
      Player player = Bukkit.getPlayer(state.playerId);
      if (player != null && player.isOnline()) {
        clientBreakSpeedSuppressor.clear(player);
      } else {
        clientBreakSpeedSuppressor.forget(state.playerId);
      }
    }
    sessionManager.clearSession(session);
  }

  private void updateClientBreakSpeedSuppression(Player player, BreakType type) {
    if (ClientBreakSpeedSuppressor.shouldSuppress(wireMaterial, type)) {
      clientBreakSpeedSuppressor.apply(player);
    } else {
      clientBreakSpeedSuppressor.clear(player);
    }
  }

  private void rememberVanillaDamageIntent(Player player, Block block) {
    if (player == null || block == null || block.getType().isAir()) return;
    vanillaDamageIntents.put(
        player.getUniqueId(), new DamageIntent(block, block.getType(), tick, tick));
  }

  private boolean touchVanillaDamageIntent(Player player) {
    UUID playerId = player.getUniqueId();
    DamageIntent intent = vanillaDamageIntents.get(playerId);
    if (intent == null) return false;
    if (!intent.isActive(tick)) {
      vanillaDamageIntents.remove(playerId);
      return false;
    }
    intent.lastSwingTick = tick;
    return tick > intent.startedTick;
  }

  private void clearVanillaDamageIntent(Player player) {
    if (player != null) {
      vanillaDamageIntents.remove(player.getUniqueId());
    }
  }

  private void clearVanillaDamageIntentIfSource(Player player, Block block) {
    if (player == null || block == null) return;
    UUID playerId = player.getUniqueId();
    DamageIntent intent = vanillaDamageIntents.get(playerId);
    if (intent != null && intent.isSource(block)) {
      vanillaDamageIntents.remove(playerId);
    }
  }

  private boolean isRightClickSwing(Player player) {
    Long rightClickTick = rightClickTicks.get(player.getUniqueId());
    return rightClickTick != null && tick - rightClickTick <= 1;
  }

  void rememberRightClick(Player player) {
    if (player != null) {
      rightClickTicks.put(player.getUniqueId(), tick);
    }
  }

  boolean processSwing(Player player) {
    if (player == null || !player.isOnline()) return false;
    if (isRightClickSwing(player)) return false;
    boolean hasSession = sessionManager.getPlayerSession(player.getUniqueId()) != null;
    boolean canRetarget = touchVanillaDamageIntent(player);
    if (!hasSession && !canRetarget && player.getGameMode() != GameMode.SURVIVAL) return false;
    return tryStartOrContinueBreaking(player);
  }

  private void processPendingSwings() {
    Iterator<Map.Entry<UUID, Long>> it = pendingSwingTicks.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Long> entry = it.next();
      it.remove();
      if (tick - entry.getValue() > MAX_SWING_TICKS) continue;
      Player player = Bukkit.getPlayer(entry.getKey());
      processSwing(player);
    }
  }

  private void expireTransientPlayerState() {
    vanillaDamageIntents.entrySet().removeIf(entry -> !entry.getValue().isActive(tick));
    rightClickTicks.entrySet().removeIf(entry -> tick - entry.getValue() > MAX_SWING_TICKS);
    pendingSwingTicks.entrySet().removeIf(entry -> tick - entry.getValue() > MAX_SWING_TICKS);
  }

  private boolean updateBreakAnimation(BreakSessionManager.BreakSession session) {
    int stage = BreakAnimationStages.stageForProgress(session.progress);
    if (!session.hasFollowUpSwing() && stage > 0) {
      stage = 0;
    }
    if (stage == session.lastBreakAnimationStage) {
      return false;
    }
    session.lastBreakAnimationStage = stage;
    breakAnimationSender.show(session.block, session.type, stage / 10.0);
    return true;
  }

  private boolean shouldPlayHitSound(BreakSessionManager.BreakSession session) {
    return BreakTimingPolicy.canPlayHitSound(tick, session.lastHitSoundTick);
  }

  private void markHitSoundPlayed(BreakSessionManager.BreakSession session) {
    session.lastHitSoundTick = tick;
  }

  private void clearBreakAnimation(BreakSessionManager.BreakSession session) {
    if (session == null || session.lastBreakAnimationStage == Integer.MIN_VALUE) {
      return;
    }
    breakAnimationSender.clear(session.block);
    session.lastBreakAnimationStage = Integer.MIN_VALUE;
  }

  private void clearAllBreakAnimations() {
    for (BreakSessionManager.BreakSession session : sessionManager.sessions().values()) {
      clearBreakAnimation(session);
    }
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

  boolean isCurrentSession(Player player, Block block) {
    BreakSessionManager.BlockKey current = sessionManager.getPlayerSession(player.getUniqueId());
    return current != null && current.equals(BreakSessionManager.BlockKey.from(block));
  }

  private boolean isWorldEditWand(Player player, ItemStack item) {
    return worldEditWandGuard.isWorldEditWand(player, item);
  }

  BreakType resolveType(Block block) {
    if (block == null) return BreakType.NONE;
    if (Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block)) {
      return BreakType.MONITOR;
    }
    if (Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block)) {
      return BreakType.TERMINAL;
    }
    if (Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block)) {
      return BreakType.BUS;
    }
    if (Carriers.matchesCarrier(block, relayCarrier) && RelayMarker.isRelay(plugin, block)) {
      return BreakType.RELAY;
    }
    if (Carriers.matchesCarrier(block, storageCarrier)
        && StorageMarker.get(plugin, block).isPresent()) {
      return BreakType.STORAGE;
    }
    if (Carriers.matchesCarrier(block, storageCarrier) && StorageCoreMarker.isCore(plugin, block)) {
      return BreakType.STORAGE;
    }
    if (Carriers.matchesCarrier(block, wireMaterial) && WireMarker.isWire(plugin, block)) {
      return BreakType.WIRE;
    }
    return BreakType.NONE;
  }

  private BreakSettings settingsFor(BreakType type) {
    return switch (type) {
      case STORAGE -> breakConfig.storage();
      case TERMINAL -> breakConfig.terminal();
      case MONITOR -> breakConfig.monitor();
      case BUS -> breakConfig.bus();
      case RELAY -> breakConfig.relay();
      case WIRE -> breakConfig.wire();
      default -> breakConfig.storage();
    };
  }

  private static final class DamageIntent {
    private final Block block;
    private final Material material;
    private final long startedTick;
    private long lastSwingTick;

    private DamageIntent(Block block, Material material, long startedTick, long lastSwingTick) {
      this.block = block;
      this.material = material;
      this.startedTick = startedTick;
      this.lastSwingTick = lastSwingTick;
    }

    private boolean isActive(long now) {
      return now - lastSwingTick <= MAX_SWING_TICKS && block.getType() == material;
    }

    private boolean isSource(Block other) {
      if (other == null || other.getWorld() != block.getWorld()) return false;
      return other.getX() == block.getX()
          && other.getY() == block.getY()
          && other.getZ() == block.getZ();
    }
  }
}
