package com.zxcmc.exort.infra.db;

import com.zxcmc.exort.bus.BusFilterCodec;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.chunkloader.ChunkLoaderObservation;
import com.zxcmc.exort.chunkloader.ChunkLoaderRecord;
import com.zxcmc.exort.chunkloader.ChunkLoaderRegistryRecord;
import com.zxcmc.exort.chunkloader.ChunkLoaderRegistryStatus;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.storage.StorageClaim;
import com.zxcmc.exort.storage.StorageClaimConflictException;
import com.zxcmc.exort.storage.StorageClaimLocation;
import com.zxcmc.exort.storage.StorageClaimStore;
import com.zxcmc.exort.storage.StorageCorruption;
import com.zxcmc.exort.storage.StorageDisplayName;
import com.zxcmc.exort.storage.StorageLoadResult;
import com.zxcmc.exort.storage.StorageQuarantineEntry;
import com.zxcmc.exort.storage.StorageTier;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Database implements AutoCloseable, StorageClaimStore {
  private static final long CLOSE_TIMEOUT_SECONDS = 15L;
  private static final long FORCED_CLOSE_TIMEOUT_SECONDS = 2L;
  private static final int MAX_ITEM_BLOB_BYTES = 1_048_576;
  private static final int DB_QUEUE_CAPACITY = 8_192;
  private static final int AUDIT_OBSERVATION_CAPACITY = 4_096;
  private static final int AUDIT_OBSERVATION_BATCH = 128;

  private final Logger logger;
  private final Supplier<String> defaultSortModeName;
  private final AtomicBoolean closing = new AtomicBoolean();
  private final AtomicInteger queuedOperations = new AtomicInteger();
  private final AtomicBoolean auditDrainScheduled = new AtomicBoolean();
  private final Map<UUID, PendingAuditObservation> pendingAuditObservations =
      new ConcurrentHashMap<>();
  private final Set<CompletableFuture<?>> pendingDbFutures = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(DB_QUEUE_CAPACITY),
          r -> {
            Thread t = new Thread(r, "exort-db");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());
  private Connection connection;

  public record DeltaWrite(
      String storageId, Collection<DbItem> upserts, Collection<String> removals) {}

  public record StorageTierState(String tier, Long tierMaxItems) {}

  public Database() {
    this(null, () -> SortMode.AMOUNT.name());
  }

  public Database(Logger logger, Supplier<String> defaultSortModeName) {
    this.logger = logger;
    this.defaultSortModeName =
        defaultSortModeName == null ? () -> SortMode.AMOUNT.name() : defaultSortModeName;
  }

  public void init(File file) throws SQLException {
    File parent = file.getParentFile();
    if (parent != null) {
      try {
        Files.createDirectories(parent.toPath());
      } catch (IOException e) {
        throw new SQLException("Failed to create database directory " + parent, e);
      }
    }
    String url = "jdbc:sqlite:" + file.getAbsolutePath();
    connection = DriverManager.getConnection(url);
    try {
      applyPragmas();
      initializeSchema();
    } catch (SQLException | RuntimeException e) {
      try {
        connection.close();
      } catch (SQLException closeFailure) {
        e.addSuppressed(closeFailure);
      } finally {
        connection = null;
      }
      throw e;
    }
  }

  private void applyPragmas() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("PRAGMA journal_mode=WAL");
      stmt.execute("PRAGMA synchronous=NORMAL");
      stmt.execute("PRAGMA foreign_keys=ON");
      stmt.execute("PRAGMA busy_timeout=5000");
    }
  }

  private void createTables() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS storages (
                  id TEXT PRIMARY KEY,
                  tier TEXT NULL,
                  tier_max_items INTEGER NULL,
                  display_name TEXT NULL,
                  sort_mode TEXT NULL,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS storage_items (
                  storage_id TEXT NOT NULL,
                  item_key TEXT NOT NULL,
                  item_blob BLOB NOT NULL,
                  amount INTEGER NOT NULL,
                  PRIMARY KEY (storage_id, item_key),
                  FOREIGN KEY (storage_id) REFERENCES storages(id) ON DELETE CASCADE
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS storage_item_quarantine (
                  storage_id TEXT NOT NULL,
                  item_key TEXT NOT NULL,
                  item_blob BLOB NULL,
                  amount INTEGER NOT NULL,
                  reason TEXT NOT NULL,
                  quarantined_at INTEGER NOT NULL,
                  PRIMARY KEY (storage_id, item_key),
                  FOREIGN KEY (storage_id) REFERENCES storages(id) ON DELETE CASCADE
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS storage_claims (
                  storage_id TEXT PRIMARY KEY,
                  world_uuid TEXT NOT NULL,
                  world_key TEXT NOT NULL,
                  world_name TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  claimed_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  UNIQUE(world_uuid, x, y, z),
                  FOREIGN KEY (storage_id) REFERENCES storages(id) ON DELETE CASCADE
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS players (
                  player_uuid TEXT PRIMARY KEY,
                  last_storage_id TEXT NULL,
                  last_world TEXT NULL,
                  last_x INTEGER NULL,
                  last_y INTEGER NULL,
                  last_z INTEGER NULL,
                  updated_at INTEGER NOT NULL,
                  FOREIGN KEY (last_storage_id) REFERENCES storages(id) ON DELETE SET NULL
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS bus_settings (
                  world TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  type TEXT NOT NULL,
                  mode TEXT NOT NULL,
                  filters BLOB NULL,
                  updated_at INTEGER NOT NULL,
                  PRIMARY KEY (world, x, y, z)
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS client_culling_players (
                  player_uuid TEXT PRIMARY KEY,
                  manual_bypass INTEGER NOT NULL DEFAULT 0,
                  last_match_brand TEXT NULL,
                  last_probe_state TEXT NULL,
                  last_seen_at INTEGER NOT NULL DEFAULT 0,
                  updated_at INTEGER NOT NULL
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS chunk_loaders (
                  id TEXT PRIMARY KEY,
                  loader_type TEXT NOT NULL DEFAULT 'chunk_loader',
                  world TEXT NOT NULL,
                  world_key TEXT NOT NULL,
                  world_name TEXT NOT NULL,
                  x INTEGER NOT NULL,
                  y INTEGER NOT NULL,
                  z INTEGER NOT NULL,
                  chunk_x INTEGER NOT NULL,
                  chunk_z INTEGER NOT NULL,
                  placed_by_uuid TEXT NULL,
                  placed_by_name TEXT NOT NULL,
                  radius INTEGER NOT NULL,
                  enabled INTEGER NOT NULL DEFAULT 1,
                  bypass_limits INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  UNIQUE(world, x, y, z)
              )
          """);
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS chunk_loader_registry (
                  id TEXT PRIMARY KEY,
                  loader_type TEXT NOT NULL DEFAULT 'chunk_loader',
                  status TEXT NOT NULL,
                  placed_by_uuid TEXT NULL,
                  placed_by_name TEXT NOT NULL,
                  first_world TEXT NOT NULL,
                  first_world_key TEXT NOT NULL,
                  first_world_name TEXT NOT NULL,
                  first_x INTEGER NOT NULL,
                  first_y INTEGER NOT NULL,
                  first_z INTEGER NOT NULL,
                  first_chunk_x INTEGER NOT NULL,
                  first_chunk_z INTEGER NOT NULL,
                  last_placed_world TEXT NOT NULL,
                  last_placed_world_key TEXT NOT NULL,
                  last_placed_world_name TEXT NOT NULL,
                  last_placed_x INTEGER NOT NULL,
                  last_placed_y INTEGER NOT NULL,
                  last_placed_z INTEGER NOT NULL,
                  last_placed_chunk_x INTEGER NOT NULL,
                  last_placed_chunk_z INTEGER NOT NULL,
                  last_seen_world TEXT NULL,
                  last_seen_world_key TEXT NULL,
                  last_seen_world_name TEXT NULL,
                  last_seen_x REAL NULL,
                  last_seen_y REAL NULL,
                  last_seen_z REAL NULL,
                  last_actor_uuid TEXT NULL,
                  last_actor_name TEXT NULL,
                  last_source TEXT NULL,
                  last_reason TEXT NULL,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  last_seen_at INTEGER NULL
              )
          """);
    }
  }

  private void initializeSchema() throws SQLException {
    createTables();
    validateColumns(
        "storages", Set.of("id", "tier", "tier_max_items", "display_name", "sort_mode"));
    validateColumns(
        "storage_claims", Set.of("storage_id", "world_uuid", "world_key", "x", "y", "z"));
    validateColumns(
        "storage_item_quarantine",
        Set.of("storage_id", "item_key", "item_blob", "amount", "reason", "quarantined_at"));
    validateColumns("chunk_loaders", Set.of("id", "enabled", "bypass_limits"));
  }

  private void validateColumns(String table, Set<String> requiredColumns) throws SQLException {
    Set<String> actualColumns = new HashSet<>();
    try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          actualColumns.add(rs.getString("name").toLowerCase(Locale.ROOT));
        }
      }
    }
    Set<String> missing = new TreeSet<>(requiredColumns);
    missing.removeAll(actualColumns);
    if (!missing.isEmpty()) {
      throw new SQLException(
          "Unsupported Exort database schema: table '"
              + table
              + "' is missing columns "
              + missing
              + ". Back up the database and recreate it for Exort 0.19.0.");
    }
  }

  public CompletableFuture<Void> ensureStorage(String id) {
    Objects.requireNonNull(id, "id");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    return runDbTask(
        "ensure storage " + id,
        () -> {
          String sql =
              "INSERT OR IGNORE INTO storages(id, tier, sort_mode, created_at, updated_at)"
                  + " VALUES(?, NULL, ?, ?, ?)";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, defaultSort);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to ensure storage row", e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> setStorageTier(String storageId, String tierKey) {
    Long tierMaxItems = StorageTier.fromString(tierKey).map(StorageTier::maxItems).orElse(null);
    return setStorageTier(storageId, tierKey, tierMaxItems);
  }

  public CompletableFuture<Void> setStorageTier(
      String storageId, String tierKey, Long tierMaxItems) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(tierKey, "tierKey");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    return runDbTask(
        "set storage tier " + storageId,
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, tier_max_items, sort_mode, created_at, updated_at)"
                  + " VALUES(?, ?, ?, COALESCE((SELECT sort_mode FROM storages WHERE id = ?), ?),"
                  + " ?, ?) ON CONFLICT(id) DO UPDATE SET tier = excluded.tier, tier_max_items ="
                  + " excluded.tier_max_items, updated_at = excluded.updated_at";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            ps.setString(2, tierKey);
            setNullableLong(ps, 3, tierMaxItems);
            ps.setString(4, storageId);
            ps.setString(5, defaultSort);
            ps.setLong(6, now);
            ps.setLong(7, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to set storage tier for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> setStorageMetadata(
      String storageId, String tierKey, Long tierMaxItems, String displayName) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(tierKey, "tierKey");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    String normalizedName = StorageDisplayName.normalize(displayName);
    return runDbTask(
        "set storage metadata " + storageId,
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, tier_max_items, display_name, sort_mode,"
                  + " created_at, updated_at)"
                  + " VALUES(?, ?, ?, ?, COALESCE((SELECT sort_mode FROM storages WHERE id = ?),"
                  + " ?), ?, ?) ON CONFLICT(id) DO UPDATE SET tier = excluded.tier,"
                  + " tier_max_items = excluded.tier_max_items, display_name ="
                  + " excluded.display_name, updated_at = excluded.updated_at";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            ps.setString(2, tierKey);
            setNullableLong(ps, 3, tierMaxItems);
            ps.setString(4, normalizedName);
            ps.setString(5, storageId);
            ps.setString(6, defaultSort);
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to set storage metadata for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  @Override
  public CompletableFuture<List<StorageClaim>> loadStorageClaims() {
    return supplyDbTask(
        "load physical storage claims",
        () -> {
          List<StorageClaim> claims = new ArrayList<>();
          String sql =
              "SELECT storage_id, world_uuid, world_key, world_name, x, y, z, claimed_at,"
                  + " updated_at FROM storage_claims ORDER BY claimed_at, storage_id";
          try (PreparedStatement ps = connection.prepareStatement(sql);
              ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              claims.add(
                  new StorageClaim(
                      rs.getString("storage_id"),
                      UUID.fromString(rs.getString("world_uuid")),
                      rs.getString("world_key"),
                      rs.getString("world_name"),
                      rs.getInt("x"),
                      rs.getInt("y"),
                      rs.getInt("z"),
                      rs.getLong("claimed_at"),
                      rs.getLong("updated_at")));
            }
          } catch (SQLException | IllegalArgumentException e) {
            log(Level.SEVERE, "Failed to load physical storage claims", e);
            throw new CompletionException(e);
          }
          return List.copyOf(claims);
        });
  }

  @Override
  public CompletableFuture<Void> insertStorageClaim(
      StorageClaim claim, String tierKey, long tierMaxItems, String displayName) {
    Objects.requireNonNull(claim, "claim");
    Objects.requireNonNull(tierKey, "tierKey");
    if (tierMaxItems <= 0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("tierMaxItems must be positive"));
    }
    String normalizedName = StorageDisplayName.normalize(displayName);
    String defaultSort = defaultSortModeName();
    return runDbTask(
        "claim physical storage " + claim.storageId(),
        () -> {
          try {
            connection.setAutoCommit(false);
            String storageSql =
                "INSERT INTO storages(id, tier, tier_max_items, display_name, sort_mode,"
                    + " created_at, updated_at) VALUES(?, ?, ?, ?, COALESCE((SELECT sort_mode FROM"
                    + " storages WHERE id = ?), ?), ?, ?) ON CONFLICT(id) DO UPDATE SET tier ="
                    + " excluded.tier, tier_max_items = excluded.tier_max_items, display_name ="
                    + " excluded.display_name, updated_at = excluded.updated_at";
            try (PreparedStatement ps = connection.prepareStatement(storageSql)) {
              ps.setString(1, claim.storageId());
              ps.setString(2, tierKey);
              ps.setLong(3, tierMaxItems);
              ps.setString(4, normalizedName);
              ps.setString(5, claim.storageId());
              ps.setString(6, defaultSort);
              ps.setLong(7, claim.claimedAt());
              ps.setLong(8, claim.updatedAt());
              ps.executeUpdate();
            }
            String claimSql =
                "INSERT INTO storage_claims(storage_id, world_uuid, world_key, world_name, x, y,"
                    + " z, claimed_at, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(claimSql)) {
              ps.setString(1, claim.storageId());
              ps.setString(2, claim.worldId().toString());
              ps.setString(3, claim.worldKey());
              ps.setString(4, claim.worldName());
              ps.setInt(5, claim.x());
              ps.setInt(6, claim.y());
              ps.setInt(7, claim.z());
              ps.setLong(8, claim.claimedAt());
              ps.setLong(9, claim.updatedAt());
              ps.executeUpdate();
            }
            connection.commit();
          } catch (SQLException e) {
            rollbackQuietly("storage claim " + claim.storageId(), e);
            throw new CompletionException(storageClaimConflict(claim, e));
          } finally {
            restoreAutoCommit();
          }
        });
  }

  @Override
  public CompletableFuture<Boolean> deleteStorageClaimExact(
      String storageId, StorageClaimLocation location) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(location, "location");
    return supplyDbTask(
        "release physical storage claim " + storageId,
        () -> {
          String sql =
              "DELETE FROM storage_claims WHERE storage_id = ? AND world_uuid = ? AND x = ? AND y"
                  + " = ? AND z = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            ps.setString(2, location.worldId().toString());
            ps.setInt(3, location.x());
            ps.setInt(4, location.y());
            ps.setInt(5, location.z());
            return ps.executeUpdate() == 1;
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to release physical storage claim " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  @Override
  public CompletableFuture<Boolean> moveStorageClaimExact(
      StorageClaim source, StorageClaim destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    if (!source.storageId().equals(destination.storageId())) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Storage claim identity cannot change during a move"));
    }
    return supplyDbTask(
        "move physical storage claim " + source.storageId(),
        () -> {
          String sql =
              "UPDATE storage_claims SET world_uuid = ?, world_key = ?, world_name = ?, x = ?, y"
                  + " = ?, z = ?, updated_at = ? WHERE storage_id = ? AND world_uuid = ? AND x = ?"
                  + " AND y = ? AND z = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, destination.worldId().toString());
            ps.setString(2, destination.worldKey());
            ps.setString(3, destination.worldName());
            ps.setInt(4, destination.x());
            ps.setInt(5, destination.y());
            ps.setInt(6, destination.z());
            ps.setLong(7, destination.updatedAt());
            ps.setString(8, source.storageId());
            ps.setString(9, source.worldId().toString());
            ps.setInt(10, source.x());
            ps.setInt(11, source.y());
            ps.setInt(12, source.z());
            return ps.executeUpdate() == 1;
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to move physical storage claim " + source.storageId(), e);
            throw new CompletionException(storageClaimConflict(destination, e));
          }
        });
  }

  private void rollbackQuietly(String operation, SQLException original) {
    log(Level.SEVERE, "Failed to persist " + operation, original);
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      original.addSuppressed(rollbackFailure);
      log(Level.SEVERE, "Failed to roll back " + operation, rollbackFailure);
    }
  }

  private RuntimeException storageClaimConflict(StorageClaim claim, SQLException error) {
    if (error.getErrorCode() != 19) {
      return new IllegalStateException(
          "Failed to persist storage claim " + claim.storageId(), error);
    }
    String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
    StorageClaimConflictException.Kind kind =
        message.contains("storage_claims.storage_id")
            ? StorageClaimConflictException.Kind.STORAGE_ID
            : message.contains("storage_claims.world_uuid")
                ? StorageClaimConflictException.Kind.POSITION
                : StorageClaimConflictException.Kind.UNKNOWN;
    return new StorageClaimConflictException(
        kind,
        "Physical storage claim conflicts with an existing "
            + (kind == StorageClaimConflictException.Kind.POSITION ? "position" : "identity")
            + ": "
            + claim.storageId(),
        error);
  }

  private void restoreAutoCommit() {
    try {
      connection.setAutoCommit(true);
    } catch (SQLException e) {
      log(Level.SEVERE, "Failed to restore SQLite auto-commit", e);
    }
  }

  public CompletableFuture<Boolean> saveChunkLoader(ChunkLoaderRecord record) {
    Objects.requireNonNull(record, "record");
    return supplyDbTask(
        "save chunk loader " + record.id(),
        () -> {
          boolean found;
          try {
            connection.setAutoCommit(false);
            List<UUID> replacedIds = chunkLoaderIdsAtPositionExcept(record);
            try (PreparedStatement delete =
                connection.prepareStatement(
                    "DELETE FROM chunk_loaders WHERE world = ? AND x = ? AND y = ? AND z = ? AND"
                        + " id <> ?")) {
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
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
            connection.commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to save chunk loader " + record.id(), e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback chunk loader save", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
          return found;
        });
  }

  public CompletableFuture<Boolean> recordChunkLoaderObservation(
      ChunkLoaderObservation observation) {
    Objects.requireNonNull(observation, "observation");
    return supplyDbTask(
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
    if (closing.get()) {
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
    if (closing.get() || !auditDrainScheduled.compareAndSet(false, true)) return;
    incrementQueueDepth();
    try {
      executor.execute(this::drainAuditObservations);
    } catch (RejectedExecutionException rejected) {
      auditDrainScheduled.set(false);
      decrementQueueDepth();
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
      decrementQueueDepth();
      if (!pendingAuditObservations.isEmpty()) scheduleAuditDrain();
    }
  }

  public CompletableFuture<List<ChunkLoaderRecord>> listChunkLoaders() {
    return supplyDbTask(
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
          try (PreparedStatement ps = connection.prepareStatement(sql);
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
    return supplyDbTask(
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
          try (PreparedStatement ps = connection.prepareStatement(sql);
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
    return runDbTask(
        "delete chunk loader " + id,
        () -> {
          try (PreparedStatement ps =
              connection.prepareStatement("DELETE FROM chunk_loaders WHERE id = ?")) {
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
    return runDbTask(
        "delete chunk loader at " + worldId + " " + x + "," + y + "," + z,
        () -> {
          try (PreparedStatement ps =
              connection.prepareStatement(
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
        connection.prepareStatement("SELECT status FROM chunk_loader_registry WHERE id = ?")) {
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
        connection.prepareStatement(
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
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
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
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
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

  private static void setNullableLong(PreparedStatement ps, int index, Long value)
      throws SQLException {
    if (value == null) {
      ps.setNull(index, java.sql.Types.INTEGER);
    } else {
      ps.setLong(index, value);
    }
  }

  private String defaultSortModeName() {
    String raw = defaultSortModeName.get();
    return SortMode.fromString(raw).name();
  }

  public int queuedOperations() {
    return queuedOperations.get();
  }

  private CompletableFuture<Void> runDbTask(String action, Runnable task) {
    Objects.requireNonNull(task, "task");
    return supplyDbTask(
        action,
        () -> {
          task.run();
          return null;
        });
  }

  private <T> CompletableFuture<T> supplyDbTask(String action, Supplier<T> task) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(task, "task");
    if (closing.get()) {
      return rejectDbTask(action, null);
    }
    CompletableFuture<T> future = new CompletableFuture<>();
    pendingDbFutures.add(future);
    incrementQueueDepth();
    try {
      executor.execute(
          () -> {
            T result = null;
            Throwable failure = null;
            try {
              result = PerfStats.measure(PerfStats.Area.STORAGE_DB, task);
            } catch (RuntimeException thrown) {
              failure = unwrapCompletion(thrown);
              if (!(failure instanceof SQLException)) {
                log(Level.SEVERE, "Async database task failed: " + action, failure);
              }
            } catch (Error fatal) {
              future.completeExceptionally(fatal);
              throw fatal;
            } finally {
              decrementQueueDepth();
              pendingDbFutures.remove(future);
            }
            if (failure == null) {
              future.complete(result);
            } else {
              future.completeExceptionally(failure);
            }
          });
    } catch (RejectedExecutionException error) {
      decrementQueueDepth();
      pendingDbFutures.remove(future);
      log(Level.WARNING, "Rejected async database task: " + action, error);
      future.completeExceptionally(error);
      return future;
    }
    return future;
  }

  private <T> CompletableFuture<T> rejectDbTask(String action, RejectedExecutionException cause) {
    RejectedExecutionException error =
        cause == null
            ? new RejectedExecutionException("Database is closing; rejected async task: " + action)
            : cause;
    log(Level.WARNING, "Rejected async database task: " + action, error);
    CompletableFuture<T> failed = new CompletableFuture<>();
    failed.completeExceptionally(error);
    return failed;
  }

  private void incrementQueueDepth() {
    PerfStats.setGauge("storage-db.queueDepth", queuedOperations.incrementAndGet());
  }

  private void decrementQueueDepth() {
    PerfStats.setGauge(
        "storage-db.queueDepth",
        queuedOperations.updateAndGet(current -> Math.max(0, current - 1)));
  }

  private static Throwable unwrapCompletion(Throwable thrown) {
    Throwable current = thrown;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private record PendingAuditObservation(
      ChunkLoaderObservation observation, CompletableFuture<Boolean> result) {}

  public CompletableFuture<Optional<String>> getStorageSortMode(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return supplyDbTask(
        "read storage sort mode " + storageId,
        () -> {
          String sql = "SELECT sort_mode FROM storages WHERE id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return Optional.ofNullable(rs.getString("sort_mode"));
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to read storage sort mode for " + storageId, e);
            throw new CompletionException(e);
          }
          return Optional.empty();
        });
  }

  public CompletableFuture<Void> setStorageSortMode(String storageId, String sortMode) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(sortMode, "sortMode");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "set storage sort mode " + storageId,
        () -> {
          String sql = "UPDATE storages SET sort_mode = ?, updated_at = ? WHERE id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sortMode);
            ps.setLong(2, now);
            ps.setString(3, storageId);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to set storage sort mode for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> createStorageWithItems(
      String storageId, String tierKey, String sortMode, Collection<DbItem> items) {
    return createStorageWithItems(storageId, tierKey, null, sortMode, items);
  }

  public CompletableFuture<Void> createStorageWithItems(
      String storageId,
      String tierKey,
      Long tierMaxItems,
      String sortMode,
      Collection<DbItem> items) {
    return createStorageWithItems(storageId, tierKey, tierMaxItems, sortMode, null, items);
  }

  public CompletableFuture<Void> createStorageWithItems(
      String storageId,
      String tierKey,
      Long tierMaxItems,
      String sortMode,
      String displayName,
      Collection<DbItem> items) {
    Objects.requireNonNull(storageId, "storageId");
    long now = Instant.now().getEpochSecond();
    String mode = sortMode == null ? defaultSortModeName() : sortMode;
    String normalizedName = StorageDisplayName.normalize(displayName);
    Collection<DbItem> safeItems = items == null ? List.of() : items;
    return runDbTask(
        "create storage " + storageId,
        () -> {
          validatePersistableDbItems(storageId, safeItems, "create");
          try {
            connection.setAutoCommit(false);
            String sql =
                "INSERT OR REPLACE INTO storages(id, tier, tier_max_items, display_name,"
                    + " sort_mode, created_at, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
              ps.setString(1, storageId);
              ps.setString(2, tierKey);
              setNullableLong(ps, 3, tierMaxItems);
              ps.setString(4, normalizedName);
              ps.setString(5, mode);
              ps.setLong(6, now);
              ps.setLong(7, now);
              ps.executeUpdate();
            }
            try (PreparedStatement delete =
                connection.prepareStatement("DELETE FROM storage_items WHERE storage_id = ?")) {
              delete.setString(1, storageId);
              delete.executeUpdate();
            }
            String insertSql =
                "INSERT INTO storage_items(storage_id, item_key, item_blob, amount) VALUES(?, ?, ?,"
                    + " ?)";
            try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
              int batchSize = 0;
              for (DbItem item : safeItems) {
                insert.setString(1, storageId);
                insert.setString(2, item.key());
                insert.setBytes(3, item.blob());
                insert.setLong(4, item.amount());
                insert.addBatch();
                batchSize++;
                if (batchSize >= 1000) {
                  insert.executeBatch();
                  insert.clearBatch();
                  batchSize = 0;
                }
              }
              if (batchSize > 0) {
                insert.executeBatch();
              }
            }
            connection.commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to create storage " + storageId, e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Void> deleteStorageForInternalCleanup(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return runDbTask(
        "delete storage for internal cleanup " + storageId,
        () -> {
          try {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteItems =
                connection.prepareStatement("DELETE FROM storage_items WHERE storage_id = ?")) {
              deleteItems.setString(1, storageId);
              deleteItems.executeUpdate();
            }
            try (PreparedStatement deleteStorage =
                connection.prepareStatement("DELETE FROM storages WHERE id = ?")) {
              deleteStorage.setString(1, storageId);
              deleteStorage.executeUpdate();
            }
            connection.commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to delete storage for internal cleanup " + storageId, e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback storage cleanup transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Void> cloneStorage(String fromId, String toId, String tierKey) {
    return cloneStorage(fromId, toId, tierKey, null);
  }

  public CompletableFuture<Void> cloneStorage(
      String fromId, String toId, String tierKey, Long tierMaxItems) {
    Objects.requireNonNull(fromId, "fromId");
    Objects.requireNonNull(toId, "toId");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "clone storage " + fromId + " to " + toId,
        () -> {
          try {
            connection.setAutoCommit(false);
            int rows;
            String insertSql =
                "INSERT INTO storages(id, tier, tier_max_items, display_name, sort_mode,"
                    + " created_at, updated_at)"
                    + " SELECT ?, tier, tier_max_items, display_name, sort_mode, ?, ? FROM"
                    + " storages WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
              ps.setString(1, toId);
              ps.setLong(2, now);
              ps.setLong(3, now);
              ps.setString(4, fromId);
              rows = ps.executeUpdate();
            }
            if (rows == 0) {
              throw new SQLException("Source storage does not exist: " + fromId);
            } else if (tierKey != null) {
              try (PreparedStatement ps =
                  connection.prepareStatement(
                      "UPDATE storages SET tier = ?, tier_max_items = ?, updated_at = ? WHERE id ="
                          + " ?")) {
                ps.setString(1, tierKey);
                setNullableLong(ps, 2, tierMaxItems);
                ps.setLong(3, now);
                ps.setString(4, toId);
                ps.executeUpdate();
              }
            }
            String itemsSql =
                "INSERT INTO storage_items(storage_id, item_key, item_blob, amount)"
                    + " SELECT ?, item_key, item_blob, amount FROM storage_items WHERE storage_id"
                    + " = ?";
            try (PreparedStatement ps = connection.prepareStatement(itemsSql)) {
              ps.setString(1, toId);
              ps.setString(2, fromId);
              ps.executeUpdate();
            }
            connection.commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to clone storage " + fromId + " to " + toId, e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Optional<String>> getStorageTier(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return supplyDbTask(
        "read storage tier " + storageId,
        () -> {
          String sql = "SELECT tier FROM storages WHERE id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return Optional.ofNullable(rs.getString("tier"));
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to read storage tier for " + storageId, e);
            throw new CompletionException(e);
          }
          return Optional.empty();
        });
  }

  public CompletableFuture<Optional<StorageTierState>> getStorageTierState(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return supplyDbTask(
        "read storage tier state " + storageId,
        () -> {
          String sql = "SELECT tier, tier_max_items FROM storages WHERE id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                String tier = rs.getString("tier");
                long maxItems = rs.getLong("tier_max_items");
                Long tierMaxItems = rs.wasNull() ? null : maxItems;
                return Optional.of(new StorageTierState(tier, tierMaxItems));
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to read storage tier state for " + storageId, e);
            throw new CompletionException(e);
          }
          return Optional.empty();
        });
  }

  public CompletableFuture<Optional<String>> getStorageDisplayName(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return supplyDbTask(
        "read storage display name " + storageId,
        () -> {
          String sql = "SELECT display_name FROM storages WHERE id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return Optional.ofNullable(
                    StorageDisplayName.normalize(rs.getString("display_name")));
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to read storage display name for " + storageId, e);
            throw new CompletionException(e);
          }
          return Optional.empty();
        });
  }

  public CompletableFuture<Void> setStorageDisplayName(String storageId, String displayName) {
    Objects.requireNonNull(storageId, "storageId");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    String normalizedName = StorageDisplayName.normalize(displayName);
    return runDbTask(
        "set storage display name " + storageId,
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, display_name, sort_mode, created_at, updated_at)"
                  + " VALUES(?, NULL, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET display_name ="
                  + " excluded.display_name, updated_at = excluded.updated_at";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            ps.setString(2, normalizedName);
            ps.setString(3, defaultSort);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to set storage display name for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Boolean> storageExists(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return supplyDbTask(
        "check storage existence " + storageId,
        () -> {
          String sql = "SELECT 1 FROM storages WHERE id = ? LIMIT 1";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              return rs.next();
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to check storage existence for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> updatePlayerLastStorage(
      UUID playerId, String storageId, String world, int x, int y, int z) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(world, "world");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "update last storage for " + playerId,
        () -> {
          String sql =
              "INSERT INTO players(player_uuid, last_storage_id, last_world, last_x, last_y,"
                  + " last_z, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO"
                  + " UPDATE SET last_storage_id = excluded.last_storage_id, last_world ="
                  + " excluded.last_world, last_x = excluded.last_x, last_y = excluded.last_y,"
                  + " last_z = excluded.last_z, updated_at = excluded.updated_at";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, storageId);
            ps.setString(3, world);
            ps.setInt(4, x);
            ps.setInt(5, y);
            ps.setInt(6, z);
            ps.setLong(7, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to update last storage for " + playerId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> updatePlayerLastStorageLocation(
      String storageId, String world, int x, int y, int z) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(world, "world");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "update last storage location for " + storageId,
        () -> {
          String sql =
              "UPDATE players SET last_world = ?, last_x = ?, last_y = ?, last_z = ?, updated_at ="
                  + " ? WHERE last_storage_id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setLong(5, now);
            ps.setString(6, storageId);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to update last storage location for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Optional<PlayerLastStorage>> getPlayerLastStorage(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return supplyDbTask(
        "read last storage for " + playerId,
        () -> {
          String sql =
              """
              SELECT p.last_storage_id AS storage_id,
                     s.tier AS tier,
                     p.last_world AS world,
                     p.last_x AS x,
                     p.last_y AS y,
                     p.last_z AS z,
                     p.updated_at AS updated_at
              FROM players p
              LEFT JOIN storages s ON s.id = p.last_storage_id
              WHERE p.player_uuid = ?
              """;
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return Optional.of(
                    new PlayerLastStorage(
                        rs.getString("storage_id"),
                        rs.getString("tier"),
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getLong("updated_at")));
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to read last storage for " + playerId, e);
            throw new CompletionException(e);
          }
          return Optional.empty();
        });
  }

  public record PlayerLastStorage(
      String storageId, String tier, String world, int x, int y, int z, long updatedAt) {}

  public CompletableFuture<Map<UUID, ClientCullingState>> loadClientCullingStates() {
    return supplyDbTask(
        "load client culling states",
        () -> {
          Map<UUID, ClientCullingState> states = new HashMap<>();
          String sql =
              """
              SELECT player_uuid,
                     manual_bypass,
                     last_match_brand,
                     last_probe_state,
                     last_seen_at,
                     updated_at
              FROM client_culling_players
              """;
          try (PreparedStatement ps = connection.prepareStatement(sql);
              ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
              try {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                states.put(
                    playerId,
                    new ClientCullingState(
                        playerId,
                        rs.getInt("manual_bypass") != 0,
                        rs.getString("last_match_brand"),
                        rs.getString("last_probe_state"),
                        rs.getLong("last_seen_at"),
                        rs.getLong("updated_at")));
              } catch (IllegalArgumentException ignored) {
                // Ignore hand-edited invalid UUIDs.
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to load client culling states", e);
            throw new CompletionException(e);
          }
          return states;
        });
  }

  public CompletableFuture<Void> setClientCullingManualBypass(UUID playerId, boolean enabled) {
    Objects.requireNonNull(playerId, "playerId");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "set client culling manual bypass for " + playerId,
        () -> {
          String sql =
              """
              INSERT INTO client_culling_players(
                  player_uuid, manual_bypass, last_match_brand, last_probe_state, last_seen_at, updated_at
              )
              VALUES(?, ?, NULL, NULL, 0, ?)
              ON CONFLICT(player_uuid) DO UPDATE SET
                  manual_bypass = excluded.manual_bypass,
                  updated_at = excluded.updated_at
              """;
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, enabled ? 1 : 0);
            ps.setLong(3, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to save client culling manual bypass for " + playerId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> recordClientCullingProbeResult(
      UUID playerId, String probeState, String brand, boolean match) {
    Objects.requireNonNull(playerId, "playerId");
    long now = Instant.now().getEpochSecond();
    String normalizedState = emptyToNull(probeState);
    String normalizedBrand = emptyToNull(brand);
    return runDbTask(
        "record client culling probe result for " + playerId,
        () -> {
          String sql =
              """
              INSERT INTO client_culling_players(
                  player_uuid, manual_bypass, last_match_brand, last_probe_state, last_seen_at, updated_at
              )
              VALUES(?, 0, ?, ?, ?, ?)
              ON CONFLICT(player_uuid) DO UPDATE SET
                  last_match_brand = excluded.last_match_brand,
                  last_probe_state = excluded.last_probe_state,
                  last_seen_at = excluded.last_seen_at,
                  updated_at = excluded.updated_at
              """;
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, match ? normalizedBrand : null);
            ps.setString(3, normalizedState);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to save client culling probe result for " + playerId, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> updateClientCullingLastSeen(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "update client culling last seen for " + playerId,
        () -> {
          String sql =
              """
              INSERT INTO client_culling_players(
                  player_uuid, manual_bypass, last_match_brand, last_probe_state, last_seen_at, updated_at
              )
              VALUES(?, 0, NULL, NULL, ?, ?)
              ON CONFLICT(player_uuid) DO UPDATE SET
                  last_seen_at = excluded.last_seen_at,
                  updated_at = excluded.updated_at
              """;
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, now);
            ps.setLong(3, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to save client culling last seen for " + playerId, e);
            throw new CompletionException(e);
          }
        });
  }

  private static String emptyToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  public record ClientCullingState(
      UUID playerId,
      boolean manualBypass,
      String lastMatchBrand,
      String lastProbeState,
      long lastSeenAt,
      long updatedAt) {
    public boolean hasFreshMatch(String brand, long nowEpochSeconds, long maxAgeSeconds) {
      if (lastMatchBrand == null || lastMatchBrand.isBlank()) {
        return false;
      }
      if (brand == null || !lastMatchBrand.equalsIgnoreCase(brand.trim())) {
        return false;
      }
      return lastSeenAt > 0L && nowEpochSeconds - lastSeenAt <= maxAgeSeconds;
    }

    public ClientCullingState withManualBypass(boolean manualBypass, long updatedAt) {
      return new ClientCullingState(
          playerId, manualBypass, lastMatchBrand, lastProbeState, lastSeenAt, updatedAt);
    }

    public ClientCullingState withProbeResult(
        String probeState, String brand, boolean match, long timestamp) {
      return new ClientCullingState(
          playerId,
          manualBypass,
          match ? emptyToNull(brand) : null,
          emptyToNull(probeState),
          timestamp,
          timestamp);
    }

    public ClientCullingState withLastSeen(long timestamp) {
      return new ClientCullingState(
          playerId, manualBypass, lastMatchBrand, lastProbeState, timestamp, timestamp);
    }

    public static ClientCullingState empty(UUID playerId) {
      return new ClientCullingState(playerId, false, null, null, 0L, 0L);
    }
  }

  public CompletableFuture<Optional<BusSettings>> loadBusSettings(BusPos pos, int slots) {
    Objects.requireNonNull(pos, "pos");
    return supplyDbTask(
        "load bus settings for " + pos,
        () -> {
          String sql =
              "SELECT type, mode, filters FROM bus_settings WHERE world = ? AND x = ? AND y = ? AND"
                  + " z = ? LIMIT 1";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pos.world().toString());
            ps.setInt(2, pos.x());
            ps.setInt(3, pos.y());
            ps.setInt(4, pos.z());
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                String type = rs.getString("type");
                String mode = rs.getString("mode");
                byte[] filters = rs.getBytes("filters");
                var decoded = BusFilterCodec.decode(filters, slots);
                return Optional.of(
                    new BusSettings(
                        pos, BusType.fromString(type), BusMode.fromString(mode), decoded));
              }
            }
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to load bus settings for " + pos, e);
            throw new CompletionException(e);
          }
          return Optional.empty();
        });
  }

  public CompletableFuture<Void> saveBusSettings(BusSettings settings, int slots) {
    Objects.requireNonNull(settings, "settings");
    long now = Instant.now().getEpochSecond();
    return runDbTask(
        "save bus settings for " + settings.pos(),
        () -> {
          String sql =
              """
              INSERT INTO bus_settings(world, x, y, z, type, mode, filters, updated_at)
              VALUES(?, ?, ?, ?, ?, ?, ?, ?)
              ON CONFLICT(world, x, y, z) DO UPDATE SET
                  type = excluded.type,
                  mode = excluded.mode,
                  filters = excluded.filters,
                  updated_at = excluded.updated_at
              """;
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, settings.pos().world().toString());
            ps.setInt(2, settings.pos().x());
            ps.setInt(3, settings.pos().y());
            ps.setInt(4, settings.pos().z());
            ps.setString(5, settings.type() == null ? "IMPORT" : settings.type().name());
            ps.setString(6, settings.mode() == null ? "DISABLED" : settings.mode().name());
            ps.setBytes(7, BusFilterCodec.encode(settings.filters(), slots));
            ps.setLong(8, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to save bus settings for " + settings.pos(), e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Void> deleteBusSettings(BusPos pos) {
    Objects.requireNonNull(pos, "pos");
    return runDbTask(
        "delete bus settings for " + pos,
        () -> {
          String sql = "DELETE FROM bus_settings WHERE world = ? AND x = ? AND y = ? AND z = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pos.world().toString());
            ps.setInt(2, pos.x());
            ps.setInt(3, pos.y());
            ps.setInt(4, pos.z());
            ps.executeUpdate();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to delete bus settings for " + pos, e);
            throw new CompletionException(e);
          }
        });
  }

  public CompletableFuture<Map<String, DbItem>> loadStorage(String storageId) {
    return loadStorageWithHealth(storageId).thenApply(StorageLoadResult::items);
  }

  public CompletableFuture<StorageLoadResult> loadStorageWithHealth(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return supplyDbTask(
        "load storage " + storageId,
        () -> {
          Map<String, DbItem> items = new HashMap<>();
          List<StorageCorruption> corruptions = new ArrayList<>();
          long detectedAt = Instant.now().getEpochSecond();
          String sql =
              "SELECT item_key, item_blob, amount, length(item_blob) AS blob_len FROM"
                  + " storage_items WHERE storage_id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                String key = rs.getString("item_key");
                long amount = rs.getLong("amount");
                long blobLen = rs.getLong("blob_len");
                if (amount <= 0) {
                  String reason = "amount must be positive";
                  warnInvalidStorageRow(storageId, key, amount, blobLen, reason);
                  corruptions.add(new StorageCorruption(key, amount, reason, detectedAt));
                  continue;
                }
                if (blobLen <= 0 || blobLen > MAX_ITEM_BLOB_BYTES) {
                  String reason = "invalid serialized item blob length";
                  warnInvalidStorageRow(storageId, key, amount, blobLen, reason);
                  corruptions.add(new StorageCorruption(key, amount, reason, detectedAt));
                  continue;
                }
                byte[] blob = rs.getBytes("item_blob");
                if (blob == null || blob.length == 0 || blob.length > MAX_ITEM_BLOB_BYTES) {
                  String reason = "invalid serialized item blob";
                  warnInvalidStorageRow(
                      storageId, key, amount, blob == null ? 0L : blob.length, reason);
                  corruptions.add(new StorageCorruption(key, amount, reason, detectedAt));
                  continue;
                }
                items.put(key, new DbItem(key, blob, amount));
              }
            }
            quarantineStructuralRows(storageId, corruptions);
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to load storage " + storageId, e);
            throw new CompletionException(e);
          }
          return new StorageLoadResult(items, corruptions);
        });
  }

  public CompletableFuture<Void> quarantineStorageItems(
      String storageId, Collection<StorageQuarantineEntry> entries) {
    Objects.requireNonNull(storageId, "storageId");
    List<StorageQuarantineEntry> safeEntries =
        entries == null ? List.of() : entries.stream().filter(Objects::nonNull).toList();
    if (safeEntries.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return runDbTask(
        "quarantine corrupt storage items for " + storageId,
        () -> {
          String sql =
              """
              INSERT INTO storage_item_quarantine(
                  storage_id, item_key, item_blob, amount, reason, quarantined_at)
              VALUES(?, ?, ?, ?, ?, ?)
              ON CONFLICT(storage_id, item_key) DO UPDATE SET
                  item_blob = excluded.item_blob,
                  amount = excluded.amount,
                  reason = excluded.reason,
                  quarantined_at = excluded.quarantined_at
              """;
          try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (StorageQuarantineEntry entry : safeEntries) {
              StorageCorruption corruption = entry.corruption();
              insert.setString(1, storageId);
              insert.setString(2, corruption.itemKey());
              insert.setBytes(3, entry.originalBlob());
              insert.setLong(4, corruption.amount());
              insert.setString(5, corruption.reason());
              insert.setLong(6, corruption.detectedAt());
              insert.addBatch();
            }
            insert.executeBatch();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to quarantine corrupt items for storage " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  private void quarantineStructuralRows(String storageId, Collection<StorageCorruption> corruptions)
      throws SQLException {
    if (corruptions == null || corruptions.isEmpty()) {
      return;
    }
    String sql =
        """
        INSERT INTO storage_item_quarantine(
            storage_id, item_key, item_blob, amount, reason, quarantined_at)
        SELECT storage_id, item_key, item_blob, amount, ?, ?
        FROM storage_items
        WHERE storage_id = ? AND item_key = ?
        ON CONFLICT(storage_id, item_key) DO UPDATE SET
            item_blob = excluded.item_blob,
            amount = excluded.amount,
            reason = excluded.reason,
            quarantined_at = excluded.quarantined_at
        """;
    try (PreparedStatement insert = connection.prepareStatement(sql)) {
      for (StorageCorruption corruption : corruptions) {
        insert.setString(1, corruption.reason());
        insert.setLong(2, corruption.detectedAt());
        insert.setString(3, storageId);
        insert.setString(4, corruption.itemKey());
        insert.addBatch();
      }
      insert.executeBatch();
    }
  }

  public CompletableFuture<Void> writeSnapshot(String storageId, Collection<DbItem> items) {
    Objects.requireNonNull(storageId, "storageId");
    Collection<DbItem> safeItems = items == null ? List.of() : items;
    return runDbTask(
        "write snapshot for " + storageId,
        () -> {
          try {
            writeSnapshotInternal(storageId, safeItems);
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to write snapshot for " + storageId, e);
            throw new CompletionException(e);
          }
        });
  }

  private void writeSnapshotInternal(String storageId, Collection<DbItem> items)
      throws SQLException {
    validatePersistableDbItems(storageId, items, "snapshot");
    try {
      connection.setAutoCommit(false);
      try (PreparedStatement delete =
          connection.prepareStatement("DELETE FROM storage_items WHERE storage_id = ?")) {
        delete.setString(1, storageId);
        delete.executeUpdate();
      }
      String insertSql =
          "INSERT INTO storage_items(storage_id, item_key, item_blob, amount) VALUES(?, ?, ?, ?)";
      try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
        int batchSize = 0;
        // Limit batch size to reduce memory spikes on very large storages.
        for (DbItem item : items) {
          insert.setString(1, storageId);
          insert.setString(2, item.key());
          insert.setBytes(3, item.blob());
          insert.setLong(4, item.amount());
          insert.addBatch();
          batchSize++;
          if (batchSize >= 1000) {
            insert.executeBatch();
            insert.clearBatch();
            batchSize = 0;
          }
        }
        if (batchSize > 0) {
          insert.executeBatch();
        }
      }
      try (PreparedStatement update =
          connection.prepareStatement("UPDATE storages SET updated_at = ? WHERE id = ?")) {
        update.setLong(1, Instant.now().getEpochSecond());
        update.setString(2, storageId);
        update.executeUpdate();
      }
      connection.commit();
    } catch (SQLException e) {
      try {
        connection.rollback();
      } catch (SQLException ex) {
        log(Level.SEVERE, "Failed to rollback transaction", ex);
      }
      throw e;
    } finally {
      try {
        connection.setAutoCommit(true);
      } catch (SQLException ignored) {
      }
    }
  }

  public CompletableFuture<Void> writeDelta(
      String storageId, Collection<DbItem> upserts, Collection<String> removals) {
    Objects.requireNonNull(storageId, "storageId");
    return runDbTask(
        "write delta for " + storageId,
        () -> {
          Collection<DbItem> safeUpserts = upserts == null ? List.of() : upserts;
          if (safeUpserts.isEmpty() && (removals == null || removals.isEmpty())) {
            return;
          }
          validatePersistableDbItems(storageId, safeUpserts, "delta");
          try {
            connection.setAutoCommit(false);
            if (removals != null && !removals.isEmpty()) {
              try (PreparedStatement delete =
                  connection.prepareStatement(
                      "DELETE FROM storage_items WHERE storage_id = ? AND item_key = ?")) {
                int batchSize = 0;
                for (String key : removals) {
                  if (key == null || key.isEmpty()) continue;
                  delete.setString(1, storageId);
                  delete.setString(2, key);
                  delete.addBatch();
                  batchSize++;
                  if (batchSize >= 1000) {
                    delete.executeBatch();
                    delete.clearBatch();
                    batchSize = 0;
                  }
                }
                if (batchSize > 0) {
                  delete.executeBatch();
                }
              }
            }
            if (!safeUpserts.isEmpty()) {
              String insertSql =
                  """
                      INSERT INTO storage_items(storage_id, item_key, item_blob, amount)
                      VALUES(?, ?, ?, ?)
                      ON CONFLICT(storage_id, item_key) DO UPDATE SET
                          item_blob = excluded.item_blob,
                          amount = excluded.amount
                  """;
              try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                int batchSize = 0;
                for (DbItem item : safeUpserts) {
                  insert.setString(1, storageId);
                  insert.setString(2, item.key());
                  insert.setBytes(3, item.blob());
                  insert.setLong(4, item.amount());
                  insert.addBatch();
                  batchSize++;
                  if (batchSize >= 1000) {
                    insert.executeBatch();
                    insert.clearBatch();
                    batchSize = 0;
                  }
                }
                if (batchSize > 0) {
                  insert.executeBatch();
                }
              }
            }
            try (PreparedStatement update =
                connection.prepareStatement("UPDATE storages SET updated_at = ? WHERE id = ?")) {
              update.setLong(1, Instant.now().getEpochSecond());
              update.setString(2, storageId);
              update.executeUpdate();
            }
            connection.commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to write delta for " + storageId, e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Void> writeDeltaBatch(Collection<DeltaWrite> writes) {
    Collection<DeltaWrite> safeWrites =
        writes == null
            ? List.of()
            : writes.stream().filter(write -> write != null && write.storageId() != null).toList();
    return runDbTask(
        "write storage delta batch",
        () -> {
          if (safeWrites.isEmpty()) {
            return;
          }
          for (DeltaWrite write : safeWrites) {
            validatePersistableDbItems(
                write.storageId(),
                write.upserts() == null ? List.of() : write.upserts(),
                "delta-batch");
          }
          try {
            connection.setAutoCommit(false);
            try (PreparedStatement delete =
                    connection.prepareStatement(
                        "DELETE FROM storage_items WHERE storage_id = ? AND item_key = ?");
                PreparedStatement insert =
                    connection.prepareStatement(
                        """
                        INSERT INTO storage_items(storage_id, item_key, item_blob, amount)
                        VALUES(?, ?, ?, ?)
                        ON CONFLICT(storage_id, item_key) DO UPDATE SET
                            item_blob = excluded.item_blob,
                            amount = excluded.amount
                        """);
                PreparedStatement update =
                    connection.prepareStatement(
                        "UPDATE storages SET updated_at = ? WHERE id = ?")) {
              int deleteBatch = 0;
              int insertBatch = 0;
              long now = Instant.now().getEpochSecond();
              for (DeltaWrite write : safeWrites) {
                String storageId = write.storageId();
                Collection<String> removals =
                    write.removals() == null ? List.of() : write.removals();
                for (String key : removals) {
                  if (key == null || key.isEmpty()) continue;
                  delete.setString(1, storageId);
                  delete.setString(2, key);
                  delete.addBatch();
                  deleteBatch++;
                  if (deleteBatch >= 1000) {
                    delete.executeBatch();
                    delete.clearBatch();
                    deleteBatch = 0;
                  }
                }
                Collection<DbItem> upserts = write.upserts() == null ? List.of() : write.upserts();
                for (DbItem item : upserts) {
                  insert.setString(1, storageId);
                  insert.setString(2, item.key());
                  insert.setBytes(3, item.blob());
                  insert.setLong(4, item.amount());
                  insert.addBatch();
                  insertBatch++;
                  if (insertBatch >= 1000) {
                    insert.executeBatch();
                    insert.clearBatch();
                    insertBatch = 0;
                  }
                }
                update.setLong(1, now);
                update.setString(2, storageId);
                update.addBatch();
              }
              if (deleteBatch > 0) {
                delete.executeBatch();
              }
              if (insertBatch > 0) {
                insert.executeBatch();
              }
              update.executeBatch();
            }
            connection.commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to write storage delta batch", e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  private void validatePersistableDbItems(
      String storageId, Collection<DbItem> items, String operation) {
    if (items == null) return;
    for (DbItem item : items) {
      validatePersistableDbItem(storageId, item, operation);
    }
  }

  private void validatePersistableDbItem(String storageId, DbItem item, String operation) {
    if (item == null) {
      throw invalidStorageWrite(storageId, "<null>", 0L, 0L, operation, "item is null");
    }
    if (item.key() == null || item.key().isBlank()) {
      throw invalidStorageWrite(
          storageId, item.key(), item.amount(), blobLength(item), operation, "missing key");
    }
    if (item.amount() <= 0) {
      throw invalidStorageWrite(
          storageId,
          item.key(),
          item.amount(),
          blobLength(item),
          operation,
          "amount must be positive");
    }
    byte[] blob = item.blob();
    if (blob == null || blob.length == 0 || blob.length > MAX_ITEM_BLOB_BYTES) {
      throw invalidStorageWrite(
          storageId,
          item.key(),
          item.amount(),
          blob == null ? 0L : blob.length,
          operation,
          "invalid serialized item blob length");
    }
  }

  private IllegalArgumentException invalidStorageWrite(
      String storageId, String key, long amount, long blobLength, String operation, String reason) {
    return new IllegalArgumentException(
        "Invalid storage item for "
            + operation
            + " write"
            + " (storage="
            + storageId
            + ", key="
            + key
            + ", amount="
            + amount
            + ", blobLength="
            + blobLength
            + "): "
            + reason);
  }

  private long blobLength(DbItem item) {
    if (item == null || item.blob() == null) return 0L;
    return item.blob().length;
  }

  private void warnInvalidStorageRow(
      String storageId, String key, long amount, long blobLength, String reason) {
    logWarning(
        "Storage "
            + storageId
            + ": skipping invalid DB item row: "
            + reason
            + " (key="
            + key
            + ", amount="
            + amount
            + ", blobLength="
            + blobLength
            + ")");
  }

  private void log(Level level, String message, Throwable thrown) {
    if (logger != null) {
      logger.log(level, message, thrown);
    }
  }

  private void logWarning(String message) {
    if (logger != null) {
      logger.warning(message);
    }
  }

  @Override
  public void close() {
    closing.set(true);
    executor.shutdown();
    try {
      if (!executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        logWarning(
            "Database executor did not stop within "
                + CLOSE_TIMEOUT_SECONDS
                + "s; forcing shutdown");
        executor.shutdownNow();
        executor.awaitTermination(FORCED_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log(Level.WARNING, "Interrupted while waiting for database shutdown", e);
      executor.shutdownNow();
    }
    CancellationException databaseClosed =
        new CancellationException("Database closed before queued operation completed");
    pendingDbFutures.forEach(future -> future.completeExceptionally(databaseClosed));
    pendingDbFutures.clear();
    queuedOperations.set(0);
    PerfStats.setGauge("storage-db.queueDepth", 0);
    CancellationException auditClosed =
        new CancellationException("Database closed before audit observation was persisted");
    pendingAuditObservations
        .values()
        .forEach(pending -> pending.result().completeExceptionally(auditClosed));
    pendingAuditObservations.clear();
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        log(Level.SEVERE, "Failed to close database", e);
      }
    }
  }
}
