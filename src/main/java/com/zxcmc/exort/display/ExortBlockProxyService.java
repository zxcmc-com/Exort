package com.zxcmc.exort.display;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.DisplayMarker;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.math.Position;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExortBlockProxyService implements Listener {
  private static final double MIN_VIEW_RANGE_MULTIPLIER = 0.05;
  private static final List<String> BLOCK_DISPLAY_MARKER_TYPES =
      List.of(
          "storage", "terminal", "monitor", "monitor_item", "monitor_text", DisplayTags.BUS_TAG);

  private final JavaPlugin plugin;
  private final DisplayCullingConfig.BlockProxyConfig config;
  private final boolean enabled;
  private final Material storageCarrier;
  private final Material terminalCarrier;
  private final Material monitorCarrier;
  private final Material busCarrier;
  private final BlockData proxyData;
  private final Map<UUID, PlayerState> playerStates = new HashMap<>();
  private final LinkedHashMap<PlayerBlockKey, ChangeRequest> restoreQueue = new LinkedHashMap<>();
  private final LinkedHashMap<PlayerBlockKey, ChangeRequest> proxyQueue = new LinkedHashMap<>();
  private final Map<PlayerBlockKey, Set<UUID>> hiddenDisplays = new HashMap<>();
  private int drainTaskId = -1;
  private boolean started;
  private long budgetOverruns;
  private long chunkLoads;

  public ExortBlockProxyService(
      JavaPlugin plugin,
      DisplayCullingConfig.BlockProxyConfig config,
      boolean resourceMode,
      Material storageCarrier,
      Material terminalCarrier,
      Material monitorCarrier,
      Material busCarrier) {
    this.plugin = plugin;
    this.config =
        config == null ? DisplayCullingConfig.BlockProxyConfig.defaults() : config.normalized();
    this.enabled = resourceMode && this.config.enabled();
    this.storageCarrier = storageCarrier;
    this.terminalCarrier = terminalCarrier;
    this.monitorCarrier = monitorCarrier;
    this.busCarrier = busCarrier;
    this.proxyData = createProxyData(this.config.material());
  }

  public void start() {
    if (!enabled || started) {
      updateGauges(0, 0);
      return;
    }
    started = true;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    for (Player player : Bukkit.getOnlinePlayers()) {
      scanLoadedChunks(player);
    }
    updateGauges(0, 0);
  }

  public void stop() {
    if (drainTaskId != -1) {
      Bukkit.getScheduler().cancelTask(drainTaskId);
      drainTaskId = -1;
    }
    if (started) {
      HandlerList.unregisterAll(this);
      started = false;
    }
    restoreAllNow();
    showAllHiddenDisplays();
    restoreQueue.clear();
    proxyQueue.clear();
    hiddenDisplays.clear();
    playerStates.clear();
    updateGauges(0, 0);
  }

  public void processPlayer(Player player, double viewRangeMultiplier) {
    if (!enabled || player == null || !player.isOnline()) {
      return;
    }
    PlayerState state = playerStates.get(player.getUniqueId());
    if (state == null || state.candidates.isEmpty()) {
      return;
    }
    double multiplier = normalizeMultiplier(viewRangeMultiplier);
    state.viewRangeMultiplier = multiplier;
    Location origin = player.getLocation();
    for (CandidateState candidate : new ArrayList<>(state.candidates.values())) {
      if (!candidate.key.world().equals(player.getWorld().getUID())) {
        continue;
      }
      Block block = loadedBlock(candidate.key);
      if (block == null || !isProxyCandidate(block)) {
        forgetCandidate(player.getUniqueId(), state, candidate.key);
        continue;
      }
      applyDecision(player, state, candidate, origin, multiplier);
    }
  }

  public void updateBlockDisplayDecision(
      Player player, Location displayLocation, double viewRangeMultiplier) {
    if (!enabled
        || player == null
        || displayLocation == null
        || displayLocation.getWorld() == null) {
      return;
    }
    PlayerState state = playerStates.get(player.getUniqueId());
    if (state == null) {
      return;
    }
    BlockKey key =
        new BlockKey(
            displayLocation.getWorld().getUID(),
            displayLocation.getBlockX(),
            displayLocation.getBlockY(),
            displayLocation.getBlockZ());
    CandidateState candidate = state.candidates.get(key);
    if (candidate == null) {
      return;
    }
    double multiplier = normalizeMultiplier(viewRangeMultiplier);
    state.viewRangeMultiplier = multiplier;
    applyDecision(player, state, candidate, player.getLocation(), multiplier);
  }

  public void refreshChunk(Chunk chunk) {
    if (!enabled || chunk == null || chunk.getWorld() == null) {
      return;
    }
    ChunkKey chunkKey = ChunkKey.from(chunk);
    for (Player player : Bukkit.getOnlinePlayers()) {
      PlayerState state = playerStates.get(player.getUniqueId());
      if (state != null && state.loadedChunks.contains(chunkKey)) {
        scanChunkForPlayer(player, state, chunk);
      }
    }
    updateGauges(0, 0);
  }

  public void refreshBlock(Block block) {
    if (!enabled || block == null || block.getWorld() == null) {
      return;
    }
    BlockKey key = BlockKey.from(block);
    ChunkKey chunkKey = key.chunkKey();
    for (Player player : Bukkit.getOnlinePlayers()) {
      PlayerState state = playerStates.get(player.getUniqueId());
      if (state == null || !state.loadedChunks.contains(chunkKey)) {
        continue;
      }
      if (!isProxyCandidate(block)) {
        forgetCandidate(player.getUniqueId(), state, key);
        continue;
      }
      CandidateState candidate = state.candidates.computeIfAbsent(key, CandidateState::new);
      applyDecision(player, state, candidate, player.getLocation(), state.viewRangeMultiplier);
    }
    updateGauges(0, 0);
  }

  public void restoreAndForget(Block block) {
    if (!enabled || block == null || block.getWorld() == null) {
      return;
    }
    BlockKey key = BlockKey.from(block);
    for (Player player : Bukkit.getOnlinePlayers()) {
      PlayerState state = playerStates.get(player.getUniqueId());
      if (state != null) {
        forgetCandidate(player.getUniqueId(), state, key);
      }
    }
    updateGauges(0, 0);
  }

  @EventHandler
  public void onChunkLoad(PlayerChunkLoadEvent event) {
    if (!enabled || event == null || event.getPlayer() == null || event.getChunk() == null) {
      return;
    }
    Player player = event.getPlayer();
    PlayerState state = stateFor(player);
    state.loadedChunks.add(ChunkKey.from(event.getChunk()));
    scanChunkForPlayer(player, state, event.getChunk());
    chunkLoads++;
    PerfStats.setGauge("display.blockProxy.chunkLoads", chunkLoads);
  }

  @EventHandler
  public void onChunkUnload(PlayerChunkUnloadEvent event) {
    if (!enabled || event == null || event.getPlayer() == null || event.getChunk() == null) {
      return;
    }
    UUID playerId = event.getPlayer().getUniqueId();
    PlayerState state = playerStates.get(playerId);
    if (state == null) {
      return;
    }
    ChunkKey chunkKey = ChunkKey.from(event.getChunk());
    state.loadedChunks.remove(chunkKey);
    state.candidates.keySet().removeIf(key -> key.chunkKey().equals(chunkKey));
    removeQueuedInChunk(playerId, chunkKey);
    removeHiddenInChunk(playerId, chunkKey);
    removeEmptyState(playerId, state);
    updateGauges(0, 0);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    if (event != null && event.getPlayer() != null) {
      clearPlayer(event.getPlayer().getUniqueId());
    }
  }

  @EventHandler
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    if (event != null && event.getPlayer() != null) {
      clearPlayer(event.getPlayer().getUniqueId());
      scanLoadedChunks(event.getPlayer());
    }
  }

  private void scanLoadedChunks(Player player) {
    if (player == null || !player.isOnline()) {
      return;
    }
    World world = player.getWorld();
    int centerX = player.getLocation().getBlockX() >> 4;
    int centerZ = player.getLocation().getBlockZ() >> 4;
    int radius = Math.max(1, plugin.getServer().getViewDistance());
    PlayerState state = stateFor(player);
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        int chunkX = centerX + dx;
        int chunkZ = centerZ + dz;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          continue;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        state.loadedChunks.add(ChunkKey.from(chunk));
        scanChunkForPlayer(player, state, chunk);
      }
    }
  }

  private void scanChunkForPlayer(Player player, PlayerState state, Chunk chunk) {
    if (player == null || state == null || chunk == null || chunk.getWorld() == null) {
      return;
    }
    ChunkKey chunkKey = ChunkKey.from(chunk);
    Set<BlockKey> seen = new HashSet<>();
    if (ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      ChunkMarkerStore.forEachBlock(
          plugin,
          chunk,
          (block, ignored) -> {
            if (!isProxyCandidate(block)) {
              return;
            }
            BlockKey key = BlockKey.from(block);
            seen.add(key);
            CandidateState candidate = state.candidates.computeIfAbsent(key, CandidateState::new);
            applyDecision(
                player, state, candidate, player.getLocation(), state.viewRangeMultiplier);
          });
    }
    for (Iterator<BlockKey> iterator = state.candidates.keySet().iterator(); iterator.hasNext(); ) {
      BlockKey key = iterator.next();
      if (!key.chunkKey().equals(chunkKey) || seen.contains(key)) {
        continue;
      }
      CandidateState candidate = state.candidates.get(key);
      iterator.remove();
      removeQueued(player.getUniqueId(), key, false);
      if (candidate != null && candidate.proxied) {
        enqueueRestore(player.getUniqueId(), key, true);
      }
    }
    scheduleDrainIfNeeded();
  }

  private void applyDecision(
      Player player,
      PlayerState state,
      CandidateState candidate,
      Location origin,
      double viewRangeMultiplier) {
    VisualDecision decision =
        decideVisual(
            candidate.proxied, distanceToBlock(origin, candidate.key), viewRangeMultiplier, config);
    PlayerBlockKey playerBlockKey = new PlayerBlockKey(player.getUniqueId(), candidate.key);
    if (decision != VisualDecision.PROXY) {
      proxyQueue.remove(playerBlockKey);
    }
    if (decision == VisualDecision.REAL) {
      if (candidate.proxied) {
        hideBlockDisplays(player, candidate.key);
        enqueueRestore(player.getUniqueId(), candidate.key, false);
      }
      return;
    }
    if (decision == VisualDecision.KEEP && candidate.proxied) {
      hideBlockDisplays(player, candidate.key);
      return;
    }
    if (decision == VisualDecision.PROXY && !candidate.proxied) {
      enqueueProxy(player.getUniqueId(), candidate.key);
    }
  }

  private void forgetCandidate(UUID playerId, PlayerState state, BlockKey key) {
    CandidateState candidate = state.candidates.remove(key);
    removeQueued(playerId, key, false);
    if (candidate != null && candidate.proxied) {
      enqueueRestore(playerId, key, true);
    }
    removeEmptyState(playerId, state);
  }

  private void enqueueRestore(UUID playerId, BlockKey key, boolean forgetAfterRestore) {
    PlayerBlockKey playerBlockKey = new PlayerBlockKey(playerId, key);
    proxyQueue.remove(playerBlockKey);
    restoreQueue.put(playerBlockKey, new ChangeRequest(playerId, key, true, forgetAfterRestore));
    scheduleDrainIfNeeded();
  }

  private void enqueueProxy(UUID playerId, BlockKey key) {
    PlayerBlockKey playerBlockKey = new PlayerBlockKey(playerId, key);
    if (restoreQueue.containsKey(playerBlockKey)) {
      return;
    }
    proxyQueue.put(playerBlockKey, new ChangeRequest(playerId, key, false, false));
    scheduleDrainIfNeeded();
  }

  private void scheduleDrainIfNeeded() {
    if (!enabled || drainTaskId != -1 || (restoreQueue.isEmpty() && proxyQueue.isEmpty())) {
      updateGauges(0, 0);
      return;
    }
    try {
      drainTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, this::drainQueues, 1L);
    } catch (IllegalStateException ignored) {
      drainTaskId = -1;
    }
    updateGauges(0, 0);
  }

  private void drainQueues() {
    drainTaskId = -1;
    PerfStats.measure("display.blockProxy.drain", this::drainQueuesMeasured);
  }

  private void drainQueuesMeasured() {
    int budget = config.maxBlockChangesPerTick();
    List<PreparedChange> changes = new ArrayList<>(Math.min(256, budget));
    budget = drainQueue(restoreQueue, budget, changes);
    budget = drainQueue(proxyQueue, budget, changes);
    int sent = sendPreparedChanges(changes);
    int restored = 0;
    for (PreparedChange change : changes) {
      if (change.request.restore()) {
        restored++;
      }
    }
    if (!restoreQueue.isEmpty() || !proxyQueue.isEmpty()) {
      budgetOverruns++;
      PerfStats.setGauge("display.blockProxy.budgetOverrun", budgetOverruns);
      scheduleDrainIfNeeded();
    }
    updateGauges(sent, restored);
  }

  private int drainQueue(
      LinkedHashMap<PlayerBlockKey, ChangeRequest> queue,
      int budget,
      List<PreparedChange> changes) {
    Iterator<Map.Entry<PlayerBlockKey, ChangeRequest>> iterator = queue.entrySet().iterator();
    while (budget > 0 && iterator.hasNext()) {
      ChangeRequest request = iterator.next().getValue();
      iterator.remove();
      PreparedChange change = prepareChange(request);
      if (change == null) {
        continue;
      }
      changes.add(change);
      budget--;
    }
    return budget;
  }

  private PreparedChange prepareChange(ChangeRequest request) {
    Player player = Bukkit.getPlayer(request.playerId());
    if (player == null || !player.isOnline()) {
      return null;
    }
    Block block = loadedBlock(request.blockKey());
    if (block == null) {
      return null;
    }
    PlayerState state = playerStates.get(request.playerId());
    if (!request.restore()) {
      if (state == null
          || !state.candidates.containsKey(request.blockKey())
          || !isProxyCandidate(block)) {
        return null;
      }
    }
    BlockData blockData = request.restore() ? block.getBlockData() : proxyData;
    return new PreparedChange(player, request, blockData);
  }

  private int sendPreparedChanges(List<PreparedChange> changes) {
    if (changes.isEmpty()) {
      return 0;
    }
    Map<Player, List<PreparedChange>> byPlayer = new LinkedHashMap<>();
    for (PreparedChange change : changes) {
      byPlayer.computeIfAbsent(change.player, ignored -> new ArrayList<>()).add(change);
    }
    int sent = 0;
    for (Map.Entry<Player, List<PreparedChange>> entry : byPlayer.entrySet()) {
      Map<Position, BlockData> packetChanges = new LinkedHashMap<>();
      for (PreparedChange change : entry.getValue()) {
        BlockKey key = change.request.blockKey();
        packetChanges.put(Position.block(key.x(), key.y(), key.z()), change.blockData);
      }
      for (PreparedChange change : entry.getValue()) {
        if (!change.request.restore()) {
          hideBlockDisplays(change.player, change.request.blockKey());
        }
      }
      try {
        entry.getKey().sendMultiBlockChange(packetChanges);
      } catch (RuntimeException ignored) {
        for (PreparedChange change : entry.getValue()) {
          if (!change.request.restore()) {
            showHiddenBlockDisplays(change.player, change.request.blockKey());
          }
        }
        continue;
      }
      sent += packetChanges.size();
      for (PreparedChange change : entry.getValue()) {
        markApplied(change);
      }
    }
    return sent;
  }

  private void markApplied(PreparedChange change) {
    PlayerState state = playerStates.get(change.request.playerId());
    if (change.request.restore()) {
      CandidateState candidate =
          state == null ? null : state.candidates.get(change.request.blockKey());
      if (candidate != null) {
        candidate.proxied = false;
      }
      showHiddenBlockDisplays(change.player, change.request.blockKey());
      if (state != null && change.request.forgetAfterRestore()) {
        state.candidates.remove(change.request.blockKey());
      }
      removeEmptyState(change.request.playerId(), state);
      return;
    }
    if (state == null) {
      return;
    }
    CandidateState candidate = state.candidates.get(change.request.blockKey());
    if (candidate != null) {
      candidate.proxied = true;
      hideBlockDisplays(change.player, change.request.blockKey());
    }
    removeEmptyState(change.request.playerId(), state);
  }

  private void restoreAllNow() {
    List<PreparedChange> changes = new ArrayList<>();
    for (Map.Entry<UUID, PlayerState> stateEntry : playerStates.entrySet()) {
      Player player = Bukkit.getPlayer(stateEntry.getKey());
      if (player == null || !player.isOnline()) {
        continue;
      }
      for (CandidateState candidate : stateEntry.getValue().candidates.values()) {
        if (!candidate.proxied) {
          continue;
        }
        Block block = loadedBlock(candidate.key);
        if (block != null) {
          changes.add(
              new PreparedChange(
                  player,
                  new ChangeRequest(stateEntry.getKey(), candidate.key, true, false),
                  block.getBlockData()));
        }
      }
    }
    sendPreparedChanges(changes);
  }

  private boolean isProxyCandidate(Block block) {
    if (block == null || block.getWorld() == null) {
      return false;
    }
    if (Carriers.matchesCarrier(block, storageCarrier)
        && (StorageMarker.get(plugin, block).isPresent()
            || StorageCoreMarker.isCore(plugin, block))) {
      return true;
    }
    if (Carriers.matchesCarrier(block, terminalCarrier)
        && TerminalMarker.isTerminal(plugin, block)) {
      return true;
    }
    if (Carriers.matchesCarrier(block, monitorCarrier) && MonitorMarker.isMonitor(plugin, block)) {
      return true;
    }
    return Carriers.matchesCarrier(block, busCarrier) && BusMarker.isBus(plugin, block);
  }

  private void hideBlockDisplays(Player player, BlockKey key) {
    if (player == null || !player.isOnline()) {
      return;
    }
    Block block = loadedBlock(key);
    if (block == null) {
      return;
    }
    PlayerBlockKey playerBlockKey = new PlayerBlockKey(player.getUniqueId(), key);
    Set<UUID> hidden =
        hiddenDisplays.computeIfAbsent(playerBlockKey, ignored -> new LinkedHashSet<>());
    for (Display display : blockDisplays(block)) {
      try {
        player.hideEntity(plugin, display);
        hidden.add(display.getUniqueId());
      } catch (RuntimeException ignored) {
        // Client-only fallback; a failed hide should not affect the real world state.
      }
    }
    if (hidden.isEmpty()) {
      hiddenDisplays.remove(playerBlockKey);
    }
  }

  private void showHiddenBlockDisplays(Player player, BlockKey key) {
    if (player == null) {
      return;
    }
    PlayerBlockKey playerBlockKey = new PlayerBlockKey(player.getUniqueId(), key);
    Set<UUID> hidden = hiddenDisplays.remove(playerBlockKey);
    if (hidden == null || hidden.isEmpty() || !player.isOnline()) {
      return;
    }
    for (UUID displayId : hidden) {
      Entity entity = Bukkit.getEntity(displayId);
      if (entity == null || !entity.isValid()) {
        continue;
      }
      try {
        player.showEntity(plugin, entity);
      } catch (RuntimeException ignored) {
        // Best-effort visual restore; the next normal display refresh can resync the entity too.
      }
    }
  }

  private void showAllHiddenDisplays() {
    for (Map.Entry<PlayerBlockKey, Set<UUID>> entry : new ArrayList<>(hiddenDisplays.entrySet())) {
      Player player = Bukkit.getPlayer(entry.getKey().playerId());
      if (player == null || !player.isOnline()) {
        continue;
      }
      for (UUID displayId : entry.getValue()) {
        Entity entity = Bukkit.getEntity(displayId);
        if (entity == null || !entity.isValid()) {
          continue;
        }
        try {
          player.showEntity(plugin, entity);
        } catch (RuntimeException ignored) {
          // Best-effort cleanup during plugin stop/reload.
        }
      }
    }
  }

  private List<Display> blockDisplays(Block block) {
    List<Display> displays = new ArrayList<>();
    Set<UUID> seen = new HashSet<>();
    for (String markerType : BLOCK_DISPLAY_MARKER_TYPES) {
      addBlockDisplay(block, markerType, displays, seen);
    }
    return displays;
  }

  private void addBlockDisplay(
      Block block, String markerType, List<Display> displays, Set<UUID> seen) {
    UUID displayId = DisplayMarker.get(plugin, markerType, block).orElse(null);
    if (displayId == null || !seen.add(displayId)) {
      return;
    }
    Entity entity = Bukkit.getEntity(displayId);
    if (entity instanceof Display display && display.isValid()) {
      displays.add(display);
    }
  }

  private Block loadedBlock(BlockKey key) {
    World world = Bukkit.getWorld(key.world());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      return null;
    }
    return world.getBlockAt(key.x(), key.y(), key.z());
  }

  private PlayerState stateFor(Player player) {
    return playerStates.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
  }

  private void clearPlayer(UUID playerId) {
    showHiddenForPlayer(playerId);
    playerStates.remove(playerId);
    restoreQueue.keySet().removeIf(key -> key.playerId().equals(playerId));
    proxyQueue.keySet().removeIf(key -> key.playerId().equals(playerId));
    hiddenDisplays.keySet().removeIf(key -> key.playerId().equals(playerId));
    updateGauges(0, 0);
  }

  private void removeQueued(UUID playerId, BlockKey blockKey, boolean restore) {
    PlayerBlockKey key = new PlayerBlockKey(playerId, blockKey);
    proxyQueue.remove(key);
    if (restore) {
      restoreQueue.remove(key);
    }
  }

  private void removeQueuedInChunk(UUID playerId, ChunkKey chunkKey) {
    restoreQueue
        .keySet()
        .removeIf(
            key -> key.playerId().equals(playerId) && key.blockKey().chunkKey().equals(chunkKey));
    proxyQueue
        .keySet()
        .removeIf(
            key -> key.playerId().equals(playerId) && key.blockKey().chunkKey().equals(chunkKey));
  }

  private void removeHiddenInChunk(UUID playerId, ChunkKey chunkKey) {
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      hiddenDisplays
          .keySet()
          .removeIf(
              key -> key.playerId().equals(playerId) && key.blockKey().chunkKey().equals(chunkKey));
      return;
    }
    List<PlayerBlockKey> keys = new ArrayList<>();
    for (PlayerBlockKey key : hiddenDisplays.keySet()) {
      if (key.playerId().equals(playerId) && key.blockKey().chunkKey().equals(chunkKey)) {
        keys.add(key);
      }
    }
    for (PlayerBlockKey key : keys) {
      showHiddenBlockDisplays(player, key.blockKey());
    }
  }

  private void showHiddenForPlayer(UUID playerId) {
    Player player = Bukkit.getPlayer(playerId);
    if (player == null || !player.isOnline()) {
      hiddenDisplays.keySet().removeIf(key -> key.playerId().equals(playerId));
      return;
    }
    List<PlayerBlockKey> keys = new ArrayList<>();
    for (PlayerBlockKey key : hiddenDisplays.keySet()) {
      if (key.playerId().equals(playerId)) {
        keys.add(key);
      }
    }
    for (PlayerBlockKey key : keys) {
      showHiddenBlockDisplays(player, key.blockKey());
    }
  }

  private void removeEmptyState(UUID playerId, PlayerState state) {
    if (state != null && state.loadedChunks.isEmpty() && state.candidates.isEmpty()) {
      playerStates.remove(playerId);
    }
  }

  private int proxiedCount() {
    int count = 0;
    for (PlayerState state : playerStates.values()) {
      for (CandidateState candidate : state.candidates.values()) {
        if (candidate.proxied) {
          count++;
        }
      }
    }
    return count;
  }

  private void updateGauges(int sent, int restored) {
    PerfStats.setGauge("display.blockProxy.proxied", proxiedCount());
    PerfStats.setGauge("display.blockProxy.restored", restored);
    PerfStats.setGauge("display.blockProxy.queued", (long) restoreQueue.size() + proxyQueue.size());
    PerfStats.setGauge("display.blockProxy.sent", sent);
    PerfStats.setGauge("display.blockProxy.budgetOverrun", budgetOverruns);
    PerfStats.setGauge("display.blockProxy.chunkLoads", chunkLoads);
  }

  static VisualDecision decideVisual(
      boolean currentlyProxied,
      double distanceBlocks,
      double viewRangeMultiplier,
      DisplayCullingConfig.BlockProxyConfig config) {
    DisplayCullingConfig.BlockProxyConfig safeConfig =
        config == null ? DisplayCullingConfig.BlockProxyConfig.defaults() : config.normalized();
    double distance =
        Double.isFinite(distanceBlocks) ? Math.max(0.0, distanceBlocks) : Double.MAX_VALUE;
    double multiplier = normalizeMultiplier(viewRangeMultiplier);
    double renderDistance = safeConfig.baseRenderDistanceBlocks() * multiplier;
    double restoreDistance =
        Math.max(safeConfig.forceRealDistance(), renderDistance + safeConfig.restoreBufferBlocks());
    double proxyDistance = restoreDistance + safeConfig.enterBufferBlocks();
    if (distance <= safeConfig.forceRealDistance()) {
      return VisualDecision.REAL;
    }
    if (currentlyProxied) {
      return distance <= restoreDistance ? VisualDecision.REAL : VisualDecision.KEEP;
    }
    return distance >= proxyDistance ? VisualDecision.PROXY : VisualDecision.KEEP;
  }

  private static double normalizeMultiplier(double viewRangeMultiplier) {
    if (!Double.isFinite(viewRangeMultiplier)) {
      return 1.0;
    }
    return Math.max(MIN_VIEW_RANGE_MULTIPLIER, viewRangeMultiplier);
  }

  private static BlockData createProxyData(Material material) {
    Material safeMaterial = material == null ? Material.NETHERITE_BLOCK : material;
    try {
      return safeMaterial.createBlockData();
    } catch (IllegalArgumentException ex) {
      return Material.NETHERITE_BLOCK.createBlockData();
    }
  }

  private static double distanceToBlock(Location origin, BlockKey key) {
    if (origin == null
        || origin.getWorld() == null
        || !origin.getWorld().getUID().equals(key.world())) {
      return Double.POSITIVE_INFINITY;
    }
    double dx = origin.getX() - (key.x() + 0.5);
    double dy = origin.getY() - (key.y() + 0.5);
    double dz = origin.getZ() - (key.z() + 0.5);
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  enum VisualDecision {
    PROXY,
    REAL,
    KEEP
  }

  private static final class PlayerState {
    private final Set<ChunkKey> loadedChunks = new HashSet<>();
    private final Map<BlockKey, CandidateState> candidates = new HashMap<>();
    private double viewRangeMultiplier = 1.0;
  }

  private static final class CandidateState {
    private final BlockKey key;
    private boolean proxied;

    private CandidateState(BlockKey key) {
      this.key = key;
    }
  }

  private record ChunkKey(UUID world, int x, int z) {
    private static ChunkKey from(Chunk chunk) {
      return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
  }

  private record BlockKey(UUID world, int x, int y, int z) {
    private static BlockKey from(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private ChunkKey chunkKey() {
      return new ChunkKey(world, x >> 4, z >> 4);
    }
  }

  private record PlayerBlockKey(UUID playerId, BlockKey blockKey) {}

  private record ChangeRequest(
      UUID playerId, BlockKey blockKey, boolean restore, boolean forgetAfterRestore) {}

  private record PreparedChange(Player player, ChangeRequest request, BlockData blockData) {}
}
