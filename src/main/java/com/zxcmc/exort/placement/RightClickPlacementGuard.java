package com.zxcmc.exort.placement;

import com.zxcmc.exort.breaking.CustomBlockBreaker;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.items.CustomItems;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class RightClickPlacementGuard implements Listener, Runnable {
  private static final int LEFT_CLICK_SUPPRESS_TICKS = 8;
  private static final double MIN_GUARD_SCALE = 0.0625;
  private static final double MAX_GUARD_SCALE = 1.0;
  private static final double SMALL_ARMOR_STAND_RADIUS = 0.125;
  private static final double SMALL_ARMOR_STAND_HEIGHT = 0.9875;
  private static final int PREDICTED_GUARD_TTL_TICKS = 3;
  private static final double HIT_MOTION_EPSILON = 1.0E-5;
  private static final double MAX_HIT_MOTION_SQUARED = 4.0;
  private static final double DIAGONAL_BOUNDARY_TIME_TOLERANCE = 0.35;

  private final Plugin plugin;
  private final CustomItems customItems;
  private final CustomBlockBreaker customBlockBreaker;
  private final ExortBlockTargetResolver targets;
  private final PlacementGuardBackend backend;
  private final int pollIntervalTicks;
  private final int targetRangeBlocks;
  private final double guardScale;
  private final double cornerInset;
  private final Map<UUID, Map<GuardKey, ActiveGuard>> guardsByPlayer = new HashMap<>();
  private final Map<UUID, UUID> ownerByEntity = new HashMap<>();
  private final Map<UUID, Integer> suppressUntilTick = new HashMap<>();
  private final Map<UUID, AimTrace> lastTraceByPlayer = new HashMap<>();
  private final Map<UUID, AimPrediction> lastPredictionByPlayer = new HashMap<>();
  private int taskId = -1;

  public RightClickPlacementGuard(
      Plugin plugin,
      CustomItems customItems,
      CustomBlockBreaker customBlockBreaker,
      ExortBlockTargetResolver targets,
      PlacementGuardBackend backend,
      int pollIntervalTicks,
      int targetRangeBlocks,
      double guardScale,
      double cornerInset) {
    this.plugin = plugin;
    this.customItems = customItems;
    this.customBlockBreaker = customBlockBreaker;
    this.targets = targets;
    this.backend = backend;
    this.pollIntervalTicks = Math.max(1, pollIntervalTicks);
    this.targetRangeBlocks = Math.max(1, targetRangeBlocks);
    this.guardScale = clampGuardScale(guardScale);
    this.cornerInset = Math.max(0.0, cornerInset);
  }

  public void start() {
    if (taskId != -1) return;
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 1L, pollIntervalTicks);
  }

  public void stop() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    for (UUID playerId : Set.copyOf(guardsByPlayer.keySet())) {
      removeGuard(playerId);
    }
    ownerByEntity.clear();
    suppressUntilTick.clear();
    lastTraceByPlayer.clear();
    lastPredictionByPlayer.clear();
  }

  @Override
  public void run() {
    PerfStats.measure(PerfStats.Area.PLACEMENT_GUARD, this::runMeasured);
  }

  private void runMeasured() {
    Set<UUID> online = new HashSet<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      online.add(player.getUniqueId());
      updateGuard(player);
    }
    for (UUID playerId : Set.copyOf(guardsByPlayer.keySet())) {
      if (!online.contains(playerId)) {
        removeGuard(playerId);
      }
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    removeGuard(playerId);
    suppressUntilTick.remove(playerId);
    lastTraceByPlayer.remove(playerId);
    lastPredictionByPlayer.remove(playerId);
  }

  @EventHandler(ignoreCancelled = true)
  public void onSneak(PlayerToggleSneakEvent event) {
    if (event.isSneaking()) {
      UUID playerId = event.getPlayer().getUniqueId();
      removeGuard(playerId);
      lastTraceByPlayer.remove(playerId);
      lastPredictionByPlayer.remove(playerId);
    } else {
      updateGuard(event.getPlayer());
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    removeGuard(playerId);
    lastTraceByPlayer.remove(playerId);
    lastPredictionByPlayer.remove(playerId);
  }

  @EventHandler(ignoreCancelled = true)
  public void onChangedWorld(PlayerChangedWorldEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    removeGuard(playerId);
    lastTraceByPlayer.remove(playerId);
    lastPredictionByPlayer.remove(playerId);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    if (!event.hasChangedOrientation() && !event.hasChangedPosition()) {
      return;
    }
    updateGuard(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHeldItem(PlayerItemHeldEvent event) {
    Player player = event.getPlayer();
    ItemStack main = player.getInventory().getItem(event.getNewSlot());
    updateGuard(player, main, player.getInventory().getItemInOffHand());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
    updateGuard(event.getPlayer(), event.getMainHandItem(), event.getOffHandItem());
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void onBlockUse(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
      return;
    }
    BlockFace face = event.getBlockFace();
    if (face == null || face == BlockFace.SELF) {
      return;
    }
    Block clickedBlock = event.getClickedBlock();
    Block placementBlock = clickedBlock.getRelative(face);
    if (backend.usesServerEntities()) {
      removeForeignGuardsBlocking(event.getPlayer().getUniqueId(), placementBlock);
    }
    correctMissedClientPrediction(event.getPlayer(), clickedBlock, placementBlock);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onGuardInteract(PlayerInteractEntityEvent event) {
    handleGuardInteract(event, event.getRightClicked(), event.getHand());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onGuardInteractAt(PlayerInteractAtEntityEvent event) {
    handleGuardInteract(event, event.getRightClicked(), event.getHand());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onGuardArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
    handleGuardInteract(event, event.getRightClicked(), event.getHand());
  }

  private void handleGuardInteract(
      PlayerInteractEntityEvent event, Entity clicked, EquipmentSlot hand) {
    UUID ownerId = ownerByEntity.get(clicked.getUniqueId());
    if (ownerId == null) return;
    event.setCancelled(true);

    Player player = event.getPlayer();
    if (!ownerId.equals(player.getUniqueId())) return;
    GuardEntry guard = guardForEntity(ownerId, event.getRightClicked());
    if (guard == null) {
      ownerByEntity.remove(event.getRightClicked().getUniqueId());
      event.getRightClicked().remove();
      return;
    }
    Block target = guard.guard().exortBlock().resolve();
    if (player.isSneaking() || target == null || !targets.isExortBlock(target)) {
      removeGuard(ownerId, guard.key());
      return;
    }
    forwardBlockInteract(player, target, hand);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onGuardAttack(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player)) return;
    UUID ownerId = ownerByEntity.get(event.getEntity().getUniqueId());
    if (ownerId == null) return;
    event.setCancelled(true);
    if (!ownerId.equals(player.getUniqueId())) return;
    GuardEntry guard = guardForEntity(ownerId, event.getEntity());
    if (guard == null) {
      ownerByEntity.remove(event.getEntity().getUniqueId());
      event.getEntity().remove();
      return;
    }
    Block target = guard.guard().exortBlock().resolve();
    removeGuard(ownerId);
    suppressUntilTick.put(ownerId, Bukkit.getCurrentTick() + LEFT_CLICK_SUPPRESS_TICKS);
    if (target != null && targets.isExortBlock(target)) {
      customBlockBreaker.handlePlacementGuardAttack(player, target);
    }
  }

  private void updateGuard(Player player) {
    updateGuard(
        player,
        player.getInventory().getItemInMainHand(),
        player.getInventory().getItemInOffHand());
  }

  private void updateGuard(Player player, ItemStack main, ItemStack offhand) {
    UUID playerId = player.getUniqueId();
    if (!shouldMaintainGuard(player, main, offhand)) {
      removeGuard(playerId);
      lastTraceByPlayer.remove(playerId);
      lastPredictionByPlayer.remove(playerId);
      return;
    }
    Map<GuardKey, GuardTarget> targets = findGuardTargets(player);
    if (targets.isEmpty()) {
      removeGuard(playerId);
      return;
    }
    ensureGuards(player, targets);
  }

  private boolean shouldMaintainGuard(Player player, ItemStack main, ItemStack offhand) {
    if (player == null || !player.isOnline() || player.isDead() || player.isSneaking()) {
      return false;
    }
    if (isSuppressed(player.getUniqueId())) {
      return false;
    }
    if (PlaceableItemClassifier.isPotentialPlacementItem(main)) {
      return true;
    }
    return PlaceableItemClassifier.isPotentialPlacementItem(offhand)
        && shouldUseOffhand(player, main);
  }

  private boolean shouldUseOffhand(Player player, ItemStack main) {
    if (main == null || main.getType() == Material.AIR) return true;
    if (main.hasItemMeta() && customItems.isCustomItem(main)) return false;
    Material type = main.getType();
    if (type.isEdible() && player.getFoodLevel() >= 20) return true;
    if (PlaceableItemClassifier.isPotentialPlacementItem(main)) return false;
    return !type.isEdible();
  }

  private boolean isSuppressed(UUID playerId) {
    Integer until = suppressUntilTick.get(playerId);
    if (until == null) return false;
    if (Bukkit.getCurrentTick() <= until) return true;
    suppressUntilTick.remove(playerId);
    return false;
  }

  private Map<GuardKey, GuardTarget> findGuardTargets(Player player) {
    UUID playerId = player.getUniqueId();
    RayTraceResult trace =
        player
            .getWorld()
            .rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                targetRangeBlocks,
                FluidCollisionMode.NEVER,
                false);
    if (trace == null || trace.getHitBlock() == null) {
      lastTraceByPlayer.remove(playerId);
      lastPredictionByPlayer.remove(playerId);
      return Map.of();
    }
    Block hitBlock = trace.getHitBlock();
    BlockFace face = trace.getHitBlockFace();
    if (face == null || face == BlockFace.SELF) {
      face = estimateClickedFace(player, hitBlock);
    }
    Vector hitPosition = trace.getHitPosition();
    AimTrace previous = lastTraceByPlayer.get(playerId);
    AimPrediction prediction = predictAim(playerId, hitBlock, face, hitPosition, previous);
    lastTraceByPlayer.put(playerId, new AimTrace(face, hitPosition.clone()));

    Map<GuardKey, GuardTarget> result = new LinkedHashMap<>();
    addGuardTarget(result, hitBlock, face, hitPosition);
    if (prediction != null) {
      addGuardTarget(
          result,
          neighborInFacePlane(hitBlock, face, prediction.firstStep(), prediction.secondStep()),
          face,
          hitPosition);
    }
    return result;
  }

  private AimPrediction predictAim(
      UUID playerId, Block hitBlock, BlockFace face, Vector hitPosition, AimTrace previous) {
    PlaneStep step = predictNextStep(hitBlock, face, hitPosition, previous);
    if (step != null) {
      AimPrediction prediction =
          new AimPrediction(
              face,
              step.first(),
              step.second(),
              Bukkit.getCurrentTick() + PREDICTED_GUARD_TTL_TICKS);
      lastPredictionByPlayer.put(playerId, prediction);
      return prediction;
    }
    AimPrediction prediction = lastPredictionByPlayer.get(playerId);
    if (prediction != null && prediction.isValid(face)) {
      return prediction;
    }
    lastPredictionByPlayer.remove(playerId);
    return null;
  }

  private PlaneStep predictNextStep(
      Block hitBlock, BlockFace face, Vector hitPosition, AimTrace previous) {
    if (previous == null || previous.face() != face || hitPosition == null) {
      return null;
    }
    if (hitPosition.distanceSquared(previous.hitPosition()) > MAX_HIT_MOTION_SQUARED) {
      return null;
    }
    FaceMotion motion = faceMotion(hitBlock, face, hitPosition, previous.hitPosition());
    double firstTime = boundaryTime(motion.firstCoord(), motion.firstDelta());
    double secondTime = boundaryTime(motion.secondCoord(), motion.secondDelta());
    if (!Double.isFinite(firstTime) && !Double.isFinite(secondTime)) {
      return null;
    }
    double minTime = Math.min(firstTime, secondTime);
    int firstStep = 0;
    int secondStep = 0;
    if (Double.isFinite(firstTime)
        && firstTime <= minTime * (1.0 + DIAGONAL_BOUNDARY_TIME_TOLERANCE)) {
      firstStep = stepDirection(motion.firstDelta());
    }
    if (Double.isFinite(secondTime)
        && secondTime <= minTime * (1.0 + DIAGONAL_BOUNDARY_TIME_TOLERANCE)) {
      secondStep = stepDirection(motion.secondDelta());
    }
    if (firstStep == 0 && secondStep == 0) {
      return null;
    }
    return new PlaneStep(firstStep, secondStep);
  }

  private FaceMotion faceMotion(
      Block block, BlockFace face, Vector hitPosition, Vector previousHitPosition) {
    return switch (face) {
      case NORTH, SOUTH ->
          new FaceMotion(
              clampUnit(hitPosition.getX() - block.getX()),
              clampUnit(hitPosition.getY() - block.getY()),
              hitPosition.getX() - previousHitPosition.getX(),
              hitPosition.getY() - previousHitPosition.getY());
      case EAST, WEST ->
          new FaceMotion(
              clampUnit(hitPosition.getZ() - block.getZ()),
              clampUnit(hitPosition.getY() - block.getY()),
              hitPosition.getZ() - previousHitPosition.getZ(),
              hitPosition.getY() - previousHitPosition.getY());
      default ->
          new FaceMotion(
              clampUnit(hitPosition.getX() - block.getX()),
              clampUnit(hitPosition.getZ() - block.getZ()),
              hitPosition.getX() - previousHitPosition.getX(),
              hitPosition.getZ() - previousHitPosition.getZ());
    };
  }

  private double boundaryTime(double coord, double delta) {
    if (Math.abs(delta) < HIT_MOTION_EPSILON) {
      return Double.POSITIVE_INFINITY;
    }
    return delta > 0.0 ? (1.0 - coord) / delta : coord / -delta;
  }

  private int stepDirection(double delta) {
    if (delta > HIT_MOTION_EPSILON) return 1;
    if (delta < -HIT_MOTION_EPSILON) return -1;
    return 0;
  }

  private double clampUnit(double value) {
    if (value < 0.0) return 0.0;
    if (value > 1.0) return 1.0;
    return value;
  }

  private Block neighborInFacePlane(Block center, BlockFace face, int first, int second) {
    return switch (face) {
      case NORTH, SOUTH -> center.getRelative(first, second, 0);
      case EAST, WEST -> center.getRelative(0, second, first);
      default -> center.getRelative(first, 0, second);
    };
  }

  private void addGuardTarget(
      Map<GuardKey, GuardTarget> result, Block exortBlock, BlockFace face, Vector hitPosition) {
    if (!isLoaded(exortBlock) || !targets.isExortBlock(exortBlock)) {
      return;
    }
    Block placementBlock = exortBlock.getRelative(face);
    if (!isLoaded(placementBlock) || !isReplaceable(placementBlock)) {
      return;
    }
    Location guardLocation = guardLocation(placementBlock, face, hitPosition);
    GuardTarget target =
        new GuardTarget(BlockKey.of(exortBlock), BlockKey.of(placementBlock), guardLocation);
    result.putIfAbsent(target.key(), target);
  }

  private boolean isLoaded(Block block) {
    return block != null && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
  }

  private boolean isReplaceable(Block block) {
    if (block == null) return false;
    return block.getType().isAir() || block.isReplaceable();
  }

  private void removeForeignGuardsBlocking(UUID playerId, Block placementBlock) {
    BlockKey placementKey = BlockKey.of(placementBlock);
    for (UUID ownerId : Set.copyOf(guardsByPlayer.keySet())) {
      if (ownerId.equals(playerId)) {
        continue;
      }
      Map<GuardKey, ActiveGuard> guards = guardsByPlayer.get(ownerId);
      if (guards == null) {
        continue;
      }
      for (GuardKey guardKey : Set.copyOf(guards.keySet())) {
        ActiveGuard guard = guards.get(guardKey);
        if (guard != null && guard.placementBlock().equals(placementKey)) {
          removeGuard(ownerId, guardKey);
        }
      }
    }
  }

  private void correctMissedClientPrediction(
      Player player, Block clickedBlock, Block placementBlock) {
    if (player.isSneaking()
        || !targets.isExortBlock(clickedBlock)
        || !isLoaded(placementBlock)
        || !isReplaceable(placementBlock)) {
      return;
    }
    if (!shouldMaintainGuard(
        player,
        player.getInventory().getItemInMainHand(),
        player.getInventory().getItemInOffHand())) {
      return;
    }
    sendBlockCorrection(player, placementBlock);
    BlockKey placementKey = BlockKey.of(placementBlock);
    Bukkit.getScheduler().runTask(plugin, () -> sendBlockCorrection(player, placementKey));
  }

  private void sendBlockCorrection(Player player, Block block) {
    player.sendBlockChange(block.getLocation(), block.getBlockData());
  }

  private void sendBlockCorrection(Player player, BlockKey blockKey) {
    if (!player.isOnline()) return;
    Block block = blockKey.resolve();
    if (block != null) {
      sendBlockCorrection(player, block);
    }
  }

  private void ensureGuards(Player player, Map<GuardKey, GuardTarget> targets) {
    UUID playerId = player.getUniqueId();
    Map<GuardKey, ActiveGuard> existingGuards =
        guardsByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
    for (GuardKey guardKey : Set.copyOf(existingGuards.keySet())) {
      if (!targets.containsKey(guardKey)) {
        removeGuard(playerId, guardKey);
      }
    }
    existingGuards = guardsByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
    for (Map.Entry<GuardKey, GuardTarget> entry : targets.entrySet()) {
      ensureGuard(player, playerId, entry.getKey(), entry.getValue());
    }
    if (existingGuards.isEmpty()) {
      guardsByPlayer.remove(playerId);
    }
  }

  private void ensureGuard(Player player, UUID playerId, GuardKey guardKey, GuardTarget target) {
    Map<GuardKey, ActiveGuard> guards = guardsByPlayer.get(playerId);
    if (guards == null) return;
    ActiveGuard existing = guards.get(guardKey);
    if (existing != null && existing.handle().isValid()) {
      if (!existing.sameLocation(target.location())) {
        existing.handle().move(player, target);
        guards.put(guardKey, new ActiveGuard(existing.handle(), target));
      }
      return;
    }
    if (existing != null) {
      removeGuard(playerId, guardKey);
      guards = guardsByPlayer.computeIfAbsent(playerId, ignored -> new HashMap<>());
    }
    PlacementGuardHandle handle = backend.createGuard(player, target);
    if (handle == null) return;
    guards.put(guardKey, new ActiveGuard(handle, target));
    UUID entityId = handle.bukkitEntityUuid();
    if (entityId != null) {
      ownerByEntity.put(entityId, playerId);
    }
  }

  private Location guardLocation(Block placementBlock, BlockFace face, Vector hitPosition) {
    double radius = SMALL_ARMOR_STAND_RADIUS * guardScale;
    double height = SMALL_ARMOR_STAND_HEIGHT * guardScale;
    double inset = Math.min(0.45, cornerInset);
    double horizontalInset = Math.min(inset, Math.max(0.0, 0.5 - radius));
    double verticalInset = Math.min(inset, Math.max(0.0, 1.0 - height));
    double lowX = placementBlock.getX() + radius + horizontalInset;
    double highX = placementBlock.getX() + 1.0 - radius - horizontalInset;
    double lowY = placementBlock.getY() + verticalInset;
    double highY = placementBlock.getY() + 1.0 - height - verticalInset;
    double lowZ = placementBlock.getZ() + radius + horizontalInset;
    double highZ = placementBlock.getZ() + 1.0 - radius - horizontalInset;

    Location[] candidates =
        switch (face) {
          case NORTH -> {
            yield new Location[] {
              new Location(placementBlock.getWorld(), highX, lowY, highZ),
              new Location(placementBlock.getWorld(), lowX, lowY, highZ),
              new Location(placementBlock.getWorld(), highX, highY, highZ),
              new Location(placementBlock.getWorld(), lowX, highY, highZ)
            };
          }
          case SOUTH -> {
            yield new Location[] {
              new Location(placementBlock.getWorld(), lowX, lowY, lowZ),
              new Location(placementBlock.getWorld(), highX, lowY, lowZ),
              new Location(placementBlock.getWorld(), lowX, highY, lowZ),
              new Location(placementBlock.getWorld(), highX, highY, lowZ)
            };
          }
          case WEST -> {
            yield new Location[] {
              new Location(placementBlock.getWorld(), highX, lowY, lowZ),
              new Location(placementBlock.getWorld(), highX, lowY, highZ),
              new Location(placementBlock.getWorld(), highX, highY, lowZ),
              new Location(placementBlock.getWorld(), highX, highY, highZ)
            };
          }
          case EAST -> {
            yield new Location[] {
              new Location(placementBlock.getWorld(), lowX, lowY, highZ),
              new Location(placementBlock.getWorld(), lowX, lowY, lowZ),
              new Location(placementBlock.getWorld(), lowX, highY, highZ),
              new Location(placementBlock.getWorld(), lowX, highY, lowZ)
            };
          }
          case DOWN -> {
            yield new Location[] {
              new Location(placementBlock.getWorld(), lowX, highY, highZ),
              new Location(placementBlock.getWorld(), highX, highY, highZ),
              new Location(placementBlock.getWorld(), lowX, highY, lowZ),
              new Location(placementBlock.getWorld(), highX, highY, lowZ)
            };
          }
          default -> {
            yield new Location[] {
              new Location(placementBlock.getWorld(), lowX, lowY, highZ),
              new Location(placementBlock.getWorld(), highX, lowY, highZ),
              new Location(placementBlock.getWorld(), lowX, lowY, lowZ),
              new Location(placementBlock.getWorld(), highX, lowY, lowZ)
            };
          }
        };

    return farthestFromHit(candidates, hitPosition, height);
  }

  private Location farthestFromHit(Location[] candidates, Vector hitPosition, double height) {
    Location best = candidates[0];
    if (hitPosition == null) {
      return best;
    }
    double bestDistance = guardDistanceSquared(best, hitPosition, height);
    for (int i = 1; i < candidates.length; i++) {
      double distance = guardDistanceSquared(candidates[i], hitPosition, height);
      if (distance > bestDistance) {
        best = candidates[i];
        bestDistance = distance;
      }
    }
    return best;
  }

  private double guardDistanceSquared(Location location, Vector hitPosition, double height) {
    double dx = location.getX() - hitPosition.getX();
    double dy = location.getY() + height * 0.5 - hitPosition.getY();
    double dz = location.getZ() - hitPosition.getZ();
    return dx * dx + dy * dy + dz * dz;
  }

  private double clampGuardScale(double value) {
    if (Double.isNaN(value) || value < MIN_GUARD_SCALE) {
      return MIN_GUARD_SCALE;
    }
    if (value > MAX_GUARD_SCALE) {
      return MAX_GUARD_SCALE;
    }
    return value;
  }

  private void removeGuard(UUID playerId) {
    Map<GuardKey, ActiveGuard> guards = guardsByPlayer.remove(playerId);
    if (guards == null) return;
    for (ActiveGuard guard : guards.values()) {
      UUID entityId = guard.handle().bukkitEntityUuid();
      if (entityId != null) {
        ownerByEntity.remove(entityId);
      }
      guard.handle().remove();
    }
  }

  private void removeGuard(UUID playerId, GuardKey guardKey) {
    Map<GuardKey, ActiveGuard> guards = guardsByPlayer.get(playerId);
    if (guards == null) return;
    ActiveGuard guard = guards.remove(guardKey);
    if (guard == null) return;
    UUID entityId = guard.handle().bukkitEntityUuid();
    if (entityId != null) {
      ownerByEntity.remove(entityId);
    }
    guard.handle().remove();
    if (guards.isEmpty()) {
      guardsByPlayer.remove(playerId);
    }
  }

  private void forwardBlockInteract(Player player, Block target, EquipmentSlot hand) {
    EquipmentSlot safeHand = hand == null ? EquipmentSlot.HAND : hand;
    ItemStack item = player.getInventory().getItem(safeHand);
    PlayerInteractEvent forwarded =
        new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            item,
            target,
            estimateClickedFace(player, target),
            safeHand,
            new Vector(0.5, 0.5, 0.5));
    Bukkit.getPluginManager().callEvent(forwarded);
  }

  private BlockFace estimateClickedFace(Player player, Block block) {
    Location eye = player.getEyeLocation();
    Vector center = block.getLocation().add(0.5, 0.5, 0.5).toVector();
    Vector fromCenter = eye.toVector().subtract(center);
    double ax = Math.abs(fromCenter.getX());
    double ay = Math.abs(fromCenter.getY());
    double az = Math.abs(fromCenter.getZ());
    if (ay >= ax && ay >= az) {
      return fromCenter.getY() >= 0.0 ? BlockFace.UP : BlockFace.DOWN;
    }
    if (ax >= az) {
      return fromCenter.getX() >= 0.0 ? BlockFace.EAST : BlockFace.WEST;
    }
    return fromCenter.getZ() >= 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
  }

  private boolean sameEntity(PlacementGuardHandle expected, Entity actual) {
    UUID entityId = expected.bukkitEntityUuid();
    return actual != null && entityId != null && entityId.equals(actual.getUniqueId());
  }

  private GuardEntry guardForEntity(UUID ownerId, Entity entity) {
    Map<GuardKey, ActiveGuard> guards = guardsByPlayer.get(ownerId);
    if (guards == null || entity == null) {
      return null;
    }
    for (Map.Entry<GuardKey, ActiveGuard> entry : guards.entrySet()) {
      ActiveGuard guard = entry.getValue();
      if (sameEntity(guard.handle(), entity)) {
        return new GuardEntry(entry.getKey(), guard);
      }
    }
    return null;
  }

  private record AimTrace(BlockFace face, Vector hitPosition) {}

  private record AimPrediction(BlockFace face, int firstStep, int secondStep, int expiresAtTick) {
    boolean isValid(BlockFace currentFace) {
      return face == currentFace && Bukkit.getCurrentTick() <= expiresAtTick;
    }
  }

  private record PlaneStep(int first, int second) {}

  private record FaceMotion(
      double firstCoord, double secondCoord, double firstDelta, double secondDelta) {}

  private record GuardEntry(GuardKey key, ActiveGuard guard) {}

  private record ActiveGuard(PlacementGuardHandle handle, GuardTarget target) {
    boolean sameLocation(Location other) {
      Location location = target.location();
      return location.getWorld() == other.getWorld() && location.distanceSquared(other) < 1.0E-6;
    }

    BlockKey exortBlock() {
      return target.exortBlock();
    }

    BlockKey placementBlock() {
      return target.placementBlock();
    }
  }
}
