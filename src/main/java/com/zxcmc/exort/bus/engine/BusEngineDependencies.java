package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.network.NetworkGraphCacheProvider;
import com.zxcmc.exort.network.TerminalLinkFinder;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public record BusEngineDependencies(
    Plugin plugin,
    StorageKeys keys,
    int wireLimit,
    int wireHardCap,
    Material wireMaterial,
    Material storageCarrier,
    Consumer<String> storageRenderer) {
  public BusEngineDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
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
        busBlock, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
  }

  long topologyVersion() {
    if (plugin instanceof NetworkGraphCacheProvider provider
        && provider.getNetworkGraphCache() != null) {
      return provider.getNetworkGraphCache().currentVersion();
    }
    return 0L;
  }

  void renderStorage(String storageId) {
    storageRenderer.accept(storageId);
  }
}
