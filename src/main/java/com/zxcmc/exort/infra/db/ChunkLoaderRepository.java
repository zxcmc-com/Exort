package com.zxcmc.exort.infra.db;

import com.zxcmc.exort.chunkloader.ChunkLoaderObservation;
import com.zxcmc.exort.chunkloader.ChunkLoaderRecord;
import com.zxcmc.exort.chunkloader.ChunkLoaderRegistryRecord;
import com.zxcmc.exort.chunkloader.ChunkLoaderRegistryStatus;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.debug.PerfStats;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ChunkLoaderRepository implements AutoCloseable {
  private static final int AUDIT_OBSERVATION_CAPACITY = 4_096;
  private static final int AUDIT_OBSERVATION_BATCH = 128;

  private final SqliteDatabase database;
  private final Logger logger;
  private final AtomicBoolean auditDrainScheduled = new AtomicBoolean();
  private final Map<UUID, PendingAuditObservation> pendingAuditObservations =
      new ConcurrentHashMap<>();

  ChunkLoaderRepository(SqliteDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = logger;
  }

  public CompletableFuture<Boolean> saveChunkLoader(ChunkLoaderRecord record) {
    Objects.requireNonNull(record, "record");
    return database.supply(
        "save chunk loader " + record.id(),
        () -> {
          boolean found;
          try {
            database.connection().setAutoCommit(false);
            List<UUID> replacedIds = chunkLoaderIdsAtPositionExcept(record);
            try (PreparedStatement delete =
                database
                    .connection()
                    .prepareStatement(
                        "DELETE FROM chunk_loaders WHERE world = ? AND x = ? AND y = ? AND z = ?"
                            + " AND id <> ?")) {
              delete.setString(1, record.worldId().toString());
              delete.setInt(2, record.x());
              delete.setInt(3, record.y());
              delete.setInt(4, record.z());
              delete.setString(5, record.id().toString());
              delete.executeUpdate();
            }
            String sql =
                """
                INSERT INTO chunk_loaders(
                    id, loader_type, world, world_key, world_name, x, y, z, chunk_x, chunk_z,
                    placed_by_uuid, placed_by_name, radius, enabled, bypass_limits, created_at,
                    updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    loader_type = excluded.loader_type,
                    world = excluded.world,
                    world_key = excluded.world_key,
                    world_name = excluded.world_name,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    chunk_x = excluded.chunk_x,
                    chunk_z = excluded.chunk_z,
                    placed_by_uuid = excluded.placed_by_uuid,
                    placed_by_name = excluded.placed_by_name,
                    radius = excluded.radius,
                    enabled = excluded.enabled,
                    bypass_limits = excluded.bypass_limits,
                    updated_at = excluded.updated_at
                """;
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
              bindChunkLoader(ps, record);
              ps.executeUpdate();
            }
            found = upsertChunkLoaderRegistryActive(record);
            long now = Instant.now().getEpochSecond();
            for (UUID replacedId : replacedIds) {
              upsertChunkLoaderRegistryObservation(
                  new ChunkLoaderObservation(
                      replacedId,
                      ChunkLoaderRegistryStatus.LOST,
                      record.worldId(),
                      record.worldKey(),
                      record.worldName(),
                      record.x() + 0.5D,
                      record.y() + 1.0D,
                      record.z() + 0.5D,
                      record.placedByUuid(),
                      record.placedByName(),
                      "position_replaced",
                      "position_replaced",
                      now));
            }
            database.connection().commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to save chunk loader " + record.id(), e);
            try {
              database.connection().rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback chunk loader save", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              database.connection().setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
          return found;
        });
  }

  public CompletableFuture<Boolean> recordChunkLoaderObservation(
      ChunkLoaderObservation observation) {
    Objects.requireNonNull(observation, "observation");
    return database.supply(
        "record chunk loader observation " + observation.id(),
        () -> {
          try {
            return upsertChunkLoaderRegistryObservation(observation);
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to record chunk loader observation " + observation.id(), e);
            throw new CompletionException(e);
          }
        });
  }

  /** Coalesced best-effort audit path that cannot crowd out integrity-sensitive DB work. */
  public CompletableFuture<Boolean> recordChunkLoaderObservationBestEffort(
      ChunkLoaderObservation observation) {
    Objects.requireNonNull(observation, "observation");
    if (database.isClosing()) {
      return CompletableFuture.failedFuture(
          new RejectedExecutionException("Database is closing; audit observation rejected"));
    }
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    synchronized (pendingAuditObservations) {
      if (!pendingAuditObservations.containsKey(observation.id())
          && pendingAuditObservations.size() >= AUDIT_OBSERVATION_CAPACITY) {
        PerfStats.incrementCounter("storage-db.auditDropped");
        return CompletableFuture.completedFuture(false);
      }
      PendingAuditObservation previous =
          pendingAuditObservations.put(
              observation.id(), new PendingAuditObservation(observation, result));
      if (previous != null) previous.result().complete(false);
    }
    scheduleAuditDrain();
    return result;
  }

  private void scheduleAuditDrain() {
    if (database.isClosing() || !auditDrainScheduled.compareAndSet(false, true)) return;
    if (!database.execute("drain chunk loader audit observations", this::drainAuditObservations)) {
      auditDrainScheduled.set(false);
      dropPendingAuditObservations();
    }
  }

  private void dropPendingAuditObservations() {
    List<PendingAuditObservation> dropped;
    synchronized (pendingAuditObservations) {
      dropped = List.copyOf(pendingAuditObservations.values());
      pendingAuditObservations.clear();
    }
    dropped.forEach(pending -> pending.result().complete(false));
    for (int i = 0; i < dropped.size(); i++) {
      PerfStats.incrementCounter("storage-db.auditDropped");
    }
  }

  private void drainAuditObservations() {
    try {
      int processed = 0;
      for (var entry : pendingAuditObservations.entrySet()) {
        if (processed++ >= AUDIT_OBSERVATION_BATCH) break;
        PendingAuditObservation pending = entry.getValue();
        if (!pendingAuditObservations.remove(entry.getKey(), pending)) continue;
        try {
          pending.result().complete(upsertChunkLoaderRegistryObservation(pending.observation()));
        } catch (SQLException error) {
          log(Level.WARNING, "Failed to persist coalesced Chunk Loader audit observation", error);
          pending.result().completeExceptionally(error);
        }
      }
    } finally {
      auditDrainScheduled.set(false);
      if (!pendingAuditObservations.isEmpty()) scheduleAuditDrain();
    }
  }

  public CompletableFuture<List<ChunkLoaderRecord>> listChunkLoaders() {
    return database.supply(
        "list chunk loaders",
        () -> {
          List<ChunkLoaderRecord> records = new ArrayList<>();
          String sql =
              """
              SELECT id, loader_type, world, world_key, world_name, x, y, z, chunk_x, chunk_z,
                     placed_by_uuid, placed_by_name, radius, enabled, bypass_limits, created_at,
                     updated_at
              FROM chunk_loaders
              """;
          try (PreparedStatement ps = database.connection().prepareStatement(sql);
              ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              ChunkLoaderRecord record = readChunkLoader(rs);
              if (record != null) {
                records.add(record);
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to list chunk loaders", e);
            throw new CompletionException(e);
          }
          return List.copyOf(records);
        });
  }

  public CompletableFuture<List<ChunkLoaderRegistryRecord>> listChunkLoaderRegistry() {
    return database.supply(
        "list chunk loader registry",
        () -> {
          List<ChunkLoaderRegistryRecord> records = new ArrayList<>();
          String sql =
              """
              SELECT id, loader_type, status, placed_by_uuid, placed_by_name,
                     first_world, first_world_key, first_world_name,
                     first_x, first_y, first_z, first_chunk_x, first_chunk_z,
                     last_placed_world, last_placed_world_key, last_placed_world_name,
                     last_placed_x, last_placed_y, last_placed_z,
                     last_placed_chunk_x, last_placed_chunk_z,
                     last_seen_world, last_seen_world_key, last_seen_world_name,
                     last_seen_x, last_seen_y, last_seen_z,
                     last_actor_uuid, last_actor_name, last_source, last_reason,
                     created_at, updated_at, last_seen_at
              FROM chunk_loader_registry
              ORDER BY created_at DESC, updated_at DESC, id DESC
              """;
          try (PreparedStatement ps = database.connection().prepareStatement(sql);
              ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              ChunkLoaderRegistryRecord record = readChunkLoaderRegistry(rs);
              if (record != null) {
                records.add(record);
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to list chunk loader registry", e);
            throw new CompletionException(e);
          }
          return List.copyOf(records);
        });
  }

  public CompletableFuture<Void> deleteChunkLoader(UUID id) {
    Objects.requireNonNull(id, "id");
    return database.run(
        "delete chunk loader " + id,
        () -> {
          try (PreparedStatement ps =
              database.connection().prepareStatement("DELETE FROM chunk_loaders WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to delete chunk loader " + id, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> deleteChunkLoaderAt(UUID worldId, int x, int y, int z) {
    Objects.requireNonNull(worldId, "worldId");
    return database.run(
        "delete chunk loader at " + worldId + " " + x + "," + y + "," + z,
        () -> {
          try (PreparedStatement ps =
              database
                  .connection()
                  .prepareStatement(
                      "DELETE FROM chunk_loaders WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, worldId.toString());
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to delete chunk loader at " + worldId, e);
            throw new CompletionException(e);
          }
        });
  }

  private boolean upsertChunkLoaderRegistryActive(ChunkLoaderRecord record) throws SQLException {
    Optional<ChunkLoaderRegistryStatus> previous = chunkLoaderRegistryStatus(record.id());
    long now = Instant.now().getEpochSecond();
    if (previous.isPresent()) {
      updateChunkLoaderRegistryActive(record, now);
    } else {
      insertChunkLoaderRegistryActive(record, now);
    }
    return previous.orElse(null) == ChunkLoaderRegistryStatus.LOST;
  }

  private boolean upsertChunkLoaderRegistryObservation(ChunkLoaderObservation observation)
      throws SQLException {
    Optional<ChunkLoaderRegistryStatus> previous = chunkLoaderRegistryStatus(observation.id());
    if (previous.isPresent()) {
      updateChunkLoaderRegistryObservation(observation);
    } else {
      insertChunkLoaderRegistryObservationFallback(observation);
    }
    return previous.orElse(null) == ChunkLoaderRegistryStatus.LOST
        && observation.status().isObserved();
  }

  private Optional<ChunkLoaderRegistryStatus> chunkLoaderRegistryStatus(UUID id)
      throws SQLException {
    try (PreparedStatement ps =
        database
            .connection()
            .prepareStatement("SELECT status FROM chunk_loader_registry WHERE id = ?")) {
      ps.setString(1, id.toString());
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next()
            ? Optional.of(ChunkLoaderRegistryStatus.fromDb(rs.getString("status")))
            : Optional.empty();
      }
    }
  }

  private List<UUID> chunkLoaderIdsAtPositionExcept(ChunkLoaderRecord record) throws SQLException {
    List<UUID> ids = new ArrayList<>();
    try (PreparedStatement ps =
        database
            .connection()
            .prepareStatement(
                "SELECT id FROM chunk_loaders WHERE world = ? AND x = ? AND y = ? AND z = ? AND id"
                    + " <> ?")) {
      ps.setString(1, record.worldId().toString());
      ps.setInt(2, record.x());
      ps.setInt(3, record.y());
      ps.setInt(4, record.z());
      ps.setString(5, record.id().toString());
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          try {
            ids.add(UUID.fromString(rs.getString("id")));
          } catch (IllegalArgumentException e) {
            logWarning("Skipping invalid chunk loader id at replaced position: " + e.getMessage());
          }
        }
      }
    }
    return ids;
  }

  private void updateChunkLoaderRegistryActive(ChunkLoaderRecord record, long now)
      throws SQLException {
    String sql =
        """
        UPDATE chunk_loader_registry SET
            loader_type = ?,
            status = ?,
            placed_by_uuid = COALESCE(placed_by_uuid, ?),
            placed_by_name = CASE
                WHEN placed_by_name IS NULL OR placed_by_name = '' OR placed_by_name = 'unknown'
                THEN ?
                ELSE placed_by_name
            END,
            last_placed_world = ?,
            last_placed_world_key = ?,
            last_placed_world_name = ?,
            last_placed_x = ?,
            last_placed_y = ?,
            last_placed_z = ?,
            last_placed_chunk_x = ?,
            last_placed_chunk_z = ?,
            last_seen_world = ?,
            last_seen_world_key = ?,
            last_seen_world_name = ?,
            last_seen_x = ?,
            last_seen_y = ?,
            last_seen_z = ?,
            last_actor_uuid = ?,
            last_actor_name = ?,
            last_source = ?,
            last_reason = NULL,
            updated_at = ?,
            last_seen_at = ?
        WHERE id = ?
        """;
    try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
      int i = 1;
      ps.setString(i++, record.type().id());
      ps.setString(i++, ChunkLoaderRegistryStatus.ACTIVE.dbValue());
      setNullableUuid(ps, i++, record.placedByUuid());
      ps.setString(i++, record.placedByName());
      ps.setString(i++, record.worldId().toString());
      ps.setString(i++, record.worldKey());
      ps.setString(i++, record.worldName());
      ps.setInt(i++, record.x());
      ps.setInt(i++, record.y());
      ps.setInt(i++, record.z());
      ps.setInt(i++, record.chunkX());
      ps.setInt(i++, record.chunkZ());
      ps.setString(i++, record.worldId().toString());
      ps.setString(i++, record.worldKey());
      ps.setString(i++, record.worldName());
      ps.setDouble(i++, record.x() + 0.5D);
      ps.setDouble(i++, record.y() + 1.0D);
      ps.setDouble(i++, record.z() + 0.5D);
      setNullableUuid(ps, i++, record.placedByUuid());
      ps.setString(i++, record.placedByName());
      ps.setString(i++, "place");
      ps.setLong(i++, now);
      ps.setLong(i++, now);
      ps.setString(i, record.id().toString());
      ps.executeUpdate();
    }
  }

  private void insertChunkLoaderRegistryActive(ChunkLoaderRecord record, long now)
      throws SQLException {
    String sql =
        """
        INSERT INTO chunk_loader_registry(
            id, loader_type, status, placed_by_uuid, placed_by_name,
            first_world, first_world_key, first_world_name, first_x, first_y, first_z,
            first_chunk_x, first_chunk_z,
            last_placed_world, last_placed_world_key, last_placed_world_name,
            last_placed_x, last_placed_y, last_placed_z, last_placed_chunk_x, last_placed_chunk_z,
            last_seen_world, last_seen_world_key, last_seen_world_name,
            last_seen_x, last_seen_y, last_seen_z,
            last_actor_uuid, last_actor_name, last_source, last_reason,
            created_at, updated_at, last_seen_at
        ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
      int i = 1;
      ps.setString(i++, record.id().toString());
      ps.setString(i++, record.type().id());
      ps.setString(i++, ChunkLoaderRegistryStatus.ACTIVE.dbValue());
      setNullableUuid(ps, i++, record.placedByUuid());
      ps.setString(i++, record.placedByName());
      bindRegistryPlacementFields(ps, i, record);
      i += 8;
      bindRegistryPlacementFields(ps, i, record);
      i += 8;
      ps.setString(i++, record.worldId().toString());
      ps.setString(i++, record.worldKey());
      ps.setString(i++, record.worldName());
      ps.setDouble(i++, record.x() + 0.5D);
      ps.setDouble(i++, record.y() + 1.0D);
      ps.setDouble(i++, record.z() + 0.5D);
      setNullableUuid(ps, i++, record.placedByUuid());
      ps.setString(i++, record.placedByName());
      ps.setString(i++, "place");
      ps.setNull(i++, java.sql.Types.VARCHAR);
      ps.setLong(i++, record.createdAt());
      ps.setLong(i++, now);
      ps.setLong(i, now);
      ps.executeUpdate();
    }
  }

  private void updateChunkLoaderRegistryObservation(ChunkLoaderObservation observation)
      throws SQLException {
    String sql =
        """
        UPDATE chunk_loader_registry SET
            status = ?,
            last_seen_world = ?,
            last_seen_world_key = ?,
            last_seen_world_name = ?,
            last_seen_x = ?,
            last_seen_y = ?,
            last_seen_z = ?,
            last_actor_uuid = ?,
            last_actor_name = ?,
            last_source = ?,
            last_reason = ?,
            updated_at = ?,
            last_seen_at = ?
        WHERE id = ?
        """;
    try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
      int i = 1;
      ps.setString(i++, observation.status().dbValue());
      ps.setString(i++, observation.worldId().toString());
      ps.setString(i++, observation.worldKey());
      ps.setString(i++, observation.worldName());
      ps.setDouble(i++, observation.x());
      ps.setDouble(i++, observation.y());
      ps.setDouble(i++, observation.z());
      setNullableUuid(ps, i++, observation.actorUuid());
      setNullableString(ps, i++, observation.actorName());
      setNullableString(ps, i++, observation.source());
      setNullableString(ps, i++, observation.reason());
      ps.setLong(i++, observation.observedAt());
      ps.setLong(i++, observation.observedAt());
      ps.setString(i, observation.id().toString());
      ps.executeUpdate();
    }
  }

  private void insertChunkLoaderRegistryObservationFallback(ChunkLoaderObservation observation)
      throws SQLException {
    String sql =
        """
        INSERT INTO chunk_loader_registry(
            id, status, placed_by_uuid, placed_by_name,
            first_world, first_world_key, first_world_name, first_x, first_y, first_z,
            first_chunk_x, first_chunk_z,
            last_placed_world, last_placed_world_key, last_placed_world_name,
            last_placed_x, last_placed_y, last_placed_z, last_placed_chunk_x, last_placed_chunk_z,
            last_seen_world, last_seen_world_key, last_seen_world_name,
            last_seen_x, last_seen_y, last_seen_z,
            last_actor_uuid, last_actor_name, last_source, last_reason,
            created_at, updated_at, last_seen_at
        ) VALUES(?, ?, NULL, 'unknown', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    int blockX = (int) Math.floor(observation.x());
    int blockY = (int) Math.floor(observation.y());
    int blockZ = (int) Math.floor(observation.z());
    int chunkX = blockX >> 4;
    int chunkZ = blockZ >> 4;
    try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
      int i = 1;
      ps.setString(i++, observation.id().toString());
      ps.setString(i++, observation.status().dbValue());
      ps.setString(i++, observation.worldId().toString());
      ps.setString(i++, observation.worldKey());
      ps.setString(i++, observation.worldName());
      ps.setInt(i++, blockX);
      ps.setInt(i++, blockY);
      ps.setInt(i++, blockZ);
      ps.setInt(i++, chunkX);
      ps.setInt(i++, chunkZ);
      ps.setString(i++, observation.worldId().toString());
      ps.setString(i++, observation.worldKey());
      ps.setString(i++, observation.worldName());
      ps.setInt(i++, blockX);
      ps.setInt(i++, blockY);
      ps.setInt(i++, blockZ);
      ps.setInt(i++, chunkX);
      ps.setInt(i++, chunkZ);
      ps.setString(i++, observation.worldId().toString());
      ps.setString(i++, observation.worldKey());
      ps.setString(i++, observation.worldName());
      ps.setDouble(i++, observation.x());
      ps.setDouble(i++, observation.y());
      ps.setDouble(i++, observation.z());
      setNullableUuid(ps, i++, observation.actorUuid());
      setNullableString(ps, i++, observation.actorName());
      setNullableString(ps, i++, observation.source());
      setNullableString(ps, i++, observation.reason());
      ps.setLong(i++, observation.observedAt());
      ps.setLong(i++, observation.observedAt());
      ps.setLong(i, observation.observedAt());
      ps.executeUpdate();
    }
  }

  private void bindRegistryPlacementFields(
      PreparedStatement ps, int start, ChunkLoaderRecord record) throws SQLException {
    int i = start;
    ps.setString(i++, record.worldId().toString());
    ps.setString(i++, record.worldKey());
    ps.setString(i++, record.worldName());
    ps.setInt(i++, record.x());
    ps.setInt(i++, record.y());
    ps.setInt(i++, record.z());
    ps.setInt(i++, record.chunkX());
    ps.setInt(i, record.chunkZ());
  }

  private void bindChunkLoader(PreparedStatement ps, ChunkLoaderRecord record) throws SQLException {
    ps.setString(1, record.id().toString());
    ps.setString(2, record.type().id());
    ps.setString(3, record.worldId().toString());
    ps.setString(4, record.worldKey());
    ps.setString(5, record.worldName());
    ps.setInt(6, record.x());
    ps.setInt(7, record.y());
    ps.setInt(8, record.z());
    ps.setInt(9, record.chunkX());
    ps.setInt(10, record.chunkZ());
    if (record.placedByUuid() == null) {
      ps.setNull(11, java.sql.Types.VARCHAR);
    } else {
      ps.setString(11, record.placedByUuid().toString());
    }
    ps.setString(12, record.placedByName());
    ps.setInt(13, record.radius());
    ps.setInt(14, record.enabled() ? 1 : 0);
    ps.setInt(15, record.bypassLimits() ? 1 : 0);
    ps.setLong(16, record.createdAt());
    ps.setLong(17, record.updatedAt());
  }

  private ChunkLoaderRecord readChunkLoader(ResultSet rs) throws SQLException {
    try {
      String placerRaw = rs.getString("placed_by_uuid");
      UUID placer = placerRaw == null || placerRaw.isBlank() ? null : UUID.fromString(placerRaw);
      return new ChunkLoaderRecord(
          UUID.fromString(rs.getString("id")),
          readChunkLoaderType(rs, "loader_type"),
          UUID.fromString(rs.getString("world")),
          rs.getString("world_key"),
          rs.getString("world_name"),
          rs.getInt("x"),
          rs.getInt("y"),
          rs.getInt("z"),
          rs.getInt("chunk_x"),
          rs.getInt("chunk_z"),
          placer,
          rs.getString("placed_by_name"),
          rs.getInt("radius"),
          rs.getInt("enabled") != 0,
          rs.getInt("bypass_limits") != 0,
          rs.getLong("created_at"),
          rs.getLong("updated_at"));
    } catch (IllegalArgumentException e) {
      logWarning("Skipping invalid chunk loader DB row: " + e.getMessage());
      return null;
    }
  }

  private ChunkLoaderRegistryRecord readChunkLoaderRegistry(ResultSet rs) throws SQLException {
    try {
      return new ChunkLoaderRegistryRecord(
          UUID.fromString(rs.getString("id")),
          readChunkLoaderType(rs, "loader_type"),
          ChunkLoaderRegistryStatus.fromDb(rs.getString("status")),
          readUuid(rs, "placed_by_uuid"),
          rs.getString("placed_by_name"),
          UUID.fromString(rs.getString("first_world")),
          rs.getString("first_world_key"),
          rs.getString("first_world_name"),
          rs.getInt("first_x"),
          rs.getInt("first_y"),
          rs.getInt("first_z"),
          rs.getInt("first_chunk_x"),
          rs.getInt("first_chunk_z"),
          UUID.fromString(rs.getString("last_placed_world")),
          rs.getString("last_placed_world_key"),
          rs.getString("last_placed_world_name"),
          rs.getInt("last_placed_x"),
          rs.getInt("last_placed_y"),
          rs.getInt("last_placed_z"),
          rs.getInt("last_placed_chunk_x"),
          rs.getInt("last_placed_chunk_z"),
          readUuid(rs, "last_seen_world"),
          rs.getString("last_seen_world_key"),
          rs.getString("last_seen_world_name"),
          readNullableDouble(rs, "last_seen_x"),
          readNullableDouble(rs, "last_seen_y"),
          readNullableDouble(rs, "last_seen_z"),
          readUuid(rs, "last_actor_uuid"),
          rs.getString("last_actor_name"),
          rs.getString("last_source"),
          rs.getString("last_reason"),
          rs.getLong("created_at"),
          rs.getLong("updated_at"),
          readNullableLong(rs, "last_seen_at"));
    } catch (IllegalArgumentException e) {
      logWarning("Skipping invalid chunk loader registry row: " + e.getMessage());
      return null;
    }
  }

  private static ChunkLoaderType readChunkLoaderType(ResultSet rs, String column)
      throws SQLException {
    String raw = rs.getString(column);
    return ChunkLoaderType.fromNullableId(raw)
        .orElseThrow(() -> new IllegalArgumentException("Invalid chunk loader type: " + raw));
  }

  private static UUID readUuid(ResultSet rs, String column) throws SQLException {
    String raw = rs.getString(column);
    return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
  }

  private static Double readNullableDouble(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }

  private static Long readNullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private static void setNullableUuid(PreparedStatement ps, int index, UUID value)
      throws SQLException {
    if (value == null) {
      ps.setNull(index, java.sql.Types.VARCHAR);
    } else {
      ps.setString(index, value.toString());
    }
  }

  private static void setNullableString(PreparedStatement ps, int index, String value)
      throws SQLException {
    if (value == null || value.isBlank()) {
      ps.setNull(index, java.sql.Types.VARCHAR);
    } else {
      ps.setString(index, value);
    }
  }

  private void log(Level level, String message, Throwable error) {
    if (logger != null) {
      logger.log(level, message, error);
    }
  }

  private void logWarning(String message) {
    if (logger != null) {
      logger.warning(message);
    }
  }

  private record PendingAuditObservation(
      ChunkLoaderObservation observation, CompletableFuture<Boolean> result) {}

  @Override
  public void close() {
    CancellationException closed =
        new CancellationException("Database closed before audit observation was persisted");
    pendingAuditObservations
        .values()
        .forEach(pending -> pending.result().completeExceptionally(closed));
    pendingAuditObservations.clear();
  }
}
