package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.network.NetworkGraphCache;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public record BusEngineDependencies(
    Plugin plugin,
    StorageKeys keys,
    int wireLimit,
    int wireHardCap,
    int relayRangeChunks,
    Material wireMaterial,
    Material storageCarrier,
    Material relayCarrier,
    Supplier<NetworkGraphCache> networkGraphCache,
    Consumer<String> storageRenderer) {
  public BusEngineDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(storageRenderer, "storageRenderer");
  }

  Logger logger() {
    return plugin.getLogger();
  }

  boolean isBus(Block block) {
    return BusMarker.isBus(plugin, block);
  }

  TerminalLinkFinder.StorageSearchResult findLinkedStorage(Block busBlock) {
    return TerminalLinkFinder.find(
        busBlock,
        keys,
        plugin,
        networkGraphCache.get(),
        wireLimit,
        wireHardCap,
        wireMaterial,
        storageCarrier,
        relayCarrier,
        relayRangeChunks);
  }

  long topologyVersion() {
    NetworkGraphCache cache = networkGraphCache.get();
    return cache == null ? 0L : cache.currentVersion();
  }

  void renderStorage(String storageId) {
    storageRenderer.accept(storageId);
  }
}
