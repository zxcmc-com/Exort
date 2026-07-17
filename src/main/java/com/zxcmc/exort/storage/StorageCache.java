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
  private static final int MAX_ITEM_BLOB_BYTES = StoredItemCodec.MAX_BLOB_BYTES;

  public record RemovalRequest(String key, ItemStack sample, long amount) {}

  public record LoadResult(List<StorageQuarantineEntry> quarantineEntries) {
    public LoadResult {
      quarantineEntries = quarantineEntries == null ? List.of() : List.copyOf(quarantineEntries);
    }
  }

  public static final class ReservedItem {
    private final String key;
    private final ItemStack sample;
    private final long amount;
    private final long weight;
    private final byte[] blob;
    private boolean restoreClaimed;

    private ReservedItem(String key, ItemStack sample, long amount, long weight, byte[] blob) {
      this.key = key;
      this.sample = sample;
      this.amount = amount;
      this.weight = weight;
      this.blob = blob == null ? null : Arrays.copyOf(blob, blob.length);
    }

    public String key() {
      return key;
    }

    public ItemStack sample() {
      return ItemKeyUtil.cloneSample(sample);
    }

    public long amount() {
      return amount;
    }

    private synchronized boolean claimRestore(long requested) {
      if (restoreClaimed || requested <= 0 || requested > amount) {
        return false;
      }
      restoreClaimed = true;
      return true;
    }
  }

  public record AddResult(
      boolean accepted, String key, StoredItemCodec.Failure failure, String detail) {
    public AddResult {
      Objects.requireNonNull(failure, "failure");
      detail = detail == null ? "" : detail;
    }

    static AddResult accepted(String key) {
      return new AddResult(true, key, StoredItemCodec.Failure.NONE, "");
    }

    static AddResult rejected(StoredItemCodec.Failure failure, String detail) {
      return new AddResult(false, null, failure, detail);
    }
  }

  private record AddPlan(
      StorageItem existing,
      StoredItemCodec.PreparedItem prepared,
      String key,
      long amount,
      long weight) {}

  private record AddPlanning(AddPlan plan, AddResult result) {
    static AddPlanning accepted(AddPlan plan) {
      return new AddPlanning(Objects.requireNonNull(plan, "plan"), null);
    }

    static AddPlanning rejected(AddResult result) {
      return new AddPlanning(null, Objects.requireNonNull(result, "result"));
    }
  }

  public static final class PreparedAdd {
    private final StorageCache owner;
    private final AddPlan plan;
    private final AddResult result;
    private final long version;
    private boolean consumed;

    private PreparedAdd(StorageCache owner, AddPlan plan, AddResult result, long version) {
      this.owner = owner;
      this.plan = plan;
      this.result = result;
      this.version = version;
    }

    public AddResult result() {
      return result;
    }

    public boolean accepted() {
      return result.accepted();
    }
  }

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
  private final StoredItemCodec storedItemCodec;
  private final Map<String, StorageItem> items = new HashMap<>();
  private final Set<String> dirtyKeys = new HashSet<>();
  private final Set<String> removedKeys = new HashSet<>();
  private List<StorageCorruption> corruptions = List.of();
  private boolean dirty;
  private int viewers;
  private boolean loaded;
  private long totalAmount;
  // Cached "effective total" that includes nested item weights to avoid full scans on each UI
  // refresh.
  private long totalWeighted;
  private long version;
  private SortMode sortMode = SortMode.AMOUNT;
  private String displayName;
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
    this(storageId, keys, logger, cacheDebugService, new StoredItemCodec());
  }

  public StorageCache(
      String storageId,
      StorageKeys keys,
      Logger logger,
      Supplier<CacheDebugService> cacheDebugService,
      StoredItemCodec storedItemCodec) {
    this.storageId = storageId;
    this.keys = keys;
    this.logger = logger;
    this.cacheDebugService = cacheDebugService;
    this.storedItemCodec = Objects.requireNonNull(storedItemCodec, "storedItemCodec");
    this.lastAccessMs = System.currentTimeMillis();
  }

  public synchronized String getStorageId() {
    return storageId;
  }

  public synchronized LoadResult loadFromDb(Map<String, DbItem> data) {
    return loadFromDb(data, List.of());
  }

  public synchronized LoadResult loadFromDb(
      Map<String, DbItem> data, Collection<StorageCorruption> structuralCorruptions) {
    items.clear();
    dirtyKeys.clear();
    removedKeys.clear();
    List<StorageCorruption> detected =
        structuralCorruptions == null ? new ArrayList<>() : new ArrayList<>(structuralCorruptions);
    List<StorageQuarantineEntry> quarantineEntries = new ArrayList<>();
    totalAmount = 0;
    totalWeighted = 0;
    dirty = false;
    version++;
    touch();
    Map<String, DbItem> safeData = data == null ? Map.of() : data;
    for (DbItem item : safeData.values()) {
      if (item == null || item.amount() <= 0) {
        recordCorruption(
            item,
            "amount must be positive",
            detected,
            quarantineEntries,
            System.currentTimeMillis());
        continue;
      }
      byte[] blob = item.blob();
      if (blob == null || blob.length == 0 || blob.length > MAX_ITEM_BLOB_BYTES) {
        recordCorruption(
            item,
            "invalid serialized item blob length",
            detected,
            quarantineEntries,
            System.currentTimeMillis());
        continue;
      }
      StoredItemCodec.Preflight persisted = storedItemCodec.decodePersisted(item.key(), blob);
      if (!persisted.accepted()) {
        recordCorruption(
            item,
            "persisted item rejected (" + persisted.failure() + "): " + persisted.detail(),
            detected,
            quarantineEntries,
            System.currentTimeMillis());
        continue;
      }
      StoredItemCodec.PreparedItem prepared = persisted.item();
      String key = prepared.key();
      long weight;
      try {
        weight = nestedWeight(prepared.internalSample());
      } catch (RuntimeException e) {
        recordCorruption(
            item,
            "item weight cannot be read: " + failureDetail(e),
            detected,
            quarantineEntries,
            System.currentTimeMillis());
        continue;
      }
      long weighted = saturatingMultiply(item.amount(), weight);
      if (weight <= 0
          || weight == Long.MAX_VALUE
          || weighted == Long.MAX_VALUE
          || wouldOverflow(totalAmount, item.amount())
          || wouldOverflow(totalWeighted, weighted)) {
        recordCorruption(
            item,
            "item amount or nested weight would overflow storage totals",
            detected,
            quarantineEntries,
            System.currentTimeMillis());
        continue;
      }
      StorageItem existing = items.get(key);
      if (existing == null) {
        items.put(
            key,
            new StorageItem(
                key, prepared.internalSample(), item.amount(), weight, prepared.internalBlob()));
      } else {
        existing.add(item.amount());
      }
      if (!key.equals(item.key())) {
        removedKeys.add(item.key());
        dirtyKeys.add(key);
        dirty = true;
        if (logger != null) {
          logger.warning(
              "Storage "
                  + storageId
                  + " normalized a version-sensitive item representation; the previous row key"
                  + " will be replaced on the next flush.");
        }
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
    corruptions = List.copyOf(detected);
    dirty = dirty || !dirtyKeys.isEmpty() || !removedKeys.isEmpty();
    loaded = true;
    if (!corruptions.isEmpty()) {
      String reasons = degradationReasonSummary();
      if (logger != null) {
        logger.severe(
            "Storage "
                + storageId
                + " loaded in DEGRADED_READ_ONLY state; corruptRows="
                + corruptions.size()
                + ", reasons="
                + reasons
                + ". Original rows were retained and quarantined for recovery.");
      }
      log(
          CacheDebugService.EventType.LOAD,
          "cache load DEGRADED_READ_ONLY: "
              + storageId
              + " corruptRows="
              + corruptions.size()
              + " reasons="
              + reasons);
    }
    return new LoadResult(quarantineEntries);
  }

  public synchronized int refreshCustomItems(
      CustomItems customItems, WirelessTerminalService wirelessService, boolean inStorage) {
    if (isReadOnly()) return 0;
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

  public synchronized String getDisplayName() {
    touch();
    return displayName;
  }

  public synchronized void setDisplayName(String displayName) {
    String normalized = StorageDisplayName.normalize(displayName);
    if (Objects.equals(this.displayName, normalized)) {
      touch();
      return;
    }
    this.displayName = normalized;
    version++;
    touch();
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

  public synchronized boolean isDegraded() {
    return !corruptions.isEmpty();
  }

  public synchronized boolean isReadOnly() {
    return isDegraded();
  }

  public synchronized List<StorageCorruption> corruptionSnapshot() {
    return corruptions;
  }

  public synchronized String healthSummary() {
    if (corruptions.isEmpty()) {
      return "HEALTHY";
    }
    return "DEGRADED_READ_ONLY corruptRows="
        + corruptions.size()
        + " reasons="
        + degradationReasonSummary();
  }

  public synchronized void addItem(String key, ItemStack sample, long amount) {
    AddResult result = tryAddItem(key, sample, amount);
    if (!result.accepted()) {
      throw new IllegalArgumentException(
          "Storage "
              + storageId
              + ": rejected item add (failure="
              + result.failure()
              + ", detail="
              + result.detail()
              + ")");
    }
  }

  public synchronized AddResult preflightItem(String key, ItemStack sample, long amount) {
    return prepareAdd(key, sample, amount).result();
  }

  public synchronized AddResult tryAddItem(String key, ItemStack sample, long amount) {
    return commitPrepared(prepareAdd(key, sample, amount));
  }

  public synchronized PreparedAdd prepareAdd(String key, ItemStack sample, long amount) {
    AddPlanning planning = planAdd(key, sample, amount);
    AddResult result =
        planning.result() == null ? AddResult.accepted(planning.plan().key()) : planning.result();
    return new PreparedAdd(this, planning.plan(), result, version);
  }

  public synchronized AddResult commitPrepared(PreparedAdd prepared) {
    if (prepared == null || prepared.owner != this || prepared.consumed) {
      return AddResult.rejected(
          StoredItemCodec.Failure.STALE_PREFLIGHT, "preflight token is invalid or already used");
    }
    prepared.consumed = true;
    if (!prepared.result.accepted()) {
      return prepared.result;
    }
    if (isReadOnly()) {
      return readOnlyAddResult();
    }
    if (prepared.version != version) {
      return AddResult.rejected(
          StoredItemCodec.Failure.STALE_PREFLIGHT, "storage changed after item preflight");
    }
    applyAdd(prepared.plan);
    return AddResult.accepted(prepared.plan.key());
  }

  private void applyAdd(AddPlan plan) {
    touch();
    if (isVerbose()) {
      log(
          CacheDebugService.EventType.ADD,
          "cache add: " + storageId + " key=" + plan.key() + " amount=" + plan.amount(),
          plan.amount());
    }
    StorageItem existing = plan.existing();
    if (existing == null) {
      StoredItemCodec.PreparedItem prepared = plan.prepared();
      items.put(
          plan.key(),
          new StorageItem(
              plan.key(),
              prepared.internalSample(),
              plan.amount(),
              plan.weight(),
              prepared.internalBlob()));
    } else {
      existing.add(plan.amount());
    }
    totalWeighted = saturatingAdd(totalWeighted, saturatingMultiply(plan.amount(), plan.weight()));
    markChanged(plan.key());
    totalAmount = saturatingAdd(totalAmount, plan.amount());
    dirty = true;
    version++;
  }

  private AddPlanning planAdd(String key, ItemStack sample, long amount) {
    if (isReadOnly()) {
      return AddPlanning.rejected(readOnlyAddResult());
    }
    if (amount <= 0) {
      return AddPlanning.rejected(
          AddResult.rejected(StoredItemCodec.Failure.INVALID_AMOUNT, "amount must be positive"));
    }
    StorageItem existing = key == null ? null : items.get(key);
    StoredItemCodec.PreparedItem prepared = null;
    String resolvedKey = key;
    long weight;
    if (existing == null) {
      StoredItemCodec.Preflight preflight = storedItemCodec.preflight(key, sample);
      if (!preflight.accepted()) {
        return AddPlanning.rejected(AddResult.rejected(preflight.failure(), preflight.detail()));
      }
      prepared = preflight.item();
      resolvedKey = prepared.key();
      try {
        weight = nestedWeight(prepared.internalSample());
      } catch (RuntimeException error) {
        return AddPlanning.rejected(
            AddResult.rejected(
                StoredItemCodec.Failure.INVALID_WEIGHT,
                error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())));
      }
    } else {
      weight = existing.weight();
    }
    if (weight <= 0
        || weight == Long.MAX_VALUE
        || wouldOverflow(totalAmount, amount)
        || wouldOverflow(existing == null ? 0L : existing.amount(), amount)
        || wouldMultiplyOverflow(amount, weight)
        || wouldOverflow(totalWeighted, amount * weight)) {
      return AddPlanning.rejected(
          AddResult.rejected(
              StoredItemCodec.Failure.COUNT_OVERFLOW,
              "item count or nested weight would overflow"));
    }
    return AddPlanning.accepted(new AddPlan(existing, prepared, resolvedKey, amount, weight));
  }

  public synchronized long removeItem(String key, long amount) {
    if (isReadOnly()) return 0L;
    return removeItemInternal(key, amount);
  }

  public synchronized Optional<ReservedItem> reserveItem(String key, long amount) {
    if (isReadOnly()) return Optional.empty();
    if (amount <= 0) return Optional.empty();
    StorageItem existing = items.get(key);
    if (existing == null || existing.amount() <= 0) return Optional.empty();
    ItemStack sample = ItemKeyUtil.cloneSample(existing.sample());
    long weight = existing.weight();
    byte[] blob = existing.blob();
    long removed = removeItemInternal(key, amount);
    if (removed <= 0) return Optional.empty();
    return Optional.of(new ReservedItem(key, sample, removed, weight, blob));
  }

  public synchronized void restoreReserved(ReservedItem reserved) {
    restoreReserved(reserved, reserved == null ? 0L : reserved.amount);
  }

  public synchronized void restoreReserved(ReservedItem reserved, long amount) {
    if (isReadOnly()) return;
    if (reserved == null || !reserved.claimRestore(amount)) return;
    StorageItem existing = items.get(reserved.key);
    if (existing == null) {
      items.put(
          reserved.key,
          new StorageItem(
              reserved.key,
              reserved.sample,
              amount,
              reserved.weight,
              reserved.blob == null ? null : Arrays.copyOf(reserved.blob, reserved.blob.length)));
    } else {
      existing.add(amount);
    }
    totalAmount = saturatingAdd(totalAmount, amount);
    totalWeighted = saturatingAdd(totalWeighted, saturatingMultiply(amount, reserved.weight));
    markChanged(reserved.key);
    dirty = true;
    version++;
    touch();
  }

  public synchronized Optional<List<ReservedItem>> reserveAll(
      List<RemovalRequest> requests, WirelessTerminalService ws) {
    if (isReadOnly()) return Optional.empty();
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
          restoreReserved(rollback);
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
    if (isReadOnly()) return 0L;
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
    if (isReadOnly()) return 0L;
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
      DbItem dbItem = runtimeDbItem(item);
      if (dbItem != null) {
        snap.add(dbItem);
      }
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
      DbItem dbItem = runtimeDbItem(item);
      if (dbItem != null) {
        upserts.add(dbItem);
      }
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

  DbItem runtimeDbItem(StorageItem item) {
    if (item == null || item.amount() <= 0) {
      return null;
    }
    return new DbItem(item.key(), blobFor(item), item.amount());
  }

  private byte[] blobFor(StorageItem item) {
    byte[] blob = item.blob();
    if (blob != null && blob.length > 0 && blob.length <= MAX_ITEM_BLOB_BYTES) {
      return blob;
    }
    try {
      blob = item.sample().serializeAsBytes();
    } catch (RuntimeException e) {
      logInvalidDbItem(
          new DbItem(item.key(), null, item.amount()),
          "failed to serialize item sample: " + e.getMessage());
      throw new IllegalStateException(
          "Storage "
              + storageId
              + ": item cannot be serialized for persistence"
              + " (key="
              + item.key()
              + ", amount="
              + item.amount()
              + ")",
          e);
    }
    if (blob.length == 0 || blob.length > MAX_ITEM_BLOB_BYTES) {
      logInvalidDbItem(new DbItem(item.key(), blob, item.amount()), "serialized blob size invalid");
      throw new IllegalStateException(
          "Storage "
              + storageId
              + ": item cannot be persisted"
              + " (key="
              + item.key()
              + ", amount="
              + item.amount()
              + ", blobLength="
              + blob.length
              + ")");
    }
    item.setBlob(blob);
    return blob;
  }

  private void warnInvalidDbItem(DbItem item, String reason) {
    logInvalidDbItem(item, "skipping invalid storage item: " + reason);
  }

  private void recordCorruption(
      DbItem item,
      String reason,
      List<StorageCorruption> detected,
      List<StorageQuarantineEntry> quarantineEntries,
      long detectedAtMillis) {
    String safeReason = sanitizeReason(reason);
    warnInvalidDbItem(item, safeReason);
    String key = item == null || item.key() == null ? "<null>" : item.key();
    long amount = item == null ? 0L : item.amount();
    StorageCorruption corruption =
        new StorageCorruption(key, amount, safeReason, Math.max(0L, detectedAtMillis / 1000L));
    detected.add(corruption);
    quarantineEntries.add(
        new StorageQuarantineEntry(corruption, item == null ? null : item.blob()));
  }

  private AddResult readOnlyAddResult() {
    return AddResult.rejected(
        StoredItemCodec.Failure.READ_ONLY,
        "storage is degraded and read-only because corrupt persisted item rows were detected");
  }

  private String degradationReasonSummary() {
    return corruptions.stream()
        .map(StorageCorruption::reason)
        .distinct()
        .limit(3)
        .reduce((left, right) -> left + "; " + right)
        .orElse("unknown");
  }

  private static String sanitizeReason(String reason) {
    String normalized = reason == null ? "unknown" : reason.replace('\n', ' ').replace('\r', ' ');
    return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
  }

  private static String failureDetail(RuntimeException error) {
    String message = error.getMessage();
    return error.getClass().getSimpleName()
        + (message == null || message.isBlank() ? "" : ": " + message);
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

  private static boolean wouldOverflow(long current, long addition) {
    return current < 0 || addition < 0 || current > Long.MAX_VALUE - addition;
  }

  private static boolean wouldMultiplyOverflow(long left, long right) {
    return left <= 0 || right <= 0 || left > Long.MAX_VALUE / right;
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
