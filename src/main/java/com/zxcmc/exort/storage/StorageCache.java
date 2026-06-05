package com.zxcmc.exort.storage;

import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.gui.SortMode;
import com.zxcmc.exort.infra.db.DbItem;
import com.zxcmc.exort.items.CustomItems;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class StorageCache {
  private static final int MAX_ITEM_BLOB_BYTES = 1_048_576;

  public record RemovalRequest(String key, ItemStack sample, long amount) {}

  public record ReservedItem(String key, ItemStack sample, long amount) {}

  private record RemovalPlan(
      boolean success,
      Map<String, Long> removals,
      String failedKey,
      long failedRequired,
      long failedAvailable) {
    static RemovalPlan success(Map<String, Long> removals) {
      return new RemovalPlan(true, removals, null, 0L, 0L);
    }

    static RemovalPlan failure(String failedKey, long required, long available) {
      return new RemovalPlan(false, Map.of(), failedKey, required, available);
    }
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
      this.amount = saturatingAdd(this.amount, delta);
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

  public static final class StorageItemView {
    private final String key;
    private final ItemStack sample;
    private final long amount;
    private final long weight;

    StorageItemView(String key, ItemStack sample, long amount, long weight) {
      this.key = key;
      this.sample = sample;
      this.amount = amount;
      this.weight = weight;
    }

    public String key() {
      return key;
    }

    public long amount() {
      return amount;
    }

    public long weight() {
      return weight;
    }

    public ItemStack sampleCopy() {
      return ItemKeyUtil.cloneSample(sample);
    }
  }

  private final String storageId;
  private final StorageKeys keys;
  private final Logger logger;
  private final Supplier<CacheDebugService> cacheDebugService;
  private final Map<String, StorageItem> items = new HashMap<>();
  private final Set<String> dirtyKeys = new HashSet<>();
  private final Set<String> removedKeys = new HashSet<>();
  private boolean dirty;
  private int viewers;
  private boolean loaded;
  private long totalAmount;
  // Cached "effective total" that includes nested item weights to avoid full scans on each UI
  // refresh.
  private long totalWeighted;
  private long version;
  private SortMode sortMode = SortMode.AMOUNT;
  private long lastAccessMs;
  private long lastTouchMs;
  private String lastTouchSource;
  private static final StackWalker TOUCH_WALKER =
      StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  public StorageCache(
      String storageId,
      StorageKeys keys,
      Logger logger,
      Supplier<CacheDebugService> cacheDebugService) {
    this.storageId = storageId;
    this.keys = keys;
    this.logger = logger;
    this.cacheDebugService = cacheDebugService;
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
    dirty = false;
    version++;
    touch();
    for (DbItem item : data.values()) {
      if (item == null || item.amount() <= 0) {
        warnInvalidDbItem(item, "amount must be positive");
        continue;
      }
      byte[] blob = item.blob();
      if (blob == null || blob.length == 0 || blob.length > MAX_ITEM_BLOB_BYTES) {
        warnInvalidDbItem(item, "invalid serialized item blob length");
        continue;
      }
      ItemStack stack;
      try {
        stack = ItemKeyUtil.deserialize(blob);
      } catch (RuntimeException e) {
        warnInvalidDbItem(item, "serialized item blob cannot be decoded: " + e.getMessage());
        continue;
      }
      if (stack == null || stack.getType().isAir()) {
        warnInvalidDbItem(item, "serialized item is empty");
        continue;
      }
      ItemKeyUtil.SampleData normalized = ItemKeyUtil.sampleData(stack);
      String key = item.key();
      if (key == null || key.isBlank()) {
        key = normalized.key();
        dirtyKeys.add(key);
        dirty = true;
        logInvalidDbItem(item, "missing item key; rebuilt key from item blob");
      } else if (!key.equals(normalized.key())) {
        removedKeys.add(key);
        key = normalized.key();
        dirtyKeys.add(key);
        dirty = true;
        logInvalidDbItem(item, "item key did not match blob; rebuilt key from item blob");
      }
      long weight = nestedWeight(normalized.sample());
      long weighted = saturatingMultiply(item.amount(), weight);
      if (weighted == Long.MAX_VALUE) {
        warnInvalidDbItem(item, "weighted amount is too large");
        continue;
      }
      StorageItem existing = items.get(key);
      if (existing == null) {
        items.put(
            key,
            new StorageItem(key, normalized.sample(), item.amount(), weight, normalized.bytes()));
      } else {
        existing.add(item.amount());
      }
      totalAmount = saturatingAdd(totalAmount, item.amount());
      totalWeighted = saturatingAdd(totalWeighted, weighted);
    }
    log(
        CacheDebugService.EventType.LOAD,
        "cache load: "
            + storageId
            + " items="
            + items.size()
            + " total="
            + totalAmount
            + " weighted="
            + totalWeighted);
    dirty = dirty || !dirtyKeys.isEmpty() || !removedKeys.isEmpty();
    loaded = true;
  }

  public synchronized int refreshCustomItems(
      CustomItems customItems, WirelessTerminalService wirelessService, boolean inStorage) {
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
      newTotal = saturatingAdd(newTotal, item.amount());
      newWeighted = saturatingAdd(newWeighted, saturatingMultiply(item.amount(), weight));
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
      log(
          CacheDebugService.EventType.ADD,
          "cache add: " + storageId + " key=" + key + " amount=" + amount,
          amount);
    }
    StorageItem existing = items.get(key);
    if (existing == null) {
      ItemStack sampleClone = ItemKeyUtil.sampleItem(sample);
      byte[] blob = sampleClone.serializeAsBytes();
      long weight = nestedWeight(sampleClone);
      items.put(key, new StorageItem(key, sampleClone, amount, weight, blob));
      totalWeighted = saturatingAdd(totalWeighted, saturatingMultiply(amount, weight));
    } else {
      existing.add(amount);
      totalWeighted = saturatingAdd(totalWeighted, saturatingMultiply(amount, existing.weight()));
    }
    markChanged(key);
    totalAmount = saturatingAdd(totalAmount, amount);
    dirty = true;
    version++;
  }

  public synchronized long removeItem(String key, long amount) {
    return removeItemInternal(key, amount);
  }

  public synchronized Optional<ReservedItem> reserveItem(String key, long amount) {
    if (amount <= 0) return Optional.empty();
    StorageItem existing = items.get(key);
    if (existing == null || existing.amount() <= 0) return Optional.empty();
    ItemStack sample = ItemKeyUtil.cloneSample(existing.sample());
    long removed = removeItemInternal(key, amount);
    if (removed <= 0) return Optional.empty();
    return Optional.of(new ReservedItem(key, sample, removed));
  }

  public synchronized Optional<List<ReservedItem>> reserveAll(
      List<RemovalRequest> requests, WirelessTerminalService ws) {
    if (requests == null || requests.isEmpty()) return Optional.of(List.of());
    RemovalPlan plan = planRemovals(requests, ws);
    if (!plan.success()) {
      if (isVerbose()) {
        log(
            CacheDebugService.EventType.REMOVE_ALL_FAIL,
            "cache reserveAll FAILED: "
                + storageId
                + " key="
                + plan.failedKey()
                + " need="
                + plan.failedRequired()
                + " available="
                + plan.failedAvailable());
      }
      return Optional.empty();
    }
    List<ReservedItem> reserved = new ArrayList<>(plan.removals().size());
    for (Map.Entry<String, Long> entry : plan.removals().entrySet()) {
      Optional<ReservedItem> item = reserveItem(entry.getKey(), entry.getValue());
      if (item.isEmpty() || item.get().amount() != entry.getValue()) {
        item.ifPresent(reserved::add);
        for (ReservedItem rollback : reserved) {
          addItem(rollback.key(), rollback.sample(), rollback.amount());
        }
        if (isVerbose()) {
          log(
              CacheDebugService.EventType.REMOVE_ALL_FAIL,
              "cache reserveAll FAILED during apply: "
                  + storageId
                  + " key="
                  + entry.getKey()
                  + " need="
                  + entry.getValue()
                  + " reserved="
                  + item.map(ReservedItem::amount).orElse(0L));
        }
        return Optional.empty();
      }
      reserved.add(item.get());
    }
    if (isVerbose()) {
      log(
          CacheDebugService.EventType.REMOVE_ALL_OK,
          "cache reserveAll OK: " + storageId + " requests=" + requests.size());
    }
    return Optional.of(reserved);
  }

  private long removeItemInternal(String key, long amount) {
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
    totalAmount = Math.max(0L, totalAmount - removed);
    totalWeighted = Math.max(0L, totalWeighted - saturatingMultiply(removed, existing.weight()));
    if (removed > 0) {
      dirty = true;
      version++;
    }
    if (removed > 0 && isVerbose()) {
      log(
          CacheDebugService.EventType.REMOVE,
          "cache remove: "
              + storageId
              + " key="
              + key
              + " removed="
              + removed
              + " remaining="
              + getAmount(key),
          removed);
    }
    return removed;
  }

  public synchronized long removeMatchingWireless(
      WirelessTerminalService ws, ItemStack sample, long amount) {
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
      totalAmount = Math.max(0L, totalAmount - remove);
      totalWeighted =
          Math.max(0L, totalWeighted - saturatingMultiply(remove, storageItem.weight()));
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
    return reserveAll(requests, ws).isPresent();
  }

  private RemovalPlan planRemovals(List<RemovalRequest> requests, WirelessTerminalService ws) {
    Map<String, Long> availableByKey = new HashMap<>(items.size());
    for (Map.Entry<String, StorageItem> entry : items.entrySet()) {
      StorageItem item = entry.getValue();
      if (item != null && item.amount() > 0) {
        availableByKey.put(entry.getKey(), item.amount());
      }
    }

    Map<String, Long> removals = new LinkedHashMap<>();
    for (RemovalRequest req : requests) {
      if (req == null) continue;
      long remaining = Math.max(0L, req.amount());
      if (remaining <= 0) continue;

      long required = remaining;
      long allocated = 0;
      if (req.key() != null) {
        allocated += allocateRemoval(req.key(), remaining, availableByKey, removals);
        remaining -= allocated;
      }

      if (remaining > 0 && ws != null && req.sample() != null && ws.isWireless(req.sample())) {
        for (Map.Entry<String, StorageItem> entry : items.entrySet()) {
          String key = entry.getKey();
          Long available = availableByKey.get(key);
          if (available == null || available <= 0) continue;
          StorageItem storageItem = entry.getValue();
          if (storageItem == null || storageItem.amount() <= 0) continue;
          ItemStack storedSample = storageItem.sample();
          if (!ws.isWireless(storedSample)) continue;
          if (!matchesWireless(ws, storedSample, req.sample())) continue;
          long take = allocateRemoval(key, remaining, availableByKey, removals);
          remaining -= take;
          allocated += take;
          if (remaining <= 0) break;
        }
      }

      if (remaining > 0) {
        return RemovalPlan.failure(req.key(), required, allocated);
      }
    }
    return RemovalPlan.success(removals);
  }

  private long allocateRemoval(
      String key, long requested, Map<String, Long> availableByKey, Map<String, Long> removals) {
    if (key == null || requested <= 0) return 0;
    long available = Math.max(0L, availableByKey.getOrDefault(key, 0L));
    long take = Math.min(requested, available);
    if (take <= 0) return 0;
    availableByKey.put(key, available - take);
    Long planned = removals.get(key);
    removals.put(key, (planned == null ? 0L : planned) + take);
    return take;
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

  private boolean matchesWireless(
      WirelessTerminalService ws, ItemStack candidate, ItemStack reference) {
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
      list.add(
          new StorageItem(
              item.key(),
              ItemKeyUtil.cloneSample(item.sample()),
              item.amount(),
              item.weight(),
              item.blob()));
    }
    return list;
  }

  public synchronized List<StorageItemView> itemViewsSnapshot() {
    touch();
    List<StorageItemView> list = new ArrayList<>(items.size());
    for (StorageItem item : items.values()) {
      if (item.amount() <= 0) continue;
      list.add(new StorageItemView(item.key(), item.sample(), item.amount(), item.weight()));
    }
    return list;
  }

  public synchronized List<DbItem> snapshotItems() {
    touch();
    List<DbItem> snap = new ArrayList<>(items.size());
    for (StorageItem item : items.values()) {
      if (item.amount() <= 0) continue;
      byte[] blob = blobFor(item);
      if (blob == null) continue;
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
      byte[] blob = blobFor(item);
      if (blob == null) continue;
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
    var debug = cacheDebugService();
    if (debug != null && debug.shouldTrace(storageId)) {
      lastTouchMs = now;
      lastTouchSource = captureTouchSource();
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
    return TOUCH_WALKER.walk(
        frames ->
            frames
                .filter(
                    frame -> {
                      Class<?> cls = frame.getDeclaringClass();
                      if (cls == null) return false;
                      String name = cls.getName();
                      return !name.equals(StorageCache.class.getName())
                          && !name.equals(StorageManager.class.getName())
                          && !name.startsWith("java.");
                    })
                .findFirst()
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                .orElse("unknown"));
  }

  private boolean isVerbose() {
    var debug = cacheDebugService();
    return debug != null && debug.isEnabled();
  }

  private void log(CacheDebugService.EventType type, String message) {
    var debug = cacheDebugService();
    if (debug != null) {
      debug.record(type, storageId, message);
    }
  }

  private void log(CacheDebugService.EventType type, String message, long amount) {
    var debug = cacheDebugService();
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
    if (nested >= Long.MAX_VALUE - 1) {
      return Long.MAX_VALUE;
    }
    return Math.max(1, 1 + nested);
  }

  public long nestedCount(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return 0;
    Long val =
        stack
            .getItemMeta()
            .getPersistentDataContainer()
            .get(keys.nestedCount(), PersistentDataType.LONG);
    if (val == null) return 0;
    return Math.max(0, val);
  }

  private byte[] blobFor(StorageItem item) {
    byte[] blob = item.blob();
    if (blob != null && blob.length > 0 && blob.length <= MAX_ITEM_BLOB_BYTES) {
      return blob;
    }
    try {
      blob = item.sample().serializeAsBytes();
      if (blob == null || blob.length == 0 || blob.length > MAX_ITEM_BLOB_BYTES) {
        logInvalidDbItem(
            new DbItem(item.key(), blob, item.amount()), "serialized blob size invalid");
        return null;
      }
      item.setBlob(blob);
      return blob;
    } catch (RuntimeException e) {
      logInvalidDbItem(
          new DbItem(item.key(), null, item.amount()),
          "failed to serialize item sample: " + e.getMessage());
      return null;
    }
  }

  private void warnInvalidDbItem(DbItem item, String reason) {
    logInvalidDbItem(item, "skipping invalid storage item: " + reason);
  }

  private void logInvalidDbItem(DbItem item, String message) {
    if (logger == null) return;
    String key = item == null ? "<null>" : String.valueOf(item.key());
    long amount = item == null ? 0L : item.amount();
    logger.warning(
        "Storage " + storageId + ": " + message + " (key=" + key + ", amount=" + amount + ")");
  }

  private CacheDebugService cacheDebugService() {
    return cacheDebugService == null ? null : cacheDebugService.get();
  }

  private static long saturatingAdd(long left, long right) {
    if (right <= 0) return left;
    if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
    return left + right;
  }

  private static long saturatingMultiply(long left, long right) {
    if (left <= 0 || right <= 0) return 0L;
    if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
    return left * right;
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
