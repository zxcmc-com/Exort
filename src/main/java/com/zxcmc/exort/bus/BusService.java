package com.zxcmc.exort.bus;

import com.zxcmc.exort.bus.engine.BusEngine;
import com.zxcmc.exort.bus.engine.BusEngineDependencies;
import com.zxcmc.exort.bus.engine.BusRegistry;
import com.zxcmc.exort.bus.resolver.BusTargetResolver;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.storage.StorageManager;
import com.zxcmc.exort.wireless.WirelessTerminalService;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

public final class BusService {
  private final Plugin plugin;
  private final BusRegistry registry;
  private final BusEngine engine;

  public BusService(
      BusEngineDependencies dependencies,
      StorageManager storageManager,
      Database database,
      Material busCarrier,
      BusRuntimeConfig runtimeConfig,
      WirelessTerminalService wirelessService) {
    this.plugin = dependencies.plugin();
    this.registry = new BusRegistry(plugin, database);
    this.engine =
        new BusEngine(
            dependencies, storageManager, busCarrier, runtimeConfig, wirelessService, registry);
  }

  public void start() {
    engine.start();
  }

  public void stop() {
    try {
      engine.stop();
    } catch (RuntimeException | LinkageError e) {
      plugin.getLogger().log(Level.SEVERE, "Failed to stop bus engine cleanly.", e);
    }
    try {
      registry.flushSettings();
    } catch (RuntimeException | LinkageError e) {
      plugin.getLogger().log(Level.SEVERE, "Failed to flush bus settings during shutdown.", e);
    } finally {
      registry.clear();
    }
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

  public void saveSettings(BusState state, Block busBlock) {
    registry.saveSettings(state, busBlock);
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
