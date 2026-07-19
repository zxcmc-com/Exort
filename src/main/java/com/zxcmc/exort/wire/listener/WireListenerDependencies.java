package com.zxcmc.exort.wire.listener;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.feedback.PlayerFeedback;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.network.NetworkGraphCache;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record WireListenerDependencies(
    JavaPlugin plugin,
    RegionProtection regionProtection,
    WorldEditWandGuard worldEditWandGuard,
    BossBarManager bossBarManager,
    PlayerFeedback playerFeedback,
    StorageKeys keys,
    Supplier<NetworkGraphCache> networkGraphCache,
    int wireLimit,
    int wireHardCap,
    Material wireMaterial,
    long peekDurationTicks,
    Material storageCarrier,
    Material relayCarrier,
    int relayRangeChunks) {
  public WireListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(playerFeedback, "playerFeedback");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(networkGraphCache, "networkGraphCache");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
  }
}
