package com.zxcmc.exort.infra.db;

import com.zxcmc.exort.storage.StorageClaim;
import com.zxcmc.exort.storage.StorageClaimConflictException;
import com.zxcmc.exort.storage.StorageClaimLocation;
import com.zxcmc.exort.storage.StorageCorruption;
import com.zxcmc.exort.storage.StorageLoadResult;
import com.zxcmc.exort.storage.StorageNameNormalizer;
import com.zxcmc.exort.storage.StorageQuarantineEntry;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.storage.sort.SortMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class StorageRepository {
  private static final int MAX_ITEM_BLOB_BYTES = 1_048_576;

  private final SqliteDatabase database;
  private final Logger logger;
  private final Supplier<String> defaultSortModeName;
  private final Supplier<StorageTierCatalog> storageTiers;

  StorageRepository(SqliteDatabase database, Logger logger, Supplier<String> defaultSortModeName) {
    this(database, logger, defaultSortModeName, StorageTierCatalog::active);
  }

  StorageRepository(
      SqliteDatabase database,
      Logger logger,
      Supplier<String> defaultSortModeName,
      Supplier<StorageTierCatalog> storageTiers) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = logger;
    this.defaultSortModeName =
        defaultSortModeName == null ? () -> SortMode.AMOUNT.name() : defaultSortModeName;
    this.storageTiers = storageTiers == null ? StorageTierCatalog::active : storageTiers;
  }

  public CompletableFuture<Void> ensureStorage(String id) {
    Objects.requireNonNull(id, "id");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    return database.run(
        "ensure storage " + id,
        () -> {
          String sql =
              "INSERT OR IGNORE INTO storages(id, tier, sort_mode, created_at, updated_at)"
                  + " VALUES(?, NULL, ?, ?, ?)";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
    Long tierMaxItems = storageTiers.get().find(tierKey).map(StorageTier::maxItems).orElse(null);
    return setStorageTier(storageId, tierKey, tierMaxItems);
  }

  public CompletableFuture<Void> setStorageTier(
      String storageId, String tierKey, Long tierMaxItems) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(tierKey, "tierKey");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    return database.run(
        "set storage tier " + storageId,
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, tier_max_items, sort_mode, created_at, updated_at)"
                  + " VALUES(?, ?, ?, COALESCE((SELECT sort_mode FROM storages WHERE id = ?), ?),"
                  + " ?, ?) ON CONFLICT(id) DO UPDATE SET tier = excluded.tier, tier_max_items ="
                  + " excluded.tier_max_items, updated_at = excluded.updated_at";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
    String normalizedName = StorageNameNormalizer.normalize(displayName);
    return database.run(
        "set storage metadata " + storageId,
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, tier_max_items, display_name, sort_mode,"
                  + " created_at, updated_at)"
                  + " VALUES(?, ?, ?, ?, COALESCE((SELECT sort_mode FROM storages WHERE id = ?),"
                  + " ?), ?, ?) ON CONFLICT(id) DO UPDATE SET tier = excluded.tier,"
                  + " tier_max_items = excluded.tier_max_items, display_name ="
                  + " excluded.display_name, updated_at = excluded.updated_at";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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

  public CompletableFuture<List<StorageClaim>> loadStorageClaims() {
    return database.supply(
        "load physical storage claims",
        () -> {
          List<StorageClaim> claims = new ArrayList<>();
          String sql =
              "SELECT storage_id, world_uuid, world_key, world_name, x, y, z, claimed_at,"
                  + " updated_at FROM storage_claims ORDER BY claimed_at, storage_id";
          try (PreparedStatement ps = database.connection().prepareStatement(sql);
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

  public CompletableFuture<Void> insertStorageClaim(
      StorageClaim claim, String tierKey, long tierMaxItems, String displayName) {
    Objects.requireNonNull(claim, "claim");
    Objects.requireNonNull(tierKey, "tierKey");
    if (tierMaxItems <= 0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("tierMaxItems must be positive"));
    }
    String normalizedName = StorageNameNormalizer.normalize(displayName);
    String defaultSort = defaultSortModeName();
    return database.run(
        "claim physical storage " + claim.storageId(),
        () -> {
          try {
            database.connection().setAutoCommit(false);
            String storageSql =
                "INSERT INTO storages(id, tier, tier_max_items, display_name, sort_mode,"
                    + " created_at, updated_at) VALUES(?, ?, ?, ?, COALESCE((SELECT sort_mode FROM"
                    + " storages WHERE id = ?), ?), ?, ?) ON CONFLICT(id) DO UPDATE SET tier ="
                    + " excluded.tier, tier_max_items = excluded.tier_max_items, display_name ="
                    + " excluded.display_name, updated_at = excluded.updated_at";
            try (PreparedStatement ps = database.connection().prepareStatement(storageSql)) {
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
            try (PreparedStatement ps = database.connection().prepareStatement(claimSql)) {
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
            database.connection().commit();
          } catch (SQLException e) {
            rollbackQuietly("storage claim " + claim.storageId(), e);
            throw new CompletionException(storageClaimConflict(claim, e));
          } finally {
            restoreAutoCommit();
          }
        });
  }

  public CompletableFuture<Boolean> deleteStorageClaimExact(
      String storageId, StorageClaimLocation location) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(location, "location");
    return database.supply(
        "release physical storage claim " + storageId,
        () -> {
          String sql =
              "DELETE FROM storage_claims WHERE storage_id = ? AND world_uuid = ? AND x = ? AND y"
                  + " = ? AND z = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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

  public CompletableFuture<Boolean> moveStorageClaimExact(
      StorageClaim source, StorageClaim destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    if (!source.storageId().equals(destination.storageId())) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Storage claim identity cannot change during a move"));
    }
    return database.supply(
        "move physical storage claim " + source.storageId(),
        () -> {
          String sql =
              "UPDATE storage_claims SET world_uuid = ?, world_key = ?, world_name = ?, x = ?, y"
                  + " = ?, z = ?, updated_at = ? WHERE storage_id = ? AND world_uuid = ? AND x = ?"
                  + " AND y = ? AND z = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
      database.connection().rollback();
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
      database.connection().setAutoCommit(true);
    } catch (SQLException e) {
      log(Level.SEVERE, "Failed to restore SQLite auto-commit", e);
    }
  }

  public CompletableFuture<Optional<String>> getStorageSortMode(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return database.supply(
        "read storage sort mode " + storageId,
        () -> {
          String sql = "SELECT sort_mode FROM storages WHERE id = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
    return database.run(
        "set storage sort mode " + storageId,
        () -> {
          String sql = "UPDATE storages SET sort_mode = ?, updated_at = ? WHERE id = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
    String normalizedName = StorageNameNormalizer.normalize(displayName);
    List<DbItem> safeItems = snapshotItems(items);
    return database.run(
        "create storage " + storageId,
        () -> {
          validatePersistableDbItems(storageId, safeItems, "create");
          try {
            database.connection().setAutoCommit(false);
            String sql =
                "INSERT OR REPLACE INTO storages(id, tier, tier_max_items, display_name,"
                    + " sort_mode, created_at, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
                database
                    .connection()
                    .prepareStatement("DELETE FROM storage_items WHERE storage_id = ?")) {
              delete.setString(1, storageId);
              delete.executeUpdate();
            }
            String insertSql =
                "INSERT INTO storage_items(storage_id, item_key, item_blob, amount) VALUES(?, ?, ?,"
                    + " ?)";
            try (PreparedStatement insert = database.connection().prepareStatement(insertSql)) {
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
            database.connection().commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to create storage " + storageId, e);
            try {
              database.connection().rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              database.connection().setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Void> deleteStorageForInternalCleanup(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return database.run(
        "delete storage for internal cleanup " + storageId,
        () -> {
          try {
            database.connection().setAutoCommit(false);
            try (PreparedStatement deleteItems =
                database
                    .connection()
                    .prepareStatement("DELETE FROM storage_items WHERE storage_id = ?")) {
              deleteItems.setString(1, storageId);
              deleteItems.executeUpdate();
            }
            try (PreparedStatement deleteStorage =
                database.connection().prepareStatement("DELETE FROM storages WHERE id = ?")) {
              deleteStorage.setString(1, storageId);
              deleteStorage.executeUpdate();
            }
            database.connection().commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to delete storage for internal cleanup " + storageId, e);
            try {
              database.connection().rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback storage cleanup transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              database.connection().setAutoCommit(true);
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
    return database.run(
        "clone storage " + fromId + " to " + toId,
        () -> {
          try {
            database.connection().setAutoCommit(false);
            int rows;
            String insertSql =
                "INSERT INTO storages(id, tier, tier_max_items, display_name, sort_mode,"
                    + " created_at, updated_at)"
                    + " SELECT ?, tier, tier_max_items, display_name, sort_mode, ?, ? FROM"
                    + " storages WHERE id = ?";
            try (PreparedStatement ps = database.connection().prepareStatement(insertSql)) {
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
                  database
                      .connection()
                      .prepareStatement(
                          "UPDATE storages SET tier = ?, tier_max_items = ?, updated_at = ? WHERE"
                              + " id = ?")) {
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
            try (PreparedStatement ps = database.connection().prepareStatement(itemsSql)) {
              ps.setString(1, toId);
              ps.setString(2, fromId);
              ps.executeUpdate();
            }
            database.connection().commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to clone storage " + fromId + " to " + toId, e);
            try {
              database.connection().rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              database.connection().setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Optional<String>> getStorageTier(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return database.supply(
        "read storage tier " + storageId,
        () -> {
          String sql = "SELECT tier FROM storages WHERE id = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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

  public CompletableFuture<Optional<Database.StorageTierState>> getStorageTierState(
      String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return database.supply(
        "read storage tier state " + storageId,
        () -> {
          String sql = "SELECT tier, tier_max_items FROM storages WHERE id = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                String tier = rs.getString("tier");
                long maxItems = rs.getLong("tier_max_items");
                Long tierMaxItems = rs.wasNull() ? null : maxItems;
                return Optional.of(new Database.StorageTierState(tier, tierMaxItems));
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
    return database.supply(
        "read storage display name " + storageId,
        () -> {
          String sql = "SELECT display_name FROM storages WHERE id = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return Optional.ofNullable(
                    StorageNameNormalizer.normalize(rs.getString("display_name")));
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
    String normalizedName = StorageNameNormalizer.normalize(displayName);
    return database.run(
        "set storage display name " + storageId,
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, display_name, sort_mode, created_at, updated_at)"
                  + " VALUES(?, NULL, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET display_name ="
                  + " excluded.display_name, updated_at = excluded.updated_at";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
    return database.supply(
        "check storage existence " + storageId,
        () -> {
          String sql = "SELECT 1 FROM storages WHERE id = ? LIMIT 1";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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

  public CompletableFuture<Map<String, DbItem>> loadStorage(String storageId) {
    return loadStorageWithHealth(storageId).thenApply(StorageLoadResult::items);
  }

  public CompletableFuture<StorageLoadResult> loadStorageWithHealth(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return database.supply(
        "load storage " + storageId,
        () -> {
          Map<String, DbItem> items = new HashMap<>();
          List<StorageCorruption> corruptions = new ArrayList<>();
          long detectedAt = Instant.now().getEpochSecond();
          String sql =
              "SELECT item_key, item_blob, amount, length(item_blob) AS blob_len FROM"
                  + " storage_items WHERE storage_id = ?";
          try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
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
    return database.run(
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
          try (PreparedStatement insert = database.connection().prepareStatement(sql)) {
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
    try (PreparedStatement insert = database.connection().prepareStatement(sql)) {
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
    List<DbItem> safeItems = snapshotItems(items);
    return database.run(
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
      database.connection().setAutoCommit(false);
      try (PreparedStatement delete =
          database
              .connection()
              .prepareStatement("DELETE FROM storage_items WHERE storage_id = ?")) {
        delete.setString(1, storageId);
        delete.executeUpdate();
      }
      String insertSql =
          "INSERT INTO storage_items(storage_id, item_key, item_blob, amount) VALUES(?, ?, ?, ?)";
      try (PreparedStatement insert = database.connection().prepareStatement(insertSql)) {
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
          database
              .connection()
              .prepareStatement("UPDATE storages SET updated_at = ? WHERE id = ?")) {
        update.setLong(1, Instant.now().getEpochSecond());
        update.setString(2, storageId);
        update.executeUpdate();
      }
      database.connection().commit();
    } catch (SQLException e) {
      try {
        database.connection().rollback();
      } catch (SQLException ex) {
        log(Level.SEVERE, "Failed to rollback transaction", ex);
      }
      throw e;
    } finally {
      try {
        database.connection().setAutoCommit(true);
      } catch (SQLException ignored) {
      }
    }
  }

  public CompletableFuture<Void> writeDelta(
      String storageId, Collection<DbItem> upserts, Collection<String> removals) {
    Objects.requireNonNull(storageId, "storageId");
    List<DbItem> safeUpserts = snapshotItems(upserts);
    List<String> safeRemovals = snapshotStrings(removals);
    return database.run(
        "write delta for " + storageId,
        () -> {
          if (safeUpserts.isEmpty() && safeRemovals.isEmpty()) {
            return;
          }
          validatePersistableDbItems(storageId, safeUpserts, "delta");
          try {
            database.connection().setAutoCommit(false);
            if (!safeRemovals.isEmpty()) {
              try (PreparedStatement delete =
                  database
                      .connection()
                      .prepareStatement(
                          "DELETE FROM storage_items WHERE storage_id = ? AND item_key = ?")) {
                int batchSize = 0;
                for (String key : safeRemovals) {
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
              try (PreparedStatement insert = database.connection().prepareStatement(insertSql)) {
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
                database
                    .connection()
                    .prepareStatement("UPDATE storages SET updated_at = ? WHERE id = ?")) {
              update.setLong(1, Instant.now().getEpochSecond());
              update.setString(2, storageId);
              update.executeUpdate();
            }
            database.connection().commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to write delta for " + storageId, e);
            try {
              database.connection().rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              database.connection().setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        });
  }

  public CompletableFuture<Void> writeDeltaBatch(Collection<Database.DeltaWrite> writes) {
    List<Database.DeltaWrite> safeWrites =
        writes == null
            ? List.of()
            : writes.stream()
                .filter(write -> write != null && write.storageId() != null)
                .map(
                    write ->
                        new Database.DeltaWrite(
                            write.storageId(), write.upserts(), write.removals()))
                .toList();
    return database.run(
        "write storage delta batch",
        () -> {
          if (safeWrites.isEmpty()) {
            return;
          }
          for (Database.DeltaWrite write : safeWrites) {
            validatePersistableDbItems(
                write.storageId(),
                write.upserts() == null ? List.of() : write.upserts(),
                "delta-batch");
          }
          try {
            database.connection().setAutoCommit(false);
            try (PreparedStatement delete =
                    database
                        .connection()
                        .prepareStatement(
                            "DELETE FROM storage_items WHERE storage_id = ? AND item_key = ?");
                PreparedStatement insert =
                    database
                        .connection()
                        .prepareStatement(
                            """
                            INSERT INTO storage_items(storage_id, item_key, item_blob, amount)
                            VALUES(?, ?, ?, ?)
                            ON CONFLICT(storage_id, item_key) DO UPDATE SET
                                item_blob = excluded.item_blob,
                                amount = excluded.amount
                            """);
                PreparedStatement update =
                    database
                        .connection()
                        .prepareStatement("UPDATE storages SET updated_at = ? WHERE id = ?")) {
              int deleteBatch = 0;
              int insertBatch = 0;
              long now = Instant.now().getEpochSecond();
              for (Database.DeltaWrite write : safeWrites) {
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
            database.connection().commit();
          } catch (SQLException e) {
            log(Level.SEVERE, "Failed to write storage delta batch", e);
            try {
              database.connection().rollback();
            } catch (SQLException ex) {
              log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
            throw new CompletionException(e);
          } finally {
            try {
              database.connection().setAutoCommit(true);
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

  static List<DbItem> snapshotItems(Collection<DbItem> items) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    List<DbItem> snapshot = new ArrayList<>(items.size());
    for (DbItem item : items) {
      snapshot.add(item == null ? null : new DbItem(item.key(), item.blob(), item.amount()));
    }
    return Collections.unmodifiableList(snapshot);
  }

  static List<String> snapshotStrings(Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return Collections.unmodifiableList(new ArrayList<>(values));
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

  private String defaultSortModeName() {
    return SortMode.fromString(defaultSortModeName.get()).name();
  }

  private static void setNullableLong(PreparedStatement statement, int index, Long value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, java.sql.Types.INTEGER);
    } else {
      statement.setLong(index, value);
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
}
