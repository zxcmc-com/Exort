package com.zxcmc.exort.core.network;

import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.keys.StorageKeys;
import com.zxcmc.exort.storage.StorageTier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class TerminalLinkFinder {
    private TerminalLinkFinder() {}

    public record StorageBlockInfo(Block block, String storageId, StorageTier tier) {}
    public record StorageSearchResult(int count, StorageBlockInfo data) {}

    public static StorageSearchResult find(Block terminal, StorageKeys keys, Plugin plugin, int wireLimit, int wireHardCap, Material wireMaterial, Material storageCarrier) {
        if (plugin instanceof ExortPlugin exort) {
            var cache = exort.getNetworkGraphCache();
            if (cache != null) {
                return cache.find(terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
            }
        }
        return NetworkGraphCache.scan(terminal, keys, plugin, wireLimit, wireHardCap, wireMaterial, storageCarrier);
    }
}
