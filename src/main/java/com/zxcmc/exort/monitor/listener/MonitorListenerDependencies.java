package com.zxcmc.exort.monitor.listener;

import com.zxcmc.exort.feedback.BossBarManager;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.integration.auth.AuthenticationGate;
import com.zxcmc.exort.integration.protection.RegionProtection;
import com.zxcmc.exort.integration.worldedit.wand.WorldEditWandGuard;
import com.zxcmc.exort.keys.StorageKeys;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

public record MonitorListenerDependencies(
    JavaPlugin plugin,
    RegionProtection regionProtection,
    StorageKeys keys,
    BossBarManager bossBarManager,
    ItemNameService itemNameService,
    AuthenticationGate authenticationGate,
    WorldEditWandGuard worldEditWandGuard,
    Material monitorCarrier,
    Material wireMaterial,
    Material storageCarrier,
    Material relayCarrier,
    IntSupplier wireLimit,
    IntSupplier wireHardCap,
    IntSupplier relayRangeChunks,
    LongSupplier storagePeekTicks,
    Predicate<Block> monitorRecentlyPlaced,
    Consumer<Block> monitorDisplayRefresh) {
  public MonitorListenerDependencies {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(regionProtection, "regionProtection");
    Objects.requireNonNull(keys, "keys");
    Objects.requireNonNull(bossBarManager, "bossBarManager");
    Objects.requireNonNull(itemNameService, "itemNameService");
    Objects.requireNonNull(authenticationGate, "authenticationGate");
    Objects.requireNonNull(worldEditWandGuard, "worldEditWandGuard");
    Objects.requireNonNull(wireLimit, "wireLimit");
    Objects.requireNonNull(wireHardCap, "wireHardCap");
    Objects.requireNonNull(relayRangeChunks, "relayRangeChunks");
    Objects.requireNonNull(storagePeekTicks, "storagePeekTicks");
    Objects.requireNonNull(monitorRecentlyPlaced, "monitorRecentlyPlaced");
    Objects.requireNonNull(monitorDisplayRefresh, "monitorDisplayRefresh");
  }
}
