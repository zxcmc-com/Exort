package com.zxcmc.exort.storage;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.core.db.DbItem;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.core.keys.StorageKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class StorageCache {
    public record RemovalRequest(String key, ItemStack sample, long amount) {
    }

    public static class StorageItem {
        private final String key;
        private final ItemStack sample;
        private final long weight;
        private byte[] blob;
        private long amount;

        public StorageItem(String key, ItemStack sample, long amount, long weight, byte[] blob) {
            this.key = key;
            this.sample = sample;
            this.weight = weight;
            this.blob = blob;
            this.amount = amount;
        }

        public String key() {
            return key;
        }

        public ItemStack sample() {
            return sample;
        }

        public long weight() {
            return weight;
        }

        public long amount() {
            return amount;
        }

        public void add(long delta) {
            this.amount += delta;
        }

        public void subtract(long delta) {
            this.amount -= delta;
        }

        public byte[] blob() {
            return blob;
        }

        public void setBlob(byte[] blob) {
            this.blob = blob;
        }
    }

    private final String storageId;
    private final StorageKeys keys;
    private final ExortPlugin plugin;
    private final Map<String, StorageItem> items = new HashMap<>();
    private final Set<String> dirtyKeys = new HashSet<>();
    private final Set<String> removedKeys = new HashSet<>();
    private boolean dirty;
    private int viewers;
    private boolean loaded;
    private long totalAmount;
    // Cached "effective total" that includes nested item weights to avoid full scans on each UI refresh.
    private long totalWeighted;
    private long version;
    private SortMode sortMode = SortMode.AMOUNT;
    private long lastAccessMs;
    private long lastTouchMs;
    private String lastTouchSource;
    private static final StackWalker TOUCH_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public StorageCache(String storageId, StorageKeys keys, ExortPlugin plugin) {
        this.storageId = storageId;
        this.keys = keys;
        this.plugin = plugin;
        this.lastAccessMs = System.currentTimeMillis();
    }

    public synchronized String getStorageId() {
        return storageId;
    }

    public synchronized void loadFromDb(Map<String, DbItem> data) {
        items.clear();
        dirtyKeys.clear();
        removedKeys.clear();
        totalAmount = 0;
        totalWeighted = 0;
        version++;
        touch();
        for (DbItem item : data.values()) {
            ItemStack stack = ItemKeyUtil.deserialize(item.blob());
            long weight = nestedWeight(stack);
            items.put(item.key(), new StorageItem(item.key(), stack, item.amount(), weight, item.blob()));
            totalAmount += item.amount();
            totalWeighted += item.amount() * weight;
        }
        log(CacheDebugService.EventType.LOAD, "cache load: " + storageId + " items=" + items.size()
                + " total=" + totalAmount + " weighted=" + totalWeighted);
        dirty = false;
        loaded = true;
    }

    public synchronized int refreshCustomItems(CustomItems customItems, WirelessTerminalService wirelessService, boolean inStorage) {
        if (customItems == null) return 0;
        if (items.isEmpty()) return 0;
        Map<String, StorageItem> updated = new HashMap<>(items.size());
        Set<String> newDirty = new HashSet<>();
        Set<String> newRemoved = new HashSet<>();
        long newTotal = 0;
        long newWeighted = 0;
        int refreshed = 0;
        boolean changed = false;
        for (StorageItem item : items.values()) {
            if (item.amount() <= 0) continue;
            ItemStack sample = item.sample();
            boolean custom = customItems.isCustomItem(sample);
            boolean updatedMeta = false;
            if (custom) {
                updatedMeta = customItems.refreshItem(sample, wirelessService, inStorage);
                if (updatedMeta) {
                    refreshed++;
                    item.setBlob(null);
                }
            }
            String newKey = custom ? ItemKeyUtil.keyFor(sample) : item.key();
            long weight = nestedWeight(sample);
            StorageItem target = updated.get(newKey);
            if (target == null) {
                ItemStack clone = ItemKeyUtil.cloneSample(sample);
                StorageItem next = new StorageItem(newKey, clone, item.amount(), weight, null);
                updated.put(newKey, next);
            } else {
                target.add(item.amount());
            }
            newTotal += item.amount();
            newWeighted += item.amount() * weight;
            if (custom) {
                if (!newKey.equals(item.key())) {
                    newRemoved.add(item.key());
                    newDirty.add(newKey);
                    changed = true;
                } else if (updatedMeta) {
                    newDirty.add(newKey);
                    changed = true;
                }
            }
        }
        if (!changed) {
            return 0;
        }
        items.clear();
        items.putAll(updated);
        totalAmount = newTotal;
        totalWeighted = newWeighted;
        if (!newRemoved.isEmpty()) {
            removedKeys.addAll(newRemoved);
        }
        if (!newDirty.isEmpty()) {
            dirtyKeys.addAll(newDirty);
        }
        dirty = true;
        version++;
        return refreshed;
    }

    public synchronized SortMode getSortMode() {
        touch();
        return sortMode;
    }

    public synchronized void setSortMode(SortMode sortMode) {
        if (sortMode == null) {
            this.sortMode = SortMode.AMOUNT;
            return;
        }
        this.sortMode = sortMode;
        touch();
    }

    public synchronized boolean isLoaded() {
        return loaded;
    }

    public synchronized void addItem(String key, ItemStack sample, long amount) {
        if (amount <= 0) return;
        touch();
        if (isVerbose()) {
            log(CacheDebugService.EventType.ADD, "cache add: " + storageId + " key=" + key + " amount=" + amount, amount);
        }
        StorageItem existing = items.get(key);
        if (existing == null) {
            ItemStack sampleClone = ItemKeyUtil.sampleItem(sample);
            byte[] blob = sampleClone.serializeAsBytes();
            long weight = nestedWeight(sampleClone);
            items.put(key, new StorageItem(key, sampleClone, amount, weight, blob));
            totalWeighted += amount * weight;
        } else {
            existing.add(amount);
            totalWeighted += amount * existing.weight();
        }
        markChanged(key);
        totalAmount += amount;
        dirty = true;
        version++;
    }

    public synchronized long removeItem(String key, long amount) {
        if (amount <= 0) return 0;
        touch();
        StorageItem existing = items.get(key);
        if (existing == null) return 0;
        long removed = Math.min(amount, existing.amount());
        existing.subtract(removed);
        if (existing.amount() <= 0) {
            items.remove(key);
            markRemoved(key);
        } else {
            markChanged(key);
        }
        totalAmount -= removed;
        totalWeighted -= removed * existing.weight();
        if (removed > 0) {
            dirty = true;
            version++;
        }
        if (removed > 0 && isVerbose()) {
            log(CacheDebugService.EventType.REMOVE, "cache remove: " + storageId + " key=" + key + " removed=" + removed + " remaining=" + getAmount(key), removed);
        }
        return removed;
    }

    public synchronized long removeMatchingWireless(WirelessTerminalService ws, ItemStack sample, long amount) {
        if (ws == null || sample == null || amount <= 0) return 0;
        if (!ws.isWireless(sample)) return 0;
        touch();
        long remaining = amount;
        Iterator<Map.Entry<String, StorageItem>> it = items.entrySet().iterator();
        while (it.hasNext() && remaining > 0) {
            Map.Entry<String, StorageItem> entry = it.next();
            StorageItem storageItem = entry.getValue();
            ItemStack storedSample = storageItem.sample();
            if (!ws.isWireless(storedSample)) continue;
            if (!matchesWireless(ws, storedSample, sample)) continue;
            long remove = Math.min(remaining, storageItem.amount());
            storageItem.subtract(remove);
            if (storageItem.amount() <= 0) {
                it.remove();
                markRemoved(entry.getKey());
            } else {
                markChanged(entry.getKey());
            }
            totalAmount -= remove;
            totalWeighted -= remove * storageItem.weight();
            remaining -= remove;
            dirty = true;
            version++;
        }
        return amount - remaining;
    }

    public synchronized long countMatchingWireless(WirelessTerminalService ws, ItemStack sample) {
        if (ws == null || sample == null) return 0L;
        if (!ws.isWireless(sample)) return 0L;
        long total = 0;
        for (StorageItem storageItem : items.values()) {
            if (storageItem.amount() <= 0) continue;
            ItemStack storedSample = storageItem.sample();
            if (!ws.isWireless(storedSample)) continue;
            if (!matchesWireless(ws, storedSample, sample)) continue;
            total += storageItem.amount();
        }
        return total;
    }

    public synchronized boolean removeAll(List<RemovalRequest> requests, WirelessTerminalService ws) {
        if (requests == null || requests.isEmpty()) return true;
        for (RemovalRequest req : requests) {
            if (req == null) continue;
            long required = Math.max(0L, req.amount());
            if (required <= 0) continue;
            long available = getAmount(req.key());
            if (ws != null && req.sample() != null && ws.isWireless(req.sample())) {
                available += countMatchingWireless(ws, req.sample());
            }
            if (available < required) {
                if (isVerbose()) {
                    log(CacheDebugService.EventType.REMOVE_ALL_FAIL, "cache removeAll FAILED: " + storageId + " key=" + req.key() + " need=" + required + " available=" + available);
                }
                return false;
            }
        }
        for (RemovalRequest req : requests) {
            if (req == null) continue;
            long remaining = Math.max(0L, req.amount());
            if (remaining <= 0) continue;
            long removed = removeItem(req.key(), remaining);
            remaining -= removed;
            if (remaining > 0 && ws != null && req.sample() != null && ws.isWireless(req.sample())) {
                removeMatchingWireless(ws, req.sample(), remaining);
            }
        }
        if (isVerbose()) {
            log(CacheDebugService.EventType.REMOVE_ALL_OK, "cache removeAll OK: " + storageId + " requests=" + requests.size());
        }
        return true;
    }

    public synchronized boolean hasMatchingWireless(WirelessTerminalService ws, ItemStack sample) {
        if (ws == null || sample == null) return false;
        if (!ws.isWireless(sample)) return false;
        touch();
        for (StorageItem storageItem : items.values()) {
            if (storageItem.amount() <= 0) continue;
            ItemStack storedSample = storageItem.sample();
            if (!ws.isWireless(storedSample)) continue;
            if (matchesWireless(ws, storedSample, sample)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesWireless(WirelessTerminalService ws, ItemStack candidate, ItemStack reference) {
        String refStorage = ws.storageId(reference);
        String candStorage = ws.storageId(candidate);
        if (refStorage != null && candStorage != null && !refStorage.equals(candStorage)) return false;
        String refOwner = ws.owner(reference);
        String candOwner = ws.owner(candidate);
        if (refOwner != null && candOwner != null && !refOwner.equals(candOwner)) return false;
        return true;
    }

    public synchronized long getAmount(String key) {
        touch();
        StorageItem existing = items.get(key);
        return existing == null ? 0 : existing.amount();
    }

    public synchronized long peekAmount(String key) {
        StorageItem existing = items.get(key);
        return existing == null ? 0 : existing.amount();
    }

    public synchronized List<StorageItem> itemsSnapshot() {
        touch();
        List<StorageItem> list = new ArrayList<>(items.size());
        for (StorageItem item : items.values()) {
            if (item.amount() <= 0) continue;
            list.add(new StorageItem(item.key(), ItemKeyUtil.cloneSample(item.sample()), item.amount(), item.weight(), item.blob()));
        }
        return list;
    }

    public synchronized List<DbItem> snapshotItems() {
        touch();
        List<DbItem> snap = new ArrayList<>(items.size());
        for (StorageItem item : items.values()) {
            if (item.amount() <= 0) continue;
            byte[] blob = item.blob();
            if (blob == null) {
                blob = item.sample().serializeAsBytes();
                item.setBlob(blob);
            }
            snap.add(new DbItem(item.key(), blob, item.amount()));
        }
        return snap;
    }

    public record Snapshot(long version, List<DbItem> items) {}

    public synchronized Snapshot snapshotWithVersion() {
        return new Snapshot(version, snapshotItems());
    }

    public synchronized DeltaSnapshot snapshotDeltaWithVersion() {
        touch();
        List<DbItem> upserts = new ArrayList<>(dirtyKeys.size());
        for (String key : dirtyKeys) {
            StorageItem item = items.get(key);
            if (item == null || item.amount() <= 0) continue;
            byte[] blob = item.blob();
            if (blob == null) {
                blob = item.sample().serializeAsBytes();
                item.setBlob(blob);
            }
            upserts.add(new DbItem(item.key(), blob, item.amount()));
        }
        Set<String> removals = removedKeys.isEmpty() ? Set.of() : new HashSet<>(removedKeys);
        return new DeltaSnapshot(version, upserts, removals);
    }

    public synchronized long totalAmount() {
        touch();
        return totalAmount;
    }

    public synchronized long effectiveTotal() {
        touch();
        return totalWeighted;
    }

    public synchronized long peekEffectiveTotal() {
        return totalWeighted;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void markClean() {
        dirty = false;
        dirtyKeys.clear();
        removedKeys.clear();
        touch();
    }

    public synchronized long version() {
        touch();
        return version;
    }

    public synchronized void markCleanIfVersion(long versionAtSnapshot) {
        if (this.version == versionAtSnapshot) {
            dirty = false;
            dirtyKeys.clear();
            removedKeys.clear();
        }
        touch();
    }

    public synchronized void viewerOpened() {
        viewers++;
        touch();
    }

    public synchronized void viewerClosed() {
        if (viewers > 0) viewers--;
        touch();
    }

    public synchronized int viewerCount() {
        return viewers;
    }

    public synchronized void touch() {
        long now = System.currentTimeMillis();
        lastAccessMs = now;
        if (plugin != null) {
            var debug = plugin.getCacheDebugService();
            if (debug != null && debug.shouldTrace(storageId)) {
                lastTouchMs = now;
                lastTouchSource = captureTouchSource();
            }
        }
    }

    public synchronized long lastAccessMs() {
        return lastAccessMs;
    }

    public synchronized long lastTouchMs() {
        return lastTouchMs;
    }

    public synchronized String lastTouchSource() {
        return lastTouchSource;
    }

    private String captureTouchSource() {
        return TOUCH_WALKER.walk(frames -> frames
                .filter(frame -> {
                    Class<?> cls = frame.getDeclaringClass();
                    if (cls == null) return false;
                    String name = cls.getName();
                    return !name.equals(StorageCache.class.getName())
                            && !name.equals("com.zxcmc.exort.storage.StorageManager")
                            && !name.startsWith("java.");
                })
                .findFirst()
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                .orElse("unknown"));
    }

    private boolean isVerbose() {
        return plugin != null && plugin.getCacheDebugService() != null && plugin.getCacheDebugService().isEnabled();
    }

    private void log(CacheDebugService.EventType type, String message) {
        if (plugin == null) return;
        var debug = plugin.getCacheDebugService();
        if (debug != null) {
            debug.record(type, storageId, message);
        }
    }

    private void log(CacheDebugService.EventType type, String message, long amount) {
        if (plugin == null) return;
        var debug = plugin.getCacheDebugService();
        if (debug != null) {
            debug.record(type, storageId, message, amount);
        }
    }

    public synchronized boolean hasViewers() {
        return viewers > 0;
    }

    public long nestedWeight(ItemStack stack) {
        // Weight is at least 1 to prevent division by zero in space calculations.
        long nested = nestedCount(stack);
        long weight = 1 + nested;
        return Math.max(1, weight);
    }

    public long nestedCount(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        Long val = stack.getItemMeta().getPersistentDataContainer().get(keys.nestedCount(), PersistentDataType.LONG);
        if (val == null) return 0;
        return Math.max(0, val);
    }

    private void markChanged(String key) {
        if (key == null) return;
        removedKeys.remove(key);
        dirtyKeys.add(key);
    }

    private void markRemoved(String key) {
        if (key == null) return;
        dirtyKeys.remove(key);
        removedKeys.add(key);
    }

    public record DeltaSnapshot(long version, List<DbItem> upserts, Set<String> removals) {}
}
