package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusRuntimeConfig;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.bus.InventorySideRules;
import com.zxcmc.exort.bus.loop.BusLoopGuard;
import com.zxcmc.exort.bus.resolver.BusTargetResolver;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BusEngine implements Runnable {
  private static final int CRAFT_PAUSE_TICKS = 2;
  private static final int CONTEXT_CACHE_TTL_TICKS = 20;
  private static final int LOOP_GUARD_REFRESH_TICKS = 20;

  private final BusEngineDependencies dependencies;
  private final StorageManager storageManager;
  private final Material busCarrier;
  private final int maxOperationsPerChunk;
  private final int activeIntervalTicks;
  private final int idleIntervalTicks;
  private final int itemsPerOperation;
  private final int maxOperationsPerTick;
  private final WirelessTerminalService wirelessService;
  private final BusTargetResolver targetResolver;
  private final BusRegistry registry;
  private final BusDueScheduler dueScheduler = new BusDueScheduler();

  private int taskId = -1;
  private final Map<BusPos, CachedContext> contextCache = new HashMap<>();
  private long loopGuardNextRefreshTick = Long.MIN_VALUE;
  private long loopGuardRegistryVersion = Long.MIN_VALUE;
  private long loopGuardTopologyVersion = Long.MIN_VALUE;
  private final Set<BusPos> loopDisabled = ConcurrentHashMap.newKeySet();
  private final Map<String, Long> craftPausedUntil = new ConcurrentHashMap<>();

  public BusEngine(
      BusEngineDependencies dependencies,
      StorageManager storageManager,
      Material busCarrier,
      BusRuntimeConfig runtimeConfig,
      WirelessTerminalService wirelessService,
      BusRegistry registry) {
    this.dependencies = dependencies;
    this.storageManager = storageManager;
    this.busCarrier = busCarrier;
    this.maxOperationsPerChunk = runtimeConfig.maxOperationsPerChunk();
    this.activeIntervalTicks = runtimeConfig.activeIntervalTicks();
    this.idleIntervalTicks = runtimeConfig.idleIntervalTicks();
    this.itemsPerOperation = runtimeConfig.itemsPerOperation();
    this.maxOperationsPerTick = runtimeConfig.maxOperationsPerTick();
    this.wirelessService = wirelessService;
    this.registry = registry;
    this.targetResolver =
        new BusTargetResolver(dependencies.plugin(), runtimeConfig.allowStorageTargets());
  }

  public void start() {
    if (taskId != -1) return;
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(dependencies.plugin(), this, 1L, 1L);
  }

  public void stop() {
    if (taskId != -1) {
      Bukkit.getScheduler().cancelTask(taskId);
      taskId = -1;
    }
    dueScheduler.clear();
    contextCache.clear();
    loopDisabled.clear();
    craftPausedUntil.clear();
  }

  public void pauseForCraft(String storageId) {
    pauseForCraft(storageId, CRAFT_PAUSE_TICKS);
  }

  public void pauseForCraft(String storageId, int ticks) {
    if (storageId == null || storageId.isBlank()) return;
    long now = Bukkit.getCurrentTick();
    long until = now + Math.max(1, ticks);
    craftPausedUntil.merge(storageId, until, (prev, next) -> prev >= next ? prev : next);
  }

  public boolean isLoopDisabled(BusPos pos) {
    return loopDisabled.contains(pos);
  }

  public Optional<BusTargetResolver.BusTarget> resolveTarget(Block busBlock, BlockFace facing) {
    return targetResolver.resolve(busBlock, facing);
  }

  @Override
  public void run() {
    PerfStats.measure(PerfStats.Area.BUS, this::runMeasured);
  }

  private void runMeasured() {
    List<BusState> list = registry.snapshotList();
    if (list.isEmpty()) {
      dueScheduler.clear();
      return;
    }
    BusTickBudget budget = new BusTickBudget(maxOperationsPerTick, maxOperationsPerChunk);
    long tick = Bukkit.getCurrentTick();
    dueScheduler.sync(list, registry.version(), tick);
    PerfStats.setGauge("bus.dueDepth", dueScheduler.size());
    refreshLoopDisabledSnapshotIfNeeded(list, tick);
    Set<String> changedStorages = new HashSet<>();
    int contextBudget = contextBudget();
    while (budget.hasGlobalBudget() && contextBudget-- > 0) {
      BusState state = dueScheduler.pollDue(tick);
      if (state == null) {
        break;
      }
      budget.recordDueAttempt();
      long nextTick;
      if (state.viewers() > 0) {
        nextTick = tick + idleIntervalTicks;
        state.setNextTick(nextTick);
        dueScheduler.schedule(state, nextTick);
        continue;
      }
      if (budget.isChunkBudgetReached(state.pos())) {
        nextTick = tick + idleIntervalTicks;
        state.setNextTick(nextTick);
        dueScheduler.schedule(state, nextTick);
        continue;
      }
      budget.recordChunkAttempt(state.pos());
      boolean moved = false;
      try {
        moved = tickBus(state, tick, changedStorages);
      } catch (RuntimeException ex) {
        dependencies.logger().log(Level.WARNING, "Bus tick failed for " + state.pos(), ex);
      }
      nextTick = tick + (moved ? activeIntervalTicks : idleIntervalTicks);
      state.setNextTick(nextTick);
      dueScheduler.schedule(state, nextTick);
    }
    if (dueScheduler.hasDue(tick)) {
      PerfStats.incrementCounter("bus.budgetOverrun");
    }
    if (!changedStorages.isEmpty()) {
      for (String storageId : changedStorages) {
        dependencies.renderStorage(storageId);
      }
    }
  }

  private int contextBudget() {
    int base = Math.max(maxOperationsPerTick, 1) * 4;
    return Math.max(64, Math.min(base, 4096));
  }

  private boolean tickBus(BusState state, long tick, Set<String> changedStorages) {
    if (state.type() == BusType.EXPORT && loopDisabled.contains(state.pos())) {
      return false;
    }
    ResolvedContext ctx = contextFor(state, tick);
    if (ctx == null) return false;
    String storageId = ctx.storageId();
    StorageTier tier = ctx.tier();
    BusTargetResolver.BusTarget target = ctx.target();
    if (storageId == null || target == null || tier == null) return false;
    if (state.type() == BusType.EXPORT && isPaused(storageId, tick)) return false;
    if (state.type() == BusType.IMPORT
        && target instanceof BusTargetResolver.StorageTarget storageTarget
        && isPaused(storageTarget.storageId(), tick)) {
      return false;
    }
    StorageCache cache = storageManager.getLoadedCache(storageId).orElse(null);
    if (cache == null || !cache.isLoaded()) {
      preloadStorage(storageId);
      return false;
    }
    return PerfStats.measure(
        "bus.move",
        () ->
            state.type() == BusType.IMPORT
                ? tickImport(state, cache, tier, target, changedStorages, tick)
                : tickExport(state, cache, target, changedStorages, tick));
  }

  private boolean isPaused(String storageId, long tick) {
    if (storageId == null) return false;
    Long until = craftPausedUntil.get(storageId);
    if (until == null) return false;
    if (tick >= until) {
      craftPausedUntil.remove(storageId);
      return false;
    }
    return true;
  }

  private boolean tickImport(
      BusState state,
      StorageCache cache,
      StorageTier tier,
      BusTargetResolver.BusTarget target,
      Set<String> changedStorages,
      long tick) {
    if (tier == null) return false;
    if (target instanceof BusTargetResolver.InventoryTarget invTarget) {
      boolean moved = tickImportInventory(state, cache, tier, invTarget, tick);
      if (moved) {
        changedStorages.add(cache.getStorageId());
      }
      return moved;
    }
    if (target instanceof BusTargetResolver.StorageTarget storageTarget) {
      if (storageTarget.storageId().equals(cache.getStorageId())) return false;
      StorageCache source = storageManager.getLoadedCache(storageTarget.storageId()).orElse(null);
      if (source == null || !source.isLoaded()) {
        preloadStorage(storageTarget.storageId());
        return false;
      }
      boolean moved = tickStorageTransfer(state, source, cache, tier);
      if (moved) {
        changedStorages.add(source.getStorageId());
        changedStorages.add(cache.getStorageId());
      }
      return moved;
    }
    return false;
  }

  private boolean tickExport(
      BusState state,
      StorageCache cache,
      BusTargetResolver.BusTarget target,
      Set<String> changedStorages,
      long tick) {
    if (target instanceof BusTargetResolver.InventoryTarget invTarget) {
      boolean moved = tickExportInventory(state, cache, invTarget, tick);
      if (moved) {
        changedStorages.add(cache.getStorageId());
      }
      return moved;
    }
    if (target instanceof BusTargetResolver.StorageTarget storageTarget) {
      if (storageTarget.storageId().equals(cache.getStorageId())) return false;
      StorageTier destTier = storageTarget.tier();
      if (destTier == null) return false;
      StorageCache dest = storageManager.getLoadedCache(storageTarget.storageId()).orElse(null);
      if (dest == null || !dest.isLoaded()) {
        preloadStorage(storageTarget.storageId());
        return false;
      }
      boolean moved = tickStorageTransfer(state, cache, dest, destTier);
      if (moved) {
        changedStorages.add(cache.getStorageId());
        changedStorages.add(dest.getStorageId());
      }
      return moved;
    }
    return false;
  }

  private void preloadStorage(String storageId) {
    if (storageId == null || storageManager.isLoading(storageId)) return;
    storageManager
        .getOrLoad(storageId)
        .whenComplete(
            (cache, err) -> {
              if (err != null) {
                dependencies
                    .logger()
                    .log(Level.WARNING, "Failed to preload bus storage " + storageId, unwrap(err));
              }
            });
  }

  private Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }

  private boolean tickImportInventory(
      BusState state,
      StorageCache cache,
      StorageTier tier,
      BusTargetResolver.InventoryTarget target,
      long tick) {
    Inventory inventory = target.inventory();
    int[] slots = InventorySideRules.extractSlots(inventory, target.state(), target.side());
    if (slots.length == 0) return false;
    int start = Math.floorMod(state.slotCursor(), slots.length);
    for (int i = 0; i < slots.length; i++) {
      int idx = (start + i) % slots.length;
      int slot = slots[idx];
      ItemStack stack = inventory.getItem(slot);
      if (stack == null || stack.getType() == Material.AIR) continue;
      if (!InventorySideRules.canExtract(inventory, target.state(), target.side(), stack, slot))
        continue;
      ItemStack storageStack = stack.clone();
      if (wirelessService != null && wirelessService.isWireless(storageStack)) {
        wirelessService.prepareForStorage(storageStack);
      }
      ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(storageStack);
      if (!allows(state.mode(), data.key(), state.filterKeys())) {
        continue;
      }
      long space = tier.maxItems() - cache.effectiveTotal();
      if (space <= 0) {
        return false;
      }
      long weight = cache.nestedWeight(storageStack);
      long maxBySpace = Math.max(0, space / Math.max(1, weight));
      if (maxBySpace <= 0) {
        return false;
      }
      int move = (int) Math.min(stack.getAmount(), Math.min(itemsPerOperation, maxBySpace));
      if (move <= 0) continue;
      stack.setAmount(stack.getAmount() - move);
      if (stack.getAmount() <= 0) {
        inventory.setItem(slot, null);
      } else {
        inventory.setItem(slot, stack);
      }
      cache.addItem(data.key(), data.sample(), move);
      refreshVisualInventory(target);
      state.setSlotCursor(idx + 1);
      return true;
    }
    state.setSlotCursor(start);
    return false;
  }

  private boolean tickExportInventory(
      BusState state, StorageCache cache, BusTargetResolver.InventoryTarget target, long tick) {
    Inventory inventory = target.inventory();
    List<StorageCache.StorageItemView> list = cachedStorageList(state, cache);
    if (list.isEmpty()) return false;
    int start = Math.floorMod(state.storageCursor(), list.size());
    for (int i = 0; i < list.size(); i++) {
      int idx = (start + i) % list.size();
      StorageCache.StorageItemView entry = list.get(idx);
      if (entry.amount() <= 0) continue;
      if (!allows(state.mode(), entry.key(), state.filterKeys())) continue;
      int desired = Math.min(itemsPerOperation, (int) Math.min(Integer.MAX_VALUE, entry.amount()));
      if (desired <= 0) continue;
      ItemStack outputSample = entry.sampleCopy();
      String outputKey = entry.key();
      if (wirelessService != null && wirelessService.isWireless(outputSample)) {
        outputSample = wirelessService.extractFromStorage(outputSample);
        outputKey = ItemKeyUtil.keyFor(outputSample);
      }
      long removed = cache.removeItem(entry.key(), desired);
      if (removed <= 0) continue;
      int moved =
          InventorySideRules.insert(
              inventory, target.state(), target.side(), outputSample, outputKey, (int) removed);
      if (moved < removed) {
        cache.addItem(entry.key(), entry.sampleCopy(), removed - moved);
      }
      if (moved > 0) {
        refreshVisualInventory(target);
        state.setStorageCursor(idx + 1);
        return true;
      }
    }
    state.setStorageCursor(start);
    return false;
  }

  private boolean tickStorageTransfer(
      BusState state, StorageCache source, StorageCache dest, StorageTier destTier) {
    if (destTier == null) return false;
    List<StorageCache.StorageItemView> list = cachedStorageList(state, source);
    if (list.isEmpty()) return false;
    int start = Math.floorMod(state.storageCursor(), list.size());
    for (int i = 0; i < list.size(); i++) {
      int idx = (start + i) % list.size();
      StorageCache.StorageItemView entry = list.get(idx);
      if (entry.amount() <= 0) continue;
      if (!allows(state.mode(), entry.key(), state.filterKeys())) continue;
      int desired = Math.min(itemsPerOperation, (int) Math.min(Integer.MAX_VALUE, entry.amount()));
      if (desired <= 0) continue;
      long space = destTier.maxItems() - dest.effectiveTotal();
      if (space <= 0) {
        return false;
      }
      long weight = entry.weight();
      long maxBySpace = Math.max(0, space / Math.max(1, weight));
      if (maxBySpace <= 0) {
        return false;
      }
      int move = (int) Math.min(desired, maxBySpace);
      if (move <= 0) continue;
      long removed = source.removeItem(entry.key(), move);
      if (removed <= 0) continue;
      dest.addItem(entry.key(), entry.sampleCopy(), removed);
      state.setStorageCursor(idx + 1);
      return true;
    }
    state.setStorageCursor(start);
    return false;
  }

  private List<StorageCache.StorageItemView> cachedStorageList(BusState state, StorageCache cache) {
    long version = cache.version();
    if (state.cachedItems() == null
        || state.cachedStorageVersion() != version
        || !cache.getStorageId().equals(state.cachedStorageId())) {
      List<StorageCache.StorageItemView> fresh = cache.itemViewsSnapshot();
      state.setCachedItems(fresh);
      state.setCachedStorageVersion(version);
      state.setCachedStorageId(cache.getStorageId());
      return fresh;
    }
    return state.cachedItems();
  }

  private static boolean allows(BusMode mode, String key, Set<String> filterKeys) {
    return switch (mode) {
      case DISABLED -> false;
      case ALL -> true;
      case WHITELIST -> filterKeys.contains(key);
      case BLACKLIST -> !filterKeys.contains(key);
    };
  }

  private void refreshVisualInventory(BusTargetResolver.InventoryTarget target) {
    if (target == null || !isVisualInventory(target.inventory())) return;
    Block block = target.block();
    if (block == null || block.getWorld() == null) return;
    if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return;
    block.getState().update(false, false);
  }

  private boolean isVisualInventory(Inventory inventory) {
    return inventory != null && "SHELF".equals(inventory.getType().name());
  }

  private record ResolvedContext(
      BusState state,
      String storageId,
      StorageTier tier,
      BusTargetResolver.BusTarget target,
      BusMode mode,
      BusType type,
      BusTargetResolver.InvKey invKey,
      boolean inventorySideSensitive) {}

  private record StorageInvKey(
      String storageId, BusTargetResolver.InvKey invKey, BlockFace sideSensitiveSide) {}

  private record CachedContext(
      ResolvedContext context,
      long settingsRevision,
      long topologyVersion,
      Material busMaterial,
      Material targetMaterial,
      long createdTick) {}

  private ResolvedContext contextFor(BusState state, long tick) {
    return PerfStats.measure("bus.resolve", () -> contextForMeasured(state, tick));
  }

  private ResolvedContext contextForMeasured(BusState state, long tick) {
    if (state == null) return null;
    CachedContext cached = contextCache.get(state.pos());
    if (cached != null && cachedContextValid(state, cached, tick)) {
      return cached.context();
    }
    Optional<ResolvedContext> computed = computeContext(state);
    if (computed.isEmpty()) {
      contextCache.remove(state.pos());
      return null;
    }
    ResolvedContext ctx = computed.get();
    Material busMaterial = blockMaterial(state.pos());
    Material targetMaterial = targetMaterial(ctx.target());
    contextCache.put(
        state.pos(),
        new CachedContext(
            ctx,
            state.settingsRevision(),
            dependencies.topologyVersion(),
            busMaterial,
            targetMaterial,
            tick));
    return ctx;
  }

  private boolean cachedContextValid(BusState state, CachedContext cached, long tick) {
    if (cached == null || cached.context() == null || state == null) return false;
    if (cached.settingsRevision() != state.settingsRevision()) return false;
    if (cached.topologyVersion() != dependencies.topologyVersion()) return false;
    if (tick - cached.createdTick() > CONTEXT_CACHE_TTL_TICKS) return false;
    BusPos pos = state.pos();
    World world = Bukkit.getWorld(pos.world());
    if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return false;
    Block busBlock = world.getBlockAt(pos.x(), pos.y(), pos.z());
    if (busBlock.getType() != cached.busMaterial()) return false;
    if (!Carriers.matchesCarrier(busBlock, busCarrier) || !dependencies.isBus(busBlock)) {
      return false;
    }
    Block target = targetBlock(cached.context().target());
    if (target == null || target.getWorld() == null) return false;
    if (!target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)) return false;
    return target.getType() == cached.targetMaterial();
  }

  private Material blockMaterial(BusPos pos) {
    if (pos == null) return Material.AIR;
    World world = Bukkit.getWorld(pos.world());
    if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) return Material.AIR;
    return world.getBlockAt(pos.x(), pos.y(), pos.z()).getType();
  }

  private Material targetMaterial(BusTargetResolver.BusTarget target) {
    Block block = targetBlock(target);
    return block == null ? Material.AIR : block.getType();
  }

  private Block targetBlock(BusTargetResolver.BusTarget target) {
    if (target instanceof BusTargetResolver.InventoryTarget invTarget) {
      return invTarget.block();
    }
    if (target instanceof BusTargetResolver.StorageTarget storageTarget) {
      return storageTarget.block();
    }
    return null;
  }

  private void refreshLoopDisabledSnapshotIfNeeded(List<BusState> list, long tick) {
    long registryVersion = registry.version();
    long topologyVersion = dependencies.topologyVersion();
    if (tick < loopGuardNextRefreshTick
        && registryVersion == loopGuardRegistryVersion
        && topologyVersion == loopGuardTopologyVersion) {
      return;
    }
    PerfStats.measure("bus.loopGuard", () -> rebuildLoopDisabledSnapshot(list, tick));
    loopGuardNextRefreshTick = tick + LOOP_GUARD_REFRESH_TICKS;
    loopGuardRegistryVersion = registryVersion;
    loopGuardTopologyVersion = topologyVersion;
  }

  private void rebuildLoopDisabledSnapshot(List<BusState> list, long tick) {
    loopDisabled.clear();
    Map<StorageInvKey, List<ResolvedContext>> grouped = new HashMap<>();
    for (BusState state : list) {
      ResolvedContext ctx = contextFor(state, tick);
      if (ctx == null || ctx.invKey() == null || ctx.storageId() == null) continue;
      StorageInvKey key = conflictKey(ctx);
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ctx);
    }
    for (var entry : grouped.entrySet()) {
      List<ResolvedContext> group = entry.getValue();
      List<ResolvedContext> imports =
          group.stream().filter(c -> c.type() == BusType.IMPORT).toList();
      List<ResolvedContext> exports =
          group.stream().filter(c -> c.type() == BusType.EXPORT).toList();
      if (imports.isEmpty() || exports.isEmpty()) continue;
      boolean allAllConflict = hasAllAllConflict(group);
      boolean filtersConflict = hasIntersectingFilters(imports, exports);
      if (!allAllConflict && !filtersConflict) continue;
      for (ResolvedContext exp : exports) {
        loopDisabled.add(exp.state().pos());
      }
    }
  }

  private StorageInvKey conflictKey(ResolvedContext ctx) {
    BlockFace side = null;
    if (ctx.inventorySideSensitive()
        && ctx.target() instanceof BusTargetResolver.InventoryTarget invTarget) {
      side = invTarget.side();
    }
    return new StorageInvKey(ctx.storageId(), ctx.invKey(), side);
  }

  private boolean hasAllAllConflict(List<ResolvedContext> group) {
    boolean importAll =
        group.stream()
            .anyMatch(
                c ->
                    c.type() == BusType.IMPORT
                        && c.mode() == BusMode.ALL
                        && !c.inventorySideSensitive());
    boolean exportAll =
        group.stream().anyMatch(c -> c.type() == BusType.EXPORT && c.mode() == BusMode.ALL);
    return importAll && exportAll;
  }

  private boolean hasIntersectingFilters(
      List<ResolvedContext> imports, List<ResolvedContext> exports) {
    for (ResolvedContext imp : imports) {
      for (ResolvedContext exp : exports) {
        if (filtersIntersect(imp, exp)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean filtersIntersect(ResolvedContext aCtx, ResolvedContext bCtx) {
    BusState a = aCtx.state();
    BusState b = bCtx.state();
    return BusLoopGuard.filtersIntersect(
        a.mode(),
        a.filterKeys(),
        aCtx.type(),
        aCtx.inventorySideSensitive(),
        b.mode(),
        b.filterKeys(),
        bCtx.type(),
        bCtx.inventorySideSensitive());
  }

  private Optional<ResolvedContext> computeContext(BusState state) {
    if (state == null || state.mode() == BusMode.DISABLED) return Optional.empty();
    BusPos pos = state.pos();
    World world = Bukkit.getWorld(pos.world());
    if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
      return Optional.empty();
    }
    Block busBlock = world.getBlockAt(pos.x(), pos.y(), pos.z());
    if (busBlock == null) return Optional.empty();
    if (!Carriers.matchesCarrier(busBlock, busCarrier) || !dependencies.isBus(busBlock)) {
      registry.unregisterBus(state.pos());
      return Optional.empty();
    }
    var link = dependencies.findLinkedStorage(busBlock);
    if (link.count() != 1 || link.data() == null) {
      return Optional.empty();
    }
    Optional<BusTargetResolver.BusTarget> targetOpt =
        targetResolver.resolve(busBlock, state.facing());
    if (targetOpt.isEmpty()) {
      return Optional.empty();
    }
    BusTargetResolver.BusTarget target = targetOpt.get();
    BusTargetResolver.InvKey invKey = null;
    boolean sideSensitiveInventory = false;
    if (target instanceof BusTargetResolver.InventoryTarget invTarget) {
      invKey = targetResolver.inventoryKey(invTarget);
      sideSensitiveInventory = InventorySideRules.isSideSensitive(invTarget.inventory());
    }
    return Optional.of(
        new ResolvedContext(
            state,
            link.data().storageId(),
            link.data().tier(),
            target,
            state.mode(),
            state.type(),
            invKey,
            sideSensitiveInventory));
  }
}
