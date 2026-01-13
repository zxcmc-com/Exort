package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.db.Database;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;

public final class BusRegistry {
  private static final int FILTER_SLOTS = 10;

  private final ExortPlugin plugin;
  private final Database database;
  private final Map<BusPos, BusState> states = new ConcurrentHashMap<>();
  private volatile List<BusState> stateList = List.of();
  private volatile boolean stateListDirty;

  public BusRegistry(ExortPlugin plugin, Database database) {
    this.plugin = plugin;
    this.database = database;
  }

  public void clear() {
    states.clear();
    stateList = List.of();
    stateListDirty = false;
  }

  public BusState getOrCreateState(BusPos pos, BusMarker.Data marker, Block busBlock) {
    if (pos == null || marker == null) return null;
    BusState existing = states.get(pos);
    if (existing != null) {
      if (busBlock != null) {
        existing.setFacing(marker.facing());
        existing.setType(marker.type());
        existing.setMode(marker.mode());
      }
      return existing;
    }
    BusState created = new BusState(pos, marker.type(), marker.facing(), marker.mode());
    states.put(pos, created);
    if (busBlock != null) {
      created.setFacing(marker.facing());
      created.setType(marker.type());
      created.setMode(marker.mode());
    }
    markDirty();
    loadSettingsIfMissing(created);
    return created;
  }

  public void unregisterBus(Block block) {
    if (block == null) return;
    unregisterBus(BusPos.of(block));
  }

  public void unregisterBus(BusPos pos) {
    if (pos == null) return;
    states.remove(pos);
    markDirty();
    database.deleteBusSettings(pos);
  }

  public void saveSettings(BusState state) {
    if (state == null) return;
    BusSettings settings =
        new BusSettings(state.pos(), state.type(), state.mode(), state.filters());
    database.saveBusSettings(settings, FILTER_SLOTS);
    BusMarker.Data marker = new BusMarker.Data(state.type(), state.facing(), state.mode());
    Block block = state.pos().block();
    if (block != null) {
      BusMarker.set(plugin, block, marker.type(), marker.facing(), marker.mode());
    }
  }

  public void scanLoadedChunks() {
    for (var world : Bukkit.getWorlds()) {
      for (var chunk : world.getLoadedChunks()) {
        scanChunk(chunk);
      }
    }
  }

  public void scanChunk(Chunk chunk) {
    Set<NamespacedKey> keys = chunk.getPersistentDataContainer().getKeys();
    if (keys.isEmpty()) return;
    String ns = plugin.getName().toLowerCase();
    for (var key : keys) {
      if (!key.getNamespace().equals(ns)) continue;
      String raw = key.getKey();
      if (!raw.startsWith("bus_")) continue;
      if (raw.startsWith("bus_display_")) continue;
      int[] xyz = MarkerCoords.parseXYZ(raw.substring("bus_".length()));
      if (xyz == null) continue;
      Block block = chunk.getWorld().getBlockAt(xyz[0], xyz[1], xyz[2]);
      BusMarker.get(plugin, block)
          .ifPresent(data -> getOrCreateState(BusPos.of(block), data, block));
    }
  }

  public List<BusState> snapshotList() {
    if (!stateListDirty) return stateList;
    stateListDirty = false;
    stateList = new ArrayList<>(states.values());
    return stateList;
  }

  private void markDirty() {
    stateListDirty = true;
  }

  private void loadSettingsIfMissing(BusState state) {
    BusPos pos = state.pos();
    database
        .loadBusSettings(pos, FILTER_SLOTS)
        .thenAccept(
            opt -> {
              if (opt.isEmpty()) return;
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        BusSettings settings = opt.get();
                        state.setType(settings.type());
                        state.setMode(settings.mode());
                        state.setFilters(settings.filters());
                      });
            });
  }
}
