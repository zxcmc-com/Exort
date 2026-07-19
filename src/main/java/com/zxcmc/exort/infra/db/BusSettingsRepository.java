package com.zxcmc.exort.infra.db;

import com.zxcmc.exort.bus.BusFilterCodec;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BusSettingsRepository {
  private final SqliteDatabase database;
  private final Logger logger;

  BusSettingsRepository(SqliteDatabase database, Logger logger) {
    this.database = Objects.requireNonNull(database, "database");
    this.logger = logger;
  }

  CompletableFuture<Optional<BusSettings>> load(BusPos pos, int slots) {
    Objects.requireNonNull(pos, "pos");
    return database.supply(
        "load bus settings for " + pos,
        () -> {
          String sql =
              "SELECT type, mode, filters FROM bus_settings WHERE world = ? AND x = ? AND y = ? AND"
                  + " z = ? LIMIT 1";
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, pos.world().toString());
            statement.setInt(2, pos.x());
            statement.setInt(3, pos.y());
            statement.setInt(4, pos.z());
            try (ResultSet result = statement.executeQuery()) {
              if (result.next()) {
                return Optional.of(
                    new BusSettings(
                        pos,
                        BusType.fromString(result.getString("type")),
                        BusMode.fromString(result.getString("mode")),
                        BusFilterCodec.decode(result.getBytes("filters"), slots)));
              }
            }
          } catch (SQLException error) {
            log(Level.SEVERE, "Failed to load bus settings for " + pos, error);
            throw new CompletionException(error);
          }
          return Optional.empty();
        });
  }

  CompletableFuture<Void> save(BusSettings settings, int slots) {
    Objects.requireNonNull(settings, "settings");
    long now = Instant.now().getEpochSecond();
    return database.run(
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
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, settings.pos().world().toString());
            statement.setInt(2, settings.pos().x());
            statement.setInt(3, settings.pos().y());
            statement.setInt(4, settings.pos().z());
            statement.setString(5, settings.type() == null ? "IMPORT" : settings.type().name());
            statement.setString(6, settings.mode() == null ? "DISABLED" : settings.mode().name());
            statement.setBytes(7, BusFilterCodec.encode(settings.filters(), slots));
            statement.setLong(8, now);
            statement.executeUpdate();
          } catch (SQLException error) {
            log(Level.SEVERE, "Failed to save bus settings for " + settings.pos(), error);
            throw new CompletionException(error);
          }
        });
  }

  CompletableFuture<Void> delete(BusPos pos) {
    Objects.requireNonNull(pos, "pos");
    return database.run(
        "delete bus settings for " + pos,
        () -> {
          String sql = "DELETE FROM bus_settings WHERE world = ? AND x = ? AND y = ? AND z = ?";
          try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, pos.world().toString());
            statement.setInt(2, pos.x());
            statement.setInt(3, pos.y());
            statement.setInt(4, pos.z());
            statement.executeUpdate();
          } catch (SQLException error) {
            log(Level.SEVERE, "Failed to delete bus settings for " + pos, error);
            throw new CompletionException(error);
          }
        });
  }

  private void log(Level level, String message, Throwable error) {
    if (logger != null) {
      logger.log(level, message, error);
    }
  }
}
