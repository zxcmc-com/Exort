package com.zxcmc.exort.infra.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.chunkloader.ChunkLoaderObservation;
import com.zxcmc.exort.chunkloader.ChunkLoaderRecord;
import com.zxcmc.exort.chunkloader.ChunkLoaderRegistryStatus;
import com.zxcmc.exort.chunkloader.ChunkLoaderType;
import com.zxcmc.exort.storage.StorageClaim;
import com.zxcmc.exort.storage.StorageClaimConflictException;
import com.zxcmc.exort.storage.StorageClaimLocation;
import com.zxcmc.exort.storage.StorageCorruption;
import com.zxcmc.exort.storage.StorageQuarantineEntry;
import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {
  @TempDir java.nio.file.Path tempDir;

  @Test
  void semanticQuarantineCopiesOriginalBlobWithoutChangingSourceRow() throws Exception {
    File file = tempDir.resolve("semantic-quarantine.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      String storageId = "storage";
      String itemKey = "item";
      byte[] original = new byte[] {3, 1, 4};
      long now = Instant.now().getEpochSecond();
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        insertStorage(connection, storageId, now);
        insertItem(connection, storageId, itemKey, original, 7L);
      }

      database
          .quarantineStorageItems(
              storageId,
              List.of(
                  new StorageQuarantineEntry(
                      new StorageCorruption(itemKey, 7L, "decode failed", now), original)))
          .get(5, TimeUnit.SECONDS);

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        try (var source =
            connection.prepareStatement(
                "SELECT item_blob, amount FROM storage_items"
                    + " WHERE storage_id = ? AND item_key = ?")) {
          source.setString(1, storageId);
          source.setString(2, itemKey);
          try (var result = source.executeQuery()) {
            assertTrue(result.next());
            assertArrayEquals(original, result.getBytes("item_blob"));
            assertEquals(7L, result.getLong("amount"));
          }
        }
        try (var quarantine =
            connection.prepareStatement(
                "SELECT item_blob, amount, reason, quarantined_at"
                    + " FROM storage_item_quarantine WHERE storage_id = ? AND item_key = ?")) {
          quarantine.setString(1, storageId);
          quarantine.setString(2, itemKey);
          try (var result = quarantine.executeQuery()) {
            assertTrue(result.next());
            assertArrayEquals(original, result.getBytes("item_blob"));
            assertEquals(7L, result.getLong("amount"));
            assertEquals("decode failed", result.getString("reason"));
            assertEquals(now, result.getLong("quarantined_at"));
          }
        }
      }
    } finally {
      database.close();
    }
  }

  @Test
  void initRejectsDatabasePathBelowRegularFile() throws Exception {
    java.nio.file.Path parentFile = tempDir.resolve("not-a-directory");
    java.nio.file.Files.writeString(parentFile, "occupied");
    try (Database database = new Database()) {
      SQLException failure =
          assertThrows(
              SQLException.class, () -> database.init(parentFile.resolve("storage.db").toFile()));

      assertTrue(failure.getMessage().contains("Failed to create database directory"));
    }
  }

  @Test
  void chunkLoaderRecordsCanBeSavedListedReplacedByPositionAndDeleted() throws Exception {
    File file = tempDir.resolve("chunk-loaders.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID worldId = new UUID(0L, 1L);
      UUID firstId = new UUID(0L, 2L);
      UUID secondId = new UUID(0L, 3L);
      UUID placerId = new UUID(0L, 4L);
      ChunkLoaderRecord first =
          new ChunkLoaderRecord(
              firstId,
              ChunkLoaderType.PERSONAL_CHUNK_LOADER,
              worldId,
              "minecraft:overworld",
              "world",
              10,
              64,
              -5,
              0,
              -1,
              placerId,
              "Alex",
              1,
              true,
              true,
              100L,
              100L);
      ChunkLoaderRecord second =
          new ChunkLoaderRecord(
              secondId,
              ChunkLoaderType.DORMANT_CHUNK_LOADER,
              worldId,
              "minecraft:overworld",
              "world",
              10,
              64,
              -5,
              0,
              -1,
              placerId,
              "Alex",
              2,
              false,
              110L,
              110L);

      database.saveChunkLoader(first).get(5, TimeUnit.SECONDS);
      assertEquals(List.of(first), database.listChunkLoaders().get(5, TimeUnit.SECONDS));
      var registry = database.listChunkLoaderRegistry().get(5, TimeUnit.SECONDS);
      assertEquals(1, registry.size());
      assertEquals(firstId, registry.getFirst().id());
      assertEquals(ChunkLoaderType.PERSONAL_CHUNK_LOADER, registry.getFirst().type());
      assertEquals(ChunkLoaderRegistryStatus.ACTIVE, registry.getFirst().status());

      database.saveChunkLoader(second).get(5, TimeUnit.SECONDS);
      assertEquals(List.of(second), database.listChunkLoaders().get(5, TimeUnit.SECONDS));

      database
          .deleteChunkLoaderAt(worldId, second.x(), second.y(), second.z())
          .get(5, TimeUnit.SECONDS);
      assertTrue(database.listChunkLoaders().get(5, TimeUnit.SECONDS).isEmpty());
    } finally {
      database.close();
    }
  }

  @Test
  void chunkLoaderRegistryTracksLostFoundAndRemovedTransitions() throws Exception {
    File file = tempDir.resolve("chunk-loader-registry.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID worldId = new UUID(0L, 11L);
      UUID loaderId = new UUID(0L, 12L);
      UUID actorId = new UUID(0L, 13L);
      ChunkLoaderRecord active =
          new ChunkLoaderRecord(
              loaderId,
              ChunkLoaderType.PERSONAL_CHUNK_LOADER,
              worldId,
              "minecraft:overworld",
              "world",
              1,
              64,
              2,
              0,
              0,
              actorId,
              "Alex",
              1,
              true,
              100L,
              100L);

      assertFalse(database.saveChunkLoader(active).get(5, TimeUnit.SECONDS));
      assertFalse(
          database
              .recordChunkLoaderObservation(
                  observation(
                      loaderId,
                      ChunkLoaderRegistryStatus.LOST,
                      worldId,
                      actorId,
                      "creative_inventory",
                      "creative_inventory",
                      110L))
              .get(5, TimeUnit.SECONDS));

      assertTrue(
          database
              .recordChunkLoaderObservation(
                  observation(
                      loaderId,
                      ChunkLoaderRegistryStatus.ITEM,
                      worldId,
                      actorId,
                      "pickup",
                      null,
                      120L))
              .get(5, TimeUnit.SECONDS));
      assertFalse(
          database
              .recordChunkLoaderObservation(
                  observation(
                      loaderId,
                      ChunkLoaderRegistryStatus.ITEM,
                      worldId,
                      actorId,
                      "drop",
                      null,
                      130L))
              .get(5, TimeUnit.SECONDS));

      var item = database.listChunkLoaderRegistry().get(5, TimeUnit.SECONDS).getFirst();
      assertEquals(ChunkLoaderType.PERSONAL_CHUNK_LOADER, item.type());
      assertEquals(ChunkLoaderRegistryStatus.ITEM, item.status());
      assertEquals("drop", item.lastSource());
      assertEquals(130L, item.lastSeenAt());

      assertFalse(
          database
              .recordChunkLoaderObservation(
                  observation(
                      loaderId,
                      ChunkLoaderRegistryStatus.REMOVED,
                      worldId,
                      actorId,
                      "/clear",
                      "/clear",
                      140L))
              .get(5, TimeUnit.SECONDS));
      assertEquals(
          ChunkLoaderRegistryStatus.REMOVED,
          database.listChunkLoaderRegistry().get(5, TimeUnit.SECONDS).getFirst().status());
    } finally {
      database.close();
    }
  }

  @Test
  void chunkLoaderRowsWithRemovedVariantIdsAreRejected() throws Exception {
    File file = tempDir.resolve("chunk-loader-removed-variant.db").toFile();
    UUID worldId = new UUID(0L, 21L);
    UUID loaderId = new UUID(0L, 22L);

    Database database = new Database();
    database.init(file);
    try {
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
          var statement = connection.createStatement()) {
        statement.execute(
            "INSERT INTO chunk_loaders(id, loader_type, world, world_key, world_name, x, y, z,"
                + " chunk_x, chunk_z, placed_by_uuid, placed_by_name, radius, created_at,"
                + " updated_at) VALUES('"
                + loaderId
                + "', 'personal', '"
                + worldId
                + "', 'minecraft:overworld', 'world', 5, 70, 6, 0, 0, NULL, 'unknown',"
                + " 1, 100, 105)");
      }

      assertTrue(database.listChunkLoaders().get(5, TimeUnit.SECONDS).isEmpty());
    } finally {
      database.close();
    }
  }

  @Test
  void rejectsLegacyChunkLoaderSchemaInsteadOfMutatingIt() throws Exception {
    File file = tempDir.resolve("legacy-chunk-loader.db").toFile();
    UUID worldId = new UUID(0L, 31L);
    UUID loaderId = new UUID(0L, 32L);

    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        var statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE chunk_loaders (
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
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              UNIQUE(world, x, y, z)
          )
          """);
      statement.execute(
          "INSERT INTO chunk_loaders(id, loader_type, world, world_key, world_name, x, y, z,"
              + " chunk_x, chunk_z, placed_by_uuid, placed_by_name, radius, created_at,"
              + " updated_at) VALUES('"
              + loaderId
              + "', 'chunk_loader', '"
              + worldId
              + "', 'minecraft:overworld', 'world', 5, 70, 6, 0, 0, NULL, 'unknown',"
              + " 1, 100, 105)");
    }

    Database database = new Database();
    SQLException error = assertThrows(SQLException.class, () -> database.init(file));
    assertTrue(error.getMessage().contains("chunk_loaders"));
    assertTrue(error.getMessage().contains("enabled"));
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        var statement = connection.createStatement();
        var columns = statement.executeQuery("PRAGMA table_info(chunk_loaders)")) {
      while (columns.next()) {
        assertFalse("enabled".equalsIgnoreCase(columns.getString("name")));
      }
    }
    database.close();
  }

  @Test
  void setStorageTierPersistsCapacitySnapshot() throws Exception {
    File file = tempDir.resolve("tier-state.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      database.setStorageTier("storage", "OBSIDIAN", 20L * 45L * 64L).get(5, TimeUnit.SECONDS);

      Database.StorageTierState state =
          database.getStorageTierState("storage").get(5, TimeUnit.SECONDS).orElseThrow();

      assertEquals("OBSIDIAN", state.tier());
      assertEquals(20L * 45L * 64L, state.tierMaxItems());
    } finally {
      database.close();
    }
  }

  @Test
  void physicalStorageClaimAndTierMetadataCommitAtomically() throws Exception {
    File file = tempDir.resolve("storage-claims.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID worldId = new UUID(0L, 91L);
      StorageClaim claim =
          new StorageClaim(
              "storage-a", worldId, "minecraft:overworld", "world", 10, 64, -2, 100, 100);

      database
          .insertStorageClaim(claim, "OBSIDIAN", 57_600L, "Main Vault")
          .get(5, TimeUnit.SECONDS);

      assertEquals(List.of(claim), database.loadStorageClaims().get(5, TimeUnit.SECONDS));
      assertEquals(
          new Database.StorageTierState("OBSIDIAN", 57_600L),
          database.getStorageTierState("storage-a").get(5, TimeUnit.SECONDS).orElseThrow());
      assertEquals(
          "Main Vault",
          database.getStorageDisplayName("storage-a").get(5, TimeUnit.SECONDS).orElseThrow());
    } finally {
      database.close();
    }
  }

  @Test
  void physicalStorageClaimsEnforceUniqueIdentityAndPositionWithoutPartialMetadata()
      throws Exception {
    File file = tempDir.resolve("storage-claim-conflict.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID worldId = new UUID(0L, 92L);
      StorageClaim first =
          new StorageClaim(
              "storage-a", worldId, "minecraft:overworld", "world", 1, 64, 1, 100, 100);
      StorageClaim samePosition =
          new StorageClaim(
              "storage-b", worldId, "minecraft:overworld", "world", 1, 64, 1, 101, 101);
      database.insertStorageClaim(first, "BASIC", 100, null).get(5, TimeUnit.SECONDS);

      ExecutionException conflict =
          assertThrows(
              ExecutionException.class,
              () ->
                  database
                      .insertStorageClaim(samePosition, "DIAMOND", 200, null)
                      .get(5, TimeUnit.SECONDS));
      StorageClaimConflictException typed =
          assertInstanceOf(StorageClaimConflictException.class, conflict.getCause());
      assertEquals(StorageClaimConflictException.Kind.POSITION, typed.kind());

      assertFalse(database.storageExists("storage-b").get(5, TimeUnit.SECONDS));
      assertEquals(List.of(first), database.loadStorageClaims().get(5, TimeUnit.SECONDS));
    } finally {
      database.close();
    }
  }

  @Test
  void physicalStorageClaimReleaseRequiresExactLocation() throws Exception {
    File file = tempDir.resolve("storage-claim-release.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID worldId = new UUID(0L, 93L);
      StorageClaim claim =
          new StorageClaim(
              "storage-a", worldId, "minecraft:overworld", "world", 5, 70, 6, 100, 100);
      database.insertStorageClaim(claim, "BASIC", 100, null).get(5, TimeUnit.SECONDS);

      assertFalse(
          database
              .deleteStorageClaimExact(
                  "storage-a",
                  new StorageClaimLocation(worldId, "minecraft:overworld", "world", 6, 70, 6))
              .get(5, TimeUnit.SECONDS));
      assertEquals(1, database.loadStorageClaims().get(5, TimeUnit.SECONDS).size());
      assertTrue(
          database.deleteStorageClaimExact("storage-a", claim.location()).get(5, TimeUnit.SECONDS));
      assertTrue(database.loadStorageClaims().get(5, TimeUnit.SECONDS).isEmpty());
    } finally {
      database.close();
    }
  }

  @Test
  void physicalStorageClaimMoveRequiresExactSourceAndKeepsIdentity() throws Exception {
    File file = tempDir.resolve("storage-claim-move.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID worldId = new UUID(0L, 94L);
      StorageClaim source =
          new StorageClaim(
              "storage-a", worldId, "minecraft:overworld", "world", 5, 70, 6, 100, 100);
      StorageClaim destination =
          new StorageClaim(
              "storage-a", worldId, "minecraft:overworld", "world", 9, 72, 10, 100, 200);
      database.insertStorageClaim(source, "BASIC", 100, null).get(5, TimeUnit.SECONDS);

      assertTrue(database.moveStorageClaimExact(source, destination).get(5, TimeUnit.SECONDS));
      assertEquals(List.of(destination), database.loadStorageClaims().get(5, TimeUnit.SECONDS));
      assertFalse(database.moveStorageClaimExact(source, destination).get(5, TimeUnit.SECONDS));
    } finally {
      database.close();
    }
  }

  @Test
  void cloneStoragePersistsOverriddenCapacitySnapshot() throws Exception {
    File file = tempDir.resolve("tier-clone.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      database.setStorageTier("source", "OBSIDIAN", 20L * 45L * 64L).get(5, TimeUnit.SECONDS);

      database.cloneStorage("source", "copy", "DIAMOND", 10L * 45L * 64L).get(5, TimeUnit.SECONDS);
      Database.StorageTierState state =
          database.getStorageTierState("copy").get(5, TimeUnit.SECONDS).orElseThrow();

      assertEquals("DIAMOND", state.tier());
      assertEquals(10L * 45L * 64L, state.tierMaxItems());
    } finally {
      database.close();
    }
  }

  @Test
  void rejectsLegacyStorageSchemaInsteadOfMutatingIt() throws Exception {
    File file = tempDir.resolve("legacy-storage.db").toFile();
    long now = Instant.now().getEpochSecond();
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        var statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE storages (
              id TEXT PRIMARY KEY,
              tier TEXT NULL,
              tier_max_items INTEGER NULL,
              sort_mode TEXT NULL,
              created_at INTEGER NOT NULL,
              updated_at INTEGER NOT NULL
          )
          """);
      statement.execute(
          "INSERT INTO storages(id, tier, tier_max_items, sort_mode, created_at, updated_at)"
              + " VALUES('storage', 'BASIC', 100, 'AMOUNT', "
              + now
              + ", "
              + now
              + ")");
    }

    Database database = new Database();
    SQLException error = assertThrows(SQLException.class, () -> database.init(file));
    assertTrue(error.getMessage().contains("storages"));
    assertTrue(error.getMessage().contains("display_name"));
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        var statement = connection.createStatement();
        var columns = statement.executeQuery("PRAGMA table_info(storages)")) {
      while (columns.next()) {
        assertFalse("display_name".equalsIgnoreCase(columns.getString("name")));
      }
    }
    database.close();
  }

  @Test
  void cloneStoragePreservesDisplayName() throws Exception {
    File file = tempDir.resolve("display-name-clone.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      database
          .setStorageMetadata("source", "OBSIDIAN", 20L * 45L * 64L, "Main Vault")
          .get(5, TimeUnit.SECONDS);

      database.cloneStorage("source", "copy", "DIAMOND", 10L * 45L * 64L).get(5, TimeUnit.SECONDS);

      assertEquals(
          "Main Vault",
          database.getStorageDisplayName("copy").get(5, TimeUnit.SECONDS).orElseThrow());
    } finally {
      database.close();
    }
  }

  @Test
  void loadStorageQuarantinesInvalidRowsWithoutDeletingOriginals() throws Exception {
    File file = tempDir.resolve("exort.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      String storageId = "storage";
      byte[] nonEmptyBlob = new byte[] {42};
      byte[] oversizedBlob = new byte[1_048_577];
      long now = Instant.now().getEpochSecond();

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        try (var storage =
            connection.prepareStatement(
                "INSERT INTO storages(id, tier, sort_mode, created_at, updated_at)"
                    + " VALUES(?, ?, ?, ?, ?)")) {
          storage.setString(1, storageId);
          storage.setString(2, "BASIC");
          storage.setString(3, "AMOUNT");
          storage.setLong(4, now);
          storage.setLong(5, now);
          storage.executeUpdate();
        }
        insertItem(connection, storageId, "valid", nonEmptyBlob, 3);
        insertItem(connection, storageId, "negative", nonEmptyBlob, -2);
        insertItem(connection, storageId, "empty", new byte[0], 1);
        insertItem(connection, storageId, "oversized", oversizedBlob, 1);
      }

      var loaded = database.loadStorage(storageId).get(5, TimeUnit.SECONDS);

      assertEquals(1, loaded.size());
      assertTrue(loaded.containsKey("valid"));
      assertArrayEquals(nonEmptyBlob, loaded.get("valid").blob());
      assertEquals(3, loaded.get("valid").amount());

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        try (var rows =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM storage_items WHERE storage_id = ?")) {
          rows.setString(1, storageId);
          try (var result = rows.executeQuery()) {
            assertTrue(result.next());
            assertEquals(4, result.getInt(1));
          }
        }
        try (var quarantine =
            connection.prepareStatement(
                "SELECT item_key, item_blob, amount, reason, quarantined_at"
                    + " FROM storage_item_quarantine WHERE storage_id = ? ORDER BY item_key")) {
          quarantine.setString(1, storageId);
          try (var result = quarantine.executeQuery()) {
            assertTrue(result.next());
            assertEquals("empty", result.getString("item_key"));
            assertArrayEquals(new byte[0], result.getBytes("item_blob"));
            assertEquals(1L, result.getLong("amount"));
            assertTrue(result.getString("reason").contains("blob length"));
            assertTrue(result.getLong("quarantined_at") > 0L);

            assertTrue(result.next());
            assertEquals("negative", result.getString("item_key"));
            assertArrayEquals(nonEmptyBlob, result.getBytes("item_blob"));
            assertEquals(-2L, result.getLong("amount"));
            assertTrue(result.getString("reason").contains("positive"));
            assertTrue(result.getLong("quarantined_at") > 0L);

            assertTrue(result.next());
            assertEquals("oversized", result.getString("item_key"));
            assertEquals(oversizedBlob.length, result.getBytes("item_blob").length);
            assertEquals(1L, result.getLong("amount"));
            assertTrue(result.getString("reason").contains("blob length"));
            assertTrue(result.getLong("quarantined_at") > 0L);
            assertFalse(result.next());
          }
        }
      }
    } finally {
      database.close();
    }
  }

  @Test
  void writeSnapshotRejectsInvalidRowsBeforeDeletingExistingRows() throws Exception {
    File file = tempDir.resolve("snapshot.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      String storageId = "storage";
      long now = Instant.now().getEpochSecond();
      byte[] oldBlob = new byte[] {7};
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        insertStorage(connection, storageId, now);
        insertItem(connection, storageId, "old", oldBlob, 9);
      }

      byte[] nonEmptyBlob = new byte[] {42};
      byte[] oversizedBlob = new byte[1_048_577];
      var items = new ArrayList<DbItem>();
      items.add(new DbItem("valid", nonEmptyBlob, 5));
      items.add(new DbItem("negative", nonEmptyBlob, -1));
      items.add(new DbItem("empty", new byte[0], 1));
      items.add(new DbItem("oversized", oversizedBlob, 1));
      items.add(null);

      ExecutionException error =
          assertThrows(
              ExecutionException.class,
              () -> database.writeSnapshot(storageId, items).get(5, TimeUnit.SECONDS));
      assertInstanceOf(IllegalArgumentException.class, error.getCause());

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
          var query =
              connection.prepareStatement(
                  "SELECT item_key, item_blob, amount FROM storage_items WHERE storage_id = ?")) {
        query.setString(1, storageId);
        try (var rs = query.executeQuery()) {
          assertTrue(rs.next());
          assertEquals("old", rs.getString("item_key"));
          assertArrayEquals(oldBlob, rs.getBytes("item_blob"));
          assertEquals(9, rs.getLong("amount"));
          assertFalse(rs.next());
        }
      }
    } finally {
      database.close();
    }
  }

  @Test
  void writeDeltaRejectsInvalidUpsertsBeforeApplyingRemovals() throws Exception {
    File file = tempDir.resolve("delta.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      String storageId = "storage";
      long now = Instant.now().getEpochSecond();
      byte[] oldBlob = new byte[] {1};
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        insertStorage(connection, storageId, now);
        insertItem(connection, storageId, "old", oldBlob, 9);
      }

      byte[] nonEmptyBlob = new byte[] {42};
      byte[] oversizedBlob = new byte[1_048_577];
      var upserts = new ArrayList<DbItem>();
      upserts.add(new DbItem("valid", nonEmptyBlob, 5));
      upserts.add(new DbItem("negative", nonEmptyBlob, -1));
      upserts.add(new DbItem("empty", new byte[0], 1));
      upserts.add(new DbItem("oversized", oversizedBlob, 1));
      upserts.add(null);

      ExecutionException error =
          assertThrows(
              ExecutionException.class,
              () ->
                  database
                      .writeDelta(storageId, upserts, java.util.List.of("old"))
                      .get(5, TimeUnit.SECONDS));
      assertInstanceOf(IllegalArgumentException.class, error.getCause());

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
          var query =
              connection.prepareStatement(
                  "SELECT item_key, item_blob, amount FROM storage_items WHERE storage_id = ?")) {
        query.setString(1, storageId);
        try (var rs = query.executeQuery()) {
          assertTrue(rs.next());
          assertEquals("old", rs.getString("item_key"));
          assertArrayEquals(oldBlob, rs.getBytes("item_blob"));
          assertEquals(9, rs.getLong("amount"));
          assertFalse(rs.next());
        }
      }
    } finally {
      database.close();
    }
  }

  @Test
  void createStorageWithItemsRejectsInvalidRowsWithoutPartialStorage() throws Exception {
    File file = tempDir.resolve("create.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      String storageId = "storage";
      byte[] nonEmptyBlob = new byte[] {42};
      byte[] oversizedBlob = new byte[1_048_577];
      var items = new ArrayList<DbItem>();
      items.add(new DbItem("valid", nonEmptyBlob, 5));
      items.add(new DbItem("negative", nonEmptyBlob, -1));
      items.add(new DbItem("empty", new byte[0], 1));
      items.add(new DbItem("oversized", oversizedBlob, 1));
      items.add(null);

      ExecutionException error =
          assertThrows(
              ExecutionException.class,
              () ->
                  database
                      .createStorageWithItems(storageId, "BASIC", "AMOUNT", items)
                      .get(5, TimeUnit.SECONDS));
      assertInstanceOf(IllegalArgumentException.class, error.getCause());

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
          var storageQuery = connection.prepareStatement("SELECT COUNT(*) FROM storages");
          var itemQuery = connection.prepareStatement("SELECT COUNT(*) FROM storage_items")) {
        try (var rs = storageQuery.executeQuery()) {
          assertTrue(rs.next());
          assertEquals(0, rs.getInt(1));
        }
        try (var rs = itemQuery.executeQuery()) {
          assertTrue(rs.next());
          assertEquals(0, rs.getInt(1));
        }
      }
    } finally {
      database.close();
    }
  }

  @Test
  void writeDeltaBatchRejectsInvalidUpsertsBeforeApplyingAnyWrite() throws Exception {
    File file = tempDir.resolve("delta-batch.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      long now = Instant.now().getEpochSecond();
      byte[] oldBlob = new byte[] {1};
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath())) {
        insertStorage(connection, "storage-a", now);
        insertStorage(connection, "storage-b", now);
        insertItem(connection, "storage-a", "old-a", oldBlob, 3);
        insertItem(connection, "storage-b", "old-b", oldBlob, 4);
      }

      ExecutionException error =
          assertThrows(
              ExecutionException.class,
              () ->
                  database
                      .writeDeltaBatch(
                          java.util.List.of(
                              new Database.DeltaWrite(
                                  "storage-a",
                                  java.util.List.of(new DbItem("valid", new byte[] {2}, 5)),
                                  java.util.List.of("old-a")),
                              new Database.DeltaWrite(
                                  "storage-b",
                                  java.util.List.of(new DbItem("invalid", new byte[0], 1)),
                                  java.util.List.of("old-b"))))
                      .get(5, TimeUnit.SECONDS));
      assertInstanceOf(IllegalArgumentException.class, error.getCause());

      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
          var query =
              connection.prepareStatement(
                  "SELECT storage_id, item_key, amount FROM storage_items ORDER BY storage_id")) {
        try (var rs = query.executeQuery()) {
          assertTrue(rs.next());
          assertEquals("storage-a", rs.getString("storage_id"));
          assertEquals("old-a", rs.getString("item_key"));
          assertEquals(3, rs.getLong("amount"));
          assertTrue(rs.next());
          assertEquals("storage-b", rs.getString("storage_id"));
          assertEquals("old-b", rs.getString("item_key"));
          assertEquals(4, rs.getLong("amount"));
          assertFalse(rs.next());
        }
      }
    } finally {
      database.close();
    }
  }

  @Test
  void persistsClientCullingState() throws Exception {
    File file = tempDir.resolve("client-culling.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");

      database.setClientCullingManualBypass(playerId, true).get(5, TimeUnit.SECONDS);
      database
          .recordClientCullingProbeResult(playerId, "MATCH", "fabric", true)
          .get(5, TimeUnit.SECONDS);
      database.updateClientCullingLastSeen(playerId).get(5, TimeUnit.SECONDS);

      Map<UUID, Database.ClientCullingState> states =
          database.loadClientCullingStates().get(5, TimeUnit.SECONDS);
      Database.ClientCullingState state = states.get(playerId);

      assertTrue(state.manualBypass());
      assertEquals("fabric", state.lastMatchBrand());
      assertEquals("MATCH", state.lastProbeState());
      assertTrue(
          state.hasFreshMatch(
              "fabric", Instant.now().getEpochSecond(), TimeUnit.HOURS.toSeconds(12)));
    } finally {
      database.close();
    }
  }

  @Test
  void clientCullingNoMatchClearsLastMatchedBrand() throws Exception {
    File file = tempDir.resolve("client-culling-clear.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000124");

      database
          .recordClientCullingProbeResult(playerId, "MATCH", "fabric", true)
          .get(5, TimeUnit.SECONDS);
      database
          .recordClientCullingProbeResult(playerId, "NO_MATCH", "fabric", false)
          .get(5, TimeUnit.SECONDS);

      Database.ClientCullingState state =
          database.loadClientCullingStates().get(5, TimeUnit.SECONDS).get(playerId);

      assertNull(state.lastMatchBrand());
      assertEquals("NO_MATCH", state.lastProbeState());
      assertFalse(
          state.hasFreshMatch(
              "fabric", Instant.now().getEpochSecond(), TimeUnit.HOURS.toSeconds(12)));
    } finally {
      database.close();
    }
  }

  @Test
  void queueDepthReturnsToZeroAfterSuccessfulAsyncTask() throws Exception {
    File file = tempDir.resolve("queue-depth.db").toFile();
    Database database = new Database();
    database.init(file);
    try {
      database.ensureStorage("storage").get(5, TimeUnit.SECONDS);

      assertEquals(0, database.queuedOperations());
    } finally {
      database.close();
    }
  }

  @Test
  void asyncTaskAfterCloseReturnsFailedFutureAndLogsRejection() throws Exception {
    File file = tempDir.resolve("closed.db").toFile();
    CapturingHandler handler = new CapturingHandler();
    Logger logger = Logger.getLogger("exort-db-test-" + UUID.randomUUID());
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
    try {
      Database database = new Database(logger, null);
      database.init(file);
      database.close();

      var future = database.ensureStorage("after-close");

      assertEquals(0, database.queuedOperations());
      ExecutionException error =
          assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertInstanceOf(RejectedExecutionException.class, error.getCause());
      assertTrue(handler.contains("Rejected async database task: ensure storage after-close"));
    } finally {
      logger.removeHandler(handler);
    }
  }

  private void insertItem(
      java.sql.Connection connection, String storageId, String key, byte[] blob, long amount)
      throws Exception {
    try (var item =
        connection.prepareStatement(
            "INSERT INTO storage_items(storage_id, item_key, item_blob, amount)"
                + " VALUES(?, ?, ?, ?)")) {
      item.setString(1, storageId);
      item.setString(2, key);
      item.setBytes(3, blob);
      item.setLong(4, amount);
      item.executeUpdate();
    }
  }

  private void insertStorage(java.sql.Connection connection, String storageId, long now)
      throws Exception {
    try (var storage =
        connection.prepareStatement(
            "INSERT INTO storages(id, tier, sort_mode, created_at, updated_at)"
                + " VALUES(?, ?, ?, ?, ?)")) {
      storage.setString(1, storageId);
      storage.setString(2, "BASIC");
      storage.setString(3, "AMOUNT");
      storage.setLong(4, now);
      storage.setLong(5, now);
      storage.executeUpdate();
    }
  }

  private static ChunkLoaderObservation observation(
      UUID loaderId,
      ChunkLoaderRegistryStatus status,
      UUID worldId,
      UUID actorId,
      String source,
      String reason,
      long observedAt) {
    return new ChunkLoaderObservation(
        loaderId,
        status,
        worldId,
        "minecraft:overworld",
        "world",
        10.5D,
        65.0D,
        -2.5D,
        actorId,
        "Alex",
        source,
        reason,
        observedAt);
  }

  private static final class CapturingHandler extends Handler {
    private final ArrayList<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}

    private boolean contains(String message) {
      return records.stream().anyMatch(record -> record.getMessage().contains(message));
    }
  }
}
