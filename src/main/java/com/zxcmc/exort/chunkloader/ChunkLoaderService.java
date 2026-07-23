package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChunkLoaderService implements Listener {
  public static final String BYPASS_LIMITS_PERMISSION = "exort.chunkloader.bypass-limits";
  private static final int STARTUP_ACTIVATIONS_PER_TICK = 8;
  private static final long PERSONAL_GRACE_TICKS = 5L * 60L * 20L;

  public enum ToggleResult {
    ENABLED,
    DISABLED,
    ALREADY_ENABLED,
    ALREADY_DISABLED,
    INITIALIZING,
    QUOTA_EXCEEDED,
    MISSING
  }

  public enum PlacementResult {
    ALLOWED,
    INITIALIZING,
    DUPLICATE,
    QUOTA_EXCEEDED
  }

  public enum BreakOutcome {
    DROP_ITEM(ChunkLoaderRegistryStatus.ITEM, ChunkLoaderAuditEvent.BREAK, "break", "item_drop"),
    DESTROY(
        ChunkLoaderRegistryStatus.REMOVED,
        ChunkLoaderAuditEvent.DESTROY,
        "destroy",
        "creative_break");

    private final ChunkLoaderRegistryStatus status;
    private final ChunkLoaderAuditEvent auditEvent;
    private final String source;
    private final String reason;

    BreakOutcome(
        ChunkLoaderRegistryStatus status,
        ChunkLoaderAuditEvent auditEvent,
        String source,
        String reason) {
      this.status = status;
      this.auditEvent = auditEvent;
      this.source = source;
      this.reason = reason;
    }
  }

  public enum RemovalStart {
    STARTED,
    ALREADY_PENDING,
    MISSING
  }

  private final JavaPlugin plugin;
  private final Database database;
  private final Material carrier;
  private final ChunkLoaderConfig config;
  private final ChunkLoaderAuditLogger auditLogger;
  private final ChunkLoaderLifecycle lifecycle = new ChunkLoaderLifecycle();
  private final ChunkLoaderTicketLedger ticketLedger;
  private final Map<UUID, ChunkLoaderRecord> byId = new HashMap<>();
  private final Map<BlockKey, UUID> byBlock = new HashMap<>();
  private final Set<UUID> quotaBlockedIds = new HashSet<>();
  private final Set<UUID> removedDuringHydration = new HashSet<>();
  private final Set<BlockKey> removedBlocksDuringHydration = new HashSet<>();
  private final Map<UUID, BukkitTask> personalReleaseTasks = new HashMap<>();
  private final ArrayDeque<ChunkLoaderRecord> startupActivationQueue = new ArrayDeque<>();
  private final ChunkLoaderPersistenceGuard persistenceGuard = new ChunkLoaderPersistenceGuard();
  private final ChunkLoaderRemovalCoordinator removalCoordinator;
  private ChunkLoaderLifecycle.Generation generation;
  private BukkitTask startupActivationTask;

  public ChunkLoaderService(
      JavaPlugin plugin,
      Database database,
      Material carrier,
      ChunkLoaderConfig config,
      ChunkLoaderAuditLogger auditLogger) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.database = Objects.requireNonNull(database, "database");
    this.carrier = Objects.requireNonNull(carrier, "carrier");
    this.config =
        config == null
            ? new ChunkLoaderConfig(
                ChunkLoaderConfig.DEFAULT_ENABLED,
                ChunkLoaderConfig.DEFAULT_RADIUS,
                ChunkLoaderLimits.defaults(),
                ChunkLoaderAuditConfig.fromConfig(null))
            : config;
    this.auditLogger =
        auditLogger == null
            ? new ChunkLoaderAuditLogger(plugin.getLogger(), this.config.audit())
            : auditLogger;
    this.ticketLedger = new ChunkLoaderTicketLedger(this.config.limits());
    this.removalCoordinator =
        new ChunkLoaderRemovalCoordinator(task -> PluginTasks.runSyncIfEnabled(plugin, task));
  }

  public void start() {
    if (generation != null && lifecycle.isActive(generation)) {
      return;
    }
    ChunkLoaderLifecycle.Generation startedGeneration = lifecycle.start();
    generation = startedGeneration;
    database
        .listChunkLoaders()
        .whenComplete(
            (records, err) -> {
              if (!lifecycle.isActive(startedGeneration)) {
                return;
              }
              if (err != null) {
                Throwable failure = unwrap(err);
                plugin.getLogger().log(Level.WARNING, "Failed to load chunk loaders", failure);
                lifecycle.fail(startedGeneration, failure);
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin,
                  () -> {
                    if (!lifecycle.isActive(startedGeneration)) {
                      return;
                    }
                    rebuildFromDatabase(records, startedGeneration);
                  });
            });
  }

  public void stop() {
    lifecycle.stop();
    cancelStartupActivation();
    for (BukkitTask task : personalReleaseTasks.values()) {
      task.cancel();
    }
    personalReleaseTasks.clear();
    startupActivationQueue.clear();
    for (ChunkLoaderTicketLedger.Ticket ticket : ticketLedger.tickets()) {
      World world = Bukkit.getWorld(ticket.worldId());
      if (world != null) {
        world.removePluginChunkTicket(ticket.chunkX(), ticket.chunkZ(), plugin);
      }
    }
    ticketLedger.clear();
    quotaBlockedIds.clear();
    removedDuringHydration.clear();
    removedBlocksDuringHydration.clear();
    persistenceGuard.clear();
    removalCoordinator.clear();
    byId.clear();
    byBlock.clear();
    auditLogger.close();
  }

  public int radius() {
    return config.radius();
  }

  public boolean isFeatureEnabled() {
    return config.enabled();
  }

  public boolean isAuditEnabled() {
    return config.audit().enabled();
  }

  public ChunkLoaderAuditLogger auditLogger() {
    return auditLogger;
  }

  public boolean isReady() {
    return lifecycle.isReady();
  }

  public CompletableFuture<Void> readyFuture() {
    ChunkLoaderLifecycle.Generation current = generation;
    return current == null
        ? CompletableFuture.failedFuture(
            new IllegalStateException("Chunk Loader service has not started"))
        : lifecycle.readiness(current);
  }

  public PlacementResult placementResult(
      Player player, UUID id, Block block, ChunkLoaderType type) {
    if (!isReady()) {
      return PlacementResult.INITIALIZING;
    }
    if (id == null || block == null || block.getWorld() == null) {
      return PlacementResult.DUPLICATE;
    }
    if (removalCoordinator.isPending(removalKey(id, block))) {
      return PlacementResult.INITIALIZING;
    }
    ChunkLoaderRecord existing = byId.get(id);
    if (existing != null && !existing.sameBlock(block)) {
      return PlacementResult.DUPLICATE;
    }
    UUID byPosition = byBlock.get(BlockKey.of(block));
    if (byPosition != null && !byPosition.equals(id)) {
      return PlacementResult.DUPLICATE;
    }
    ChunkLoaderRecord proposed =
        ChunkLoaderRecord.placed(
            block,
            id,
            player,
            radius(),
            type == null ? ChunkLoaderType.defaultType() : type,
            bypassesLimits(player));
    if (shouldTicket(proposed, true) && quotaStatus(proposed) != null) {
      return PlacementResult.QUOTA_EXCEEDED;
    }
    return PlacementResult.ALLOWED;
  }

  public boolean isActiveLoaderId(UUID id) {
    return id != null && byId.containsKey(id);
  }

  public boolean place(Player player, Block block, UUID id) {
    return place(player, block, id, ChunkLoaderType.defaultType());
  }

  public boolean place(Player player, Block block, UUID id, ChunkLoaderType type) {
    return place(player, block, id, type, ignored -> {});
  }

  public boolean place(
      Player player,
      Block block,
      UUID id,
      ChunkLoaderType type,
      Consumer<Throwable> persistenceFailure) {
    Objects.requireNonNull(persistenceFailure, "persistenceFailure");
    if (placementResult(player, id, block, type) != PlacementResult.ALLOWED) {
      return false;
    }
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    ChunkLoaderRecord record =
        ChunkLoaderRecord.placed(block, id, player, radius(), safeType, bypassesLimits(player));
    ChunkLoaderMarker.set(
        plugin,
        block,
        id,
        safeType,
        player == null ? null : player.getUniqueId(),
        player == null ? null : player.getName(),
        record.createdAt(),
        record.enabled(),
        record.bypassLimits());
    activate(record, true);
    long persistenceOperation = persistenceGuard.begin(id);
    Location foundLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
    database
        .saveChunkLoader(record)
        .whenComplete(
            (found, err) -> {
              if (err != null) {
                Throwable failure = unwrap(err);
                plugin
                    .getLogger()
                    .log(Level.WARNING, "Failed to persist chunk loader " + id, failure);
                PluginTasks.runSyncIfEnabled(
                    plugin,
                    () ->
                        rollbackPlacement(
                            block, record, persistenceOperation, failure, persistenceFailure));
                return;
              }
              PluginTasks.runSyncIfEnabled(
                  plugin,
                  () -> {
                    if (!persistenceGuard.complete(id, persistenceOperation)) {
                      return;
                    }
                    if (Boolean.TRUE.equals(found)) {
                      auditLogger.logFound(
                          ChunkLoaderAuditEvent.PLACE,
                          player,
                          id,
                          safeType,
                          "place",
                          foundLocation);
                    }
                  });
            });
    auditLogger.log(ChunkLoaderAuditEvent.PLACE, player, id, safeType, block);
    return true;
  }

  private void rollbackPlacement(
      Block block,
      ChunkLoaderRecord failed,
      long persistenceOperation,
      Throwable failure,
      Consumer<Throwable> persistenceFailure) {
    if (!persistenceGuard.complete(failed.id(), persistenceOperation)) {
      return;
    }
    ChunkLoaderRecord current = byId.get(failed.id());
    Optional<ChunkLoaderMarker.Data> marker = ChunkLoaderMarker.get(plugin, block);
    if (!failed.equals(current) || marker.isEmpty() || !failed.id().equals(marker.get().id())) {
      return;
    }
    deactivate(failed.id(), block);
    ChunkLoaderMarker.clear(plugin, block);
    persistenceFailure.accept(failure);
    plugin
        .getLogger()
        .warning(
            "Rolled back Chunk Loader " + failed.id() + " placement after persistence failure.");
  }

  public RemovalStart breakLoader(
      Player player,
      Block block,
      BreakOutcome outcome,
      Consumer<UUID> committed,
      Consumer<Throwable> persistenceFailure) {
    Objects.requireNonNull(committed, "committed");
    Objects.requireNonNull(persistenceFailure, "persistenceFailure");
    Optional<ChunkLoaderMarker.Data> data = ChunkLoaderMarker.get(plugin, block);
    if (data.isEmpty()) {
      cleanupAt(block, "break_missing_marker");
      return RemovalStart.MISSING;
    }
    BreakOutcome safeOutcome = outcome == null ? BreakOutcome.DROP_ITEM : outcome;
    UUID id = data.get().id();
    if (!isReady()) {
      removedDuringHydration.add(id);
      removedBlocksDuringHydration.add(BlockKey.of(block));
    }
    ChunkLoaderRecord record = byId.get(id);
    if (record == null || !record.sameBlock(block)) {
      long now = Instant.now().getEpochSecond();
      record =
          ChunkLoaderRecord.fromBlock(
              block,
              id,
              data.get().type(),
              data.get().placedByUuid(),
              data.get().placedByName(),
              radius(),
              data.get().enabled(),
              data.get().bypassLimits(),
              data.get().createdAt() > 0L ? data.get().createdAt() : now,
              now);
    }
    ChunkLoaderRecord removedRecord = record;
    boolean started =
        removalCoordinator.start(
            removalKey(id, block),
            () -> database.deleteChunkLoader(id),
            () -> commitBreak(player, block, removedRecord, safeOutcome, committed),
            err -> {
              Throwable failure = unwrap(err);
              logDeleteFailure(failure);
              rollbackHydrationRemoval(block, id);
              persistenceFailure.accept(failure);
            });
    return started ? RemovalStart.STARTED : RemovalStart.ALREADY_PENDING;
  }

  public ToggleResult setEnabled(Player player, Block block, boolean enabled) {
    if (!isReady()) {
      return ToggleResult.INITIALIZING;
    }
    Optional<ChunkLoaderMarker.Data> data = ChunkLoaderMarker.get(plugin, block);
    if (data.isEmpty()) {
      cleanupAt(block, "toggle_missing_marker");
      return ToggleResult.MISSING;
    }
    long now = Instant.now().getEpochSecond();
    UUID id = data.get().id();
    if (removalCoordinator.isPending(removalKey(id, block))) {
      return ToggleResult.INITIALIZING;
    }
    ChunkLoaderRecord current = byId.get(id);
    if (current == null) {
      current =
          ChunkLoaderRecord.fromBlock(
              block,
              id,
              data.get().type(),
              data.get().placedByUuid(),
              data.get().placedByName(),
              radius(),
              data.get().enabled(),
              data.get().bypassLimits(),
              data.get().createdAt() > 0L ? data.get().createdAt() : now,
              now);
    }
    if (current.enabled() == enabled) {
      return enabled ? ToggleResult.ALREADY_ENABLED : ToggleResult.ALREADY_DISABLED;
    }
    ChunkLoaderRecord updated = current.withEnabled(enabled, now);
    if (enabled && shouldTicket(updated, ownChunkLoaded(block)) && quotaStatus(updated) != null) {
      return ToggleResult.QUOTA_EXCEEDED;
    }
    ChunkLoaderMarker.set(
        plugin,
        block,
        updated.id(),
        updated.type(),
        updated.placedByUuid(),
        updated.placedByName(),
        updated.createdAt(),
        updated.enabled(),
        updated.bypassLimits());
    activate(updated, ownChunkLoaded(block));
    long persistenceOperation = persistenceGuard.begin(id);
    ChunkLoaderRecord previous = current;
    database
        .saveChunkLoader(updated)
        .whenComplete(
            (ignored, err) -> {
              if (err == null) {
                PluginTasks.runSyncIfEnabled(
                    plugin, () -> persistenceGuard.complete(id, persistenceOperation));
                return;
              }
              logSaveFailure(err);
              PluginTasks.runSyncIfEnabled(
                  plugin, () -> rollbackToggle(block, previous, updated, persistenceOperation));
            });
    auditLogger.log(
        enabled ? ChunkLoaderAuditEvent.ENABLE : ChunkLoaderAuditEvent.DISABLE,
        player,
        updated.id(),
        updated.type(),
        block);
    return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
  }

  public void cleanupAt(Block block, String reason) {
    cleanupAt(block, reason, () -> {}, ignored -> {});
  }

  public RemovalStart cleanupAt(
      Block block, String reason, Runnable committed, Consumer<Throwable> persistenceFailure) {
    Objects.requireNonNull(committed, "committed");
    Objects.requireNonNull(persistenceFailure, "persistenceFailure");
    if (block == null || block.getWorld() == null) {
      return RemovalStart.MISSING;
    }
    Optional<ChunkLoaderMarker.Data> marker = ChunkLoaderMarker.get(plugin, block);
    if (!isReady()) {
      removedBlocksDuringHydration.add(BlockKey.of(block));
    }
    UUID id = marker.map(ChunkLoaderMarker.Data::id).orElse(null);
    ChunkLoaderType auditType =
        marker.map(ChunkLoaderMarker.Data::type).orElse(ChunkLoaderType.defaultType());
    if (id == null) {
      id = byBlock.get(BlockKey.of(block));
    }
    if (id != null) {
      if (!isReady()) {
        removedDuringHydration.add(id);
      }
      ChunkLoaderRecord record = byId.get(id);
      if (record == null) {
        long now = Instant.now().getEpochSecond();
        ChunkLoaderType type =
            marker.map(ChunkLoaderMarker.Data::type).orElse(ChunkLoaderType.defaultType());
        boolean enabled = marker.map(ChunkLoaderMarker.Data::enabled).orElse(true);
        boolean bypassLimits = marker.map(ChunkLoaderMarker.Data::bypassLimits).orElse(false);
        record =
            ChunkLoaderRecord.fromBlock(
                block, id, type, null, null, radius(), enabled, bypassLimits, now, now);
      }
      auditType = record.type();
      UUID removedId = id;
      ChunkLoaderRecord removedRecord = record;
      ChunkLoaderType removedType = auditType;
      boolean started =
          removalCoordinator.start(
              removalKey(removedId, block),
              () -> database.deleteChunkLoader(removedId),
              () -> {
                if (commitCleanup(block, removedRecord, removedType, reason)) {
                  committed.run();
                }
              },
              err -> {
                Throwable failure = unwrap(err);
                logDeleteFailure(failure);
                rollbackHydrationRemoval(block, removedId);
                persistenceFailure.accept(failure);
              });
      return started ? RemovalStart.STARTED : RemovalStart.ALREADY_PENDING;
    } else {
      ChunkLoaderRemovalCoordinator.Key removalKey =
          ChunkLoaderRemovalCoordinator.Key.block(
              block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
      ChunkLoaderType removedType = auditType;
      boolean started =
          removalCoordinator.start(
              removalKey,
              () ->
                  database.deleteChunkLoaderAt(
                      block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()),
              () -> {
                if (commitLocationCleanup(block, removedType, reason)) {
                  committed.run();
                }
              },
              err -> {
                Throwable failure = unwrap(err);
                logDeleteFailure(failure);
                persistenceFailure.accept(failure);
              });
      return started ? RemovalStart.STARTED : RemovalStart.ALREADY_PENDING;
    }
  }

  private void commitBreak(
      Player player,
      Block block,
      ChunkLoaderRecord record,
      BreakOutcome outcome,
      Consumer<UUID> committed) {
    UUID id = record.id();
    ChunkLoaderRecord current = byId.get(id);
    if (current != null && !current.sameBlock(block)) {
      compensateSupersededRemoval(current, block, "break_superseded_by_relocation");
      return;
    }
    boolean targetUnchanged =
        ownChunkLoaded(block)
            && Carriers.matchesCarrier(block, carrier)
            && ChunkLoaderMarker.get(plugin, block)
                .map(marker -> id.equals(marker.id()))
                .orElse(false);
    deactivate(id, block);
    if (targetUnchanged) {
      ChunkLoaderMarker.clear(plugin, block);
    }
    recordRemovalObservation(record, outcome.status, player, outcome.source, outcome.reason);
    auditLogger.log(outcome.auditEvent, player, id, record.type(), block, outcome.reason);
    if (!targetUnchanged) {
      auditLogger.log(
          ChunkLoaderAuditEvent.CLEANUP,
          player,
          id,
          record.type(),
          block,
          "break_target_changed_before_commit");
      return;
    }
    try {
      committed.accept(id);
    } catch (RuntimeException error) {
      plugin.getLogger().log(Level.SEVERE, "Failed to finalize Chunk Loader break " + id, error);
    }
  }

  private boolean commitCleanup(
      Block block, ChunkLoaderRecord record, ChunkLoaderType auditType, String reason) {
    UUID id = record.id();
    ChunkLoaderRecord current = byId.get(id);
    if (current != null && !current.sameBlock(block)) {
      compensateSupersededRemoval(current, block, "cleanup_superseded_by_relocation");
      return false;
    }
    boolean targetUnchanged =
        ownChunkLoaded(block)
            && ChunkLoaderMarker.get(plugin, block)
                .map(marker -> id.equals(marker.id()))
                .orElse(false);
    deactivate(id, block);
    if (targetUnchanged) {
      ChunkLoaderMarker.clear(plugin, block);
    }
    recordRemovalObservation(record, ChunkLoaderRegistryStatus.LOST, null, "cleanup", reason);
    auditLogger.log(ChunkLoaderAuditEvent.CLEANUP, null, id, auditType, block, reason);
    return targetUnchanged;
  }

  private boolean commitLocationCleanup(Block block, ChunkLoaderType auditType, String reason) {
    boolean targetUnchanged =
        ownChunkLoaded(block) && ChunkLoaderMarker.get(plugin, block).isEmpty();
    auditLogger.log(ChunkLoaderAuditEvent.CLEANUP, null, null, auditType, block, reason);
    return targetUnchanged;
  }

  private void recordRemovalObservation(
      ChunkLoaderRecord record,
      ChunkLoaderRegistryStatus status,
      Player player,
      String source,
      String reason) {
    ChunkLoaderObservation observation =
        ChunkLoaderObservation.fromRecord(record, status, player, source, reason);
    if (observation == null) {
      return;
    }
    database
        .recordChunkLoaderObservation(observation)
        .exceptionally(
            err -> {
              logSaveFailure(err);
              return false;
            });
  }

  private void rollbackHydrationRemoval(Block block, UUID id) {
    removedDuringHydration.remove(id);
    removedBlocksDuringHydration.remove(BlockKey.of(block));
    if (isReady()) {
      reconcileBlock(block);
      return;
    }
    readyFuture()
        .whenComplete(
            (ignored, error) -> {
              if (error == null) {
                PluginTasks.runSyncIfEnabled(plugin, () -> reconcileBlock(block));
              }
            });
  }

  private void compensateSupersededRemoval(
      ChunkLoaderRecord relocated, Block sourceBlock, String reason) {
    database
        .saveChunkLoader(relocated)
        .exceptionally(
            error -> {
              logSaveFailure(error);
              return false;
            });
    auditLogger.log(
        ChunkLoaderAuditEvent.CLEANUP, null, relocated.id(), relocated.type(), sourceBlock, reason);
  }

  public void reconcileBlock(Block block) {
    if (!isReady() || block == null || block.getWorld() == null) {
      return;
    }
    Optional<ChunkLoaderMarker.Data> marker = ChunkLoaderMarker.get(plugin, block);
    boolean valid = marker.isPresent() && Carriers.matchesCarrier(block, carrier);
    if (!valid) {
      if (byBlock.containsKey(BlockKey.of(block))) {
        cleanupAt(block, "reconcile_missing_marker_or_carrier");
      }
      return;
    }
    ChunkLoaderMarker.Data data = marker.get();
    ChunkLoaderRemovalCoordinator.Key supersededRemoval =
        removalCoordinator.cancelByLoaderId(data.id());
    ChunkLoaderRecord existing = byId.get(data.id());
    if (existing != null && !existing.sameBlock(block)) {
      if (supersededRemoval == null) {
        ChunkLoaderMarker.clear(plugin, block);
        if (Carriers.matchesCarrier(block, carrier)) {
          block.setType(Material.AIR, false);
        }
        auditLogger.log(
            ChunkLoaderAuditEvent.CLEANUP, null, data.id(), data.type(), block, "duplicate_marker");
        return;
      }
      ChunkLoaderRecord relocated = recordFromMarker(block, data);
      activate(relocated, true);
      compensateSupersededRemoval(relocated, block, "pending_removal_relocated");
      return;
    }
    if (existing != null) {
      if (supersededRemoval != null) {
        compensateSupersededRemoval(existing, block, "pending_removal_restored");
      }
      return;
    }
    ChunkLoaderRecord restored = recordFromMarker(block, data);
    activate(restored, true);
    Location foundLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
    database
        .saveChunkLoader(restored)
        .whenComplete(
            (found, err) -> {
              if (err != null) {
                logSaveFailure(err);
                return;
              }
              if (Boolean.TRUE.equals(found)) {
                PluginTasks.runSyncIfEnabled(
                    plugin,
                    () ->
                        auditLogger.logFound(
                            ChunkLoaderAuditEvent.PLACE,
                            null,
                            data.id(),
                            data.type(),
                            "restore_from_marker",
                            foundLocation));
              }
            });
    auditLogger.log(
        ChunkLoaderAuditEvent.CLEANUP, null, data.id(), data.type(), block, "restore_from_marker");
  }

  private ChunkLoaderRecord recordFromMarker(Block block, ChunkLoaderMarker.Data data) {
    long now = Instant.now().getEpochSecond();
    return ChunkLoaderRecord.fromBlock(
        block,
        data.id(),
        data.type(),
        data.placedByUuid(),
        data.placedByName(),
        radius(),
        data.enabled(),
        data.bypassLimits(),
        data.createdAt() > 0L ? data.createdAt() : now,
        now);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    if (!isReady()) {
      return;
    }
    Chunk chunk = event.getChunk();
    if (chunk == null || chunk.getWorld() == null) {
      return;
    }
    validateRecordsInChunk(chunk);
    restoreMarkersInChunk(chunk);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
    if (!isReady()) {
      return;
    }
    UUID playerId = event.getPlayer().getUniqueId();
    for (ChunkLoaderRecord record : new ArrayList<>(byId.values())) {
      if (record.type() != ChunkLoaderType.PERSONAL_CHUNK_LOADER) continue;
      if (!record.enabled()) continue;
      if (!playerId.equals(record.placedByUuid())) continue;
      cancelPersonalRelease(record.id());
      activateTickets(record, "owner_join");
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent event) {
    if (!isReady()) {
      return;
    }
    UUID playerId = event.getPlayer().getUniqueId();
    for (ChunkLoaderRecord record : new ArrayList<>(byId.values())) {
      if (record.type() != ChunkLoaderType.PERSONAL_CHUNK_LOADER) continue;
      if (!playerId.equals(record.placedByUuid())) continue;
      if (!ticketLedger.isReserved(record.id())) continue;
      schedulePersonalRelease(record);
    }
  }

  private void rebuildFromDatabase(
      List<ChunkLoaderRecord> records, ChunkLoaderLifecycle.Generation startedGeneration) {
    if (!plugin.isEnabled()) {
      return;
    }
    startupActivationQueue.clear();
    List<ChunkLoaderRecord> safeRecords = records == null ? List.of() : records;
    for (ChunkLoaderRecord record : safeRecords.stream().sorted(startupOrder()).toList()) {
      if (removedDuringHydration.contains(record.id())
          || removedBlocksDuringHydration.contains(BlockKey.of(record))) {
        continue;
      }
      if (!registerHydratedRecord(record)) {
        continue;
      }
      World world = Bukkit.getWorld(record.worldId());
      boolean ownChunkLoaded =
          world != null && world.isChunkLoaded(record.chunkX(), record.chunkZ());
      if (shouldTicket(record, ownChunkLoaded)) {
        startupActivationQueue.addLast(record);
      }
      if (ownChunkLoaded) {
        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        validateRecord(record, block);
      }
    }
    if (startupActivationQueue.isEmpty()) {
      lifecycle.markReady(startedGeneration);
      return;
    }
    startupActivationTask =
        Bukkit.getScheduler()
            .runTaskTimer(plugin, () -> processStartupActivations(startedGeneration), 1L, 1L);
  }

  static Comparator<ChunkLoaderRecord> startupOrder() {
    return Comparator.comparingLong(ChunkLoaderRecord::createdAt)
        .thenComparing(ChunkLoaderRecord::id);
  }

  private boolean registerHydratedRecord(ChunkLoaderRecord record) {
    ChunkLoaderRecord existingId = byId.get(record.id());
    if (existingId != null) {
      plugin
          .getLogger()
          .warning(
              "Ignoring duplicate Chunk Loader id "
                  + record.id()
                  + " at "
                  + record.worldName()
                  + " "
                  + record.x()
                  + ","
                  + record.y()
                  + ","
                  + record.z()
                  + "; oldest record remains authoritative.");
      return false;
    }
    BlockKey blockKey = BlockKey.of(record);
    UUID existingPosition = byBlock.get(blockKey);
    if (existingPosition != null) {
      plugin
          .getLogger()
          .warning(
              "Ignoring Chunk Loader "
                  + record.id()
                  + " because position "
                  + record.worldName()
                  + " "
                  + record.x()
                  + ","
                  + record.y()
                  + ","
                  + record.z()
                  + " is already claimed by "
                  + existingPosition
                  + ".");
      return false;
    }
    byId.put(record.id(), record);
    byBlock.put(blockKey, record.id());
    return true;
  }

  private void processStartupActivations(ChunkLoaderLifecycle.Generation startedGeneration) {
    if (!lifecycle.isActive(startedGeneration)) {
      cancelStartupActivation();
      return;
    }
    int processed = 0;
    while (processed < STARTUP_ACTIVATIONS_PER_TICK) {
      ChunkLoaderRecord queued = startupActivationQueue.pollFirst();
      if (queued == null) {
        cancelStartupActivation();
        lifecycle.markReady(startedGeneration);
        return;
      }
      ChunkLoaderRecord current = byId.get(queued.id());
      if (queued.equals(current)) {
        World world = Bukkit.getWorld(current.worldId());
        boolean ownChunkLoaded =
            world != null && world.isChunkLoaded(current.chunkX(), current.chunkZ());
        if (shouldTicket(current, ownChunkLoaded)) {
          activateTickets(current, "startup_ticket");
        }
      }
      processed++;
    }
    if (startupActivationQueue.isEmpty()) {
      cancelStartupActivation();
      lifecycle.markReady(startedGeneration);
    }
  }

  private void cancelStartupActivation() {
    if (startupActivationTask != null) {
      startupActivationTask.cancel();
      startupActivationTask = null;
    }
  }

  private void validateRecordsInChunk(Chunk chunk) {
    for (ChunkLoaderRecord record : new ArrayList<>(byId.values())) {
      if (!record.worldId().equals(chunk.getWorld().getUID())) continue;
      if (record.chunkX() != chunk.getX() || record.chunkZ() != chunk.getZ()) continue;
      Block block = chunk.getWorld().getBlockAt(record.x(), record.y(), record.z());
      validateRecord(record, block);
      ChunkLoaderRecord current = byId.get(record.id());
      if (current != null && shouldTicket(current, true)) {
        activateTickets(current, "chunk_load");
      }
    }
  }

  private void validateRecord(ChunkLoaderRecord record, Block block) {
    boolean valid =
        block != null
            && Carriers.matchesCarrier(block, carrier)
            && ChunkLoaderMarker.get(plugin, block)
                .map(data -> record.id().equals(data.id()))
                .orElse(false);
    if (!valid) {
      cleanupAt(block, "stale_db_record");
    }
  }

  private void restoreMarkersInChunk(Chunk chunk) {
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      return;
    }
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, ignored) -> {
          if (ChunkLoaderMarker.isChunkLoader(plugin, block)) {
            reconcileBlock(block);
          }
        });
  }

  public ChunkLoaderRuntimeState runtimeState(UUID id) {
    if (id == null) {
      return ChunkLoaderRuntimeState.MISSING;
    }
    ChunkLoaderRecord record = byId.get(id);
    if (record == null) {
      return ChunkLoaderRuntimeState.MISSING;
    }
    if (!record.enabled()) {
      return ChunkLoaderRuntimeState.DISABLED;
    }
    if (!config.enabled()) {
      return ChunkLoaderRuntimeState.FEATURE_DISABLED;
    }
    if (record.type() == ChunkLoaderType.PERSONAL_CHUNK_LOADER
        && personalReleaseTasks.containsKey(id)) {
      return ChunkLoaderRuntimeState.OWNER_GRACE;
    }
    if (ticketLedger.isReserved(id)) {
      return ChunkLoaderRuntimeState.TICKETED;
    }
    if (quotaBlockedIds.contains(id)) {
      return ChunkLoaderRuntimeState.QUOTA_BLOCKED;
    }
    if (Bukkit.getWorld(record.worldId()) == null) {
      return ChunkLoaderRuntimeState.WORLD_UNAVAILABLE;
    }
    if (record.type() == ChunkLoaderType.PERSONAL_CHUNK_LOADER) {
      return ChunkLoaderRuntimeState.OWNER_OFFLINE;
    }
    if (record.type() == ChunkLoaderType.DORMANT_CHUNK_LOADER) {
      return ChunkLoaderRuntimeState.SLEEPING;
    }
    return ChunkLoaderRuntimeState.REGISTERED;
  }

  private void activate(ChunkLoaderRecord record, boolean ownChunkLoaded) {
    if (record == null) {
      return;
    }
    ChunkLoaderRecord previous = byId.put(record.id(), record);
    if (previous != null) {
      byBlock.remove(BlockKey.of(previous), previous.id());
      releaseTickets(previous);
      cancelPersonalRelease(previous.id());
    }
    byBlock.put(BlockKey.of(record), record.id());
    if (shouldTicket(record, ownChunkLoaded)) {
      activateTickets(record);
    }
  }

  private void deactivate(UUID id, Block fallbackBlock) {
    if (id == null) {
      if (fallbackBlock != null) {
        byBlock.remove(BlockKey.of(fallbackBlock));
      }
      return;
    }
    ChunkLoaderRecord record = byId.remove(id);
    persistenceGuard.cancel(id);
    quotaBlockedIds.remove(id);
    cancelPersonalRelease(id);
    if (record != null) {
      byBlock.remove(BlockKey.of(record), id);
      releaseTickets(record);
      return;
    }
    if (fallbackBlock != null) {
      byBlock.remove(BlockKey.of(fallbackBlock), id);
    }
  }

  private boolean shouldTicket(ChunkLoaderRecord record, boolean ownChunkLoaded) {
    if (!config.enabled()) {
      return false;
    }
    if (!record.enabled()) {
      return false;
    }
    return switch (record.type()) {
      case CHUNK_LOADER -> true;
      case PERSONAL_CHUNK_LOADER ->
          record.placedByUuid() != null && Bukkit.getPlayer(record.placedByUuid()) != null;
      case DORMANT_CHUNK_LOADER -> ownChunkLoaded;
    };
  }

  private void schedulePersonalRelease(ChunkLoaderRecord record) {
    cancelPersonalRelease(record.id());
    BukkitTask task =
        Bukkit.getScheduler()
            .runTaskLater(
                plugin,
                () -> {
                  personalReleaseTasks.remove(record.id());
                  Player owner = Bukkit.getPlayer(record.placedByUuid());
                  ChunkLoaderRecord current = byId.get(record.id());
                  if (current == null || current.type() != ChunkLoaderType.PERSONAL_CHUNK_LOADER) {
                    return;
                  }
                  if (owner != null && owner.isOnline()) {
                    return;
                  }
                  releaseTickets(current, "owner_grace_expired");
                },
                PERSONAL_GRACE_TICKS);
    personalReleaseTasks.put(record.id(), task);
  }

  private void cancelPersonalRelease(UUID id) {
    BukkitTask task = personalReleaseTasks.remove(id);
    if (task != null) {
      task.cancel();
    }
  }

  private void activateTickets(ChunkLoaderRecord record) {
    activateTickets(record, "runtime_ticket");
  }

  private void activateTickets(ChunkLoaderRecord record, String reason) {
    if (!config.enabled()
        || record == null
        || !record.enabled()
        || ticketLedger.isReserved(record.id())) {
      return;
    }
    World world = Bukkit.getWorld(record.worldId());
    if (world == null) {
      return;
    }
    ChunkLoaderTicketLedger.Reservation reservation = ticketLedger.reserve(record, radius());
    if (!reservation.allowed()) {
      if (quotaBlockedIds.add(record.id())) {
        plugin
            .getLogger()
            .warning(
                "Chunk Loader "
                    + record.id()
                    + " is inactive because limit "
                    + reservation.status()
                    + " was reached.");
      }
      return;
    }
    quotaBlockedIds.remove(record.id());
    for (ChunkLoaderTicketLedger.Ticket ticket : reservation.newTickets()) {
      world.addPluginChunkTicket(ticket.chunkX(), ticket.chunkZ(), plugin);
    }
    auditLogger.logTicket(ChunkLoaderAuditEvent.TICKET_ACQUIRE, record, reason);
  }

  private void releaseTickets(ChunkLoaderRecord record) {
    releaseTickets(record, "runtime_ticket");
  }

  private void releaseTickets(ChunkLoaderRecord record, String reason) {
    if (record == null || !ticketLedger.isReserved(record.id())) {
      return;
    }
    World world = Bukkit.getWorld(record.worldId());
    ChunkLoaderTicketLedger.Release release = ticketLedger.release(record.id());
    for (ChunkLoaderTicketLedger.Ticket ticket : release.removedTickets()) {
      if (world != null) {
        world.removePluginChunkTicket(ticket.chunkX(), ticket.chunkZ(), plugin);
      }
    }
    auditLogger.logTicket(ChunkLoaderAuditEvent.TICKET_RELEASE, record, reason);
  }

  private Void logDeleteFailure(Throwable err) {
    plugin.getLogger().log(Level.WARNING, "Failed to delete chunk loader record", unwrap(err));
    return null;
  }

  private Void logSaveFailure(Throwable err) {
    plugin.getLogger().log(Level.WARNING, "Failed to save chunk loader record", unwrap(err));
    return null;
  }

  private boolean ownChunkLoaded(Block block) {
    return block != null
        && block.getWorld() != null
        && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private static ChunkLoaderRemovalCoordinator.Key removalKey(UUID id, Block block) {
    return ChunkLoaderRemovalCoordinator.Key.loader(
        id, block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
  }

  private ChunkLoaderTicketLedger.Status quotaStatus(ChunkLoaderRecord record) {
    ChunkLoaderTicketLedger.Status status = ticketLedger.check(record, radius());
    return status == ChunkLoaderTicketLedger.Status.RESERVED
            || status == ChunkLoaderTicketLedger.Status.ALREADY_RESERVED
        ? null
        : status;
  }

  private void rollbackToggle(
      Block block,
      ChunkLoaderRecord previous,
      ChunkLoaderRecord failed,
      long persistenceOperation) {
    if (!persistenceGuard.complete(failed.id(), persistenceOperation) || !isReady()) {
      return;
    }
    ChunkLoaderRecord current = byId.get(failed.id());
    Optional<ChunkLoaderMarker.Data> marker = ChunkLoaderMarker.get(plugin, block);
    if (!failed.equals(current)
        || marker.isEmpty()
        || !failed.id().equals(marker.get().id())
        || failed.enabled() != marker.get().enabled()) {
      return;
    }
    ChunkLoaderMarker.set(
        plugin,
        block,
        previous.id(),
        previous.type(),
        previous.placedByUuid(),
        previous.placedByName(),
        previous.createdAt(),
        previous.enabled(),
        previous.bypassLimits());
    activate(previous, ownChunkLoaded(block));
    plugin
        .getLogger()
        .warning("Rolled back Chunk Loader " + failed.id() + " toggle after persistence failure.");
  }

  private Throwable unwrap(Throwable err) {
    Throwable current = err;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static boolean bypassesLimits(Player player) {
    return player != null && player.hasPermission(BYPASS_LIMITS_PERMISSION);
  }

  private record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    static BlockKey of(ChunkLoaderRecord record) {
      return new BlockKey(record.worldId(), record.x(), record.y(), record.z());
    }
  }
}
