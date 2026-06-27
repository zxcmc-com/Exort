package com.zxcmc.exort.wire.listener;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

public record WireListenerDependencies(
    JavaPlugin plugin,
    RegionProtection regionProtection,
    WorldEditWandGuard worldEditWandGuard,
    BossBarManager bossBarManager,
    StorageKeys keys,
    int wireLimit,
    int wireHardCap,
    Material wireMaterial,
    long peekDurationTicks,
    Material storageCarrier) {
  public WireListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(wireMaterial, "wireMaterial");
    Objects.requireNonNull(storageCarrier, "storageCarrier");
  }
}
