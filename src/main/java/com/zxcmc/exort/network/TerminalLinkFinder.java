package com.zxcmc.exort.network;

import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class TerminalLinkFinder {
  private TerminalLinkFinder() {}

  public record StorageBlockInfo(Block block, String storageId, StorageTier tier) {}

  public record StorageSearchResult(int count, StorageBlockInfo data) {}

  public static StorageSearchResult find(
      Block terminal,
      StorageKeys keys,
      Plugin plugin,
      int wireLimit,
      int wireHardCap,
      Material wireMaterial,
      Material storageCarrier,
      Material bridgeCarrier,
      int bridgeRangeChunks) {
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
                bridgeCarrier,
                bridgeRangeChunks));
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
      Material bridgeCarrier,
      int bridgeRangeChunks) {
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
            bridgeCarrier,
            bridgeRangeChunks);
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
        bridgeCarrier,
        bridgeRangeChunks);
  }
}
