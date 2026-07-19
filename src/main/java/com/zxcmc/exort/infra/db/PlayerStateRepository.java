package com.zxcmc.exort.infra.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PlayerStateRepository {
  private final SqliteDatabase database;
  private final Logger logger;

  PlayerStateRepository(SqliteDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = logger;
  }

  CompletableFuture<Void> updateLastStorage(
      UUID playerId, String storageId, String world, int x, int y, int z) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(world, "world");
    long now = Instant.now().getEpochSecond();
    return database.run(
        "update last storage for " + playerId,
        () -> {
          String sql =
              "INSERT INTO players(player_uuid, last_storage_id, last_world, last_x, last_y,"
                  + " last_z, updated_at) VALUES(?, ?, ?, ?, ?, ?, ?) ON CONFLICT(player_uuid) DO"
                  + " UPDATE SET last_storage_id = excluded.last_storage_id, last_world ="
                  + " excluded.last_world, last_x = excluded.last_x, last_y = excluded.last_y,"
                  + " last_z = excluded.last_z, updated_at = excluded.updated_at";
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, storageId);
            statement.setString(3, world);
            statement.setInt(4, x);
            statement.setInt(5, y);
            statement.setInt(6, z);
            statement.setLong(7, now);
            statement.executeUpdate();
          } catch (SQLException error) {
            fail("Failed to update last storage for " + playerId, error);
          }
        });
  }

  CompletableFuture<Void> updateLastStorageLocation(
      String storageId, String world, int x, int y, int z) {
    Objects.requireNonNull(storageId, "storageId");
    Objects.requireNonNull(world, "world");
    long now = Instant.now().getEpochSecond();
    return database.run(
        "update last storage location for " + storageId,
        () -> {
          String sql =
              "UPDATE players SET last_world = ?, last_x = ?, last_y = ?, last_z = ?, updated_at ="
                  + " ? WHERE last_storage_id = ?";
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            statement.setLong(5, now);
            statement.setString(6, storageId);
            statement.executeUpdate();
          } catch (SQLException error) {
            fail("Failed to update last storage location for " + storageId, error);
          }
        });
  }

  CompletableFuture<Optional<Database.PlayerLastStorage>> getLastStorage(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return database.supply(
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
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
              if (result.next()) {
                return Optional.of(
                    new Database.PlayerLastStorage(
                        result.getString("storage_id"),
                        result.getString("tier"),
                        result.getString("world"),
                        result.getInt("x"),
                        result.getInt("y"),
                        result.getInt("z"),
                        result.getLong("updated_at")));
              }
            }
          } catch (SQLException error) {
            fail("Failed to read last storage for " + playerId, error);
          }
          return Optional.empty();
        });
  }

  CompletableFuture<Map<UUID, Database.ClientCullingState>> loadCullingStates() {
    return database.supply(
        "load client culling states",
        () -> {
          Map<UUID, Database.ClientCullingState> states = new HashMap<>();
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
          try (PreparedStatement statement = database.connection().prepareStatement(sql);
              ResultSet result = statement.executeQuery()) {
            while (result.next()) {
              try {
                UUID playerId = UUID.fromString(result.getString("player_uuid"));
                states.put(
                    playerId,
                    new Database.ClientCullingState(
                        playerId,
                        result.getInt("manual_bypass") != 0,
                        result.getString("last_match_brand"),
                        result.getString("last_probe_state"),
                        result.getLong("last_seen_at"),
                        result.getLong("updated_at")));
              } catch (IllegalArgumentException ignored) {
                // Ignore hand-edited invalid UUIDs.
              }
            }
          } catch (SQLException error) {
            fail("Failed to load client culling states", error);
          }
          return Map.copyOf(states);
        });
  }

  CompletableFuture<Void> setManualBypass(UUID playerId, boolean enabled) {
    Objects.requireNonNull(playerId, "playerId");
    long now = Instant.now().getEpochSecond();
    return database.run(
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
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, enabled ? 1 : 0);
            statement.setLong(3, now);
            statement.executeUpdate();
          } catch (SQLException error) {
            fail("Failed to save client culling manual bypass for " + playerId, error);
          }
        });
  }

  CompletableFuture<Void> recordProbeResult(
      UUID playerId, String probeState, String brand, boolean match) {
    Objects.requireNonNull(playerId, "playerId");
    long now = Instant.now().getEpochSecond();
    String normalizedState = emptyToNull(probeState);
    String normalizedBrand = emptyToNull(brand);
    return database.run(
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
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, match ? normalizedBrand : null);
            statement.setString(3, normalizedState);
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
          } catch (SQLException error) {
            fail("Failed to save client culling probe result for " + playerId, error);
          }
        });
  }

  CompletableFuture<Void> updateLastSeen(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    long now = Instant.now().getEpochSecond();
    return database.run(
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
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, now);
            statement.setLong(3, now);
            statement.executeUpdate();
          } catch (SQLException error) {
            fail("Failed to save client culling last seen for " + playerId, error);
          }
        });
  }

  private void fail(String message, SQLException error) {
    if (logger != null) {
      logger.log(Level.SEVERE, message, error);
    }
    throw new CompletionException(error);
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
