package com.zxcmc.exort.display.culling;

import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.display.core.DisplayEntityIndex;
import com.zxcmc.exort.display.core.DisplayMetadataNormalizer;
import com.zxcmc.exort.display.core.DisplayMetadataService;
import com.zxcmc.exort.display.core.DisplayRole;
import com.zxcmc.exort.display.proxy.ExortBlockProxyService;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protocol.PacketEnhancements;
import com.zxcmc.exort.text.ExortText;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DisplayCullingService implements Listener {
  private static final long CLIENT_CULLING_CACHE_TTL_SECONDS = 12L * 60L * 60L;
  private static final int DEBUG_SAMPLE_LIMIT = 8;
  private static final double PAPER_SHOW_DISTANCE_BUFFER_BLOCKS = 4.0;
  private static final double MOTION_SAMPLE_MIN_DISTANCE_SQUARED = 0.36;
  private static final double MOTION_SAME_DIRECTION_DOT = 0.35;
  private static final double MOTION_DIRECTION_CHANGE_DISTANCE_BLOCKS = 4.0;
  private static final double MOTION_FORWARD_BACK_BUFFER_BLOCKS = 4.0;
  private static final long DEBUG_NORMAL_SUMMARY_INTERVAL_TICKS = 40L;
  private static final long DEBUG_COMPACT_SUMMARY_INTERVAL_TICKS = 200L;
  private static final int GLOBAL_SCAN_WORK_PER_TICK = 4096;
  private static final int PLAYER_SCAN_WORK_PER_SLICE = 512;
  private static final int PLAYER_SLICES_PER_TICK =
      GLOBAL_SCAN_WORK_PER_TICK / PLAYER_SCAN_WORK_PER_SLICE;

  private final JavaPlugin plugin;
  private final DisplayCullingConfig config;
  private final PacketEnhancements packetEnhancements;
  private final DisplayEntityIndex index;
  private final DisplayMetadataService metadataService;
  private final ExortBlockProxyService blockProxyService;
  private final Database database;
  private final Map<UUID, Map<UUID, TrackedDisplay>> trackedByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, AdaptiveViewRangeState> adaptiveStates = new ConcurrentHashMap<>();
  private final Map<UUID, PlayerMotionState> motionStates = new ConcurrentHashMap<>();
  private final Set<UUID> clientCullingBypass = ConcurrentHashMap.newKeySet();
  private final Set<UUID> autoClientCullingBypass = ConcurrentHashMap.newKeySet();
  private final Set<UUID> clientCullingAutoSuppressed = ConcurrentHashMap.newKeySet();
  private final Map<UUID, ClientCullingProbeStatus> clientCullingProbeStatuses =
      new ConcurrentHashMap<>();
  private final Map<UUID, Database.ClientCullingState> clientCullingDbStates =
      new ConcurrentHashMap<>();
  private final Set<UUID> debugViewers = ConcurrentHashMap.newKeySet();
  private final DebugSummary debugSummary = new DebugSummary();
  private final Object debugSummaryLock = new Object();
  private final Map<UUID, PlayerScan> playerScans = new HashMap<>();
  private final Map<UUID, PlayerCullingStats> playerStats = new HashMap<>();
  private final RoundRobinWorkQueue<UUID> scanQueue = new RoundRobinWorkQueue<>();
  private final RoundRobinWorkQueue<UUID> bypassQueue = new RoundRobinWorkQueue<>();
  private final Set<UUID> onlinePlayers = new HashSet<>();
  private final Set<UUID> onlineBypassPlayers = new HashSet<>();
  private final VisibilityMutationGuard visibilityMutationGuard = new VisibilityMutationGuard();
  private final CullingTotals totals = new CullingTotals();
  private final ClientCullingTranslationProbe translationProbe;
  private DisplayCullingBackend backend;
  private int taskId = -1;
  private int debugSummaryTaskId = -1;
  private long debugSummaryTaskIntervalTicks = -1L;
  private long clientStateLifecycleGeneration;
  private long clientStateMutationRevision;
  private volatile boolean running;
  private volatile boolean debugConsoleExplicit;
  private volatile DebugMode debugMode = DebugMode.NORMAL;
  private long serverTickSequence;
  private int visibilityChangesThisTick;
  private int rangeChangesThisTick;
  private int paperHiddenThisTick;
  private int adaptiveSkipsThisTick;
  private int directionalKeepsThisTick;

  public DisplayCullingService(
      JavaPlugin plugin,
      DisplayCullingConfig config,
      PacketEnhancements packetEnhancements,
      DisplayEntityIndex index,
      DisplayMetadataService metadataService,
      ExortBlockProxyService blockProxyService,
      Database database) {
    this.plugin = plugin;
    this.config = config;
    this.packetEnhancements = packetEnhancements;
    this.index = index;
    this.metadataService = metadataService;
    this.blockProxyService = blockProxyService;
    this.database = database;
    this.translationProbe =
        new ClientCullingTranslationProbe(
            plugin,
            config.clientCullingBypass().translationProbe(),
            this::cachedClientProbeStatus,
            this::recordClientProbeStatus);
  }

  public void start() {
    if (!config.enabled() || taskId != -1) {
      return;
    }
    running = true;
    long lifecycleGeneration = ++clientStateLifecycleGeneration;
    loadPersistentClientCullingStates(lifecycleGeneration, clientStateMutationRevision);
    backend = createBackend();
    normalizeAndIndexLoadedDisplays();
    Bukkit.getPluginManager().registerEvents(this, plugin);
    translationProbe.start();
    for (Player player : Bukkit.getOnlinePlayers()) {
      translationProbe.schedule(player);
      registerOnlinePlayer(player);
    }
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 1L, 1L);
    ExortLog.info("[Display] Density culling enabled using " + backend.name() + " backend.");
  }

  public void stop() {
    running = false;
    clientStateLifecycleGeneration++;
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      persistClientCullingLastSeen(player.getUniqueId());
    }
    translationProbe.stop();
    HandlerList.unregisterAll(this);
    restoreAll();
    visibilityMutationGuard.clear();
    clearDebug();
    trackedByPlayer.clear();
    adaptiveStates.clear();
    motionStates.clear();
    playerScans.clear();
    playerStats.clear();
    scanQueue.clear();
    bypassQueue.clear();
    onlinePlayers.clear();
    onlineBypassPlayers.clear();
    totals.clear();
    autoClientCullingBypass.clear();
    clientCullingAutoSuppressed.clear();
    clientCullingProbeStatuses.clear();
    if (index != null) {
      index.clear();
    }
    PerfStats.setGauge("display.entities", 0L);
    PerfStats.setGauge("display.culling.scanWork", 0L);
    PerfStats.setGauge("display.culling.pendingPlayers", 0L);
  }

  public DebugMode getDebugMode() {
    return debugMode;
  }

  public boolean isDebugEnabled() {
    return debugConsoleExplicit || !debugViewers.isEmpty();
  }

  public ClientCullingBypassStatus clientCullingBypassStatus(UUID playerId) {
    boolean manualListed = playerId != null && clientCullingBypass.contains(playerId);
    boolean autoDetected = playerId != null && autoClientCullingBypass.contains(playerId);
    boolean autoSuppressed = playerId != null && clientCullingAutoSuppressed.contains(playerId);
    ClientCullingProbeStatus probeStatus =
        playerId == null
            ? defaultProbeStatus()
            : clientCullingProbeStatuses.getOrDefault(playerId, defaultProbeStatus());
    return new ClientCullingBypassStatus(
        config.clientCullingBypass().enabled(),
        manualListed,
        autoDetected,
        autoSuppressed,
        probeStatus);
  }

  public ClientCullingBypassStatus setClientCullingBypass(UUID playerId, boolean enabled) {
    if (playerId == null) {
      return clientCullingBypassStatus(null);
    }
    if (enabled) {
      clientCullingBypass.add(playerId);
      clientCullingAutoSuppressed.remove(playerId);
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        activateClientCullingBypass(player);
      }
    } else {
      clientCullingBypass.remove(playerId);
      autoClientCullingBypass.remove(playerId);
      clientCullingAutoSuppressed.add(playerId);
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        leaveClientBypass(player);
      }
    }
    persistClientCullingManualBypass(playerId, enabled);
    return clientCullingBypassStatus(playerId);
  }

  public void startDebug(CommandSender sender, DebugMode mode) {
    if (mode != null) {
      debugMode = mode;
    }
    if (sender instanceof Player player) {
      debugViewers.add(player.getUniqueId());
    } else {
      debugConsoleExplicit = true;
    }
    updateDebugSummaryTask();
  }

  public void stopDebug(CommandSender sender) {
    if (sender instanceof Player player) {
      debugViewers.remove(player.getUniqueId());
    } else {
      debugConsoleExplicit = false;
    }
    updateDebugSummaryTask();
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    if (event != null && event.getPlayer() != null) {
      translationProbe.schedule(event.getPlayer());
      registerOnlinePlayer(event.getPlayer());
    }
  }

  @EventHandler
  public void onTrack(PlayerTrackEntityEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    Entity entity = event.getEntity();
    if (entity != null
        && visibilityMutationGuard.contains(
            event.getPlayer().getUniqueId(), entity.getUniqueId())) {
      return;
    }
    if (entity instanceof Display display && isCullableDisplay(display)) {
      metadataService.normalize(display);
      if (!isClientCullingBypassed(event.getPlayer().getUniqueId())) {
        track(event.getPlayer(), display, true);
        applyTrackedDisplayImmediately(event.getPlayer(), display);
      }
    }
  }

  @EventHandler
  public void onUntrack(PlayerUntrackEntityEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    Entity entity = event.getEntity();
    if (entity != null
        && visibilityMutationGuard.contains(
            event.getPlayer().getUniqueId(), entity.getUniqueId())) {
      return;
    }
    if (entity instanceof Display display) {
      untrack(event.getPlayer(), display.getUniqueId());
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    if (event != null && event.getPlayer() != null) {
      UUID playerId = event.getPlayer().getUniqueId();
      persistClientCullingLastSeen(playerId);
      translationProbe.cancel(playerId);
      trackedByPlayer.remove(playerId);
      adaptiveStates.remove(playerId);
      motionStates.remove(playerId);
      autoClientCullingBypass.remove(playerId);
      clientCullingAutoSuppressed.remove(playerId);
      clientCullingProbeStatuses.remove(playerId);
      debugViewers.remove(playerId);
      unregisterOnlinePlayer(playerId);
      updateDebugSummaryTask();
    }
  }

  private DisplayCullingBackend createBackend() {
    if (config.backend() != DisplayCullingConfig.Backend.PAPER && packetEnhancements != null) {
      PacketEnhancements.DisplayCullingPackets packets =
          packetEnhancements.tryCreateDisplayCullingPackets();
      if (packets != null) {
        return new PacketEventsDisplayCullingBackend(packets);
      }
      if (config.backend() == DisplayCullingConfig.Backend.PACKET_EVENTS) {
        ExortLog.warn("[Display] PacketEvents culling unavailable; falling back to Paper API.");
      }
    }
    return new PaperDisplayCullingBackend(plugin);
  }

  private void normalizeAndIndexLoadedDisplays() {
    if (metadataService == null) {
      return;
    }
    int normalized = 0;
    for (World world : Bukkit.getWorlds()) {
      for (Display display : world.getEntitiesByClass(Display.class)) {
        if (!isCullableDisplay(display)) {
          continue;
        }
        metadataService.normalize(display);
        normalized++;
      }
    }
    PerfStats.setGauge("display.metadata.normalizedLoaded", normalized);
  }

  private boolean isClientCullingBypassed(UUID playerId) {
    return playerId != null
        && config.clientCullingBypass().enabled()
        && (clientCullingBypass.contains(playerId)
            || (autoClientCullingBypass.contains(playerId)
                && !clientCullingAutoSuppressed.contains(playerId)));
  }

  private ClientCullingProbeStatus defaultProbeStatus() {
    return config.clientCullingBypass().translationProbe().enabled()
        ? ClientCullingProbeStatus.notRun()
        : ClientCullingProbeStatus.disabled();
  }

  private void loadPersistentClientCullingStates(long lifecycleGeneration, long mutationRevision) {
    if (database == null) {
      return;
    }
    database
        .loadClientCullingStates()
        .whenComplete(
            (states, error) -> {
              if (error != null) {
                plugin
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "Failed to load client culling bypass states",
                        ExortLog.unwrap(error));
                return;
              }
              try {
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () ->
                            installPersistentClientCullingStates(
                                states, lifecycleGeneration, mutationRevision));
              } catch (RuntimeException e) {
                if (running) {
                  plugin
                      .getLogger()
                      .log(Level.WARNING, "Failed to install client culling bypass states", e);
                }
              }
            });
  }

  private void installPersistentClientCullingStates(
      Map<UUID, Database.ClientCullingState> states,
      long lifecycleGeneration,
      long mutationRevision) {
    if (!canInstallPersistentClientCullingStates(
        running,
        lifecycleGeneration,
        clientStateLifecycleGeneration,
        mutationRevision,
        clientStateMutationRevision)) {
      return;
    }
    clientCullingDbStates.clear();
    clientCullingDbStates.putAll(states);
    clientCullingBypass.clear();
    for (Database.ClientCullingState state : states.values()) {
      if (state.manualBypass()) {
        clientCullingBypass.add(state.playerId());
      }
    }
  }

  static boolean canInstallPersistentClientCullingStates(
      boolean running,
      long expectedLifecycleGeneration,
      long currentLifecycleGeneration,
      long expectedMutationRevision,
      long currentMutationRevision) {
    return running
        && expectedLifecycleGeneration == currentLifecycleGeneration
        && expectedMutationRevision == currentMutationRevision;
  }

  private ClientCullingProbeStatus cachedClientProbeStatus(Player player, String brand) {
    if (player == null || brand == null || brand.isBlank()) {
      return null;
    }
    Database.ClientCullingState state = clientCullingDbStates.get(player.getUniqueId());
    long now = Instant.now().getEpochSecond();
    if (state == null || !state.hasFreshMatch(brand, now, CLIENT_CULLING_CACHE_TTL_SECONDS)) {
      return null;
    }
    long ageSeconds = Math.max(0L, now - state.lastSeenAt());
    return ClientCullingProbeStatus.cachedMatch(brand, ageSeconds);
  }

  private void recordClientProbeStatus(Player player, ClientCullingProbeStatus status) {
    if (player == null || status == null) {
      return;
    }
    UUID playerId = player.getUniqueId();
    clientCullingProbeStatuses.put(playerId, status);
    if (status.state() == ClientCullingProbeState.MATCH
        || status.state() == ClientCullingProbeState.CACHED_MATCH) {
      if (status.state() == ClientCullingProbeState.MATCH) {
        persistClientCullingProbeResult(playerId, status);
      }
      if (!clientCullingAutoSuppressed.contains(playerId)
          && autoClientCullingBypass.add(playerId)) {
        activateClientCullingBypass(player);
      }
      return;
    }
    if (status.state().terminal()) {
      persistClientCullingProbeResult(playerId, status);
    }
    if (status.state().terminal()
        && autoClientCullingBypass.remove(playerId)
        && !clientCullingBypass.contains(playerId)) {
      leaveClientBypass(player);
    }
  }

  private void persistClientCullingManualBypass(UUID playerId, boolean enabled) {
    if (playerId == null) {
      return;
    }
    clientStateMutationRevision++;
    long now = Instant.now().getEpochSecond();
    clientCullingDbStates.compute(
        playerId,
        (ignored, state) ->
            (state == null ? Database.ClientCullingState.empty(playerId) : state)
                .withManualBypass(enabled, now));
    if (database != null) {
      database.setClientCullingManualBypass(playerId, enabled);
    }
  }

  private void persistClientCullingProbeResult(UUID playerId, ClientCullingProbeStatus status) {
    if (playerId == null || status == null) {
      return;
    }
    clientStateMutationRevision++;
    boolean match = status.state() == ClientCullingProbeState.MATCH;
    long now = Instant.now().getEpochSecond();
    clientCullingDbStates.compute(
        playerId,
        (ignored, state) ->
            (state == null ? Database.ClientCullingState.empty(playerId) : state)
                .withProbeResult(status.state().name(), status.brand(), match, now));
    if (database != null) {
      database.recordClientCullingProbeResult(
          playerId, status.state().name(), status.brand(), match);
    }
  }

  private void persistClientCullingLastSeen(UUID playerId) {
    if (playerId == null) {
      return;
    }
    clientStateMutationRevision++;
    long now = Instant.now().getEpochSecond();
    clientCullingDbStates.compute(
        playerId,
        (ignored, state) ->
            (state == null ? Database.ClientCullingState.empty(playerId) : state)
                .withLastSeen(now));
    if (database != null) {
      database.updateClientCullingLastSeen(playerId);
    }
  }

  private void activateClientCullingBypass(Player player) {
    if (player == null) {
      return;
    }
    UUID playerId = player.getUniqueId();
    adaptiveStates.remove(playerId);
    motionStates.remove(playerId);
    removePlayerStats(playerId);
    if (onlinePlayers.contains(playerId)) {
      onlineBypassPlayers.add(playerId);
      bypassQueue.add(playerId);
    }
    Map<UUID, TrackedDisplay> states = trackedByPlayer.get(playerId);
    if (states == null || states.isEmpty()) {
      trackedByPlayer.remove(playerId);
      playerScans.remove(playerId);
      scanQueue.remove(playerId);
      return;
    }
    PlayerScan scan = playerScans.computeIfAbsent(playerId, ignored -> new PlayerScan());
    scan.beginRestore(states);
    scanQueue.add(playerId);
  }

  private void leaveClientBypass(Player player) {
    if (player == null) {
      return;
    }
    UUID playerId = player.getUniqueId();
    onlineBypassPlayers.remove(playerId);
    bypassQueue.remove(playerId);
    playerScans.computeIfAbsent(playerId, ignored -> new PlayerScan()).nextDueTick = 0L;
    scanQueue.add(playerId);
  }

  private void registerOnlinePlayer(Player player) {
    if (player == null) {
      return;
    }
    UUID playerId = player.getUniqueId();
    onlinePlayers.add(playerId);
    scanQueue.add(playerId);
    if (isClientCullingBypassed(playerId)) {
      onlineBypassPlayers.add(playerId);
      activateClientCullingBypass(player);
    } else {
      onlineBypassPlayers.remove(playerId);
      bypassQueue.remove(playerId);
      playerScans.computeIfAbsent(playerId, ignored -> new PlayerScan()).nextDueTick = 0L;
    }
  }

  private void unregisterOnlinePlayer(UUID playerId) {
    if (playerId == null) {
      return;
    }
    onlinePlayers.remove(playerId);
    onlineBypassPlayers.remove(playerId);
    scanQueue.remove(playerId);
    bypassQueue.remove(playerId);
    playerScans.remove(playerId);
    trackedByPlayer.remove(playerId);
    adaptiveStates.remove(playerId);
    motionStates.remove(playerId);
    removePlayerStats(playerId);
  }

  private void tick() {
    PerfStats.measure("display.culling.tick", this::tickMeasured);
  }

  private void tickMeasured() {
    serverTickSequence++;
    visibilityChangesThisTick = 0;
    rangeChangesThisTick = 0;
    paperHiddenThisTick = 0;
    adaptiveSkipsThisTick = 0;
    directionalKeepsThisTick = 0;
    int staleRestored = 0;
    int scanWork = 0;
    int completedScans = 0;
    int resetScans = 0;
    processClientBypassPlayers();
    int slices = Math.min(PLAYER_SLICES_PER_TICK, scanQueue.size());
    for (int slice = 0; slice < slices && scanWork < GLOBAL_SCAN_WORK_PER_TICK; slice++) {
      UUID playerId = scanQueue.next();
      Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline() || !onlinePlayers.contains(playerId)) {
        unregisterOnlinePlayer(playerId);
        continue;
      }
      if (isClientCullingBypassed(playerId)) {
        if (onlineBypassPlayers.add(playerId)) {
          activateClientCullingBypass(player);
        }
        PlayerScan scan = playerScans.get(playerId);
        if (scan != null && scan.stage == ScanStage.RESTORE) {
          RestoreSliceResult restoreResult =
              processPlayerRestoreSlice(
                  player,
                  scan,
                  Math.min(PLAYER_SCAN_WORK_PER_SLICE, GLOBAL_SCAN_WORK_PER_TICK - scanWork));
          scanWork += restoreResult.work();
          staleRestored += restoreResult.restored();
          if (restoreResult.complete()) {
            finishClientBypassRestore(playerId);
          }
        } else {
          scanQueue.remove(playerId);
        }
        continue;
      }
      onlineBypassPlayers.remove(playerId);
      bypassQueue.remove(playerId);
      PlayerScan scan = playerScans.computeIfAbsent(playerId, ignored -> new PlayerScan());
      if (scan.stage == ScanStage.IDLE && serverTickSequence < scan.nextDueTick) {
        continue;
      }
      ScanSliceResult result =
          processPlayerSlice(
              player,
              scan,
              Math.min(PLAYER_SCAN_WORK_PER_SLICE, GLOBAL_SCAN_WORK_PER_TICK - scanWork));
      scanWork += result.work();
      staleRestored += result.staleRestored();
      completedScans += result.completedScans();
      resetScans += result.resetScans();
    }

    PerfStats.addCounter("display.culling.scanExamined", scanWork);
    PerfStats.addCounter("display.culling.scanCompleted", completedScans);
    PerfStats.addCounter("display.culling.scanReset", resetScans);
    PerfStats.setGauge("display.culling.scanWork", scanWork);
    PerfStats.setGauge("display.culling.pendingPlayers", scanQueue.size());
    PerfStats.setGauge("display.entities", totals.tracked);
    PerfStats.setGauge("display.culling.nearby", totals.nearby);
    PerfStats.setGauge("display.culling.hiddenActive", totals.hidden);
    PerfStats.setGauge("display.culling.visibilityChanges", visibilityChangesThisTick);
    PerfStats.setGauge("display.culling.rangeChanges", rangeChangesThisTick);
    PerfStats.setGauge("display.culling.paperHidden", paperHiddenThisTick);
    PerfStats.setGauge("display.culling.adaptiveSkips", adaptiveSkipsThisTick);
    PerfStats.setGauge("display.culling.directionalKeeps", directionalKeepsThisTick);
    PerfStats.setGauge("display.culling.adaptiveLevel", totals.maxAdaptiveLevel());
    PerfStats.setGauge("display.culling.clientBypassPlayers", onlineBypassPlayers.size());
    recordDebugTick(
        new TickCullingStats(
            onlinePlayers.size(),
            onlineBypassPlayers.size(),
            totals.nearby,
            totals.tracked,
            totals.hidden,
            totals.dirty,
            staleRestored,
            visibilityChangesThisTick,
            rangeChangesThisTick,
            paperHiddenThisTick,
            adaptiveSkipsThisTick,
            directionalKeepsThisTick,
            totals.maxAdaptiveLevel(),
            totals.roleSnapshot()));
  }

  private void processClientBypassPlayers() {
    int slices = Math.min(PLAYER_SLICES_PER_TICK, bypassQueue.size());
    for (int slice = 0; slice < slices; slice++) {
      UUID playerId = bypassQueue.next();
      Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
      if (player == null
          || !player.isOnline()
          || !onlinePlayers.contains(playerId)
          || !isClientCullingBypassed(playerId)) {
        bypassQueue.remove(playerId);
        onlineBypassPlayers.remove(playerId);
        continue;
      }
      processBlockProxies(player, 1.0);
    }
  }

  private ScanSliceResult processPlayerSlice(Player player, PlayerScan scan, int budget) {
    if (budget <= 0) {
      return ScanSliceResult.EMPTY;
    }
    Map<UUID, TrackedDisplay> states =
        trackedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
    int work = 0;
    int staleRestored = 0;
    int completedScans = 0;
    int resetScans = 0;
    while (work < budget) {
      if (scan.stage == ScanStage.IDLE) {
        startPlayerScan(player, scan);
      }
      if ((scan.stage == ScanStage.QUERY || scan.stage == ScanStage.STALE)
          && scan.movedSection(player)) {
        scan.beginRestore(states);
        removePlayerStats(player.getUniqueId());
        resetScans++;
      }
      if (scan.stage == ScanStage.QUERY) {
        int remaining = budget - work;
        DisplayEntityIndex.QueryStep step =
            scan.cursor.advance(remaining, entry -> processCandidate(player, states, scan, entry));
        work += step.examined();
        if (!step.complete()) {
          break;
        }
        finishQuery(player, states, scan);
        continue;
      }
      if (scan.stage == ScanStage.STALE || scan.stage == ScanStage.RESTORE) {
        if (scan.stateIterator.hasNext()) {
          Map.Entry<UUID, TrackedDisplay> entry = scan.stateIterator.next();
          work++;
          if (scan.stage == ScanStage.RESTORE) {
            Display display = index.resolve(entry.getKey());
            if (display != null) {
              forceShow(player, display, entry.getValue());
            }
            states.remove(entry.getKey(), entry.getValue());
            staleRestored++;
          } else {
            staleRestored += processTrackedState(player, states, scan, entry);
          }
          continue;
        }
        if (scan.stage == ScanStage.RESTORE) {
          scan.stage = ScanStage.IDLE;
          scan.nextDueTick = 0L;
          continue;
        }
        completePlayerScan(player.getUniqueId(), states, scan);
        completedScans++;
        break;
      }
    }
    return new ScanSliceResult(work, staleRestored, completedScans, resetScans);
  }

  private RestoreSliceResult processPlayerRestoreSlice(Player player, PlayerScan scan, int budget) {
    if (budget <= 0) {
      return RestoreSliceResult.EMPTY;
    }
    Map<UUID, TrackedDisplay> states = trackedByPlayer.get(player.getUniqueId());
    if (states == null || states.isEmpty()) {
      return new RestoreSliceResult(0, 0, true);
    }
    if (scan.stage != ScanStage.RESTORE || scan.stateIterator == null) {
      scan.beginRestore(states);
    }
    int work = 0;
    int restored = 0;
    while (work < budget && scan.stateIterator.hasNext()) {
      Map.Entry<UUID, TrackedDisplay> entry = scan.stateIterator.next();
      work++;
      Display display = index.resolve(entry.getKey());
      if (display != null) {
        forceShow(player, display, entry.getValue());
      }
      states.remove(entry.getKey(), entry.getValue());
      restored++;
    }
    return new RestoreSliceResult(work, restored, !scan.stateIterator.hasNext());
  }

  private void finishClientBypassRestore(UUID playerId) {
    trackedByPlayer.remove(playerId);
    playerScans.remove(playerId);
    scanQueue.remove(playerId);
    PerfStats.incrementCounter("display.culling.clientBypassRestores");
  }

  private void startPlayerScan(Player player, PlayerScan scan) {
    Location origin = player.getLocation();
    AdaptiveViewRangeState adaptiveState =
        adaptiveStates.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new AdaptiveViewRangeState(config.adaptiveViewRange()));
    int rangeLevel = effectiveRangeLevel(adaptiveState);
    MotionSnapshot motion =
        motionStates
            .computeIfAbsent(player.getUniqueId(), ignored -> new PlayerMotionState())
            .sample(origin);
    scan.beginQuery(origin, index.openQuery(origin, config.maxDistance()), rangeLevel, motion);
    processBlockProxies(player, blockProxyRangeMultiplier(rangeLevel));
  }

  private void processCandidate(
      Player player,
      Map<UUID, TrackedDisplay> states,
      PlayerScan scan,
      DisplayEntityIndex.Entry entry) {
    Display display = index.resolve(entry.entityUuid());
    if (display == null || !isCullableDisplay(display)) {
      index.unregister(entry.entityUuid());
      return;
    }
    TrackedDisplay tracked =
        states.computeIfAbsent(
            entry.entityUuid(), ignored -> new TrackedDisplay(entry.role(), true));
    if (tracked.lastSeenGeneration == scan.generation) {
      return;
    }
    tracked.lastSeenGeneration = scan.generation;
    tracked.role = entry.role();
    scan.nearby++;
    incrementRole(scan.roles, entry.role(), 1);
    applyDensityDecision(
        player, scan.origin, display, tracked, scan.rangeLevel, scan.motionSnapshot);
  }

  private void finishQuery(Player player, Map<UUID, TrackedDisplay> states, PlayerScan scan) {
    AdaptiveViewRangeState adaptiveState =
        adaptiveStates.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new AdaptiveViewRangeState(config.adaptiveViewRange()));
    scan.markAllDirty =
        adaptiveState.update(scan.nearby, ++scan.completedCycles, config.intervalTicks());
    if (scan.markAllDirty) {
      PerfStats.incrementCounter("display.culling.adaptiveLevelChanged");
    }
    scan.beginStale(states);
  }

  private int processTrackedState(
      Player player,
      Map<UUID, TrackedDisplay> states,
      PlayerScan scan,
      Map.Entry<UUID, TrackedDisplay> entry) {
    TrackedDisplay tracked = entry.getValue();
    if (scan.markAllDirty) {
      tracked.markDirty();
    }
    if (tracked.lastSeenGeneration != scan.generation) {
      Display display = index.resolve(entry.getKey());
      if (display == null || !shouldKeepManagedOutsideScan(player, display, tracked)) {
        if (display != null) {
          forceShow(player, display, tracked);
        }
        states.remove(entry.getKey(), tracked);
        scan.staleRestored++;
        return 1;
      }
    }
    scan.tracked++;
    if (tracked.hidden) {
      scan.hidden++;
    }
    if (tracked.dirty) {
      scan.dirty++;
    }
    return 0;
  }

  private void completePlayerScan(
      UUID playerId, Map<UUID, TrackedDisplay> states, PlayerScan scan) {
    PlayerCullingStats stats =
        new PlayerCullingStats(
            scan.nearby,
            scan.tracked,
            scan.hidden,
            scan.dirty,
            scan.staleRestored,
            scan.rangeLevel,
            scan.roles.isEmpty() ? Map.of() : Map.copyOf(scan.roles));
    PlayerCullingStats previous = playerStats.put(playerId, stats);
    totals.replace(previous, stats);
    if (states.isEmpty()) {
      trackedByPlayer.remove(playerId, states);
    }
    scan.complete(serverTickSequence + config.intervalTicks());
  }

  private void removePlayerStats(UUID playerId) {
    PlayerCullingStats previous = playerStats.remove(playerId);
    totals.replace(previous, null);
  }

  private void applyDensityDecision(
      Player player,
      Location origin,
      Display display,
      TrackedDisplay tracked,
      int rangeLevel,
      MotionSnapshot motion) {
    DisplayRole role = tracked.role;
    Location displayLocation = display.getLocation();
    double distanceSquared = safeDistanceSquared(origin, displayLocation);
    boolean forceVisible = distanceSquared <= square(config.forceVisibleDistance());
    int roleLevel = forceVisible ? 0 : rangeLevelForRole(rangeLevel);
    roleLevel = applyForwardRetention(origin, displayLocation, tracked, motion, roleLevel);
    double multiplier = config.adaptiveViewRange().rangeMultiplier(role, roleLevel);
    float effectiveViewRange =
        (float) Math.max(0.05, DisplayMetadataNormalizer.BASE_VIEW_RANGE * multiplier);
    boolean hide =
        shouldHideForDensitySquared(
            backend.supportsPerPlayerViewRange(),
            roleLevel > 0,
            role,
            forceVisible,
            tracked.hidden,
            distanceSquared,
            config.maxDistance(),
            multiplier);
    if (hide) {
      adaptiveSkipsThisTick++;
    }
    applyVisibility(player, display, tracked, hide, effectiveViewRange, roleLevel);
    if (role == DisplayRole.BLOCK) {
      processBlockDisplayDecision(player, displayLocation, tracked);
    }
  }

  private void applyTrackedDisplayImmediately(Player player, Display display) {
    if (backend == null || player == null || display == null || !display.isValid()) {
      return;
    }
    Map<UUID, TrackedDisplay> states = trackedByPlayer.get(player.getUniqueId());
    if (states == null) {
      return;
    }
    TrackedDisplay tracked = states.get(display.getUniqueId());
    if (tracked == null) {
      return;
    }
    Location origin = player.getLocation();
    AdaptiveViewRangeState adaptiveState =
        adaptiveStates.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new AdaptiveViewRangeState(config.adaptiveViewRange()));
    PlayerMotionState motionState = motionStates.get(player.getUniqueId());
    applyDensityDecision(
        player,
        origin,
        display,
        tracked,
        effectiveRangeLevel(adaptiveState),
        motionState == null ? MotionSnapshot.inactive() : motionState.snapshot());
  }

  private boolean shouldKeepManagedOutsideScan(
      Player player, Display display, TrackedDisplay tracked) {
    if (player == null || display == null || tracked == null) {
      return false;
    }
    double distanceSquared = safeDistanceSquared(player.getLocation(), display.getLocation());
    if (distanceSquared <= square(config.maxDistance())) {
      return false;
    }
    float baseViewRange = metadataService.baseViewRange(tracked.role);
    return tracked.hidden
        || (tracked.sentViewRange != null && tracked.sentViewRange < baseViewRange - 0.001f);
  }

  private void track(Player player, Display display, boolean dirtyExisting) {
    if (player == null || display == null || !display.isValid()) {
      return;
    }
    DisplayRole role = DisplayRole.fromTags(display.getScoreboardTags());
    if (role == null) {
      return;
    }
    Map<UUID, TrackedDisplay> states =
        trackedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
    PlayerScan scan = playerScans.get(player.getUniqueId());
    long currentGeneration =
        scan == null || scan.stage == ScanStage.IDLE ? Long.MIN_VALUE : scan.generation;
    states.compute(
        display.getUniqueId(),
        (ignored, current) -> {
          if (current == null) {
            TrackedDisplay tracked = new TrackedDisplay(role, true);
            tracked.clientTracked = true;
            tracked.lastSeenGeneration = currentGeneration;
            return tracked;
          }
          current.role = role;
          current.clientTracked = true;
          current.lastSeenGeneration = currentGeneration;
          if (dirtyExisting) {
            current.markDirty();
          }
          return current;
        });
  }

  private void untrack(Player player, UUID displayId) {
    Map<UUID, TrackedDisplay> states = trackedByPlayer.get(player.getUniqueId());
    if (states == null) {
      return;
    }
    TrackedDisplay tracked = states.remove(displayId);
    if (tracked != null) {
      Display display = Bukkit.getEntity(displayId) instanceof Display d ? d : null;
      if (display != null) {
        forceShow(player, display, tracked);
      }
    }
    if (states.isEmpty()) {
      trackedByPlayer.remove(player.getUniqueId());
    }
  }

  private void applyVisibility(
      Player player,
      Display display,
      TrackedDisplay tracked,
      boolean hide,
      float effectiveViewRange,
      int roleLevel) {
    boolean rangeChanged =
        backend.supportsPerPlayerViewRange()
            && (tracked.sentViewRange == null
                || Math.abs(tracked.sentViewRange - effectiveViewRange) > 0.001f);
    if (tracked.hidden == hide && !tracked.dirty && !rangeChanged) {
      return;
    }
    if (visibilityChangesThisTick >= config.maxVisibilityChangesPerTick()) {
      PerfStats.incrementCounter("display.culling.budgetOverrun");
      return;
    }
    boolean ok =
        visibilityMutationGuard.run(
            player.getUniqueId(),
            display.getUniqueId(),
            () ->
                hide
                    ? backend.hide(player, display, effectiveViewRange)
                    : backend.show(player, display, effectiveViewRange));
    if (!ok) {
      return;
    }
    visibilityChangesThisTick++;
    if (rangeChanged) {
      rangeChangesThisTick++;
      tracked.sentViewRange = effectiveViewRange;
      PerfStats.incrementCounter("display.culling.rangeChanges");
    }
    if (hide) {
      paperHiddenThisTick++;
    }
    tracked.hidden = hide;
    tracked.dirty = false;
    tracked.clientTracked = tracked.clientTracked || !hide;
    tracked.appliedRoleLevel = roleLevel;
    if (!hide && !backend.supportsPerPlayerViewRange()) {
      metadataService.resync(display);
    }
    PerfStats.incrementCounter(hide ? "display.culling.hidden" : "display.culling.shown");
  }

  private void forceShow(Player player, Display display, TrackedDisplay tracked) {
    if (backend == null || player == null || display == null || tracked == null) {
      return;
    }
    float viewRange = metadataService.baseViewRange(tracked.role);
    if (tracked.hidden
        || tracked.dirty
        || tracked.sentViewRange == null
        || Math.abs(tracked.sentViewRange - viewRange) > 0.001f) {
      boolean shown =
          visibilityMutationGuard.run(
              player.getUniqueId(),
              display.getUniqueId(),
              () -> backend.show(player, display, viewRange));
      if (!shown) {
        return;
      }
      if (!backend.supportsPerPlayerViewRange()) {
        metadataService.resync(display);
      }
      tracked.hidden = false;
      tracked.dirty = false;
      tracked.sentViewRange = viewRange;
      tracked.appliedRoleLevel = 0;
      PerfStats.incrementCounter("display.culling.forceShown");
    }
  }

  private void restoreAll() {
    if (backend == null) {
      return;
    }
    for (var playerEntry : trackedByPlayer.entrySet()) {
      Player player = Bukkit.getPlayer(playerEntry.getKey());
      if (player == null) {
        continue;
      }
      for (var displayEntry : playerEntry.getValue().entrySet()) {
        Display display = Bukkit.getEntity(displayEntry.getKey()) instanceof Display d ? d : null;
        if (display != null) {
          forceShow(player, display, displayEntry.getValue());
        }
      }
    }
    normalizeAndIndexLoadedDisplays();
  }

  private static boolean isLowPriority(DisplayRole role) {
    return role == DisplayRole.WIRE
        || role == DisplayRole.MONITOR_CONTENT
        || role == DisplayRole.HOLOGRAM;
  }

  private double blockProxyRangeMultiplier(int rangeLevel) {
    if (backend == null || !backend.supportsPerPlayerViewRange()) {
      return 1.0;
    }
    return config
        .adaptiveViewRange()
        .rangeMultiplier(DisplayRole.BLOCK, rangeLevelForRole(rangeLevel));
  }

  private void processBlockProxies(Player player, double viewRangeMultiplier) {
    if (blockProxyService != null) {
      blockProxyService.processPlayer(player, viewRangeMultiplier);
    }
  }

  private void processBlockDisplayDecision(
      Player player, Location displayLocation, TrackedDisplay tracked) {
    if (blockProxyService == null || player == null || displayLocation == null || tracked == null) {
      return;
    }
    double multiplier = 1.0;
    if (backend != null && backend.supportsPerPlayerViewRange()) {
      float baseViewRange = Math.max(0.05f, metadataService.baseViewRange(DisplayRole.BLOCK));
      float activeViewRange = tracked.sentViewRange == null ? baseViewRange : tracked.sentViewRange;
      multiplier = Math.max(0.05, activeViewRange / baseViewRange);
    }
    blockProxyService.updateBlockDisplayDecision(player, displayLocation, multiplier);
  }

  private int effectiveRangeLevel(AdaptiveViewRangeState adaptiveState) {
    return adaptiveState == null ? 0 : adaptiveState.levelIndex();
  }

  private int rangeLevelForRole(int rangeLevel) {
    return Math.max(0, Math.min(rangeLevel, config.adaptiveViewRange().maxLevel()));
  }

  private int applyForwardRetention(
      Location origin,
      Location displayLocation,
      TrackedDisplay tracked,
      MotionSnapshot motion,
      int targetRoleLevel) {
    if (targetRoleLevel <= 0) {
      if (tracked != null) {
        tracked.clearForwardRetention();
      }
      return targetRoleLevel;
    }
    if (tracked == null
        || !tracked.clientTracked
        || tracked.hidden
        || motion == null
        || !motion.active()
        || displayLocation == null) {
      return targetRoleLevel;
    }
    double projection = motion.forwardProjection(origin, displayLocation);
    if (Double.isNaN(projection)) {
      return targetRoleLevel;
    }
    if (projection < -MOTION_FORWARD_BACK_BUFFER_BLOCKS) {
      tracked.clearForwardRetention();
      return targetRoleLevel;
    }
    int observedRoleLevel = tracked.appliedRoleLevel >= 0 ? tracked.appliedRoleLevel : 0;
    if (tracked.retainedMotionSegment != motion.segmentId()) {
      tracked.retainedMotionSegment = motion.segmentId();
      tracked.retainedRoleLevel = observedRoleLevel;
    } else {
      tracked.retainedRoleLevel = Math.min(tracked.retainedRoleLevel, observedRoleLevel);
    }
    int retainedRoleLevel = Math.max(0, tracked.retainedRoleLevel);
    int roleLevel = Math.min(targetRoleLevel, retainedRoleLevel);
    if (roleLevel < targetRoleLevel) {
      directionalKeepsThisTick++;
    }
    return roleLevel;
  }

  static boolean shouldHideForDensity(
      boolean supportsPerPlayerViewRange,
      boolean adaptiveActive,
      DisplayRole role,
      boolean forceVisible,
      boolean currentlyHidden,
      double distance,
      double maxDistance,
      double multiplier) {
    return shouldHideForDensitySquared(
        supportsPerPlayerViewRange,
        adaptiveActive,
        role,
        forceVisible,
        currentlyHidden,
        square(distance),
        maxDistance,
        multiplier);
  }

  static boolean shouldHideForDensitySquared(
      boolean supportsPerPlayerViewRange,
      boolean adaptiveActive,
      DisplayRole role,
      boolean forceVisible,
      boolean currentlyHidden,
      double distanceSquared,
      double maxDistance,
      double multiplier) {
    if (supportsPerPlayerViewRange || !adaptiveActive || !isLowPriority(role) || forceVisible) {
      return false;
    }
    double hideDistance = maxDistance * multiplier;
    if (!currentlyHidden) {
      return distanceSquared > square(hideDistance);
    }
    double showDistance = Math.max(0.0, hideDistance - PAPER_SHOW_DISTANCE_BUFFER_BLOCKS);
    return distanceSquared > square(showDistance);
  }

  private static double safeDistance(Location first, Location second) {
    double distanceSquared = safeDistanceSquared(first, second);
    return Double.isInfinite(distanceSquared)
        ? Double.POSITIVE_INFINITY
        : Math.sqrt(distanceSquared);
  }

  private static double safeDistanceSquared(Location first, Location second) {
    if (first == null
        || second == null
        || first.getWorld() == null
        || !first.getWorld().equals(second.getWorld())) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.max(0.0, first.distanceSquared(second));
  }

  private static double square(double value) {
    if (Double.isInfinite(value)) {
      return Double.POSITIVE_INFINITY;
    }
    if (Double.isNaN(value)) {
      return Double.NaN;
    }
    return value * value;
  }

  private void recordDebugTick(TickCullingStats stats) {
    if (!isDebugEnabled()) {
      return;
    }
    if (debugMode != DebugMode.FULL) {
      synchronized (debugSummaryLock) {
        debugSummary.record(stats);
      }
      return;
    }
    sendDebug(formatDebugTick(stats));
    for (Player player : Bukkit.getOnlinePlayers()) {
      for (DebugDisplaySample sample : debugSamples(player)) {
        sendDebug(formatDebugSample(player, sample));
      }
    }
  }

  private List<DebugDisplaySample> debugSamples(Player player) {
    Location origin = player.getLocation();
    Map<UUID, TrackedDisplay> states = trackedByPlayer.get(player.getUniqueId());
    if (origin.getWorld() == null || states == null || states.isEmpty()) {
      return List.of();
    }
    return states.entrySet().stream()
        .map(entry -> debugSample(origin, entry.getKey(), entry.getValue()))
        .filter(sample -> sample != null)
        .sorted((left, right) -> Double.compare(left.distance(), right.distance()))
        .limit(DEBUG_SAMPLE_LIMIT)
        .toList();
  }

  private DebugDisplaySample debugSample(Location origin, UUID displayId, TrackedDisplay tracked) {
    Display display = Bukkit.getEntity(displayId) instanceof Display d ? d : null;
    if (display == null || !display.isValid()) {
      return null;
    }
    Location location = display.getLocation();
    double distance = safeDistance(origin, location);
    String locationText =
        location.getWorld().getName()
            + " "
            + location.getBlockX()
            + " "
            + location.getBlockY()
            + " "
            + location.getBlockZ();
    return new DebugDisplaySample(
        tracked.role,
        tracked.hidden,
        tracked.dirty,
        distance,
        tracked.sentViewRange == null
            ? metadataService.baseViewRange(tracked.role)
            : tracked.sentViewRange,
        locationText,
        tagSummary(display));
  }

  private void updateDebugSummaryTask() {
    long intervalTicks = debugSummaryIntervalTicks();
    boolean shouldRun = isDebugEnabled() && intervalTicks > 0L;
    if (shouldRun && (debugSummaryTaskId == -1 || debugSummaryTaskIntervalTicks != intervalTicks)) {
      if (debugSummaryTaskId != -1) {
        Bukkit.getScheduler().cancelTask(debugSummaryTaskId);
        resetDebugSummary();
      }
      debugSummaryTaskId =
          Bukkit.getScheduler()
              .runTaskTimer(plugin, this::flushDebugSummary, intervalTicks, intervalTicks)
              .getTaskId();
      debugSummaryTaskIntervalTicks = intervalTicks;
    } else if (!shouldRun && debugSummaryTaskId != -1) {
      Bukkit.getScheduler().cancelTask(debugSummaryTaskId);
      debugSummaryTaskId = -1;
      debugSummaryTaskIntervalTicks = -1L;
      resetDebugSummary();
    }
  }

  private void flushDebugSummary() {
    long intervalTicks = debugSummaryIntervalTicks();
    if (!isDebugEnabled() || intervalTicks <= 0L) {
      return;
    }
    DebugSummary snapshot;
    synchronized (debugSummaryLock) {
      if (debugSummary.isEmpty()) {
        return;
      }
      snapshot = debugSummary.copyAndReset();
    }
    sendDebug(formatDebugSummary(snapshot, intervalTicks));
  }

  private long debugSummaryIntervalTicks() {
    return switch (debugMode) {
      case NORMAL -> DEBUG_NORMAL_SUMMARY_INTERVAL_TICKS;
      case COMPACT -> DEBUG_COMPACT_SUMMARY_INTERVAL_TICKS;
      case FULL -> -1L;
    };
  }

  private void resetDebugSummary() {
    synchronized (debugSummaryLock) {
      debugSummary.reset();
    }
  }

  private void clearDebug() {
    if (debugSummaryTaskId != -1) {
      Bukkit.getScheduler().cancelTask(debugSummaryTaskId);
      debugSummaryTaskId = -1;
      debugSummaryTaskIntervalTicks = -1L;
    }
    debugViewers.clear();
    debugConsoleExplicit = false;
    resetDebugSummary();
  }

  private void sendDebug(Component line) {
    if (line == null) {
      return;
    }
    if (debugConsoleExplicit || !debugViewers.isEmpty()) {
      Bukkit.getConsoleSender().sendMessage(line);
    }
    for (UUID viewerId : debugViewers) {
      Player player = Bukkit.getPlayer(viewerId);
      if (player == null || !player.isOnline()) {
        debugViewers.remove(viewerId);
        continue;
      }
      player.sendMessage(line);
    }
  }

  private Component formatDebugTick(TickCullingStats stats) {
    return debugPrefix()
        .append(Component.text("tick:", NamedTextColor.GOLD))
        .append(Component.text(" backend=" + backendName(), NamedTextColor.GRAY))
        .append(Component.text(" players=" + stats.players(), NamedTextColor.GRAY))
        .append(Component.text(" clientBypass=" + stats.clientBypassPlayers(), NamedTextColor.GRAY))
        .append(Component.text(" nearby=" + stats.nearby(), NamedTextColor.GRAY))
        .append(Component.text(" tracked=" + stats.tracked(), NamedTextColor.GRAY))
        .append(Component.text(" hidden=" + stats.hidden(), NamedTextColor.DARK_GRAY))
        .append(Component.text(" dirty=" + stats.dirty(), NamedTextColor.YELLOW))
        .append(Component.text(" stale=" + stats.staleRestored(), NamedTextColor.YELLOW))
        .append(
            Component.text(" changes=" + stats.visibilityChanges(), NamedTextColor.LIGHT_PURPLE))
        .append(Component.text(" rangeMeta=" + stats.rangeChanges(), NamedTextColor.AQUA))
        .append(Component.text(" paperHidden=" + stats.paperHidden(), NamedTextColor.DARK_GRAY))
        .append(Component.text(" adaptiveSkips=" + stats.adaptiveSkips(), NamedTextColor.GRAY))
        .append(Component.text(" dirKeeps=" + stats.directionalKeeps(), NamedTextColor.GRAY))
        .append(Component.text(" adaptiveLevel=" + stats.adaptiveLevel(), NamedTextColor.GRAY))
        .append(Component.text(" roles=" + rolesToString(stats.roles()), NamedTextColor.GRAY));
  }

  private Component formatDebugSummary(DebugSummary snapshot, long intervalTicks) {
    return debugPrefix()
        .append(
            Component.text(
                "summary (" + formatTickDuration(intervalTicks) + "):", NamedTextColor.GOLD))
        .append(Component.text(" ticks=" + snapshot.ticks, NamedTextColor.GRAY))
        .append(Component.text(" players=" + snapshot.maxPlayers, NamedTextColor.GRAY))
        .append(
            Component.text(" clientBypass=" + snapshot.maxClientBypassPlayers, NamedTextColor.GRAY))
        .append(Component.text(" nearby=" + snapshot.maxNearby, NamedTextColor.GRAY))
        .append(Component.text(" tracked=" + snapshot.maxTracked, NamedTextColor.GRAY))
        .append(Component.text(" hiddenMax=" + snapshot.maxHidden, NamedTextColor.DARK_GRAY))
        .append(Component.text(" stale=" + snapshot.staleRestored, NamedTextColor.YELLOW))
        .append(
            Component.text(" changes=" + snapshot.visibilityChanges, NamedTextColor.LIGHT_PURPLE))
        .append(Component.text(" rangeMeta=" + snapshot.rangeChanges, NamedTextColor.AQUA))
        .append(Component.text(" paperHidden=" + snapshot.paperHidden, NamedTextColor.DARK_GRAY))
        .append(Component.text(" adaptiveSkips=" + snapshot.adaptiveSkips, NamedTextColor.GRAY))
        .append(Component.text(" dirKeeps=" + snapshot.directionalKeeps, NamedTextColor.GRAY))
        .append(
            Component.text(" adaptiveLevelMax=" + snapshot.maxAdaptiveLevel, NamedTextColor.GRAY))
        .append(Component.text(" roles=" + rolesToString(snapshot.roles), NamedTextColor.GRAY));
  }

  private Component formatDebugSample(Player player, DebugDisplaySample sample) {
    return debugPrefix()
        .append(Component.text(player.getName() + " ", NamedTextColor.GOLD))
        .append(Component.text(sample.role().name().toLowerCase(Locale.ROOT), NamedTextColor.AQUA))
        .append(Component.text(" hidden=" + sample.hidden(), NamedTextColor.GRAY))
        .append(Component.text(" dirty=" + sample.dirty(), NamedTextColor.GRAY))
        .append(Component.text(" dist=" + formatDouble(sample.distance()), NamedTextColor.GRAY))
        .append(
            Component.text(" viewRange=" + formatDouble(sample.viewRange()), NamedTextColor.GRAY))
        .append(Component.text(" loc=" + sample.location(), NamedTextColor.DARK_GRAY))
        .append(Component.text(" tags=" + sample.tags(), NamedTextColor.DARK_GRAY));
  }

  private Component debugPrefix() {
    return Component.text("[Exort] ", ExortText.PREFIX)
        .append(Component.text("culling ", NamedTextColor.GOLD));
  }

  private String backendName() {
    return backend == null ? "inactive" : backend.name();
  }

  private static boolean isCullableDisplay(Display display) {
    if (display == null) {
      return false;
    }
    return isCullableDisplayTags(display.getScoreboardTags());
  }

  static boolean isCullableDisplayTags(Set<String> tags) {
    return DisplayRole.fromTags(tags) != null;
  }

  private static void incrementRole(Map<DisplayRole, Integer> roles, DisplayRole role, int amount) {
    if (role == null || amount <= 0) {
      return;
    }
    Integer current = roles.get(role);
    roles.put(role, current == null ? amount : current + amount);
  }

  private static String rolesToString(Map<DisplayRole, Integer> roles) {
    if (roles == null || roles.isEmpty()) {
      return "none";
    }
    List<String> parts = new ArrayList<>();
    for (DisplayRole role : DisplayRole.values()) {
      Integer count = roles.get(role);
      if (count != null && count > 0) {
        parts.add(role.name().toLowerCase(Locale.ROOT) + "=" + count);
      }
    }
    return parts.isEmpty() ? "none" : String.join(",", parts);
  }

  private static String tagSummary(Display display) {
    return display.getScoreboardTags().stream()
        .sorted()
        .limit(4)
        .reduce((left, right) -> left + "," + right)
        .orElse("none");
  }

  private static String formatDouble(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "?";
    }
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private static String formatTickDuration(long ticks) {
    if (ticks % 20L == 0L) {
      return (ticks / 20L) + "s";
    }
    return String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
  }

  public enum DebugMode {
    COMPACT,
    NORMAL,
    FULL;

    public static DebugMode fromString(String raw) {
      if (raw == null || raw.isBlank()) {
        return NORMAL;
      }
      try {
        return DebugMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
  }

  private record TickCullingStats(
      int players,
      int clientBypassPlayers,
      int nearby,
      int tracked,
      int hidden,
      int dirty,
      int staleRestored,
      int visibilityChanges,
      int rangeChanges,
      int paperHidden,
      int adaptiveSkips,
      int directionalKeeps,
      int adaptiveLevel,
      Map<DisplayRole, Integer> roles) {}

  private record PlayerCullingStats(
      int nearby,
      int tracked,
      int hidden,
      int dirty,
      int staleRestored,
      int adaptiveLevel,
      Map<DisplayRole, Integer> roles) {}

  private enum ScanStage {
    IDLE,
    QUERY,
    STALE,
    RESTORE
  }

  private record ScanSliceResult(int work, int staleRestored, int completedScans, int resetScans) {
    private static final ScanSliceResult EMPTY = new ScanSliceResult(0, 0, 0, 0);
  }

  private record RestoreSliceResult(int work, int restored, boolean complete) {
    private static final RestoreSliceResult EMPTY = new RestoreSliceResult(0, 0, false);
  }

  private static final class PlayerScan {
    private ScanStage stage = ScanStage.IDLE;
    private long nextDueTick;
    private long generation;
    private long completedCycles;
    private UUID worldId;
    private int sectionX;
    private int sectionY;
    private int sectionZ;
    private Location origin;
    private DisplayEntityIndex.QueryCursor cursor;
    private Iterator<Map.Entry<UUID, TrackedDisplay>> stateIterator;
    private MotionSnapshot motionSnapshot = MotionSnapshot.inactive();
    private int rangeLevel;
    private int nearby;
    private int tracked;
    private int hidden;
    private int dirty;
    private int staleRestored;
    private boolean markAllDirty;
    private final EnumMap<DisplayRole, Integer> roles = new EnumMap<>(DisplayRole.class);

    private void beginQuery(
        Location origin,
        DisplayEntityIndex.QueryCursor cursor,
        int rangeLevel,
        MotionSnapshot motionSnapshot) {
      stage = ScanStage.QUERY;
      generation++;
      this.origin = origin.clone();
      worldId = origin.getWorld().getUID();
      sectionX = origin.getBlockX() >> 4;
      sectionY = origin.getBlockY() >> 4;
      sectionZ = origin.getBlockZ() >> 4;
      this.cursor = cursor;
      this.rangeLevel = rangeLevel;
      this.motionSnapshot = motionSnapshot;
      stateIterator = null;
      nearby = 0;
      tracked = 0;
      hidden = 0;
      dirty = 0;
      staleRestored = 0;
      markAllDirty = false;
      roles.clear();
    }

    private boolean movedSection(Player player) {
      Location current = player.getLocation();
      return current.getWorld() == null
          || !worldId.equals(current.getWorld().getUID())
          || sectionX != (current.getBlockX() >> 4)
          || sectionY != (current.getBlockY() >> 4)
          || sectionZ != (current.getBlockZ() >> 4);
    }

    private void beginStale(Map<UUID, TrackedDisplay> states) {
      stage = ScanStage.STALE;
      stateIterator = states.entrySet().iterator();
      cursor = null;
    }

    private void beginRestore(Map<UUID, TrackedDisplay> states) {
      stage = ScanStage.RESTORE;
      stateIterator = states.entrySet().iterator();
      cursor = null;
    }

    private void complete(long nextDueTick) {
      stage = ScanStage.IDLE;
      this.nextDueTick = nextDueTick;
      origin = null;
      cursor = null;
      stateIterator = null;
      motionSnapshot = MotionSnapshot.inactive();
    }
  }

  private static final class CullingTotals {
    private int nearby;
    private int tracked;
    private int hidden;
    private int dirty;
    private final EnumMap<DisplayRole, Integer> roles = new EnumMap<>(DisplayRole.class);
    private final java.util.TreeMap<Integer, Integer> adaptiveLevels = new java.util.TreeMap<>();

    private void replace(PlayerCullingStats previous, PlayerCullingStats next) {
      add(previous, -1);
      add(next, 1);
    }

    private void add(PlayerCullingStats stats, int direction) {
      if (stats == null) {
        return;
      }
      nearby += direction * stats.nearby();
      tracked += direction * stats.tracked();
      hidden += direction * stats.hidden();
      dirty += direction * stats.dirty();
      for (var entry : stats.roles().entrySet()) {
        roles.merge(entry.getKey(), direction * entry.getValue(), Integer::sum);
        if (roles.get(entry.getKey()) == 0) {
          roles.remove(entry.getKey());
        }
      }
      adaptiveLevels.compute(
          stats.adaptiveLevel(),
          (ignored, count) -> {
            int updated = (count == null ? 0 : count) + direction;
            return updated <= 0 ? null : updated;
          });
    }

    private int maxAdaptiveLevel() {
      return adaptiveLevels.isEmpty() ? 0 : adaptiveLevels.lastKey();
    }

    private Map<DisplayRole, Integer> roleSnapshot() {
      return roles.isEmpty() ? Map.of() : Map.copyOf(roles);
    }

    private void clear() {
      nearby = 0;
      tracked = 0;
      hidden = 0;
      dirty = 0;
      roles.clear();
      adaptiveLevels.clear();
    }
  }

  public enum ClientCullingProbeState {
    DISABLED(false),
    NOT_RUN(false),
    PENDING(false),
    CACHED_MATCH(true),
    MATCH(true),
    NO_MATCH(true),
    SKIPPED(true),
    ERROR(true);

    private final boolean terminal;

    ClientCullingProbeState(boolean terminal) {
      this.terminal = terminal;
    }

    public boolean terminal() {
      return terminal;
    }
  }

  public record ClientCullingProbeStatus(
      ClientCullingProbeState state, String brand, String key, String response, String detail) {
    public static ClientCullingProbeStatus notRun() {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.NOT_RUN, "unknown", null, null, "");
    }

    public static ClientCullingProbeStatus disabled() {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.DISABLED, "unknown", null, null, "translation probe disabled");
    }

    public static ClientCullingProbeStatus pending(String brand, String detail) {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.PENDING, brand, null, null, detail);
    }

    public static ClientCullingProbeStatus match(String brand, String key, String response) {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.MATCH, brand, key, response, "EntityCulling translation matched");
    }

    public static ClientCullingProbeStatus cachedMatch(String brand, long ageSeconds) {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.CACHED_MATCH,
          brand,
          null,
          null,
          "lastSeenAgeAtProbe=" + Math.max(0L, ageSeconds) + "s");
    }

    public static ClientCullingProbeStatus noMatch(String brand, String response, String detail) {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.NO_MATCH, brand, null, response, detail);
    }

    public static ClientCullingProbeStatus skipped(String brand, String detail) {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.SKIPPED, brand, null, null, detail);
    }

    public static ClientCullingProbeStatus error(
        String brand, String key, String response, String detail) {
      return new ClientCullingProbeStatus(
          ClientCullingProbeState.ERROR, brand, key, response, detail);
    }

    public String summary() {
      String brandText = sanitize(brand);
      String detailText = sanitize(detail);
      String responseText = sanitize(response);
      return switch (state) {
        case DISABLED -> "disabled";
        case NOT_RUN -> "not-run";
        case PENDING -> "pending brand=" + brandText + " detail=" + detailText;
        case CACHED_MATCH -> "cached-match brand=" + brandText + " detail=" + detailText;
        case MATCH ->
            "match brand=" + brandText + " key=" + sanitize(key) + " response=" + responseText;
        case NO_MATCH ->
            "no-match brand=" + brandText + " response=" + responseText + " detail=" + detailText;
        case SKIPPED -> "skipped brand=" + brandText + " detail=" + detailText;
        case ERROR -> "error brand=" + brandText + " detail=" + detailText;
      };
    }

    private static String sanitize(String value) {
      if (value == null || value.isBlank()) {
        return "-";
      }
      String trimmed = value.trim().replace('\n', ' ');
      return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 77) + "...";
    }
  }

  public record ClientCullingBypassStatus(
      boolean configEnabled,
      boolean playerListed,
      boolean autoDetected,
      boolean autoSuppressed,
      ClientCullingProbeStatus probeStatus) {
    public boolean active() {
      return configEnabled && (playerListed || (autoDetected && !autoSuppressed));
    }

    public String source() {
      if (!configEnabled) {
        return "config-disabled";
      }
      if (playerListed) {
        return "manual";
      }
      if (autoSuppressed) {
        return "suppressed";
      }
      if (!autoDetected) {
        return "none";
      }
      return probeStatus != null && probeStatus.state() == ClientCullingProbeState.CACHED_MATCH
          ? "translation-probe-cache"
          : "translation-probe";
    }
  }

  private record DebugDisplaySample(
      DisplayRole role,
      boolean hidden,
      boolean dirty,
      double distance,
      double viewRange,
      String location,
      String tags) {}

  record MotionSnapshot(UUID worldId, double x, double z, boolean active, long segmentId) {
    static MotionSnapshot inactive() {
      return new MotionSnapshot(null, 0.0, 0.0, false, -1L);
    }

    double forwardProjection(Location origin, Location target) {
      if (!active
          || worldId == null
          || origin == null
          || target == null
          || origin.getWorld() == null
          || target.getWorld() == null
          || !worldId.equals(origin.getWorld().getUID())
          || !worldId.equals(target.getWorld().getUID())) {
        return Double.NaN;
      }
      return forwardProjection(worldId, origin.getX(), origin.getZ(), target.getX(), target.getZ());
    }

    boolean isForward(
        UUID currentWorldId, double originX, double originZ, double targetX, double targetZ) {
      double projection = forwardProjection(currentWorldId, originX, originZ, targetX, targetZ);
      return !Double.isNaN(projection) && projection >= -MOTION_FORWARD_BACK_BUFFER_BLOCKS;
    }

    double forwardProjection(
        UUID currentWorldId, double originX, double originZ, double targetX, double targetZ) {
      if (!active || worldId == null || currentWorldId == null || !worldId.equals(currentWorldId)) {
        return Double.NaN;
      }
      return DisplayCullingService.forwardProjection(x, z, targetX - originX, targetZ - originZ);
    }
  }

  private static final class DebugSummary {
    private long ticks;
    private int maxPlayers;
    private int maxClientBypassPlayers;
    private int maxNearby;
    private int maxTracked;
    private int maxHidden;
    private int maxAdaptiveLevel;
    private long staleRestored;
    private long visibilityChanges;
    private long rangeChanges;
    private long paperHidden;
    private long adaptiveSkips;
    private long directionalKeeps;
    private final EnumMap<DisplayRole, Integer> roles = new EnumMap<>(DisplayRole.class);

    private void record(TickCullingStats stats) {
      ticks++;
      maxPlayers = Math.max(maxPlayers, stats.players());
      maxClientBypassPlayers = Math.max(maxClientBypassPlayers, stats.clientBypassPlayers());
      maxNearby = Math.max(maxNearby, stats.nearby());
      maxTracked = Math.max(maxTracked, stats.tracked());
      maxHidden = Math.max(maxHidden, stats.hidden());
      maxAdaptiveLevel = Math.max(maxAdaptiveLevel, stats.adaptiveLevel());
      staleRestored += stats.staleRestored();
      visibilityChanges += stats.visibilityChanges();
      rangeChanges += stats.rangeChanges();
      paperHidden += stats.paperHidden();
      adaptiveSkips += stats.adaptiveSkips();
      directionalKeeps += stats.directionalKeeps();
      for (var roleEntry : stats.roles().entrySet()) {
        incrementRole(roles, roleEntry.getKey(), roleEntry.getValue());
      }
    }

    private void reset() {
      ticks = 0L;
      maxPlayers = 0;
      maxClientBypassPlayers = 0;
      maxNearby = 0;
      maxTracked = 0;
      maxHidden = 0;
      maxAdaptiveLevel = 0;
      staleRestored = 0L;
      visibilityChanges = 0L;
      rangeChanges = 0L;
      paperHidden = 0L;
      adaptiveSkips = 0L;
      directionalKeeps = 0L;
      roles.clear();
    }

    private boolean isEmpty() {
      return ticks == 0L;
    }

    private DebugSummary copyAndReset() {
      DebugSummary copy = new DebugSummary();
      copy.ticks = ticks;
      copy.maxPlayers = maxPlayers;
      copy.maxClientBypassPlayers = maxClientBypassPlayers;
      copy.maxNearby = maxNearby;
      copy.maxTracked = maxTracked;
      copy.maxHidden = maxHidden;
      copy.maxAdaptiveLevel = maxAdaptiveLevel;
      copy.staleRestored = staleRestored;
      copy.visibilityChanges = visibilityChanges;
      copy.rangeChanges = rangeChanges;
      copy.paperHidden = paperHidden;
      copy.adaptiveSkips = adaptiveSkips;
      copy.directionalKeeps = directionalKeeps;
      copy.roles.putAll(roles);
      reset();
      return copy;
    }
  }

  private static final class TrackedDisplay {
    private DisplayRole role;
    private boolean hidden;
    private boolean dirty;
    private boolean clientTracked;
    private Float sentViewRange;
    private int appliedRoleLevel = -1;
    private long retainedMotionSegment = Long.MIN_VALUE;
    private int retainedRoleLevel = -1;
    private long lastSeenGeneration = Long.MIN_VALUE;

    TrackedDisplay(DisplayRole role, boolean dirty) {
      this.role = role;
      this.dirty = dirty;
    }

    TrackedDisplay markDirty() {
      dirty = true;
      return this;
    }

    void clearForwardRetention() {
      retainedMotionSegment = Long.MIN_VALUE;
      retainedRoleLevel = -1;
    }
  }

  static final class PlayerMotionState {
    private UUID worldId;
    private double lastX;
    private double lastZ;
    private double directionX;
    private double directionZ;
    private double pendingDirectionX;
    private double pendingDirectionZ;
    private double pendingDistance;
    private boolean active;
    private long segmentId;

    MotionSnapshot sample(Location location) {
      if (location == null || location.getWorld() == null) {
        active = false;
        clearPendingDirection();
        return snapshot();
      }
      return sample(location.getWorld().getUID(), location.getX(), location.getZ());
    }

    MotionSnapshot sample(UUID currentWorldId, double x, double z) {
      if (currentWorldId == null) {
        active = false;
        clearPendingDirection();
        return snapshot();
      }
      if (worldId == null || !worldId.equals(currentWorldId)) {
        worldId = currentWorldId;
        lastX = x;
        lastZ = z;
        active = false;
        clearPendingDirection();
        segmentId++;
        return snapshot();
      }
      double dx = x - lastX;
      double dz = z - lastZ;
      double distanceSquared = dx * dx + dz * dz;
      if (distanceSquared < MOTION_SAMPLE_MIN_DISTANCE_SQUARED) {
        return snapshot();
      }
      double distance = Math.sqrt(distanceSquared);
      double sampleX = dx / distance;
      double sampleZ = dz / distance;

      if (!active) {
        setDirection(sampleX, sampleZ);
        segmentId++;
        clearPendingDirection();
        lastX = x;
        lastZ = z;
        return snapshot();
      }

      if (sampleX * directionX + sampleZ * directionZ >= MOTION_SAME_DIRECTION_DOT) {
        setDirection(directionX * 0.75 + sampleX * 0.25, directionZ * 0.75 + sampleZ * 0.25);
        clearPendingDirection();
      } else if (accumulatePendingDirection(sampleX, sampleZ, distance)
          >= MOTION_DIRECTION_CHANGE_DISTANCE_BLOCKS) {
        setDirection(pendingDirectionX, pendingDirectionZ);
        segmentId++;
        clearPendingDirection();
      }
      lastX = x;
      lastZ = z;
      return snapshot();
    }

    MotionSnapshot snapshot() {
      return new MotionSnapshot(worldId, directionX, directionZ, active, segmentId);
    }

    private void setDirection(double x, double z) {
      double length = Math.sqrt(x * x + z * z);
      if (length <= 0.0001) {
        active = false;
        clearPendingDirection();
        return;
      }
      directionX = x / length;
      directionZ = z / length;
      active = true;
    }

    private double accumulatePendingDirection(double sampleX, double sampleZ, double distance) {
      if (pendingDistance <= 0.0
          || pendingDirectionX * sampleX + pendingDirectionZ * sampleZ
              < MOTION_SAME_DIRECTION_DOT) {
        pendingDirectionX = sampleX;
        pendingDirectionZ = sampleZ;
        pendingDistance = distance;
        return pendingDistance;
      }
      double weightedX = pendingDirectionX * pendingDistance + sampleX * distance;
      double weightedZ = pendingDirectionZ * pendingDistance + sampleZ * distance;
      pendingDistance += distance;
      double length = Math.sqrt(weightedX * weightedX + weightedZ * weightedZ);
      if (length > 0.0001) {
        pendingDirectionX = weightedX / length;
        pendingDirectionZ = weightedZ / length;
      }
      return pendingDistance;
    }

    private void clearPendingDirection() {
      pendingDirectionX = 0.0;
      pendingDirectionZ = 0.0;
      pendingDistance = 0.0;
    }
  }

  static final class VisibilityMutationGuard {
    private final Set<VisibilityMutation> active = new HashSet<>();

    boolean contains(UUID playerId, UUID displayId) {
      return active.contains(new VisibilityMutation(playerId, displayId));
    }

    boolean run(UUID playerId, UUID displayId, BooleanSupplier mutation) {
      VisibilityMutation key = new VisibilityMutation(playerId, displayId);
      if (!active.add(key)) {
        return false;
      }
      try {
        return mutation.getAsBoolean();
      } finally {
        active.remove(key);
      }
    }

    void clear() {
      active.clear();
    }
  }

  private record VisibilityMutation(UUID playerId, UUID displayId) {}

  static boolean isWithinForwardRetention(
      double directionX, double directionZ, double relativeX, double relativeZ) {
    double projection = forwardProjection(directionX, directionZ, relativeX, relativeZ);
    return !Double.isNaN(projection) && projection >= -MOTION_FORWARD_BACK_BUFFER_BLOCKS;
  }

  static double forwardProjection(
      double directionX, double directionZ, double relativeX, double relativeZ) {
    double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
    if (directionLength <= 0.0001) {
      return Double.NaN;
    }
    double normalizedX = directionX / directionLength;
    double normalizedZ = directionZ / directionLength;
    return relativeX * normalizedX + relativeZ * normalizedZ;
  }
}
