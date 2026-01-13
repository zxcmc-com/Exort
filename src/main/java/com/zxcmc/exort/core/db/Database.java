package com.zxcmc.exort.core.db;

import com.zxcmc.exort.bus.BusFilterCodec;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.gui.SortMode;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class Database implements AutoCloseable {
  private final ExortPlugin plugin;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "exort-db");
            t.setDaemon(true);
            return t;
          });
  private Connection connection;

  public Database(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public void init(File file) throws SQLException {
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    String url = "jdbc:sqlite:" + file.getAbsolutePath();
    connection = DriverManager.getConnection(url);
    applyPragmas();
    ensureSchema();
  }

  private void applyPragmas() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("PRAGMA journal_mode=WAL");
      stmt.execute("PRAGMA synchronous=NORMAL");
      stmt.execute("PRAGMA foreign_keys=ON");
    }
  }

  private void createTables() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS storages (
                  id TEXT PRIMARY KEY,
                  tier TEXT NULL,
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
    }
  }

  private void ensureSchema() throws SQLException {
    createTables();
    ensureColumn("storages", "sort_mode", "TEXT");
  }

  private void ensureColumn(String table, String column, String type) throws SQLException {
    boolean exists = false;
    try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          String name = rs.getString("name");
          if (column.equalsIgnoreCase(name)) {
            exists = true;
            break;
          }
        }
      }
    }
    if (!exists) {
      try (Statement stmt = connection.createStatement()) {
        stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
      }
    }
  }

  public CompletableFuture<Void> ensureStorage(String id) {
    Objects.requireNonNull(id, "id");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    return CompletableFuture.runAsync(
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
            plugin.getLogger().log(Level.SEVERE, "Failed to ensure storage row", e);
          }
        },
        executor);
  }

  public CompletableFuture<Void> setStorageTier(String storageId, String tierKey) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(tierKey, "tierKey");
    long now = Instant.now().getEpochSecond();
    String defaultSort = defaultSortModeName();
    return CompletableFuture.runAsync(
        () -> {
          String sql =
              "INSERT INTO storages(id, tier, sort_mode, created_at, updated_at) VALUES(?, ?,"
                  + " COALESCE((SELECT sort_mode FROM storages WHERE id = ?), ?), ?, ?) ON"
                  + " CONFLICT(id) DO UPDATE SET tier = excluded.tier, updated_at ="
                  + " excluded.updated_at";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            ps.setString(2, tierKey);
            ps.setString(3, storageId);
            ps.setString(4, defaultSort);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
          } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set storage tier for " + storageId, e);
          }
        },
        executor);
  }

  private String defaultSortModeName() {
    String raw = plugin.getConfig().getString("defaultSortMode", "AMOUNT");
    return SortMode.fromString(raw).name();
  }

  public CompletableFuture<Optional<String>> getStorageSortMode(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return CompletableFuture.supplyAsync(
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
            plugin
                .getLogger()
                .log(Level.SEVERE, "Failed to read storage sort mode for " + storageId, e);
          }
          return Optional.empty();
        },
        executor);
  }

  public CompletableFuture<Void> setStorageSortMode(String storageId, String sortMode) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(sortMode, "sortMode");
    long now = Instant.now().getEpochSecond();
    return CompletableFuture.runAsync(
        () -> {
          String sql = "UPDATE storages SET sort_mode = ?, updated_at = ? WHERE id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sortMode);
            ps.setLong(2, now);
            ps.setString(3, storageId);
            ps.executeUpdate();
          } catch (SQLException e) {
            plugin
                .getLogger()
                .log(Level.SEVERE, "Failed to set storage sort mode for " + storageId, e);
          }
        },
        executor);
  }

  public CompletableFuture<Optional<String>> getStorageTier(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return CompletableFuture.supplyAsync(
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
            plugin.getLogger().log(Level.SEVERE, "Failed to read storage tier for " + storageId, e);
          }
          return Optional.empty();
        },
        executor);
  }

  public CompletableFuture<Boolean> storageExists(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return CompletableFuture.supplyAsync(
        () -> {
          String sql = "SELECT 1 FROM storages WHERE id = ? LIMIT 1";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              return rs.next();
            }
          } catch (SQLException e) {
            plugin
                .getLogger()
                .log(Level.SEVERE, "Failed to check storage existence for " + storageId, e);
          }
          return false;
        },
        executor);
  }

  public CompletableFuture<Void> updatePlayerLastStorage(
      UUID playerId, String storageId, String world, int x, int y, int z) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(world, "world");
    long now = Instant.now().getEpochSecond();
    return CompletableFuture.runAsync(
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
            plugin
                .getLogger()
                .log(Level.SEVERE, "Failed to update last storage for " + playerId, e);
          }
        },
        executor);
  }

  public CompletableFuture<Optional<PlayerLastStorage>> getPlayerLastStorage(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return CompletableFuture.supplyAsync(
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
            plugin.getLogger().log(Level.SEVERE, "Failed to read last storage for " + playerId, e);
          }
          return Optional.empty();
        },
        executor);
  }

  public record PlayerLastStorage(
      String storageId, String tier, String world, int x, int y, int z, long updatedAt) {}

  public CompletableFuture<Optional<BusSettings>> loadBusSettings(BusPos pos, int slots) {
    Objects.requireNonNull(pos, "pos");
    return CompletableFuture.supplyAsync(
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
            plugin.getLogger().log(Level.SEVERE, "Failed to load bus settings for " + pos, e);
          }
          return Optional.empty();
        },
        executor);
  }

  public CompletableFuture<Void> saveBusSettings(BusSettings settings, int slots) {
    Objects.requireNonNull(settings, "settings");
    long now = Instant.now().getEpochSecond();
    return CompletableFuture.runAsync(
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
            plugin
                .getLogger()
                .log(Level.SEVERE, "Failed to save bus settings for " + settings.pos(), e);
          }
        },
        executor);
  }

  public CompletableFuture<Void> deleteBusSettings(BusPos pos) {
    Objects.requireNonNull(pos, "pos");
    return CompletableFuture.runAsync(
        () -> {
          String sql = "DELETE FROM bus_settings WHERE world = ? AND x = ? AND y = ? AND z = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pos.world().toString());
            ps.setInt(2, pos.x());
            ps.setInt(3, pos.y());
            ps.setInt(4, pos.z());
            ps.executeUpdate();
          } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete bus settings for " + pos, e);
          }
        },
        executor);
  }

  public CompletableFuture<Map<String, DbItem>> loadStorage(String storageId) {
    Objects.requireNonNull(storageId, "storageId");
    return CompletableFuture.supplyAsync(
        () -> {
          Map<String, DbItem> items = new HashMap<>();
          String sql = "SELECT item_key, item_blob, amount FROM storage_items WHERE storage_id = ?";
          try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, storageId);
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                String key = rs.getString("item_key");
                byte[] blob = rs.getBytes("item_blob");
                long amount = rs.getLong("amount");
                items.put(key, new DbItem(key, blob, amount));
              }
            }
          } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load storage " + storageId, e);
          }
          return items;
        },
        executor);
  }

  public CompletableFuture<Void> writeSnapshot(String storageId, Collection<DbItem> items) {
    Objects.requireNonNull(storageId, "storageId");
    return CompletableFuture.runAsync(
        () -> {
          try {
            connection.setAutoCommit(false);
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
              // Limit batch size to reduce memory spikes on very large storages.
              for (DbItem item : items) {
                if (item.amount() <= 0) continue;
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
            plugin.getLogger().log(Level.SEVERE, "Failed to write snapshot for " + storageId, e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        },
        executor);
  }

  public CompletableFuture<Void> writeDelta(
      String storageId, Collection<DbItem> upserts, Collection<String> removals) {
    Objects.requireNonNull(storageId, "storageId");
    return CompletableFuture.runAsync(
        () -> {
          if ((upserts == null || upserts.isEmpty()) && (removals == null || removals.isEmpty())) {
            return;
          }
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
            if (upserts != null && !upserts.isEmpty()) {
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
                for (DbItem item : upserts) {
                  if (item == null || item.amount() <= 0) continue;
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
            plugin.getLogger().log(Level.SEVERE, "Failed to write delta for " + storageId, e);
            try {
              connection.rollback();
            } catch (SQLException ex) {
              plugin.getLogger().log(Level.SEVERE, "Failed to rollback transaction", ex);
            }
          } finally {
            try {
              connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
          }
        },
        executor);
  }

  @Override
  public void close() {
    executor.shutdownNow();
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "Failed to close database", e);
      }
    }
  }
}
