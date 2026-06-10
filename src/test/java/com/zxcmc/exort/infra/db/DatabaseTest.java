package com.zxcmc.exort.infra.db;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
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
  void loadStorageSkipsClearlyInvalidRowsBeforeCacheDecode() throws Exception {
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
