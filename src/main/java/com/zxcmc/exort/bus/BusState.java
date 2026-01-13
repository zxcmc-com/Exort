package com.zxcmc.exort.bus;

import com.zxcmc.exort.core.items.ItemKeyUtil;
import com.zxcmc.exort.storage.StorageCache;
import java.util.*;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

public final class BusState {
  private static final int FILTER_SLOTS = 10;

  private final BusPos pos;
  private BusType type;
  private BlockFace facing;
  private BusMode mode;
  private final ItemStack[] filters = new ItemStack[FILTER_SLOTS];
  private final Map<String, Integer> filterCounts = new HashMap<>();
  private long nextTick;
  private int slotCursor;
  private int storageCursor;
  private int viewers;
  private long cachedStorageVersion = -1L;
  private String cachedStorageId;
  private List<StorageCache.StorageItem> cachedItems;

  public BusState(BusPos pos, BusType type, BlockFace facing, BusMode mode) {
    this.pos = pos;
    this.type = type == null ? BusType.IMPORT : type;
    this.facing = facing == null ? BlockFace.NORTH : facing;
    this.mode = mode == null ? BusMode.DISABLED : mode;
  }

  public BusPos pos() {
    return pos;
  }

  public BusType type() {
    return type;
  }

  public void setType(BusType type) {
    if (type != null) {
      this.type = type;
    }
  }

  public BlockFace facing() {
    return facing;
  }

  public void setFacing(BlockFace facing) {
    if (facing != null) {
      this.facing = facing;
    }
  }

  public BusMode mode() {
    return mode;
  }

  public void setMode(BusMode mode) {
    this.mode = mode == null ? BusMode.DISABLED : mode;
  }

  public ItemStack[] filters() {
    return Arrays.copyOf(filters, filters.length);
  }

  public void setFilters(ItemStack[] newFilters) {
    filterCounts.clear();
    for (int i = 0; i < filters.length; i++) {
      ItemStack stack = (newFilters != null && i < newFilters.length) ? newFilters[i] : null;
      if (stack != null && !stack.getType().isAir()) {
        ItemStack sample = ItemKeyUtil.sampleItem(stack);
        filters[i] = sample;
        addFilterKey(ItemKeyUtil.keyFor(sample));
      } else {
        filters[i] = null;
      }
    }
  }

  public void setFilter(int index, ItemStack sample) {
    if (index < 0 || index >= filters.length) return;
    ItemStack prev = filters[index];
    if (prev != null && !prev.getType().isAir()) {
      removeFilterKey(ItemKeyUtil.keyFor(prev));
    }
    if (sample == null || sample.getType().isAir()) {
      filters[index] = null;
      return;
    }
    ItemStack normalized = ItemKeyUtil.sampleItem(sample);
    filters[index] = normalized;
    addFilterKey(ItemKeyUtil.keyFor(normalized));
  }

  public Set<String> filterKeys() {
    return Collections.unmodifiableSet(filterCounts.keySet());
  }

  private void addFilterKey(String key) {
    filterCounts.put(key, filterCounts.getOrDefault(key, 0) + 1);
  }

  private void removeFilterKey(String key) {
    Integer count = filterCounts.get(key);
    if (count == null) return;
    if (count <= 1) {
      filterCounts.remove(key);
    } else {
      filterCounts.put(key, count - 1);
    }
  }

  public long nextTick() {
    return nextTick;
  }

  public void setNextTick(long nextTick) {
    this.nextTick = nextTick;
  }

  public int slotCursor() {
    return slotCursor;
  }

  public void setSlotCursor(int slotCursor) {
    this.slotCursor = slotCursor;
  }

  public int storageCursor() {
    return storageCursor;
  }

  public void setStorageCursor(int storageCursor) {
    this.storageCursor = storageCursor;
  }

  public int viewers() {
    return viewers;
  }

  public void viewerOpened() {
    viewers++;
  }

  public void viewerClosed() {
    if (viewers > 0) viewers--;
  }

  public long cachedStorageVersion() {
    return cachedStorageVersion;
  }

  public void setCachedStorageVersion(long cachedStorageVersion) {
    this.cachedStorageVersion = cachedStorageVersion;
  }

  public String cachedStorageId() {
    return cachedStorageId;
  }

  public void setCachedStorageId(String cachedStorageId) {
    this.cachedStorageId = cachedStorageId;
  }

  public List<StorageCache.StorageItem> cachedItems() {
    return cachedItems;
  }

  public void setCachedItems(List<StorageCache.StorageItem> cachedItems) {
    this.cachedItems = cachedItems;
  }
}
