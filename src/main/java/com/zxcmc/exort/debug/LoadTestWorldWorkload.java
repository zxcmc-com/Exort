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
  private static final int MIN_WATER_BUILD_ACTIONS_PER_TICK = 32;
  private static final int MAX_WATER_BUILD_ACTIONS_PER_TICK = 96;
  private static final int WATER_BUILD_ACTIONS_PER_LANE = 4;
  private static final int MOVES_BEFORE_EMPTY_TEARDOWN = 3;
  private static final int MAX_WORLD_ACTIONS_PER_TICK = 64;
  private static final int MIN_WORLD_ACTIONS_PER_TICK = 4;
  private static final int WATER_ROW_LANES = 8;
  private static final int WATER_BASE_Z_OFFSET = 20;
  private static final int WATER_Y_OFFSET = 4;
  private static final int WATER_WIDTH_Z = 4;
  private static final int WATER_SLOT_STRIDE_Z = 6;
  private static final int WATER_SOURCE_SPACING = 6;
  private static final int WATER_CHANNEL_Z = 1;
  private static final int WATER_WIRE_Z = 2;
  private static final int WATER_SPREAD_STABILIZATION_TICKS = 20 * 4;
  private static final Material WATER_PLATFORM_MATERIAL = Material.STONE;
  private static final Material WATER_WALL_MATERIAL = Material.SMOOTH_STONE;

  private final JavaPlugin plugin;
  private final Database database;
  private final LoadTestRuntimeDependencies dependencies;
  private final World world;
  private final String runId;
  private final List<LaneState> lanes;
  private final int waterOriginX;
  private final int waterOriginZ;
  private final Set<ChunkKey> baseChunkTickets;
  private final Set<ChunkKey> waterChunkTickets = new LinkedHashSet<>();
  private final Set<BlockKey> trackedBlocks = new LinkedHashSet<>();
  private final Set<String> storageIds = new LinkedHashSet<>();
  private final Set<BusPos> busPositions = new LinkedHashSet<>();
  private final ItemKeyUtil.SampleData benchmarkItem;
  private final StorageTier tier;
  private int laneCursor;
  private int waterLaneCursor;
  private int lastOperations;
  private long waterReadyTick = Long.MIN_VALUE;
  private long totalPlacements;

  private LoadTestWorldWorkload(
      JavaPlugin plugin,
      Database database,
      LoadTestRuntimeDependencies dependencies,
      World world,
      String runId,
      List<LaneState> lanes,
      int waterOriginX,
      int waterOriginZ,
      Set<ChunkKey> baseChunkTickets,
      StorageTier tier) {
    this.plugin = plugin;
    this.database = database;
    this.dependencies = dependencies;
    this.world = world;
    this.runId = runId;
    this.lanes = lanes;
    this.waterOriginX = waterOriginX;
    this.waterOriginZ = waterOriginZ;
    this.baseChunkTickets = Set.copyOf(baseChunkTickets);
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
    LoadedChunk first = chunks.getFirst();
    Set<ChunkKey> baseChunkTickets = new LinkedHashSet<>();
    for (LoadedChunk chunk : chunks) {
      baseChunkTickets.add(ChunkKey.of(chunk));
    }
    LoadTestWorldWorkload workload =
        new LoadTestWorldWorkload(
            plugin,
            database,
            dependencies,
            world,
            runId,
            lanes,
            first.x() * 16 + 1,
            first.z() * 16 + WATER_BASE_Z_OFFSET,
            baseChunkTickets,
            tier.get());
    workload.prepareWaterChunkTickets();
    workload.cleanupPlannedArea();
    return Optional.of(workload);
  }

  int tick(long elapsedTicks) {
    if (lanes.isEmpty()) {
      lastOperations = 0;
      return 0;
    }
    int budget = normalWorldActionBudget();
    int operations = 0;
    int visited = 0;
    while (budget > 0 && visited < lanes.size()) {
      LaneState lane = lanes.get(laneCursor);
      laneCursor = (laneCursor + 1) % lanes.size();
      visited++;
      int laneOps = advanceLane(lane, elapsedTicks, budget);
      if (laneOps <= 0) continue;
      operations += laneOps;
      budget -= laneOps;
    }
    operations += advanceWaterStressBuild(waterBuildBudget());
    updateWaterReadiness(elapsedTicks);
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
    return lanes.size() * (LoadTestWorldPlanner.template().size() + waterStressPlannedBlockCount());
  }

  int operationBudget() {
    return normalWorldActionBudget() + waterBuildBudget();
  }

  int waterWireLength() {
    return waterWireLengthValue();
  }

  int waterSourceCount() {
    return lanes.size() * waterSourcesPerLane();
  }

  int extraChunkTicketCount() {
    return waterChunkTickets.size();
  }

  boolean readyForMeasurement(long elapsedTicks) {
    if (lanes.isEmpty()) {
      return true;
    }
    return waterReadyTick != Long.MIN_VALUE
        && elapsedTicks - waterReadyTick >= WATER_SPREAD_STABILIZATION_TICKS;
  }

  long totalPlacements() {
    return totalPlacements;
  }

  long estimatedCost(int cpuIterationsPerOp) {
    int operations = Math.max(lastOperations, operationBudget());
    long operationCost = (long) operations * (long) Math.max(1, cpuIterationsPerOp);
    long waterFlowCost =
        (long) lanes.size()
            * (long) waterWireLengthValue()
            * (long) Math.max(1, cpuIterationsPerOp / 4);
    return operationCost + waterFlowCost;
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
    cleanupWaterChunkTickets();
    invalidateNetwork();
  }

  private int advanceLane(LaneState lane, long elapsedTicks, int budget) {
    if (lane.phase == Phase.EMPTY) {
      return buildLane(lane, budget);
    }
    if (lane.phase == Phase.INTERACT) {
      int ops = interactLane(lane, elapsedTicks, budget);
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
      return Math.max(1, ops + buildLane(lane, Math.max(1, budget - ops)));
    }
    return 0;
  }

  private int buildLane(LaneState lane, int budget) {
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
      markAndTrack(lane, block);
      operations++;
      totalPlacements++;
    }
    lane.phase = Phase.INTERACT;
    lane.interactions = 0;
    refreshLane(lane);
    return Math.max(1, operations);
  }

  private int interactLane(LaneState lane, long elapsedTicks, int budget) {
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

  private boolean canBuildWaterStressLane(LaneState lane) {
    int length = waterWireLengthValue();
    for (int dx = -1; dx <= length; dx++) {
      for (int dz = 0; dz < WATER_WIDTH_Z; dz++) {
        if (!canReplace(waterBlockAt(lane, dx, -1, dz))) {
          return false;
        }
        if (!canReplace(waterBlockAt(lane, dx, 0, dz))) {
          return false;
        }
      }
    }
    return true;
  }

  private int advanceWaterStressBuild(int budget) {
    if (budget <= 0) {
      return 0;
    }
    int operations = 0;
    int visitedCompleteOrBlocked = 0;
    while (operations < budget && !lanes.isEmpty() && visitedCompleteOrBlocked < lanes.size()) {
      LaneState lane = lanes.get(waterLaneCursor);
      if (lane.waterActive || lane.waterBlocked) {
        advanceWaterLaneCursor();
        visitedCompleteOrBlocked++;
        continue;
      }
      int laneOps = ensureWaterStressLane(lane, budget - operations);
      operations += laneOps;
      if (lane.waterActive || lane.waterBlocked) {
        advanceWaterLaneCursor();
        visitedCompleteOrBlocked++;
      } else if (laneOps <= 0) {
        advanceWaterLaneCursor();
        visitedCompleteOrBlocked++;
      } else {
        visitedCompleteOrBlocked = 0;
      }
    }
    return operations;
  }

  private void updateWaterReadiness(long elapsedTicks) {
    if (waterReadyTick != Long.MIN_VALUE || !waterStressBuildComplete()) {
      return;
    }
    waterReadyTick = elapsedTicks;
  }

  private boolean waterStressBuildComplete() {
    for (LaneState lane : lanes) {
      if (!lane.waterActive && !lane.waterBlocked) {
        return false;
      }
    }
    return true;
  }

  private int ensureWaterStressLane(LaneState lane, int budget) {
    if (budget <= 0) {
      return 0;
    }
    if (lane.waterActive) {
      return 0;
    }
    if (lane.waterBuildStage != WaterBuildStage.IDLE) {
      return buildWaterStressLane(lane, budget);
    }
    if (!canBuildWaterStressLane(lane)) {
      lane.waterBlocked = true;
      return 0;
    }
    startWaterStressBuild(lane);
    return buildWaterStressLane(lane, budget);
  }

  private void startWaterStressBuild(LaneState lane) {
    lane.waterBuildStage = WaterBuildStage.STRUCTURE;
    lane.waterStructureCursor = 0;
    lane.waterWireCursor = 0;
    lane.waterSourceCursor = 0;
    lane.waterBlocked = false;
  }

  private int buildWaterStressLane(LaneState lane, int budget) {
    int length = waterWireLengthValue();
    int operations = 0;
    while (operations < budget && lane.waterBuildStage != WaterBuildStage.IDLE) {
      if (lane.waterBuildStage == WaterBuildStage.STRUCTURE) {
        int totalStructureBlocks = waterStressPlannedBlockCount();
        if (lane.waterStructureCursor >= totalStructureBlocks) {
          lane.waterBuildStage = WaterBuildStage.WIRE;
          continue;
        }
        placeWaterStructureBlock(lane, lane.waterStructureCursor++);
        operations++;
        totalPlacements++;
        continue;
      }
      if (lane.waterBuildStage == WaterBuildStage.WIRE) {
        if (lane.waterWireCursor >= length) {
          lane.waterBuildStage = WaterBuildStage.SOURCE;
          continue;
        }
        Block wire = waterBlockAt(lane, lane.waterWireCursor++, 0, WATER_WIRE_Z);
        Carriers.applyCarrier(wire, dependencies.materials().wire());
        WireMarker.setWire(plugin, wire);
        dependencies.displayRefreshService().refreshWireAndNeighbors(wire);
        operations++;
        totalPlacements++;
        continue;
      }
      int sourceDx = lane.waterSourceCursor * WATER_SOURCE_SPACING;
      if (sourceDx >= length) {
        lane.waterBuildStage = WaterBuildStage.IDLE;
        lane.waterActive = true;
        continue;
      }
      Block source = waterBlockAt(lane, sourceDx, 0, WATER_CHANNEL_Z);
      source.setType(Material.WATER, true);
      lane.waterSourceCursor++;
      operations++;
      totalPlacements++;
    }
    return operations;
  }

  private void placeWaterStructureBlock(LaneState lane, int cursor) {
    int areaIndex = cursor / 2;
    int layer = cursor % 2;
    int dx = (areaIndex / WATER_WIDTH_Z) - 1;
    int dz = areaIndex % WATER_WIDTH_Z;
    Block block = waterBlockAt(lane, dx, layer == 0 ? -1 : 0, dz);
    cleanupBenchmarkBlock(block);
    if (layer == 0) {
      block.setType(WATER_PLATFORM_MATERIAL, false);
    } else if (isWaterWall(dx, dz, waterWireLengthValue())) {
      block.setType(WATER_WALL_MATERIAL, false);
    } else {
      block.setType(Material.AIR, false);
    }
    markAndTrackWater(lane, block);
  }

  private boolean isWaterWall(int dx, int dz, int length) {
    return dx == -1 || dx == length || dz == 0 || dz == WATER_WIDTH_Z - 1;
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

  private void markAndTrack(LaneState lane, Block block) {
    markBenchmark(block);
    track(lane, block);
  }

  private void markAndTrackWater(LaneState lane, Block block) {
    markBenchmark(block);
    trackWater(lane, block);
  }

  private void track(LaneState lane, Block block) {
    BlockKey key = BlockKey.of(block);
    trackedBlocks.add(key);
    lane.blocks.add(key);
  }

  private void trackWater(LaneState lane, Block block) {
    BlockKey key = BlockKey.of(block);
    trackedBlocks.add(key);
    lane.waterBlocks.add(key);
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

  private Block waterBlockAt(LaneState lane, int dx, int dy, int dz) {
    return world.getBlockAt(
        waterBaseX(lane) + dx, lane.y + WATER_Y_OFFSET + dy, waterBaseZ(lane) + dz);
  }

  private int waterBaseX(LaneState lane) {
    return waterOriginX + (lane.index % WATER_ROW_LANES) * waterBasinStrideX();
  }

  private int waterBaseZ(LaneState lane) {
    return waterOriginZ + (lane.index / WATER_ROW_LANES) * WATER_SLOT_STRIDE_Z;
  }

  private int waterBasinStrideX() {
    return waterWireLengthValue() + 4;
  }

  private int waterWireLengthValue() {
    int hardCap = dependencies.wireHardCap();
    int configured = dependencies.wireLimit();
    return Math.max(1, Math.min(configured, hardCap > 0 ? hardCap : configured));
  }

  private int waterSourcesPerLane() {
    return Math.max(1, ((waterWireLengthValue() - 1) / WATER_SOURCE_SPACING) + 1);
  }

  private int waterStressPlannedBlockCount() {
    return (waterWireLengthValue() + 2) * WATER_WIDTH_Z * 2;
  }

  private int waterBuildBudget() {
    return Math.min(
        MAX_WATER_BUILD_ACTIONS_PER_TICK,
        Math.max(MIN_WATER_BUILD_ACTIONS_PER_TICK, lanes.size() * WATER_BUILD_ACTIONS_PER_LANE));
  }

  private int normalWorldActionBudget() {
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
      cleanupWaterStressLane(lane);
    }
    cleanupStorageRows();
  }

  private int cleanupWaterStressLane(LaneState lane) {
    int length = waterWireLengthValue();
    int operations = 0;
    for (int dx = -1; dx <= length; dx++) {
      for (int dz = 0; dz < WATER_WIDTH_Z; dz++) {
        if (cleanupBenchmarkBlock(waterBlockAt(lane, dx, -1, dz))) {
          operations++;
        }
        if (cleanupBenchmarkBlock(waterBlockAt(lane, dx, 0, dz))) {
          operations++;
        }
      }
    }
    lane.waterBlocks.clear();
    lane.waterBuildStage = WaterBuildStage.IDLE;
    lane.waterStructureCursor = 0;
    lane.waterWireCursor = 0;
    lane.waterSourceCursor = 0;
    lane.waterActive = false;
    lane.waterBlocked = false;
    return operations;
  }

  private void advanceWaterLaneCursor() {
    waterLaneCursor = (waterLaneCursor + 1) % Math.max(1, lanes.size());
  }

  private void prepareWaterChunkTickets() {
    for (LaneState lane : lanes) {
      int minX = waterBaseX(lane) - 1;
      int maxX = waterBaseX(lane) + waterWireLengthValue();
      int minZ = waterBaseZ(lane);
      int maxZ = waterBaseZ(lane) + WATER_WIDTH_Z - 1;
      for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
        for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
          ChunkKey key = new ChunkKey(world.getUID(), chunkX, chunkZ);
          if (baseChunkTickets.contains(key) || !waterChunkTickets.add(key)) {
            continue;
          }
          world.getChunkAt(chunkX, chunkZ).addPluginChunkTicket(plugin);
        }
      }
    }
  }

  private void cleanupWaterChunkTickets() {
    for (ChunkKey key : new ArrayList<>(waterChunkTickets)) {
      World resolved = Bukkit.getWorld(key.world());
      if (resolved == null) continue;
      resolved.getChunkAt(key.x(), key.z()).removePluginChunkTicket(plugin);
    }
    waterChunkTickets.clear();
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

  private record ChunkKey(UUID world, int x, int z) {
    static ChunkKey of(LoadedChunk chunk) {
      return new ChunkKey(chunk.world(), chunk.x(), chunk.z());
    }
  }

  private enum Phase {
    EMPTY,
    INTERACT,
    MOVE
  }

  private enum WaterBuildStage {
    IDLE,
    STRUCTURE,
    WIRE,
    SOURCE
  }

  private static final class LaneState {
    private final int index;
    private final LoadedChunk chunk;
    private final int baseSlot;
    private final int y;
    private final Set<BlockKey> blocks = new LinkedHashSet<>();
    private final Set<BlockKey> waterBlocks = new LinkedHashSet<>();
    private int slot;
    private int generation;
    private int interactions;
    private int waterStructureCursor;
    private int waterWireCursor;
    private int waterSourceCursor;
    private int moves;
    private boolean waterActive;
    private boolean waterBlocked;
    private Phase phase = Phase.EMPTY;
    private WaterBuildStage waterBuildStage = WaterBuildStage.IDLE;
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
