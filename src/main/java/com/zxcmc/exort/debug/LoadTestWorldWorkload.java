package com.zxcmc.exort.debug;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class LoadTestWorldWorkload {
  private static final String BENCHMARK_SECTION = "benchmark";
  private static final String FIELD_RUN = "run";
  private static final String STORAGE_ID_PREFIX = "exort-benchmark:";
  private static final int INTERACTIONS_BEFORE_MOVE = 8;
  private static final int MOVES_BEFORE_EMPTY_TEARDOWN = 3;
  private static final int MAX_WORLD_ACTIONS_PER_TICK = 64;
  private static final int MIN_WORLD_ACTIONS_PER_TICK = 4;

  private final JavaPlugin plugin;
  private final Database database;
  private final LoadTestRuntimeDependencies dependencies;
  private final World world;
  private final String runId;
  private final List<LaneState> lanes;
  private final Set<BlockKey> trackedBlocks = new LinkedHashSet<>();
  private final Set<String> storageIds = new LinkedHashSet<>();
  private final Set<BusPos> busPositions = new LinkedHashSet<>();
  private final ItemKeyUtil.SampleData benchmarkItem;
  private final StorageTier tier;
  private int laneCursor;
  private int lastOperations;
  private long totalPlacements;

  private LoadTestWorldWorkload(
      JavaPlugin plugin,
      Database database,
      LoadTestRuntimeDependencies dependencies,
      World world,
      String runId,
      List<LaneState> lanes,
      StorageTier tier) {
    this.plugin = plugin;
    this.database = database;
    this.dependencies = dependencies;
    this.world = world;
    this.runId = runId;
    this.lanes = lanes;
    this.tier = tier;
    this.benchmarkItem = ItemKeyUtil.sampleData(new ItemStack(Material.COBBLESTONE));
  }

  static Optional<LoadTestWorldWorkload> create(
      JavaPlugin plugin,
      Database database,
      LoadTestRuntimeDependencies dependencies,
      World world,
      List<LoadedChunk> chunks,
      int simulatedPlayers) {
    if (dependencies == null || world == null || chunks == null || chunks.isEmpty()) {
      return Optional.empty();
    }
    Optional<StorageTier> tier = StorageTier.allTiers().stream().findFirst();
    if (tier.isEmpty()) {
      plugin.getLogger().warning("Benchmark world workload disabled: no storage tiers loaded.");
      return Optional.empty();
    }
    String runId = UUID.randomUUID().toString();
    int lanesCount =
        Math.max(
            0,
            Math.min(
                Math.max(1, simulatedPlayers),
                chunks.size() * LoadTestWorldPlanner.SLOTS_PER_CHUNK));
    if (lanesCount <= 0) {
      return Optional.empty();
    }
    int y = benchmarkY(world);
    List<LaneState> lanes = new ArrayList<>(lanesCount);
    for (int i = 0; i < lanesCount; i++) {
      LoadedChunk chunk = chunks.get(i % chunks.size());
      int baseSlot = (i / chunks.size()) % LoadTestWorldPlanner.SLOTS_PER_CHUNK;
      lanes.add(new LaneState(i, chunk, baseSlot, y));
    }
    LoadTestWorldWorkload workload =
        new LoadTestWorldWorkload(plugin, database, dependencies, world, runId, lanes, tier.get());
    workload.cleanupPlannedArea();
    return Optional.of(workload);
  }

  int tick(long elapsedTicks) {
    if (lanes.isEmpty()) {
      lastOperations = 0;
      return 0;
    }
    int budget = worldActionBudget();
    int operations = 0;
    int visited = 0;
    while (budget > 0 && visited < lanes.size()) {
      LaneState lane = lanes.get(laneCursor);
      laneCursor = (laneCursor + 1) % lanes.size();
      visited++;
      int laneOps = advanceLane(lane, elapsedTicks);
      if (laneOps <= 0) continue;
      operations += laneOps;
      budget -= laneOps;
    }
    lastOperations = operations;
    return operations;
  }

  int lastOperations() {
    return lastOperations;
  }

  int laneCount() {
    return lanes.size();
  }

  int trackedBlockCount() {
    return trackedBlocks.size();
  }

  int plannedBlockCount() {
    return lanes.size() * LoadTestWorldPlanner.template().size();
  }

  int operationBudget() {
    return worldActionBudget();
  }

  long totalPlacements() {
    return totalPlacements;
  }

  long estimatedCost(int cpuIterationsPerOp) {
    int operations = Math.max(lastOperations, worldActionBudget());
    return (long) operations * (long) Math.max(1, cpuIterationsPerOp);
  }

  void cleanup() {
    for (LaneState lane : lanes) {
      cleanupLane(lane);
    }
    for (BlockKey key : new ArrayList<>(trackedBlocks)) {
      cleanupBenchmarkBlock(key.block(world));
    }
    cleanupStorageRows();
    trackedBlocks.clear();
    lanes.clear();
    invalidateNetwork();
  }

  private int advanceLane(LaneState lane, long elapsedTicks) {
    if (lane.phase == Phase.EMPTY) {
      return buildLane(lane);
    }
    if (lane.phase == Phase.INTERACT) {
      int ops = interactLane(lane, elapsedTicks);
      lane.interactions++;
      if (lane.interactions >= INTERACTIONS_BEFORE_MOVE) {
        lane.phase = Phase.MOVE;
      }
      return ops;
    }
    if (lane.phase == Phase.MOVE) {
      int ops = cleanupLane(lane);
      lane.slot++;
      lane.generation++;
      lane.interactions = 0;
      lane.moves++;
      if (lane.moves % MOVES_BEFORE_EMPTY_TEARDOWN == 0) {
        lane.phase = Phase.EMPTY;
        return Math.max(1, ops);
      }
      return Math.max(1, ops + buildLane(lane));
    }
    return 0;
  }

  private int buildLane(LaneState lane) {
    LoadTestWorldPlanner.Cell slotOffset =
        LoadTestWorldPlanner.slotOffset(lane.baseSlot + lane.slot);
    Set<LoadTestWorldPlanner.Cell> blocked = blockedTemplateCells(lane, slotOffset);
    LoadTestWorldPlanner.Plan plan =
        LoadTestWorldPlanner.buildPlan(blocked, LoadTestWorldPlanner.template().size());
    if (plan.placements().isEmpty()) {
      lane.phase = Phase.EMPTY;
      return 0;
    }
    int operations = 0;
    lane.storageId = storageId(lane);
    storageIds.add(lane.storageId);
    database.setStorageTier(lane.storageId, tier.key());
    dependencies.storageManager().getOrLoad(lane.storageId);
    for (LoadTestWorldPlanner.Placement placement : plan.placements()) {
      Block block = blockAt(lane, placement.cell(), slotOffset);
      if (!canReplace(block)) {
        cleanupLane(lane);
        lane.phase = Phase.EMPTY;
        return Math.max(1, operations);
      }
      cleanupBenchmarkBlock(block);
      place(lane, block, placement.kind());
      markBenchmark(block);
      track(lane, block);
      operations++;
      totalPlacements++;
    }
    lane.phase = Phase.INTERACT;
    lane.interactions = 0;
    refreshLane(lane);
    return Math.max(1, operations);
  }

  private int interactLane(LaneState lane, long elapsedTicks) {
    int operations = 1;
    if (lane.storageId != null) {
      Optional<StorageCache> cache = dependencies.storageManager().getLoadedCache(lane.storageId);
      if (cache.isPresent()) {
        cache.get().addItem(benchmarkItem.key(), benchmarkItem.sample(), 16L);
        if ((lane.interactions & 1) == 1) {
          cache.get().removeItem(benchmarkItem.key(), 4L);
        }
        if (lane.interactions > 0 && lane.interactions % 4 == 0) {
          dependencies.storageManager().flush(cache.get());
        }
      }
    }
    if (lane.terminalBlock != null) {
      dependencies
          .networkGraphCache()
          .find(
              lane.terminalBlock,
              dependencies.keys(),
              plugin,
              dependencies.wireLimit(),
              dependencies.wireHardCap(),
              dependencies.materials().wire(),
              dependencies.materials().storageCarrier());
      operations++;
    }
    if (lane.monitorBlock != null) {
      MonitorMarker.setItem(plugin, lane.monitorBlock, benchmarkItem.key(), benchmarkItem.bytes());
      if (dependencies.monitorDisplayManager() != null) {
        dependencies.monitorDisplayManager().refresh(lane.monitorBlock);
      }
      operations++;
    }
    if (lane.importBusBlock != null) {
      dependencies
          .busService()
          .getOrCreateState(
              BusPos.of(lane.importBusBlock),
              BusMarker.get(plugin, lane.importBusBlock).orElse(null),
              lane.importBusBlock);
      operations++;
    }
    if (lane.exportBusBlock != null) {
      dependencies
          .busService()
          .getOrCreateState(
              BusPos.of(lane.exportBusBlock),
              BusMarker.get(plugin, lane.exportBusBlock).orElse(null),
              lane.exportBusBlock);
      operations++;
    }
    if (elapsedTicks % 4L == 0L) {
      refreshLane(lane);
      operations++;
    }
    return operations;
  }

  private void place(LaneState lane, Block block, LoadTestWorldPlanner.Kind kind) {
    switch (kind) {
      case STORAGE -> {
        Carriers.applyCarrier(block, dependencies.materials().storageCarrier());
        StorageMarker.set(plugin, block, lane.storageId, tier, BlockFace.SOUTH);
        lane.storageBlock = block;
        if (dependencies.hologramManager() != null) {
          dependencies.hologramManager().registerStorage(block);
        }
        dependencies.displayRefreshService().refreshStorage(block);
      }
      case STORAGE_CORE -> {
        Carriers.applyCarrier(block, dependencies.materials().storageCarrier());
        StorageCoreMarker.set(plugin, block);
        dependencies.displayRefreshService().refreshStorage(block);
      }
      case WIRE -> {
        Carriers.applyCarrier(block, dependencies.materials().wire());
        WireMarker.setWire(plugin, block);
        dependencies.displayRefreshService().refreshWireAndNeighbors(block);
      }
      case TERMINAL -> {
        Carriers.applyCarrier(block, dependencies.materials().terminalCarrier());
        TerminalMarker.set(plugin, block, TerminalKind.TERMINAL, BlockFace.SOUTH);
        lane.terminalBlock = block;
        if (dependencies.hologramManager() != null) {
          dependencies.hologramManager().registerTerminal(block);
        }
        dependencies.displayRefreshService().refreshTerminal(block);
      }
      case CRAFTING_TERMINAL -> {
        Carriers.applyCarrier(block, dependencies.materials().terminalCarrier());
        TerminalMarker.set(plugin, block, TerminalKind.CRAFTING, BlockFace.SOUTH);
        if (dependencies.hologramManager() != null) {
          dependencies.hologramManager().registerTerminal(block);
        }
        dependencies.displayRefreshService().refreshTerminal(block);
      }
      case MONITOR -> {
        Carriers.applyCarrier(block, dependencies.materials().monitorCarrier());
        MonitorMarker.set(plugin, block, BlockFace.SOUTH);
        MonitorMarker.setItem(plugin, block, benchmarkItem.key(), benchmarkItem.bytes());
        lane.monitorBlock = block;
        if (dependencies.monitorDisplayManager() != null) {
          dependencies.monitorDisplayManager().registerMonitor(block);
        }
        dependencies.displayRefreshService().refreshMonitor(block);
      }
      case IMPORT_BUS -> placeBus(lane, block, BusType.IMPORT);
      case EXPORT_BUS -> placeBus(lane, block, BusType.EXPORT);
      case CHEST_TARGET -> placeTarget(block, Material.CHEST, Material.IRON_INGOT);
      case FURNACE_TARGET -> placeTarget(block, Material.FURNACE, Material.COPPER_INGOT);
    }
  }

  private void placeBus(LaneState lane, Block block, BusType type) {
    Carriers.applyCarrier(block, dependencies.materials().busCarrier());
    BusMarker.set(plugin, block, type, BlockFace.SOUTH, BusMode.ALL);
    BusPos pos = BusPos.of(block);
    busPositions.add(pos);
    dependencies
        .busService()
        .getOrCreateState(pos, BusMarker.get(plugin, block).orElse(null), block);
    dependencies.displayRefreshService().refreshBus(block);
    if (type == BusType.IMPORT) {
      lane.importBusBlock = block;
    } else {
      lane.exportBusBlock = block;
    }
  }

  private void placeTarget(Block block, Material targetType, Material itemType) {
    block.setType(targetType, false);
    BlockState state = block.getState();
    if (state instanceof InventoryHolder holder) {
      holder.getInventory().setItem(0, new ItemStack(itemType, 32));
    }
  }

  private int cleanupLane(LaneState lane) {
    int operations = 0;
    for (BlockKey key : new ArrayList<>(lane.blocks)) {
      Block block = key.block(world);
      if (cleanupBenchmarkBlock(block)) {
        operations++;
      }
      lane.blocks.remove(key);
    }
    if (lane.storageId != null) {
      storageIds.add(lane.storageId);
      dependencies.storageManager().discardCacheForInternalCleanup(lane.storageId);
      lane.storageId = null;
    }
    lane.storageBlock = null;
    lane.terminalBlock = null;
    lane.monitorBlock = null;
    lane.importBusBlock = null;
    lane.exportBusBlock = null;
    invalidateNetwork();
    return operations;
  }

  private boolean cleanupBenchmarkBlock(Block block) {
    if (block == null || !isBenchmarkOwned(block)) {
      return false;
    }
    StorageMarker.get(plugin, block)
        .map(StorageMarker.Data::storageId)
        .filter(id -> id.startsWith(STORAGE_ID_PREFIX))
        .ifPresent(storageIds::add);
    if (BusMarker.isBus(plugin, block)) {
      BusPos pos = BusPos.of(block);
      busPositions.add(pos);
      dependencies.busService().unregisterBus(block);
      database.deleteBusSettings(pos);
    }
    if (TerminalMarker.isTerminal(plugin, block) && dependencies.hologramManager() != null) {
      dependencies.hologramManager().unregisterTerminal(block);
    }
    if (StorageMarker.get(plugin, block).isPresent() && dependencies.hologramManager() != null) {
      dependencies.hologramManager().unregisterStorage(block);
    }
    if (MonitorMarker.isMonitor(plugin, block) && dependencies.monitorDisplayManager() != null) {
      dependencies.monitorDisplayManager().unregisterMonitor(block);
    }
    dependencies.displayRefreshService().removeBlockDisplays(block);
    WireMarker.clearWire(plugin, block);
    StorageMarker.clear(plugin, block);
    StorageCoreMarker.clear(plugin, block);
    TerminalMarker.clear(plugin, block);
    MonitorMarker.clear(plugin, block);
    BusMarker.clear(plugin, block);
    ChunkMarkerStore.clearBlock(plugin, block);
    block.setType(Material.AIR, false);
    trackedBlocks.remove(BlockKey.of(block));
    return true;
  }

  private void cleanupStorageRows() {
    List<CompletableFuture<Void>> deletes = new ArrayList<>();
    for (String storageId : new LinkedHashSet<>(storageIds)) {
      if (storageId == null || !storageId.startsWith(STORAGE_ID_PREFIX)) continue;
      dependencies.storageManager().discardCacheForInternalCleanup(storageId);
      deletes.add(database.deleteStorageForInternalCleanup(storageId));
    }
    for (BusPos pos : new LinkedHashSet<>(busPositions)) {
      deletes.add(database.deleteBusSettings(pos));
    }
    storageIds.clear();
    busPositions.clear();
    if (deletes.isEmpty()) return;
    try {
      CompletableFuture.allOf(deletes.toArray(CompletableFuture[]::new)).get(5L, TimeUnit.SECONDS);
    } catch (Exception e) {
      plugin.getLogger().log(Level.WARNING, "Benchmark cleanup did not finish all DB deletes.", e);
    }
  }

  private void refreshLane(LaneState lane) {
    if (lane.storageBlock != null) {
      dependencies.displayRefreshService().refreshNetworkFrom(lane.storageBlock);
      dependencies.displayRefreshService().refreshChunk(lane.storageBlock.getChunk());
    } else if (lane.terminalBlock != null) {
      dependencies.displayRefreshService().refreshNetworkFrom(lane.terminalBlock);
      dependencies.displayRefreshService().refreshChunk(lane.terminalBlock.getChunk());
    }
    invalidateNetwork();
  }

  private Set<LoadTestWorldPlanner.Cell> blockedTemplateCells(
      LaneState lane, LoadTestWorldPlanner.Cell slotOffset) {
    Set<LoadTestWorldPlanner.Cell> blocked = new HashSet<>();
    for (LoadTestWorldPlanner.Placement placement : LoadTestWorldPlanner.template()) {
      Block block = blockAt(lane, placement.cell(), slotOffset);
      if (!canReplace(block)) {
        blocked.add(placement.cell());
      }
    }
    return blocked;
  }

  private boolean canReplace(Block block) {
    if (block == null) return false;
    if (isBenchmarkOwned(block)) return true;
    return block.getType().isAir() && !hasExortMarker(block);
  }

  private boolean hasExortMarker(Block block) {
    return WireMarker.isWire(plugin, block)
        || StorageMarker.get(plugin, block).isPresent()
        || StorageCoreMarker.isCore(plugin, block)
        || TerminalMarker.isTerminal(plugin, block)
        || MonitorMarker.isMonitor(plugin, block)
        || BusMarker.isBus(plugin, block);
  }

  private boolean isBenchmarkOwned(Block block) {
    return ChunkMarkerStore.hasSection(plugin, block, BENCHMARK_SECTION);
  }

  private void markBenchmark(Block block) {
    ChunkMarkerStore.setString(plugin, block, BENCHMARK_SECTION, FIELD_RUN, runId);
  }

  private void track(LaneState lane, Block block) {
    BlockKey key = BlockKey.of(block);
    trackedBlocks.add(key);
    lane.blocks.add(key);
  }

  private Block blockAt(
      LaneState lane,
      LoadTestWorldPlanner.Cell templateCell,
      LoadTestWorldPlanner.Cell slotOffset) {
    return world.getBlockAt(
        lane.chunk.x() * 16 + 1 + slotOffset.dx() + templateCell.dx(),
        lane.y + slotOffset.dy() + templateCell.dy(),
        lane.chunk.z() * 16 + 1 + slotOffset.dz() + templateCell.dz());
  }

  private String storageId(LaneState lane) {
    return STORAGE_ID_PREFIX + runId + ":" + lane.index + ":" + lane.generation;
  }

  private int worldActionBudget() {
    return Math.min(
        MAX_WORLD_ACTIONS_PER_TICK,
        Math.max(MIN_WORLD_ACTIONS_PER_TICK, Math.max(1, lanes.size() / 2)));
  }

  private void cleanupPlannedArea() {
    for (LaneState lane : lanes) {
      for (int slot = 0; slot < LoadTestWorldPlanner.SLOTS_PER_CHUNK; slot++) {
        LoadTestWorldPlanner.Cell slotOffset =
            LoadTestWorldPlanner.slotOffset(lane.baseSlot + slot);
        for (LoadTestWorldPlanner.Placement placement : LoadTestWorldPlanner.template()) {
          cleanupBenchmarkBlock(blockAt(lane, placement.cell(), slotOffset));
        }
      }
    }
    cleanupStorageRows();
  }

  private void invalidateNetwork() {
    dependencies.networkGraphCache().invalidateAll();
    if (dependencies.hologramManager() != null) {
      dependencies.hologramManager().invalidateAll();
    }
  }

  private static int benchmarkY(World world) {
    return BenchmarkHeight.blockY(world);
  }

  record LoadedChunk(UUID world, int x, int z) {}

  private enum Phase {
    EMPTY,
    INTERACT,
    MOVE
  }

  private static final class LaneState {
    private final int index;
    private final LoadedChunk chunk;
    private final int baseSlot;
    private final int y;
    private final Set<BlockKey> blocks = new LinkedHashSet<>();
    private int slot;
    private int generation;
    private int interactions;
    private int moves;
    private Phase phase = Phase.EMPTY;
    private String storageId;
    private Block storageBlock;
    private Block terminalBlock;
    private Block monitorBlock;
    private Block importBusBlock;
    private Block exportBusBlock;

    private LaneState(int index, LoadedChunk chunk, int baseSlot, int y) {
      this.index = index;
      this.chunk = chunk;
      this.baseSlot = baseSlot;
      this.y = y;
    }
  }

  private record BlockKey(UUID world, int x, int y, int z) {
    static BlockKey of(Block block) {
      return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    Block block(World fallback) {
      World resolved = Bukkit.getWorld(world);
      World target = resolved == null ? fallback : resolved;
      return target == null ? null : target.getBlockAt(x, y, z);
    }
  }
}
