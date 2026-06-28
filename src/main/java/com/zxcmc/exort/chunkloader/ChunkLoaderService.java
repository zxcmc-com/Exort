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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkLoaderService implements Listener {
  private final JavaPlugin plugin;
  private final Database database;
  private final Material carrier;
  private final ChunkLoaderConfig config;
  private final ChunkLoaderAuditLogger auditLogger;
  private final Map<UUID, ChunkLoaderRecord> byId = new HashMap<>();
  private final Map<BlockKey, UUID> byBlock = new HashMap<>();
  private final Map<TicketKey, Integer> ticketRefs = new HashMap<>();
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
                ChunkLoaderConfig.DEFAULT_RADIUS, ChunkLoaderAuditConfig.fromConfig(null))
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
    for (TicketKey key : List.copyOf(ticketRefs.keySet())) {
      World world = Bukkit.getWorld(key.worldId());
      if (world != null) {
        world.removePluginChunkTicket(key.chunkX(), key.chunkZ(), plugin);
      }
    }
    ticketRefs.clear();
    byId.clear();
    byBlock.clear();
    auditLogger.close();
  }

  public int radius() {
    return config.radius();
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

  public boolean place(Player player, Block block, UUID id) {
    if (!canPlace(id, block)) {
      return false;
    }
    ChunkLoaderRecord record = ChunkLoaderRecord.placed(block, id, player, radius());
    ChunkLoaderMarker.set(
        plugin,
        block,
        id,
        player == null ? null : player.getUniqueId(),
        player == null ? null : player.getName(),
        record.createdAt());
    activate(record);
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
                            ChunkLoaderAuditEvent.PLACE, player, id, "place", foundLocation));
              }
            });
    auditLogger.log(ChunkLoaderAuditEvent.PLACE, player, id, block);
    return true;
  }

  public Optional<UUID> breakLoader(Player player, Block block, boolean dropsItem) {
    Optional<ChunkLoaderMarker.Data> data = ChunkLoaderMarker.get(plugin, block);
    if (data.isEmpty()) {
      cleanupAt(block, "break_missing_marker");
      return Optional.empty();
    }
    UUID id = data.get().id();
    ChunkLoaderRecord record = byId.get(id);
    if (record == null) {
      long now = Instant.now().getEpochSecond();
      record =
          ChunkLoaderRecord.fromBlock(
              block,
              id,
              data.get().placedByUuid(),
              data.get().placedByName(),
              radius(),
              data.get().createdAt() > 0L ? data.get().createdAt() : now,
              now);
    }
    deactivate(id, block);
    database.deleteChunkLoader(id).exceptionally(this::logDeleteFailure);
    ChunkLoaderObservation observation =
        ChunkLoaderObservation.fromRecord(
            record,
            dropsItem ? ChunkLoaderRegistryStatus.ITEM : ChunkLoaderRegistryStatus.REMOVED,
            player,
            "break",
            dropsItem ? "item_drop" : "creative_break");
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
    auditLogger.log(ChunkLoaderAuditEvent.BREAK, player, id, block);
    return Optional.of(id);
  }

  public void cleanupAt(Block block, String reason) {
    if (block == null || block.getWorld() == null) {
      return;
    }
    UUID id = ChunkLoaderMarker.get(plugin, block).map(ChunkLoaderMarker.Data::id).orElse(null);
    if (id == null) {
      id = byBlock.get(BlockKey.of(block));
    }
    if (id != null) {
      ChunkLoaderRecord record = byId.get(id);
      if (record == null) {
        long now = Instant.now().getEpochSecond();
        record = ChunkLoaderRecord.fromBlock(block, id, null, null, radius(), now, now);
      }
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
    auditLogger.log(ChunkLoaderAuditEvent.CLEANUP, null, id, block, reason);
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
      auditLogger.log(ChunkLoaderAuditEvent.CLEANUP, null, data.id(), block, "duplicate_marker");
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
            data.placedByUuid(),
            data.placedByName(),
            radius(),
            data.createdAt() > 0L ? data.createdAt() : now,
            now);
    activate(restored);
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
                            "restore_from_marker",
                            foundLocation));
              }
            });
    auditLogger.log(ChunkLoaderAuditEvent.CLEANUP, null, data.id(), block, "restore_from_marker");
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

  private void rebuildFromDatabase(List<ChunkLoaderRecord> records) {
    if (!plugin.isEnabled()) {
      return;
    }
    List<ChunkLoaderRecord> safeRecords = records == null ? List.of() : records;
    for (ChunkLoaderRecord record : safeRecords.stream().sorted(recordOrder()).toList()) {
      activate(record);
      World world = Bukkit.getWorld(record.worldId());
      if (world != null && world.isChunkLoaded(record.chunkX(), record.chunkZ())) {
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

  private void activate(ChunkLoaderRecord record) {
    if (record == null) {
      return;
    }
    ChunkLoaderRecord previous = byId.put(record.id(), record);
    if (previous != null) {
      byBlock.remove(BlockKey.of(previous));
      releaseTickets(previous);
    }
    byBlock.put(BlockKey.of(record), record.id());
    addTickets(record);
  }

  private void deactivate(UUID id, Block fallbackBlock) {
    if (id == null) {
      if (fallbackBlock != null) {
        byBlock.remove(BlockKey.of(fallbackBlock));
      }
      return;
    }
    ChunkLoaderRecord record = byId.remove(id);
    if (record != null) {
      byBlock.remove(BlockKey.of(record));
      releaseTickets(record);
      return;
    }
    if (fallbackBlock != null) {
      byBlock.remove(BlockKey.of(fallbackBlock));
    }
  }

  private void addTickets(ChunkLoaderRecord record) {
    World world = Bukkit.getWorld(record.worldId());
    if (world == null) {
      return;
    }
    for (ChunkLoaderArea.ChunkCoord chunk :
        ChunkLoaderArea.square(record.chunkX(), record.chunkZ(), radius())) {
      TicketKey key = new TicketKey(record.worldId(), chunk.x(), chunk.z());
      int refs = ticketRefs.getOrDefault(key, 0);
      if (refs == 0) {
        world.addPluginChunkTicket(chunk.x(), chunk.z(), plugin);
      }
      ticketRefs.put(key, refs + 1);
    }
  }

  private void releaseTickets(ChunkLoaderRecord record) {
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
  }

  private Void logDeleteFailure(Throwable err) {
    plugin.getLogger().log(Level.WARNING, "Failed to delete chunk loader record", unwrap(err));
    return null;
  }

  private Void logSaveFailure(Throwable err) {
    plugin.getLogger().log(Level.WARNING, "Failed to save chunk loader record", unwrap(err));
    return null;
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
