package com.zxcmc.exort.display;

import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.integration.protocol.ProtocolLibEnhancements;
import com.zxcmc.exort.text.ExortText;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DisplayCullingService implements Listener {
  private static final String CLIENT_BYPASS_PLAYERS_PATH =
      "performance.displayCulling.clientCullingBypass.players";
  private static final int DEBUG_SAMPLE_LIMIT = 8;
  private static final double PAPER_SHOW_DISTANCE_BUFFER_BLOCKS = 4.0;
  private static final double MOTION_SAMPLE_MIN_DISTANCE_SQUARED = 0.36;
  private static final double MOTION_SAME_DIRECTION_DOT = 0.35;
  private static final double MOTION_DIRECTION_CHANGE_DISTANCE_BLOCKS = 4.0;
  private static final double MOTION_FORWARD_BACK_BUFFER_BLOCKS = 4.0;
  private static final long DEBUG_NORMAL_SUMMARY_INTERVAL_TICKS = 40L;
  private static final long DEBUG_COMPACT_SUMMARY_INTERVAL_TICKS = 200L;

  private final JavaPlugin plugin;
  private final DisplayCullingConfig config;
  private final ProtocolLibEnhancements protocolLibEnhancements;
  private final DisplayEntityIndex index;
  private final DisplayMetadataService metadataService;
  private final Runnable maintenanceTask;
  private final Map<UUID, Map<UUID, TrackedDisplay>> trackedByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, AdaptiveViewRangeState> adaptiveStates = new ConcurrentHashMap<>();
  private final Map<UUID, PlayerMotionState> motionStates = new ConcurrentHashMap<>();
  private final Set<UUID> clientCullingBypass = ConcurrentHashMap.newKeySet();
  private final Set<UUID> debugViewers = ConcurrentHashMap.newKeySet();
  private final DebugSummary debugSummary = new DebugSummary();
  private final Object debugSummaryLock = new Object();
  private DisplayCullingBackend backend;
  private int taskId = -1;
  private int debugSummaryTaskId = -1;
  private long debugSummaryTaskIntervalTicks = -1L;
  private volatile boolean debugConsoleExplicit;
  private volatile DebugMode debugMode = DebugMode.NORMAL;
  private long tickSequence;
  private int visibilityChangesThisTick;
  private int rangeChangesThisTick;
  private int paperHiddenThisTick;
  private int adaptiveSkipsThisTick;
  private int directionalKeepsThisTick;

  public DisplayCullingService(
      JavaPlugin plugin,
      DisplayCullingConfig config,
      ProtocolLibEnhancements protocolLibEnhancements,
      DisplayEntityIndex index,
      DisplayMetadataService metadataService,
      Runnable maintenanceTask) {
    this.plugin = plugin;
    this.config = config;
    this.protocolLibEnhancements = protocolLibEnhancements;
    this.index = index;
    this.metadataService = metadataService;
    this.maintenanceTask = maintenanceTask == null ? () -> {} : maintenanceTask;
    this.clientCullingBypass.addAll(config.clientCullingBypass().players());
  }

  public void start() {
    if (!config.enabled() || taskId != -1) {
      return;
    }
    backend = createBackend();
    normalizeAndIndexLoadedDisplays();
    Bukkit.getPluginManager().registerEvents(this, plugin);
    taskId =
        Bukkit.getScheduler()
            .scheduleSyncRepeatingTask(
                plugin, this::tick, config.intervalTicks(), config.intervalTicks());
    ExortLog.info("[Display] Density culling enabled using " + backend.name() + " backend.");
  }

  public void stop() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    HandlerList.unregisterAll(this);
    restoreAll();
    clearDebug();
    trackedByPlayer.clear();
    adaptiveStates.clear();
    motionStates.clear();
    if (index != null) {
      index.clear();
    }
    PerfStats.setGauge("display.entities", 0L);
  }

  public DebugMode getDebugMode() {
    return debugMode;
  }

  public boolean isDebugEnabled() {
    return debugConsoleExplicit || !debugViewers.isEmpty();
  }

  public ClientCullingBypassStatus clientCullingBypassStatus(UUID playerId) {
    boolean listed = playerId != null && clientCullingBypass.contains(playerId);
    return new ClientCullingBypassStatus(config.clientCullingBypass().enabled(), listed);
  }

  public ClientCullingBypassStatus setClientCullingBypass(UUID playerId, boolean enabled) {
    if (playerId == null) {
      return clientCullingBypassStatus(null);
    }
    if (enabled) {
      clientCullingBypass.add(playerId);
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        restorePlayer(player);
      }
      trackedByPlayer.remove(playerId);
      adaptiveStates.remove(playerId);
      motionStates.remove(playerId);
    } else {
      clientCullingBypass.remove(playerId);
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        processPlayer(player);
      }
    }
    saveClientCullingBypass();
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

  private void saveClientCullingBypass() {
    plugin
        .getConfig()
        .set(
            CLIENT_BYPASS_PLAYERS_PATH,
            new DisplayCullingConfig.ClientCullingBypassConfig(
                    true, Set.copyOf(clientCullingBypass))
                .playerStrings());
    plugin.saveConfig();
  }

  @EventHandler
  public void onTrack(PlayerTrackEntityEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    Entity entity = event.getEntity();
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
    if (entity instanceof Display display) {
      untrack(event.getPlayer(), display.getUniqueId());
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    if (event != null && event.getPlayer() != null) {
      trackedByPlayer.remove(event.getPlayer().getUniqueId());
      adaptiveStates.remove(event.getPlayer().getUniqueId());
      motionStates.remove(event.getPlayer().getUniqueId());
      debugViewers.remove(event.getPlayer().getUniqueId());
      updateDebugSummaryTask();
    }
  }

  private DisplayCullingBackend createBackend() {
    if (config.backend() != DisplayCullingConfig.Backend.PAPER && protocolLibEnhancements != null) {
      ProtocolLibEnhancements.DisplayCullingPackets packets =
          protocolLibEnhancements.tryCreateDisplayCullingPackets();
      if (packets != null) {
        return new ProtocolLibDisplayCullingBackend(packets);
      }
      if (config.backend() == DisplayCullingConfig.Backend.PROTOCOL_LIB) {
        ExortLog.warn("[Display] ProtocolLib culling unavailable; falling back to Paper API.");
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
        && clientCullingBypass.contains(playerId);
  }

  private void tick() {
    PerfStats.measure("display.culling.tick", this::tickMeasured);
  }

  private void tickMeasured() {
    tickSequence++;
    visibilityChangesThisTick = 0;
    rangeChangesThisTick = 0;
    paperHiddenThisTick = 0;
    adaptiveSkipsThisTick = 0;
    directionalKeepsThisTick = 0;
    int trackedDisplays = 0;
    int hiddenDisplays = 0;
    int dirtyDisplays = 0;
    int nearbyDisplays = 0;
    int staleRestored = 0;
    int clientBypassPlayers = 0;
    int maxAdaptiveLevel = 0;
    EnumMap<DisplayRole, Integer> roles = new EnumMap<>(DisplayRole.class);
    Set<UUID> onlinePlayers = new HashSet<>();

    maintenanceTask.run();

    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID playerId = player.getUniqueId();
      onlinePlayers.add(playerId);
      if (isClientCullingBypassed(playerId)) {
        clientBypassPlayers++;
        restorePlayer(player);
        trackedByPlayer.remove(playerId);
        adaptiveStates.remove(playerId);
        motionStates.remove(playerId);
        continue;
      }
      PlayerCullingStats stats = processPlayer(player);
      trackedDisplays += stats.tracked();
      hiddenDisplays += stats.hidden();
      dirtyDisplays += stats.dirty();
      nearbyDisplays += stats.nearby();
      staleRestored += stats.staleRestored();
      maxAdaptiveLevel = Math.max(maxAdaptiveLevel, stats.adaptiveLevel());
      for (var roleEntry : stats.roles().entrySet()) {
        incrementRole(roles, roleEntry.getKey(), roleEntry.getValue());
      }
    }
    for (UUID playerId : new ArrayList<>(trackedByPlayer.keySet())) {
      if (!onlinePlayers.contains(playerId)) {
        trackedByPlayer.remove(playerId);
        motionStates.remove(playerId);
      }
    }
    PerfStats.setGauge("display.entities", trackedDisplays);
    PerfStats.setGauge("display.culling.nearby", nearbyDisplays);
    PerfStats.setGauge("display.culling.hiddenActive", hiddenDisplays);
    PerfStats.setGauge("display.culling.visibilityChanges", visibilityChangesThisTick);
    PerfStats.setGauge("display.culling.rangeChanges", rangeChangesThisTick);
    PerfStats.setGauge("display.culling.paperHidden", paperHiddenThisTick);
    PerfStats.setGauge("display.culling.adaptiveSkips", adaptiveSkipsThisTick);
    PerfStats.setGauge("display.culling.directionalKeeps", directionalKeepsThisTick);
    PerfStats.setGauge("display.culling.adaptiveLevel", maxAdaptiveLevel);
    PerfStats.setGauge("display.culling.clientBypassPlayers", clientBypassPlayers);
    recordDebugTick(
        new TickCullingStats(
            onlinePlayers.size(),
            clientBypassPlayers,
            nearbyDisplays,
            trackedDisplays,
            hiddenDisplays,
            dirtyDisplays,
            staleRestored,
            visibilityChangesThisTick,
            rangeChangesThisTick,
            paperHiddenThisTick,
            adaptiveSkipsThisTick,
            directionalKeepsThisTick,
            maxAdaptiveLevel,
            Map.copyOf(roles)));
  }

  private PlayerCullingStats processPlayer(Player player) {
    Map<UUID, TrackedDisplay> states =
        trackedByPlayer.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
    List<DisplayEntityIndex.Entry> nearby = index.query(player.getLocation(), config.maxDistance());
    List<CullingCandidate> candidates = new ArrayList<>(nearby.size());
    EnumMap<DisplayRole, Integer> roles = new EnumMap<>(DisplayRole.class);
    Set<UUID> seen = new HashSet<>();
    for (DisplayEntityIndex.Entry entry : nearby) {
      Display display = index.resolve(entry.entityUuid());
      if (display == null || !isCullableDisplay(display)) {
        index.unregister(entry.entityUuid());
        continue;
      }
      seen.add(entry.entityUuid());
      incrementRole(roles, entry.role(), 1);
      candidates.add(new CullingCandidate(entry, display));
    }

    AdaptiveViewRangeState adaptiveState =
        adaptiveStates.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new AdaptiveViewRangeState(config.adaptiveViewRange()));
    boolean adaptiveChanged =
        adaptiveState.update(candidates.size(), tickSequence, config.intervalTicks());
    if (adaptiveChanged) {
      states.values().forEach(TrackedDisplay::markDirty);
      PerfStats.incrementCounter("display.culling.adaptiveLevelChanged");
    }
    int rangeLevel = effectiveRangeLevel(adaptiveState);
    MotionSnapshot motion =
        motionStates
            .computeIfAbsent(player.getUniqueId(), ignored -> new PlayerMotionState())
            .sample(player.getLocation());

    int hiddenCount = 0;
    int dirtyCount = 0;
    Location origin = player.getLocation();
    for (CullingCandidate candidate : candidates) {
      DisplayEntityIndex.Entry entry = candidate.entry();
      Display display = candidate.display();
      TrackedDisplay tracked =
          states.computeIfAbsent(
              entry.entityUuid(), ignored -> new TrackedDisplay(entry.role(), true));
      tracked.role = entry.role();
      applyDensityDecision(player, origin, display, tracked, rangeLevel, motion);
      if (tracked.hidden) {
        hiddenCount++;
      }
      if (tracked.dirty) {
        dirtyCount++;
      }
    }

    int staleRestored = restoreStale(player, states, seen);
    if (states.isEmpty()) {
      trackedByPlayer.remove(player.getUniqueId());
    }
    return new PlayerCullingStats(
        candidates.size(),
        states.size(),
        hiddenCount,
        dirtyCount,
        staleRestored,
        rangeLevel,
        Map.copyOf(roles));
  }

  private void applyDensityDecision(
      Player player,
      Location origin,
      Display display,
      TrackedDisplay tracked,
      int rangeLevel,
      MotionSnapshot motion) {
    DisplayRole role = tracked.role;
    double distance = safeDistance(origin, display.getLocation());
    boolean forceVisible = distance <= config.forceVisibleDistance();
    int roleLevel = forceVisible ? 0 : rangeLevelForRole(rangeLevel);
    roleLevel = applyForwardRetention(origin, display, tracked, motion, roleLevel);
    double multiplier = config.adaptiveViewRange().rangeMultiplier(role, roleLevel);
    float effectiveViewRange =
        (float) Math.max(0.05, DisplayMetadataNormalizer.BASE_VIEW_RANGE * multiplier);
    boolean hide =
        shouldHideForDensity(
            backend.supportsPerPlayerViewRange(),
            roleLevel > 0,
            role,
            forceVisible,
            tracked.hidden,
            distance,
            config.maxDistance(),
            multiplier);
    if (hide) {
      adaptiveSkipsThisTick++;
    }
    applyVisibility(player, display, tracked, hide, effectiveViewRange, roleLevel);
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

  private int restoreStale(
      Player player, Map<UUID, TrackedDisplay> states, Set<UUID> seenDisplayIds) {
    int restored = 0;
    for (var iterator = states.entrySet().iterator(); iterator.hasNext(); ) {
      var entry = iterator.next();
      if (seenDisplayIds.contains(entry.getKey())) {
        continue;
      }
      TrackedDisplay tracked = entry.getValue();
      Display display = index.resolve(entry.getKey());
      if (display != null && shouldKeepManagedOutsideScan(player, display, tracked)) {
        continue;
      }
      if (display != null) {
        forceShow(player, display, tracked);
      }
      iterator.remove();
      restored++;
    }
    return restored;
  }

  private boolean shouldKeepManagedOutsideScan(
      Player player, Display display, TrackedDisplay tracked) {
    if (player == null || display == null || tracked == null) {
      return false;
    }
    double distance = safeDistance(player.getLocation(), display.getLocation());
    if (distance <= config.maxDistance()) {
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
    states.compute(
        display.getUniqueId(),
        (ignored, current) -> {
          if (current == null) {
            TrackedDisplay tracked = new TrackedDisplay(role, true);
            tracked.clientTracked = true;
            return tracked;
          }
          current.role = role;
          current.clientTracked = true;
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
        hide
            ? backend.hide(player, display, effectiveViewRange)
            : backend.show(player, display, effectiveViewRange);
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
      backend.show(player, display, viewRange);
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

  private void restorePlayer(Player player) {
    if (backend == null || player == null || !player.isOnline()) {
      return;
    }
    Map<UUID, TrackedDisplay> states = trackedByPlayer.get(player.getUniqueId());
    if (states != null) {
      for (var displayEntry : states.entrySet()) {
        Display display = Bukkit.getEntity(displayEntry.getKey()) instanceof Display d ? d : null;
        if (display != null) {
          forceShow(player, display, displayEntry.getValue());
        }
      }
    }
    PerfStats.incrementCounter("display.culling.clientBypassRestores");
  }

  private static boolean isLowPriority(DisplayRole role) {
    return role == DisplayRole.WIRE
        || role == DisplayRole.MONITOR_CONTENT
        || role == DisplayRole.HOLOGRAM;
  }

  private int effectiveRangeLevel(AdaptiveViewRangeState adaptiveState) {
    return adaptiveState == null ? 0 : adaptiveState.levelIndex();
  }

  private int rangeLevelForRole(int rangeLevel) {
    return Math.max(0, Math.min(rangeLevel, config.adaptiveViewRange().maxLevel()));
  }

  private int applyForwardRetention(
      Location origin,
      Display display,
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
        || display == null) {
      return targetRoleLevel;
    }
    double projection = motion.forwardProjection(origin, display.getLocation());
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
    if (supportsPerPlayerViewRange || !adaptiveActive || !isLowPriority(role) || forceVisible) {
      return false;
    }
    double hideDistance = maxDistance * multiplier;
    if (!currentlyHidden) {
      return distance > hideDistance;
    }
    double showDistance = Math.max(0.0, hideDistance - PAPER_SHOW_DISTANCE_BUFFER_BLOCKS);
    return distance > showDistance;
  }

  private static double safeDistance(Location first, Location second) {
    if (first == null
        || second == null
        || first.getWorld() == null
        || !first.getWorld().equals(second.getWorld())) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.sqrt(Math.max(0.0, first.distanceSquared(second)));
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

  private record CullingCandidate(DisplayEntityIndex.Entry entry, Display display) {}

  public record ClientCullingBypassStatus(boolean configEnabled, boolean playerListed) {
    public boolean active() {
      return configEnabled && playerListed;
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
