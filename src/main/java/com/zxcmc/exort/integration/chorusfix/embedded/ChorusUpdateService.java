package com.zxcmc.exort.integration.chorusfix.embedded;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChorusUpdateService {
  private static final BlockFace[] DIRECT_NEIGHBORS = {
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
  };
  private static final int[][] REQUIRED_OFFSETS = {
    {0, 0, -1},
    {1, 0, 0},
    {0, 0, 1},
    {-1, 0, 0},
    {0, 1, 0},
    {0, -1, 0},
    {0, -1, -1},
    {1, -1, 0},
    {0, -1, 1},
    {-1, -1, 0}
  };

  private final Plugin plugin;
  private final Logger logger;
  private final ChorusBlockBreaker breakExecutor;
  private final Queue<QueuedBlock> queue = new ArrayDeque<>();
  private final Map<LocationKey, ProcessingMode> pending = new HashMap<>();
  private EmbeddedChorusfixConfig config;
  private ExortChorusCarrierDetector detector;
  private EmbeddedChorusfixRuntimeState paperState;
  private BukkitTask task;
  private long processedTotal;
  private long brokenTotal;
  private long correctedTotal;
  private long skippedTotal;

  public ChorusUpdateService(
      Plugin plugin,
      EmbeddedChorusfixConfig config,
      ExortChorusCarrierDetector detector,
      EmbeddedChorusfixRuntimeState paperState) {
    this(plugin, config, detector, paperState, new ChorusBreakExecutor());
  }

  ChorusUpdateService(
      Plugin plugin,
      EmbeddedChorusfixConfig config,
      ExortChorusCarrierDetector detector,
      EmbeddedChorusfixRuntimeState paperState,
      ChorusBlockBreaker breakExecutor) {
    this.plugin = plugin;
    this.logger = plugin.getLogger();
    this.breakExecutor = breakExecutor;
    this.config = config;
    this.detector = detector;
    this.paperState = paperState;
  }

  public void update(
      EmbeddedChorusfixConfig config,
      ExortChorusCarrierDetector detector,
      EmbeddedChorusfixRuntimeState paperState) {
    this.config = config;
    this.detector = detector;
    this.paperState = paperState;
    if (!active()) {
      clearQueue();
    }
  }

  public boolean active() {
    return config.enabled() && (!config.onlyWhenPaperDisabled() || paperState.disabledTrue());
  }

  public void enqueueAfterBlockPlaceNextTick(Block placed) {
    enqueueNeighborhoodNextTick(placed, recheckModeAfterBlockPlace(placed));
  }

  ProcessingMode recheckModeAfterBlockPlace(Block placed) {
    if (!active()
        || ChorusMaterial.fromMaterial(placed.getType()) != ChorusMaterial.CHORUS_FLOWER) {
      return ProcessingMode.NORMAL;
    }
    if (placedTopFlowerMayCreateProtectedStem(placed)) {
      return ProcessingMode.FLOWER_PLACEMENT_REPAIR;
    }
    ChorusWorldView world = relativeWorld(placed, ProcessingMode.NORMAL);
    if (world.typeAt(0, -1, 0) == ChorusMaterial.AIR
        && VanillaChorusRules.canFlowerSurvive(world)) {
      return ProcessingMode.VANILLA_MUTATION;
    }
    return ProcessingMode.NORMAL;
  }

  public void enqueueAfterBlockBreakNextTick(Block origin) {
    ProcessingMode mode =
        isChorusMaterial(origin.getType())
            ? ProcessingMode.VANILLA_MUTATION
            : ProcessingMode.NORMAL;
    enqueueNeighborhoodNextTick(origin, mode);
  }

  public void enqueueNeighborhoodNextTick(Block origin) {
    enqueueNeighborhoodNextTick(origin, ProcessingMode.NORMAL);
  }

  public void enqueueVanillaMutationNeighborhoodNextTick(Block origin) {
    enqueueNeighborhoodNextTick(origin, ProcessingMode.VANILLA_MUTATION);
  }

  private void enqueueNeighborhoodNextTick(Block origin, ProcessingMode mode) {
    Bukkit.getScheduler().runTask(plugin, () -> enqueueNeighborhood(origin, 0, mode));
  }

  public void enqueueNeighborhood(Block origin, int depth) {
    enqueueNeighborhood(origin, depth, ProcessingMode.NORMAL);
  }

  private void enqueueNeighborhood(Block origin, int depth, ProcessingMode mode) {
    if (!active()) {
      return;
    }
    QueueBudget budget = new QueueBudget(config.maxPerEvent());
    tryEnqueue(origin, depth, budget, mode);
    for (BlockFace face : DIRECT_NEIGHBORS) {
      Optional<Block> relative =
          loadedRelative(origin, face.getModX(), face.getModY(), face.getModZ());
      relative.ifPresent(block -> tryEnqueue(block, depth, budget, mode));
    }
  }

  public void enqueueIfChorusRelatedNextTick(Block origin) {
    if (!active() || !isChorusRelated(origin)) {
      return;
    }
    enqueueNeighborhoodNextTick(origin);
  }

  public Status status() {
    return new Status(
        active(), pending.size(), processedTotal, brokenTotal, correctedTotal, skippedTotal);
  }

  public void shutdown() {
    if (task != null) {
      task.cancel();
      task = null;
    }
    clearQueue();
  }

  private boolean isChorusRelated(Block origin) {
    if (isChorusMaterial(origin.getType())) {
      return true;
    }
    for (BlockFace face : DIRECT_NEIGHBORS) {
      Optional<Block> relative =
          loadedRelative(origin, face.getModX(), face.getModY(), face.getModZ());
      if (relative.isPresent() && isChorusMaterial(relative.get().getType())) {
        return true;
      }
    }
    return false;
  }

  boolean tryEnqueue(Block block, int depth, QueueBudget budget, ProcessingMode mode) {
    if (depth > config.maxChainDepth()
        || !budget.tryConsume()
        || !isChorusMaterial(block.getType())) {
      return false;
    }
    LocationKey key = LocationKey.from(block);
    ProcessingMode pendingMode = pending.get(key);
    if (pendingMode != null) {
      if (processingPriority(mode) > processingPriority(pendingMode)) {
        pending.put(key, mode);
        queue.add(new QueuedBlock(key, depth, mode));
        schedule();
        return true;
      }
      return false;
    }
    pending.put(key, mode);
    queue.add(new QueuedBlock(key, depth, mode));
    schedule();
    return true;
  }

  private void schedule() {
    if (task != null || !active()) {
      return;
    }
    task = Bukkit.getScheduler().runTask(plugin, this::processTick);
  }

  private void processTick() {
    task = null;
    if (!active()) {
      clearQueue();
      return;
    }
    int budget = config.maxPerTick();
    while (budget-- > 0 && !queue.isEmpty()) {
      QueuedBlock queued = queue.poll();
      ProcessingMode mode = pending.get(queued.key());
      if (mode == null || mode != queued.mode()) {
        continue;
      }
      pending.remove(queued.key());
      Optional<Block> block = queued.key().resolve();
      if (block.isEmpty()) {
        continue;
      }
      processBlock(block.get(), queued.depth(), mode);
    }
    if (!queue.isEmpty()) {
      schedule();
    }
  }

  void processBlock(Block block, int depth, ProcessingMode mode) {
    if (!loadedNeighborhood(block)) {
      skippedTotal++;
      return;
    }
    ChorusMaterial material = ChorusMaterial.fromMaterial(block.getType());
    if (!material.isChorusBlock()) {
      return;
    }

    ChorusFaceMask mask = null;
    if (material == ChorusMaterial.CHORUS_PLANT) {
      mask = ChorusFaceMask.fromBlockData(block.getBlockData()).orElse(null);
      if (mask == null) {
        skippedTotal++;
        return;
      }
    }
    if (isIgnoredMask(mask)) {
      skippedTotal++;
      return;
    }

    boolean providerClaimed = providerClaimed(block, mask, mode);
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            material, mask, config.ignoredMasks(), providerClaimed, eligibilityMode(mode));
    if (!decision.process()) {
      skippedTotal++;
      return;
    }

    ChorusWorldView world = relativeWorld(block, mode);
    if (material == ChorusMaterial.CHORUS_PLANT) {
      ChorusFaceMask plantMask = mask;
      if (plantMask == null) {
        skippedTotal++;
        return;
      }
      if (mode == ProcessingMode.FLOWER_PLACEMENT_REPAIR && plantMask.isImpossibleCustomCarrier()) {
        repairPlantAfterFlowerPlacement(block, plantMask, world, depth);
        processedTotal++;
        return;
      }
      if (mode == ProcessingMode.VANILLA_MUTATION && plantMask.isImpossibleCustomCarrier()) {
        breakAndCascade(block, depth, mode);
        processedTotal++;
        return;
      }
      processPlant(block, plantMask, world, depth, mode);
    } else if (!VanillaChorusRules.canFlowerSurvive(world)) {
      breakAndCascade(block, depth, mode);
    }
    processedTotal++;
  }

  private void processPlant(
      Block block, ChorusFaceMask current, ChorusWorldView world, int depth, ProcessingMode mode) {
    if (!VanillaChorusRules.canPlantSurvive(world)) {
      breakAndCascade(block, depth, mode);
      return;
    }
    ChorusFaceMask expected = VanillaChorusRules.recomputePlantMask(world);
    correctPlantMask(block, current, expected, depth, mode);
  }

  private void repairPlantAfterFlowerPlacement(
      Block block, ChorusFaceMask current, ChorusWorldView world, int depth) {
    if (!VanillaChorusRules.canPlantSurvive(world)) {
      breakAndCascade(block, depth, ProcessingMode.FLOWER_PLACEMENT_REPAIR);
      return;
    }
    ChorusFaceMask expected = VanillaChorusRules.recomputePlantMask(world);
    if (expected.isImpossibleCustomCarrier()) {
      skippedTotal++;
      return;
    }
    correctPlantMask(block, current, expected, depth, ProcessingMode.FLOWER_PLACEMENT_REPAIR);
  }

  private void correctPlantMask(
      Block block,
      ChorusFaceMask current,
      ChorusFaceMask expected,
      int depth,
      ProcessingMode mode) {
    if (expected.equals(current)) {
      return;
    }
    BlockData nextData = block.getBlockData().clone();
    if (!(nextData instanceof MultipleFacing facing)) {
      skippedTotal++;
      return;
    }
    expected.applyTo(facing);
    block.setBlockData(nextData, false);
    correctedTotal++;
    enqueueNeighborhood(block, depth + 1, mode);
  }

  private void breakAndCascade(Block block, int depth, ProcessingMode mode) {
    if (config.debug()) {
      logger.info("Breaking unsupported chorus block at " + block.getLocation());
    }
    if (breakExecutor.breakNaturallyWithFeedback(block)) {
      brokenTotal++;
      enqueueNeighborhood(block, depth + 1, mode);
    }
  }

  private ChorusWorldView relativeWorld(Block origin, ProcessingMode mode) {
    return (dx, dy, dz) -> {
      Optional<Block> block = loadedRelative(origin, dx, dy, dz);
      return block.map(value -> ruleMaterial(value, mode)).orElse(ChorusMaterial.OTHER);
    };
  }

  private ChorusMaterial ruleMaterial(Block block, ProcessingMode mode) {
    ChorusMaterial material = ChorusMaterial.fromMaterial(block.getType());
    if (!material.isChorusBlock()) {
      return material;
    }

    ChorusFaceMask mask = null;
    if (material == ChorusMaterial.CHORUS_PLANT) {
      mask = ChorusFaceMask.fromBlockData(block.getBlockData()).orElse(null);
      if (mask == null) {
        return ChorusMaterial.OTHER;
      }
    }
    if (isIgnoredMask(mask)) {
      return ChorusMaterial.OTHER;
    }
    if (mode == ProcessingMode.FLOWER_PLACEMENT_REPAIR
        && mask != null
        && mask.isImpossibleCustomCarrier()) {
      return ChorusMaterial.OTHER;
    }

    boolean providerClaimed = providerClaimed(block, mask, mode);
    ChorusEligibility.Decision decision =
        ChorusEligibility.evaluate(
            material, mask, config.ignoredMasks(), providerClaimed, eligibilityMode(mode));
    return decision.process() ? material : ChorusMaterial.OTHER;
  }

  private boolean isIgnoredMask(ChorusFaceMask mask) {
    return mask != null && config.ignoredMasks().contains(mask);
  }

  private boolean providerClaimed(Block block, ChorusFaceMask mask, ProcessingMode mode) {
    if (mode != ProcessingMode.NORMAL) {
      return detector.isHardCustom(block, mask);
    }
    return detector.isCustom(block, mask);
  }

  private static ChorusEligibility.Mode eligibilityMode(ProcessingMode mode) {
    return switch (mode) {
      case NORMAL -> ChorusEligibility.Mode.NORMAL;
      case FLOWER_PLACEMENT_REPAIR, VANILLA_MUTATION -> ChorusEligibility.Mode.VANILLA_MUTATION;
    };
  }

  private boolean placedTopFlowerMayCreateProtectedStem(Block placed) {
    Optional<Block> below = loadedRelative(placed, 0, -1, 0);
    if (below.isEmpty() || below.get().getType() != Material.CHORUS_PLANT) {
      return false;
    }
    Optional<ChorusFaceMask> mask = ChorusFaceMask.fromBlockData(below.get().getBlockData());
    if (mask.isEmpty()
        || isIgnoredMask(mask.get())
        || detector.isHardCustom(below.get(), mask.get())) {
      return false;
    }
    return mask.get().isImpossibleCustomCarrier()
        || (mask.get().down() && mask.get().hasHorizontal());
  }

  private static int processingPriority(ProcessingMode mode) {
    return switch (mode) {
      case NORMAL -> 0;
      case FLOWER_PLACEMENT_REPAIR -> 1;
      case VANILLA_MUTATION -> 2;
    };
  }

  private boolean loadedNeighborhood(Block origin) {
    for (int[] offset : REQUIRED_OFFSETS) {
      if (loadedRelative(origin, offset[0], offset[1], offset[2]).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  private Optional<Block> loadedRelative(Block origin, int dx, int dy, int dz) {
    int x = origin.getX() + dx;
    int y = origin.getY() + dy;
    int z = origin.getZ() + dz;
    World world = origin.getWorld();
    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
      return Optional.empty();
    }
    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
      return Optional.empty();
    }
    return Optional.of(origin.getRelative(dx, dy, dz));
  }

  private static boolean isChorusMaterial(Material material) {
    return material == Material.CHORUS_PLANT || material == Material.CHORUS_FLOWER;
  }

  private void clearQueue() {
    queue.clear();
    pending.clear();
  }

  public record Status(
      boolean active,
      int queued,
      long processedTotal,
      long brokenTotal,
      long correctedTotal,
      long skippedTotal) {}

  enum ProcessingMode {
    NORMAL,
    FLOWER_PLACEMENT_REPAIR,
    VANILLA_MUTATION
  }

  private record QueuedBlock(LocationKey key, int depth, ProcessingMode mode) {}

  private record LocationKey(UUID worldId, int x, int y, int z) {
    static LocationKey from(Block block) {
      return new LocationKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    Optional<Block> resolve() {
      World world = Bukkit.getWorld(worldId);
      if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight()) {
        return Optional.empty();
      }
      if (!world.isChunkLoaded(x >> 4, z >> 4)) {
        return Optional.empty();
      }
      return Optional.of(world.getBlockAt(x, y, z));
    }
  }
}
