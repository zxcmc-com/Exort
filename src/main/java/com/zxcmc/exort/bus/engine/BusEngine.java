package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.bus.InventorySideRules;
import com.zxcmc.exort.bus.loop.BusLoopGuard;
import com.zxcmc.exort.bus.resolver.BusTargetResolver;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.storage.StorageTier;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BusEngine implements Runnable {
    private static final int CRAFT_PAUSE_TICKS = 2;

    private final ExortPlugin plugin;
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

    private int taskId = -1;
    private int roundRobinIndex;
    private long chunkOpTick = Long.MIN_VALUE;
    private final Map<ChunkKey, Integer> chunkOps = new HashMap<>();
    private final Map<BusPos, ResolvedContext> contextCache = new HashMap<>();
    private long contextTick = Long.MIN_VALUE;
    private final Set<BusPos> loopDisabled = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> craftPausedUntil = new ConcurrentHashMap<>();

    public BusEngine(ExortPlugin plugin,
                     StorageManager storageManager,
                     Material busCarrier,
                     int activeIntervalTicks,
                     int idleIntervalTicks,
                     int itemsPerOperation,
                     int maxOperationsPerTick,
                     int maxOperationsPerChunk,
                     boolean allowStorageTargets,
                     WirelessTerminalService wirelessService,
                     BusRegistry registry) {
        this.plugin = plugin;
        this.storageManager = storageManager;
        this.busCarrier = busCarrier;
        this.maxOperationsPerChunk = Math.max(0, maxOperationsPerChunk);
        this.activeIntervalTicks = Math.max(1, activeIntervalTicks);
        this.idleIntervalTicks = Math.max(1, idleIntervalTicks);
        this.itemsPerOperation = Math.max(1, itemsPerOperation);
        this.maxOperationsPerTick = Math.max(1, maxOperationsPerTick);
        this.wirelessService = wirelessService;
        this.registry = registry;
        this.targetResolver = new BusTargetResolver(plugin, allowStorageTargets);
    }

    public void start() {
        if (taskId != -1) return;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 1L, 1L);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        chunkOps.clear();
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
        List<BusState> list = registry.snapshotList();
        if (list.isEmpty()) return;
        int opsLeft = maxOperationsPerTick;
        long tick = Bukkit.getCurrentTick();
        if (chunkOpTick != tick) {
            chunkOps.clear();
            chunkOpTick = tick;
        }
        buildContextCache(list, tick);
        Set<String> changedStorages = new HashSet<>();
        int size = list.size();
        int start = roundRobinIndex % size;
        for (int i = 0; i < size && opsLeft > 0; i++) {
            int idx = (start + i) % size;
            BusState state = list.get(idx);
            if (state == null) continue;
            if (state.viewers() > 0) {
                state.setNextTick(tick + idleIntervalTicks);
                continue;
            }
            if (tick < state.nextTick()) continue;
            if (maxOperationsPerChunk > 0 && isChunkLimitReached(state.pos())) {
                state.setNextTick(tick + idleIntervalTicks);
                continue;
            }
            boolean moved = tickBus(state, tick, changedStorages);
            state.setNextTick(tick + (moved ? activeIntervalTicks : idleIntervalTicks));
            if (moved) {
                opsLeft--;
                if (maxOperationsPerChunk > 0) {
                    incrementChunkOps(state.pos());
                }
            }
        }
        roundRobinIndex = (start + 1) % size;
        if (!changedStorages.isEmpty()) {
            for (String storageId : changedStorages) {
                plugin.getSessionManager().renderStorage(storageId, com.zxcmc.exort.gui.SortEvent.NONE);
            }
        }
    }

    private boolean tickBus(BusState state, long tick, Set<String> changedStorages) {
        if (loopDisabled.contains(state.pos())) return false;
        ResolvedContext ctx = contextCache.computeIfAbsent(state.pos(), pos -> computeContext(state).orElse(null));
        if (ctx == null) return false;
        String storageId = ctx.storageId();
        StorageTier tier = ctx.tier();
        BusTargetResolver.BusTarget target = ctx.target();
        if (storageId == null || target == null || tier == null) return false;
        if (state.type() == BusType.EXPORT && isPaused(storageId, tick)) return false;
        if (state.type() == BusType.IMPORT && target instanceof BusTargetResolver.StorageTarget storageTarget
                && isPaused(storageTarget.storageId(), tick)) {
            return false;
        }
        StorageCache cache = storageManager.getLoadedCache(storageId).orElse(null);
        if (cache == null || !cache.isLoaded()) {
            storageManager.getOrLoad(storageId);
            return false;
        }
        return state.type() == BusType.IMPORT
                ? tickImport(state, cache, tier, target, changedStorages, tick)
                : tickExport(state, cache, target, changedStorages, tick);
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

    private boolean tickImport(BusState state, StorageCache cache, StorageTier tier, BusTargetResolver.BusTarget target, Set<String> changedStorages, long tick) {
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
                storageManager.getOrLoad(storageTarget.storageId());
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

    private boolean tickExport(BusState state, StorageCache cache, BusTargetResolver.BusTarget target, Set<String> changedStorages, long tick) {
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
                storageManager.getOrLoad(storageTarget.storageId());
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

    private boolean tickImportInventory(BusState state, StorageCache cache, StorageTier tier, BusTargetResolver.InventoryTarget target, long tick) {
        Inventory inventory = target.inventory();
        int[] slots = InventorySideRules.extractSlots(inventory, target.state(), target.side());
        if (slots.length == 0) return false;
        int start = Math.floorMod(state.slotCursor(), slots.length);
        for (int i = 0; i < slots.length; i++) {
            int idx = (start + i) % slots.length;
            int slot = slots[idx];
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!InventorySideRules.canExtract(inventory, target.state(), target.side(), stack, slot)) continue;
            if (wirelessService != null && wirelessService.isWireless(stack)) {
                wirelessService.prepareForStorage(stack);
            }
            ItemKeyUtil.SampleData data = ItemKeyUtil.sampleData(stack);
            if (!allows(state.mode(), data.key(), state.filterKeys())) {
                continue;
            }
            long space = tier.maxItems() - cache.effectiveTotal();
            if (space <= 0) {
                return false;
            }
            long weight = cache.nestedWeight(stack);
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
            state.setSlotCursor(idx + 1);
            return true;
        }
        state.setSlotCursor(start);
        return false;
    }

    private boolean tickExportInventory(BusState state, StorageCache cache, BusTargetResolver.InventoryTarget target, long tick) {
        Inventory inventory = target.inventory();
        List<StorageCache.StorageItem> list = cachedStorageList(state, cache);
        if (list.isEmpty()) return false;
        int start = Math.floorMod(state.storageCursor(), list.size());
        for (int i = 0; i < list.size(); i++) {
            int idx = (start + i) % list.size();
            StorageCache.StorageItem entry = list.get(idx);
            if (entry.amount() <= 0) continue;
            if (!allows(state.mode(), entry.key(), state.filterKeys())) continue;
            int desired = Math.min(itemsPerOperation, (int) Math.min(Integer.MAX_VALUE, entry.amount()));
            if (desired <= 0) continue;
            long removed = cache.removeItem(entry.key(), desired);
            if (removed <= 0) continue;
            int moved = InventorySideRules.insert(inventory, target.state(), target.side(), entry.sample(), entry.key(), (int) removed);
            if (moved < removed) {
                cache.addItem(entry.key(), entry.sample(), removed - moved);
            }
            if (moved > 0) {
                state.setStorageCursor(idx + 1);
                return true;
            }
        }
        state.setStorageCursor(start);
        return false;
    }

    private boolean tickStorageTransfer(BusState state, StorageCache source, StorageCache dest, StorageTier destTier) {
        if (destTier == null) return false;
        List<StorageCache.StorageItem> list = cachedStorageList(state, source);
        if (list.isEmpty()) return false;
        int start = Math.floorMod(state.storageCursor(), list.size());
        for (int i = 0; i < list.size(); i++) {
            int idx = (start + i) % list.size();
            StorageCache.StorageItem entry = list.get(idx);
            if (entry.amount() <= 0) continue;
            if (!allows(state.mode(), entry.key(), state.filterKeys())) continue;
            int desired = Math.min(itemsPerOperation, (int) Math.min(Integer.MAX_VALUE, entry.amount()));
            if (desired <= 0) continue;
            long space = destTier.maxItems() - dest.effectiveTotal();
            if (space <= 0) {
                return false;
            }
            long weight = dest.nestedWeight(entry.sample());
            long maxBySpace = Math.max(0, space / Math.max(1, weight));
            if (maxBySpace <= 0) {
                return false;
            }
            int move = (int) Math.min(desired, maxBySpace);
            if (move <= 0) continue;
            long removed = source.removeItem(entry.key(), move);
            if (removed <= 0) continue;
            dest.addItem(entry.key(), entry.sample(), removed);
            state.setStorageCursor(idx + 1);
            return true;
        }
        state.setStorageCursor(start);
        return false;
    }

    private List<StorageCache.StorageItem> cachedStorageList(BusState state, StorageCache cache) {
        long version = cache.version();
        if (state.cachedItems() == null
                || state.cachedStorageVersion() != version
                || !cache.getStorageId().equals(state.cachedStorageId())) {
            List<StorageCache.StorageItem> fresh = cache.itemsSnapshot();
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

    private record ResolvedContext(BusState state,
                                   String storageId,
                                   StorageTier tier,
                                   BusTargetResolver.BusTarget target,
                                   BusMode mode,
                                   BusType type,
                                   BusTargetResolver.InvKey invKey,
                                   boolean inventorySideSensitive) {
    }

    private record StorageInvKey(String storageId, BusTargetResolver.InvKey invKey) {
    }

    private void buildContextCache(List<BusState> list, long tick) {
        if (contextTick != tick) {
            contextCache.clear();
            contextTick = tick;
        }
        loopDisabled.clear();
        Map<StorageInvKey, List<ResolvedContext>> grouped = new HashMap<>();
        for (BusState state : list) {
            ResolvedContext ctx = contextCache.computeIfAbsent(state.pos(), pos -> computeContext(state).orElse(null));
            if (ctx == null || ctx.invKey() == null || ctx.storageId() == null) continue;
            StorageInvKey key = new StorageInvKey(ctx.storageId(), ctx.invKey());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(ctx);
        }
        for (var entry : grouped.entrySet()) {
            List<ResolvedContext> group = entry.getValue();
            if (group.stream().anyMatch(ResolvedContext::inventorySideSensitive)) {
                continue;
            }
            List<ResolvedContext> imports = group.stream().filter(c -> c.type() == BusType.IMPORT).toList();
            List<ResolvedContext> exports = group.stream().filter(c -> c.type() == BusType.EXPORT).toList();
            if (imports.isEmpty() || exports.isEmpty()) continue;
            boolean allAllConflict = hasAllAllConflict(group);
            boolean filtersConflict = hasIntersectingFilters(imports, exports);
            if (!allAllConflict && !filtersConflict) continue;
            for (ResolvedContext exp : exports) {
                loopDisabled.add(exp.state().pos());
            }
        }
    }

    private boolean hasAllAllConflict(List<ResolvedContext> group) {
        boolean importAll = group.stream().anyMatch(c -> c.type() == BusType.IMPORT && c.mode() == BusMode.ALL && !c.inventorySideSensitive());
        boolean exportAll = group.stream().anyMatch(c -> c.type() == BusType.EXPORT && c.mode() == BusMode.ALL);
        return importAll && exportAll;
    }

    private boolean hasIntersectingFilters(List<ResolvedContext> imports, List<ResolvedContext> exports) {
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
                a.mode(), a.filterKeys(), aCtx.type(), aCtx.inventorySideSensitive(),
                b.mode(), b.filterKeys(), bCtx.type(), bCtx.inventorySideSensitive()
        );
    }

    private Optional<ResolvedContext> computeContext(BusState state) {
        if (state == null || state.mode() == BusMode.DISABLED) return Optional.empty();
        BusPos pos = state.pos();
        org.bukkit.World world = Bukkit.getWorld(pos.world());
        if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return Optional.empty();
        }
        Block busBlock = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (busBlock == null) return Optional.empty();
        if (!Carriers.matchesCarrier(busBlock, busCarrier) || !BusMarker.isBus(plugin, busBlock)) {
            registry.unregisterBus(state.pos());
            return Optional.empty();
        }
        TerminalLinkFinder.StorageSearchResult link = TerminalLinkFinder.find(
                busBlock,
                plugin.getKeys(),
                plugin,
                plugin.getWireLimit(),
                plugin.getWireHardCap(),
                plugin.getWireMaterial(),
                plugin.getStorageCarrier()
        );
        if (link.count() != 1 || link.data() == null) {
            return Optional.empty();
        }
        Optional<BusTargetResolver.BusTarget> targetOpt = targetResolver.resolve(busBlock, state.facing());
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
        return Optional.of(new ResolvedContext(state, link.data().storageId(), link.data().tier(), target, state.mode(), state.type(), invKey, sideSensitiveInventory));
    }

    private boolean isChunkLimitReached(BusPos pos) {
        if (pos == null || maxOperationsPerChunk <= 0) return false;
        ChunkKey key = new ChunkKey(pos.world(), pos.x() >> 4, pos.z() >> 4);
        int used = chunkOps.getOrDefault(key, 0);
        return used >= maxOperationsPerChunk;
    }

    private void incrementChunkOps(BusPos pos) {
        if (pos == null || maxOperationsPerChunk <= 0) return;
        ChunkKey key = new ChunkKey(pos.world(), pos.x() >> 4, pos.z() >> 4);
        int used = chunkOps.getOrDefault(key, 0);
        chunkOps.put(key, used + 1);
    }

    private record ChunkKey(java.util.UUID world, int x, int z) {
    }
}
