package com.zxcmc.exort.infra.db;

import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.chunkloader.ChunkLoaderObservation;
import com.zxcmc.exort.chunkloader.ChunkLoaderRecord;
import com.zxcmc.exort.chunkloader.ChunkLoaderRegistryRecord;
import com.zxcmc.exort.storage.StorageClaim;
import com.zxcmc.exort.storage.StorageClaimLocation;
import com.zxcmc.exort.storage.StorageClaimStore;
import com.zxcmc.exort.storage.StorageLoadResult;
import com.zxcmc.exort.storage.StorageQuarantineEntry;
import com.zxcmc.exort.storage.sort.SortMode;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class Database implements AutoCloseable, StorageClaimStore {
  private final Supplier<String> defaultSortModeName;
  private final SqliteDatabase sqlite;
  private final BusSettingsRepository busSettingsRepository;
  private final PlayerStateRepository playerStateRepository;
  private final ChunkLoaderRepository chunkLoaderRepository;
  private final StorageRepository storageRepository;

  public record DeltaWrite(
      String storageId, Collection<DbItem> upserts, Collection<String> removals) {
    public DeltaWrite {
      upserts = StorageRepository.snapshotItems(upserts);
      removals = StorageRepository.snapshotStrings(removals);
    }
  }

  public record StorageTierState(String tier, Long tierMaxItems) {}

  public Database() {
    this(null, () -> SortMode.AMOUNT.name());
  }

  public Database(Logger logger, Supplier<String> defaultSortModeName) {
    this.defaultSortModeName =
        defaultSortModeName == null ? () -> SortMode.AMOUNT.name() : defaultSortModeName;
    this.sqlite = new SqliteDatabase(logger);
    this.busSettingsRepository = new BusSettingsRepository(sqlite, logger);
    this.playerStateRepository = new PlayerStateRepository(sqlite, logger);
    this.chunkLoaderRepository = new ChunkLoaderRepository(sqlite, logger);
    this.storageRepository = new StorageRepository(sqlite, logger, this.defaultSortModeName);
  }

  public void init(File file) throws SQLException {
    sqlite.init(file, this::initializeSchema);
  }

  private Connection connection() {
    return sqlite.connection();
  }

  private void createTables() throws SQLException {
    try (Statement stmt = connection().createStatement()) {
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
    try (PreparedStatement ps = connection().prepareStatement("PRAGMA table_info(" + table + ")")) {
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
    return storageRepository.ensureStorage(id);
  }

  public CompletableFuture<Void> setStorageTier(String storageId, String tierKey) {
    return storageRepository.setStorageTier(storageId, tierKey);
  }

  public CompletableFuture<Void> setStorageTier(
      String storageId, String tierKey, Long tierMaxItems) {
    return storageRepository.setStorageTier(storageId, tierKey, tierMaxItems);
  }

  public CompletableFuture<Void> setStorageMetadata(
      String storageId, String tierKey, Long tierMaxItems, String displayName) {
    return storageRepository.setStorageMetadata(storageId, tierKey, tierMaxItems, displayName);
  }

  @Override
  public CompletableFuture<List<StorageClaim>> loadStorageClaims() {
    return storageRepository.loadStorageClaims();
  }

  @Override
  public CompletableFuture<Void> insertStorageClaim(
      StorageClaim claim, String tierKey, long tierMaxItems, String displayName) {
    return storageRepository.insertStorageClaim(claim, tierKey, tierMaxItems, displayName);
  }

  @Override
  public CompletableFuture<Boolean> deleteStorageClaimExact(
      String storageId, StorageClaimLocation location) {
    return storageRepository.deleteStorageClaimExact(storageId, location);
  }

  @Override
  public CompletableFuture<Boolean> moveStorageClaimExact(
      StorageClaim source, StorageClaim destination) {
    return storageRepository.moveStorageClaimExact(source, destination);
  }

  public CompletableFuture<Boolean> saveChunkLoader(ChunkLoaderRecord record) {
    return chunkLoaderRepository.saveChunkLoader(record);
  }

  public CompletableFuture<Boolean> recordChunkLoaderObservation(
      ChunkLoaderObservation observation) {
    return chunkLoaderRepository.recordChunkLoaderObservation(observation);
  }

  public CompletableFuture<Boolean> recordChunkLoaderObservationBestEffort(
      ChunkLoaderObservation observation) {
    return chunkLoaderRepository.recordChunkLoaderObservationBestEffort(observation);
  }

  public CompletableFuture<List<ChunkLoaderRecord>> listChunkLoaders() {
    return chunkLoaderRepository.listChunkLoaders();
  }

  public CompletableFuture<List<ChunkLoaderRegistryRecord>> listChunkLoaderRegistry() {
    return chunkLoaderRepository.listChunkLoaderRegistry();
  }

  public CompletableFuture<Void> deleteChunkLoader(UUID id) {
    return chunkLoaderRepository.deleteChunkLoader(id);
  }

  public CompletableFuture<Void> deleteChunkLoaderAt(UUID worldId, int x, int y, int z) {
    return chunkLoaderRepository.deleteChunkLoaderAt(worldId, x, y, z);
  }

  public int queuedOperations() {
    return sqlite.queuedOperations();
  }

  public CompletableFuture<Optional<String>> getStorageSortMode(String storageId) {
    return storageRepository.getStorageSortMode(storageId);
  }

  public CompletableFuture<Void> setStorageSortMode(String storageId, String sortMode) {
    return storageRepository.setStorageSortMode(storageId, sortMode);
  }

  public CompletableFuture<Void> createStorageWithItems(
      String storageId, String tierKey, String sortMode, Collection<DbItem> items) {
    return storageRepository.createStorageWithItems(storageId, tierKey, sortMode, items);
  }

  public CompletableFuture<Void> createStorageWithItems(
      String storageId,
      String tierKey,
      Long tierMaxItems,
      String sortMode,
      Collection<DbItem> items) {
    return storageRepository.createStorageWithItems(
        storageId, tierKey, tierMaxItems, sortMode, items);
  }

  public CompletableFuture<Void> createStorageWithItems(
      String storageId,
      String tierKey,
      Long tierMaxItems,
      String sortMode,
      String displayName,
      Collection<DbItem> items) {
    return storageRepository.createStorageWithItems(
        storageId, tierKey, tierMaxItems, sortMode, displayName, items);
  }

  public CompletableFuture<Void> deleteStorageForInternalCleanup(String storageId) {
    return storageRepository.deleteStorageForInternalCleanup(storageId);
  }

  public CompletableFuture<Void> cloneStorage(String fromId, String toId, String tierKey) {
    return storageRepository.cloneStorage(fromId, toId, tierKey);
  }

  public CompletableFuture<Void> cloneStorage(
      String fromId, String toId, String tierKey, Long tierMaxItems) {
    return storageRepository.cloneStorage(fromId, toId, tierKey, tierMaxItems);
  }

  public CompletableFuture<Optional<String>> getStorageTier(String storageId) {
    return storageRepository.getStorageTier(storageId);
  }

  public CompletableFuture<Optional<StorageTierState>> getStorageTierState(String storageId) {
    return storageRepository.getStorageTierState(storageId);
  }

  public CompletableFuture<Optional<String>> getStorageDisplayName(String storageId) {
    return storageRepository.getStorageDisplayName(storageId);
  }

  public CompletableFuture<Void> setStorageDisplayName(String storageId, String displayName) {
    return storageRepository.setStorageDisplayName(storageId, displayName);
  }

  public CompletableFuture<Boolean> storageExists(String storageId) {
    return storageRepository.storageExists(storageId);
  }

  public CompletableFuture<Void> updatePlayerLastStorage(
      UUID playerId, String storageId, String world, int x, int y, int z) {
    return playerStateRepository.updateLastStorage(playerId, storageId, world, x, y, z);
  }

  public CompletableFuture<Void> updatePlayerLastStorageLocation(
      String storageId, String world, int x, int y, int z) {
    return playerStateRepository.updateLastStorageLocation(storageId, world, x, y, z);
  }

  public CompletableFuture<Optional<PlayerLastStorage>> getPlayerLastStorage(UUID playerId) {
    return playerStateRepository.getLastStorage(playerId);
  }

  public record PlayerLastStorage(
      String storageId, String tier, String world, int x, int y, int z, long updatedAt) {}

  public CompletableFuture<Map<UUID, ClientCullingState>> loadClientCullingStates() {
    return playerStateRepository.loadCullingStates();
  }

  public CompletableFuture<Void> setClientCullingManualBypass(UUID playerId, boolean enabled) {
    return playerStateRepository.setManualBypass(playerId, enabled);
  }

  public CompletableFuture<Void> recordClientCullingProbeResult(
      UUID playerId, String probeState, String brand, boolean match) {
    return playerStateRepository.recordProbeResult(playerId, probeState, brand, match);
  }

  public CompletableFuture<Void> updateClientCullingLastSeen(UUID playerId) {
    return playerStateRepository.updateLastSeen(playerId);
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
    return busSettingsRepository.load(pos, slots);
  }

  public CompletableFuture<Void> saveBusSettings(BusSettings settings, int slots) {
    return busSettingsRepository.save(settings, slots);
  }

  public CompletableFuture<Void> deleteBusSettings(BusPos pos) {
    return busSettingsRepository.delete(pos);
  }

  public CompletableFuture<Map<String, DbItem>> loadStorage(String storageId) {
    return storageRepository.loadStorage(storageId);
  }

  public CompletableFuture<StorageLoadResult> loadStorageWithHealth(String storageId) {
    return storageRepository.loadStorageWithHealth(storageId);
  }

  public CompletableFuture<Void> quarantineStorageItems(
      String storageId, Collection<StorageQuarantineEntry> entries) {
    return storageRepository.quarantineStorageItems(storageId, entries);
  }

  public CompletableFuture<Void> writeSnapshot(String storageId, Collection<DbItem> items) {
    return storageRepository.writeSnapshot(storageId, items);
  }

  public CompletableFuture<Void> writeDelta(
      String storageId, Collection<DbItem> upserts, Collection<String> removals) {
    return storageRepository.writeDelta(storageId, upserts, removals);
  }

  public CompletableFuture<Void> writeDeltaBatch(Collection<DeltaWrite> writes) {
    return storageRepository.writeDeltaBatch(writes);
  }

  @Override
  public void close() {
    chunkLoaderRepository.close();
    sqlite.close();
  }
}
