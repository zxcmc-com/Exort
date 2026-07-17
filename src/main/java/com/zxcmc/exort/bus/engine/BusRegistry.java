package com.zxcmc.exort.bus.engine;

import com.zxcmc.exort.bus.BusFilterCodec;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusSettings;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.infra.db.Database;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Main-thread-confined Bus state registry.
 *
 * <p>Database completions must hand state changes back to the Bukkit server thread before accessing
 * this registry.
 */
public final class BusRegistry {
  private static final int FILTER_SLOTS = 10;

  private final Plugin plugin;
  private final Database database;
  private final Map<BusPos, BusState> states = new HashMap<>();
  private List<BusState> stateList = List.of();
  private boolean stateListDirty;
  private long version;

  public BusRegistry(Plugin plugin, Database database) {
    this.plugin = plugin;
    this.database = database;
  }

  public void clear() {
    states.clear();
    stateList = List.of();
    stateListDirty = false;
    version++;
  }

  public BusState getOrCreateState(BusPos pos, BusMarker.Data marker, Block busBlock) {
    if (pos == null || marker == null) return null;
    BusState existing = states.get(pos);
    if (existing != null) {
      if (busBlock != null) {
        long before = existing.settingsRevision();
        applyMarkerState(existing, marker, busBlock);
        if (existing.settingsRevision() != before) {
          markDirty();
        }
      }
      return existing;
    }
    BusState created = new BusState(pos, marker.type(), marker.facing(), marker.mode());
    states.put(pos, created);
    boolean markerHasFilters = false;
    if (busBlock != null) {
      markerHasFilters = applyMarkerState(created, marker, busBlock);
    }
    markDirty();
    if (!markerHasFilters) {
      loadSettingsIfMissing(created, marker, created.settingsRevision());
    }
    return created;
  }

  private boolean applyMarkerState(BusState state, BusMarker.Data marker, Block busBlock) {
    if (state == null || marker == null) return false;
    state.setFacing(marker.facing());
    state.setType(marker.type());
    state.setMode(marker.mode());
    if (busBlock == null) return false;
    var filters = BusMarker.getFilters(plugin, busBlock);
    filters.ifPresent(data -> state.setFilters(BusFilterCodec.decode(data, FILTER_SLOTS)));
    return filters.isPresent();
  }

  public void unregisterBus(Block block) {
    if (block == null) return;
    unregisterBus(BusPos.of(block));
  }

  public void unregisterBus(BusPos pos) {
    if (pos == null) return;
    states.remove(pos);
    markDirty();
    database
        .deleteBusSettings(pos)
        .whenComplete(
            (ignored, err) -> {
              if (err != null) {
                plugin
                    .getLogger()
                    .log(Level.WARNING, "Failed to delete bus settings for " + pos, unwrap(err));
              }
            });
  }

  public void saveSettings(BusState state) {
    saveSettings(state, null);
  }

  public void saveSettings(BusState state, Block busBlock) {
    if (state == null) return;
    markDirty();
    BusSettings settings =
        new BusSettings(state.pos(), state.type(), state.mode(), state.filters());
    database
        .saveBusSettings(settings, FILTER_SLOTS)
        .whenComplete(
            (ignored, err) -> {
              if (err != null) {
                plugin
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "Failed to save bus settings for " + settings.pos(),
                        unwrap(err));
              }
            });
    BusMarker.Data marker = new BusMarker.Data(state.type(), state.facing(), state.mode());
    Block block = busBlock != null ? busBlock : state.pos().block();
    if (block != null) {
      BusMarker.set(plugin, block, marker.type(), marker.facing(), marker.mode());
      BusMarker.setFilters(plugin, block, BusFilterCodec.encode(state.filters(), FILTER_SLOTS));
    }
  }

  public void flushSettings() {
    for (BusState state : new ArrayList<>(states.values())) {
      saveSettings(state);
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
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          if (!BusMarker.isBus(plugin, block)) return;
          BusMarker.get(plugin, block)
              .ifPresent(data -> getOrCreateState(BusPos.of(block), data, block));
        });
  }

  public List<BusState> snapshotList() {
    if (!stateListDirty) return stateList;
    stateListDirty = false;
    stateList = new ArrayList<>(states.values());
    return stateList;
  }

  public long version() {
    return version;
  }

  private void markDirty() {
    stateListDirty = true;
    version++;
  }

  private void loadSettingsIfMissing(
      BusState state, BusMarker.Data markerAtLoadStart, long revisionAtLoadStart) {
    BusPos pos = state.pos();
    database
        .loadBusSettings(pos, FILTER_SLOTS)
        .whenComplete(
            (opt, err) -> {
              if (err != null) {
                plugin
                    .getLogger()
                    .log(Level.WARNING, "Failed to load bus settings for " + pos, unwrap(err));
                return;
              }
              if (opt.isEmpty()) return;
              if (!plugin.isEnabled()) return;
              try {
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (!plugin.isEnabled()) return;
                          if (states.get(pos) != state) return;
                          if (state.settingsRevision() != revisionAtLoadStart) return;
                          if (!stateStillMatchesMarker(state, markerAtLoadStart)) return;
                          Block block = pos.block();
                          if (block != null) {
                            if (BusMarker.getFilters(plugin, block).isPresent()) return;
                            var currentMarker = BusMarker.get(plugin, block);
                            if (currentMarker.isPresent()
                                && !sameMarker(currentMarker.get(), markerAtLoadStart)) {
                              return;
                            }
                          }
                          BusSettings settings = opt.get();
                          state.setType(settings.type());
                          state.setMode(settings.mode());
                          state.setFilters(settings.filters());
                          markDirty();
                        });
              } catch (IllegalStateException ignored) {
                // Plugin is disabling between the async load and the sync handoff.
              }
            });
  }

  private boolean stateStillMatchesMarker(BusState state, BusMarker.Data marker) {
    if (state == null || marker == null) return false;
    return state.type() == marker.type()
        && state.mode() == marker.mode()
        && state.facing() == marker.facing();
  }

  private boolean sameMarker(BusMarker.Data left, BusMarker.Data right) {
    if (left == null || right == null) return false;
    return left.type() == right.type()
        && left.mode() == right.mode()
        && left.facing() == right.facing();
  }

  private Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }
}
