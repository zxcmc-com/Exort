package com.zxcmc.exort.bus;

import com.zxcmc.exort.bus.engine.BusEngine;
import com.zxcmc.exort.bus.engine.BusRegistry;
import com.zxcmc.exort.bus.resolver.BusTargetResolver;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class BusService {
  private final BusRegistry registry;
  private final BusEngine engine;

  public BusService(
      ExortPlugin plugin,
      StorageManager storageManager,
      Database database,
      Material busCarrier,
      int activeIntervalTicks,
      int idleIntervalTicks,
      int itemsPerOperation,
      int maxOperationsPerTick,
      int maxOperationsPerChunk,
      boolean allowStorageTargets,
      WirelessTerminalService wirelessService) {
    this.registry = new BusRegistry(plugin, database);
    this.engine =
        new BusEngine(
            plugin,
            storageManager,
            busCarrier,
            activeIntervalTicks,
            idleIntervalTicks,
            itemsPerOperation,
            maxOperationsPerTick,
            maxOperationsPerChunk,
            allowStorageTargets,
            wirelessService,
            registry);
  }

  public void start() {
    engine.start();
  }

  public void stop() {
    engine.stop();
    registry.clear();
  }

  public void pauseForCraft(String storageId) {
    engine.pauseForCraft(storageId);
  }

  public void pauseForCraft(String storageId, int ticks) {
    engine.pauseForCraft(storageId, ticks);
  }

  public BusState getOrCreateState(BusPos pos, BusMarker.Data marker, Block busBlock) {
    return registry.getOrCreateState(pos, marker, busBlock);
  }

  public void unregisterBus(Block block) {
    registry.unregisterBus(block);
  }

  public void unregisterBus(BusPos pos) {
    registry.unregisterBus(pos);
  }

  public void saveSettings(BusState state) {
    registry.saveSettings(state);
  }

  public void scanLoadedChunks() {
    registry.scanLoadedChunks();
  }

  public void scanChunk(Chunk chunk) {
    registry.scanChunk(chunk);
  }

  public boolean isLoopDisabled(BusPos pos) {
    return engine.isLoopDisabled(pos);
  }

  public Optional<BusTargetResolver.BusTarget> resolveTarget(Block busBlock, BlockFace facing) {
    return engine.resolveTarget(busBlock, facing);
  }
}
