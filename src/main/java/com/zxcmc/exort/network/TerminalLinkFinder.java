package com.zxcmc.exort.network;

import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class TerminalLinkFinder {
  private TerminalLinkFinder() {}

  public record StorageBlockInfo(
      Block block, String storageId, StorageTier tier, String displayName) {
    public StorageBlockInfo(Block block, String storageId, StorageTier tier) {
      this(block, storageId, tier, null);
    }
  }

  public enum StorageSearchStatus {
    NONE,
    OK,
    MULTIPLE,
    WIRE_LIMIT,
    HARD_CAP
  }

  public record StorageSearchResult(StorageSearchStatus status, int count, StorageBlockInfo data) {
    public StorageSearchResult {
      status = status == null ? statusForCount(count) : status;
      if (status == StorageSearchStatus.NONE
          || status == StorageSearchStatus.WIRE_LIMIT
          || status == StorageSearchStatus.HARD_CAP) {
        count = 0;
        data = null;
      }
    }

    public StorageSearchResult(int count, StorageBlockInfo data) {
      this(statusForCount(count), count, data);
    }

    public static StorageSearchResult none() {
      return new StorageSearchResult(StorageSearchStatus.NONE, 0, null);
    }

    public static StorageSearchResult connected(StorageBlockInfo data) {
      return new StorageSearchResult(StorageSearchStatus.OK, 1, data);
    }

    public static StorageSearchResult multiple(int count, StorageBlockInfo data) {
      return new StorageSearchResult(StorageSearchStatus.MULTIPLE, Math.max(2, count), data);
    }

    public static StorageSearchResult wireLimit() {
      return new StorageSearchResult(StorageSearchStatus.WIRE_LIMIT, 0, null);
    }

    public static StorageSearchResult hardCap() {
      return new StorageSearchResult(StorageSearchStatus.HARD_CAP, 0, null);
    }

    public boolean connected() {
      return status == StorageSearchStatus.OK && count == 1 && data != null;
    }

    public boolean truncated() {
      return status == StorageSearchStatus.WIRE_LIMIT || status == StorageSearchStatus.HARD_CAP;
    }

    private static StorageSearchStatus statusForCount(int count) {
      if (count <= 0) {
        return StorageSearchStatus.NONE;
      }
      return count == 1 ? StorageSearchStatus.OK : StorageSearchStatus.MULTIPLE;
    }
  }

  public static StorageSearchResult find(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      int relayRangeChunks) {
    return PerfStats.measure(
        PerfStats.Area.NETWORK,
        () ->
            findInternal(
                terminal,
                keys,
                plugin,
                wireLimit,
                wireHardCap,
                wireMaterial,
                storageCarrier,
                relayCarrier,
                relayRangeChunks));
  }

  public static StorageSearchResult find(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier) {
    return find(
        terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier, null, 0);
  }

  private static StorageSearchResult findInternal(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material relayCarrier,
      int relayRangeChunks) {
    if (plugin instanceof NetworkGraphCacheProvider provider) {
      var cache = provider.getNetworkGraphCache();
      if (cache != null) {
        return cache.find(
            terminal,
            keys,
            plugin,
            wireLimit,
            wireHardCap,
            wireMaterial,
            storageCarrier,
            relayCarrier,
            relayRangeChunks);
      }
    }
    return NetworkGraphCache.scan(
        terminal,
        keys,
        plugin,
        wireLimit,
        wireHardCap,
        wireMaterial,
        storageCarrier,
        relayCarrier,
        relayRangeChunks);
  }
}
