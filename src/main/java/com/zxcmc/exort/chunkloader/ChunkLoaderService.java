package com.zxcmc.exort.chunkloader;

import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import java.time.Instant;
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
import java.util.concurrent.CompletionException;
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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChunkLoaderService implements Listener {
  private static final long PERSONAL_GRACE_TICKS = 5L * 60L * 20L;

  public enum ToggleResult {
    ENABLED,
    DISABLED,
    ALREADY_ENABLED,
    ALREADY_DISABLED,
    MISSING
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

  private final JavaPlugin plugin;
  private final Database database;
  private final Material carrier;
  private final ChunkLoaderConfig config;
  private final ChunkLoaderAuditLogger auditLogger;
  private final Map<UUID, ChunkLoaderRecord> byId = new HashMap<>();
  private final Map<BlockKey, UUID> byBlock = new HashMap<>();
  private final Map<TicketKey, Integer> ticketRefs = new HashMap<>();
  private final Set<UUID> ticketedIds = new HashSet<>();
  private final Map<UUID, BukkitTask> personalReleaseTasks = new HashMap<>();
  private boolean started;

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
                ChunkLoaderAuditConfig.fromConfig(null))
            : config;
    this.auditLogger =
        auditLogger == null
            ? new ChunkLoaderAuditLogger(plugin.getLogger(), this.config.audit())
            : auditLogger;
  }

  public void start() {
    if (started) {
      return;
    }
    started = true;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    database
        .listChunkLoaders()
        .whenComplete(
            (records, err) -> {
              if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Failed to load chunk loaders", unwrap(err));
                return;
              }
              PluginTasks.runSyncIfEnabled(plugin, () -> rebuildFromDatabase(records));
            });
  }

  public void stop() {
    if (started) {
      HandlerList.unregisterAll(this);
      started = false;
    }
    for (BukkitTask task : personalReleaseTasks.values()) {
      task.cancel();
    }
    personalReleaseTasks.clear();
    for (TicketKey key : List.copyOf(ticketRefs.keySet())) {
      World world = Bukkit.getWorld(key.worldId());
      if (world != null) {
        world.removePluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
      }
    }
    ticketRefs.clear();
    ticketedIds.clear();
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

  public boolean canPlace(UUID id, Block block) {
    if (id == null || block == null || block.getWorld() == null) {
      return false;
    }
    ChunkLoaderRecord existing = byId.get(id);
    if (existing != null && !existing.sameBlock(block)) {
      return false;
    }
    UUID byPosition = byBlock.get(BlockKey.of(block));
    return byPosition == null || byPosition.equals(id);
  }

  public boolean isActiveLoaderId(UUID id) {
    return id != null && byId.containsKey(id);
  }

  public boolean place(Player player, Block block, UUID id) {
    return place(player, block, id, ChunkLoaderType.defaultType());
  }

  public boolean place(Player player, Block block, UUID id, ChunkLoaderType type) {
    if (!canPlace(id, block)) {
      return false;
    }
    ChunkLoaderType safeType = type == null ? ChunkLoaderType.defaultType() : type;
    ChunkLoaderRecord record = ChunkLoaderRecord.placed(block, id, player, radius(), safeType);
    ChunkLoaderMarker.set(
        plugin,
        block,
        id,
        safeType,
        player == null ? null : player.getUniqueId(),
        player == null ? null : player.getName(),
        record.createdAt(),
        record.enabled());
    activate(record, true);
    Location foundLocation = block.getLocation().add(0.5D, 1.0D, 0.5D);
    database
        .saveChunkLoader(record)
        .whenComplete(
            (found, err) -> {
              if (err != null) {
                plugin
                    .getLogger()
                    .log(Level.WARNING, "Failed to persist chunk loader " + id, unwrap(err));
                return;
              }
              if (Boolean.TRUE.equals(found)) {
                PluginTasks.runSyncIfEnabled(
                    plugin,
                    () ->
                        auditLogger.logFound(
                            ChunkLoaderAuditEvent.PLACE,
                            player,
                            id,
                            safeType,
                            "place",
                            foundLocation));
              }
            });
    auditLogger.log(ChunkLoaderAuditEvent.PLACE, player, id, safeType, block);
    return true;
  }

  public Optional<UUID> breakLoader(Player player, Block block, BreakOutcome outcome) {
    Optional<ChunkLoaderMarker.Data> data = ChunkLoaderMarker.get(plugin, block);
    if (data.isEmpty()) {
      cleanupAt(block, "break_missing_marker");
      return Optional.empty();
    }
    BreakOutcome safeOutcome = outcome == null ? BreakOutcome.DROP_ITEM : outcome;
    UUID id = data.get().id();
    ChunkLoaderRecord record = byId.get(id);
    if (record == null) {
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
              data.get().createdAt() > 0L ? data.get().createdAt() : now,
              now);
    }
    deactivate(id, block);
    database.deleteChunkLoader(id).exceptionally(this::logDeleteFailure);
    ChunkLoaderObservation observation =
        ChunkLoaderObservation.fromRecord(
            record, safeOutcome.status, player, safeOutcome.source, safeOutcome.reason);
    if (observation != null) {
      database
          .recordChunkLoaderObservation(observation)
          .exceptionally(
              err -> {
                logSaveFailure(err);
                return false;
              });
    }
    ChunkLoaderMarker.clear(plugin, block);
    auditLogger.log(safeOutcome.auditEvent, player, id, record.type(), block, safeOutcome.reason);
    return Optional.of(id);
  }

  public ToggleResult setEnabled(Player player, Block block, boolean enabled) {
    Optional<ChunkLoaderMarker.Data> data = ChunkLoaderMarker.get(plugin, block);
    if (data.isEmpty()) {
      cleanupAt(block, "toggle_missing_marker");
      return ToggleResult.MISSING;
    }
    long now = Instant.now().getEpochSecond();
    UUID id = data.get().id();
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
              data.get().createdAt() > 0L ? data.get().createdAt() : now,
              now);
    }
    if (current.enabled() == enabled) {
      return enabled ? ToggleResult.ALREADY_ENABLED : ToggleResult.ALREADY_DISABLED;
    }
    ChunkLoaderRecord updated = current.withEnabled(enabled, now);
    ChunkLoaderMarker.set(
        plugin,
        block,
        updated.id(),
        updated.type(),
        updated.placedByUuid(),
        updated.placedByName(),
        updated.createdAt(),
        updated.enabled());
    activate(updated, ownChunkLoaded(block));
    database
        .saveChunkLoader(updated)
        .exceptionally(
            err -> {
              logSaveFailure(err);
              return false;
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
    if (block == null || block.getWorld() == null) {
      return;
    }
    Optional<ChunkLoaderMarker.Data> marker = ChunkLoaderMarker.get(plugin, block);
    UUID id = marker.map(ChunkLoaderMarker.Data::id).orElse(null);
    ChunkLoaderType auditType =
        marker.map(ChunkLoaderMarker.Data::type).orElse(ChunkLoaderType.defaultType());
    if (id == null) {
      id = byBlock.get(BlockKey.of(block));
    }
    if (id != null) {
      ChunkLoaderRecord record = byId.get(id);
      if (record == null) {
        long now = Instant.now().getEpochSecond();
        ChunkLoaderType type =
            marker.map(ChunkLoaderMarker.Data::type).orElse(ChunkLoaderType.defaultType());
        boolean enabled = marker.map(ChunkLoaderMarker.Data::enabled).orElse(true);
        record =
            ChunkLoaderRecord.fromBlock(block, id, type, null, null, radius(), enabled, now, now);
      }
      auditType = record.type();
      deactivate(id, block);
      database.deleteChunkLoader(id).exceptionally(this::logDeleteFailure);
      ChunkLoaderObservation observation =
          ChunkLoaderObservation.fromRecord(
              record, ChunkLoaderRegistryStatus.LOST, null, "cleanup", reason);
      if (observation != null) {
        database
            .recordChunkLoaderObservation(observation)
            .exceptionally(
                err -> {
                  logSaveFailure(err);
                  return false;
                });
      }
    } else {
      database
          .deleteChunkLoaderAt(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ())
          .exceptionally(this::logDeleteFailure);
    }
    ChunkLoaderMarker.clear(plugin, block);
    auditLogger.log(ChunkLoaderAuditEvent.CLEANUP, null, id, auditType, block, reason);
  }

  public void reconcileBlock(Block block) {
    if (block == null || block.getWorld() == null) {
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
    ChunkLoaderRecord existing = byId.get(data.id());
    if (existing != null && !existing.sameBlock(block)) {
      ChunkLoaderMarker.clear(plugin, block);
      auditLogger.log(
          ChunkLoaderAuditEvent.CLEANUP, null, data.id(), data.type(), block, "duplicate_marker");
      return;
    }
    if (existing != null) {
      return;
    }
    long now = Instant.now().getEpochSecond();
    ChunkLoaderRecord restored =
        ChunkLoaderRecord.fromBlock(
            block,
            data.id(),
            data.type(),
            data.placedByUuid(),
            data.placedByName(),
            radius(),
            data.enabled(),
            data.createdAt() > 0L ? data.createdAt() : now,
            now);
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

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    Chunk chunk = event.getChunk();
    if (chunk == null || chunk.getWorld() == null) {
      return;
    }
    validateRecordsInChunk(chunk);
    restoreMarkersInChunk(chunk);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerJoin(PlayerJoinEvent event) {
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
    UUID playerId = event.getPlayer().getUniqueId();
    for (ChunkLoaderRecord record : new ArrayList<>(byId.values())) {
      if (record.type() != ChunkLoaderType.PERSONAL_CHUNK_LOADER) continue;
      if (!playerId.equals(record.placedByUuid())) continue;
      if (!ticketedIds.contains(record.id())) continue;
      schedulePersonalRelease(record);
    }
  }

  private void rebuildFromDatabase(List<ChunkLoaderRecord> records) {
    if (!plugin.isEnabled()) {
      return;
    }
    List<ChunkLoaderRecord> safeRecords = records == null ? List.of() : records;
    for (ChunkLoaderRecord record : safeRecords.stream().sorted(recordOrder()).toList()) {
      World world = Bukkit.getWorld(record.worldId());
      boolean ownChunkLoaded =
          world != null && world.isChunkLoaded(record.chunkX(), record.chunkZ());
      activate(record, ownChunkLoaded);
      if (ownChunkLoaded) {
        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        validateRecord(record, block);
      }
    }
  }

  private Comparator<ChunkLoaderRecord> recordOrder() {
    return Comparator.comparing(ChunkLoaderRecord::worldName)
        .thenComparingInt(ChunkLoaderRecord::x)
        .thenComparingInt(ChunkLoaderRecord::y)
        .thenComparingInt(ChunkLoaderRecord::z);
  }

  private void validateRecordsInChunk(Chunk chunk) {
    for (ChunkLoaderRecord record : new ArrayList<>(byId.values())) {
      if (!record.worldId().equals(chunk.getWorld().getUID())) continue;
      if (record.chunkX() != chunk.getX() || record.chunkZ() != chunk.getZ()) continue;
      Block block = chunk.getWorld().getBlockAt(record.x(), record.y(), record.z());
      validateRecord(record, block);
      ChunkLoaderRecord current = byId.get(record.id());
      if (current != null
          && current.enabled()
          && current.type() == ChunkLoaderType.DORMANT_CHUNK_LOADER) {
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
    if (ticketedIds.contains(id)) {
      return ChunkLoaderRuntimeState.TICKETED;
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
      byBlock.remove(BlockKey.of(previous));
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
    cancelPersonalRelease(id);
    if (record != null) {
      byBlock.remove(BlockKey.of(record));
      releaseTickets(record);
      return;
    }
    if (fallbackBlock != null) {
      byBlock.remove(BlockKey.of(fallbackBlock));
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
        || ticketedIds.contains(record.id())) {
      return;
    }
    World world = Bukkit.getWorld(record.worldId());
    if (world == null) {
      return;
    }
    ticketedIds.add(record.id());
    for (ChunkLoaderArea.ChunkCoord chunk :
        ChunkLoaderArea.square(record.chunkX(), record.chunkZ(), radius())) {
      TicketKey key = new TicketKey(record.worldId(), chunk.x(), chunk.z());
      int refs = ticketRefs.getOrDefault(key, 0);
      if (refs == 0) {
        world.addPluginChunkTicket(chunk.x(), chunk.z(), plugin);
      }
      ticketRefs.put(key, refs + 1);
    }
    auditLogger.logTicket(ChunkLoaderAuditEvent.TICKET_ACQUIRE, record, reason);
  }

  private void releaseTickets(ChunkLoaderRecord record) {
    releaseTickets(record, "runtime_ticket");
  }

  private void releaseTickets(ChunkLoaderRecord record, String reason) {
    if (record == null || !ticketedIds.remove(record.id())) {
      return;
    }
    World world = Bukkit.getWorld(record.worldId());
    for (ChunkLoaderArea.ChunkCoord chunk :
        ChunkLoaderArea.square(record.chunkX(), record.chunkZ(), radius())) {
      TicketKey key = new TicketKey(record.worldId(), chunk.x(), chunk.z());
      int refs = ticketRefs.getOrDefault(key, 0);
      if (refs <= 1) {
        ticketRefs.remove(key);
        if (world != null) {
          world.removePluginChunkTicket(chunk.x(), chunk.z(), plugin);
        }
      } else {
        ticketRefs.put(key, refs - 1);
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

  private Throwable unwrap(Throwable err) {
    Throwable current = err;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private record BlockKey(UUID worldId, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    static BlockKey of(ChunkLoaderRecord record) {
      return new BlockKey(record.worldId(), record.x(), record.y(), record.z());
    }
  }

  private record TicketKey(UUID worldId, int chunkX, int chunkZ) {}
}
