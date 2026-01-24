package com.zxcmc.exort.core.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.zxcmc.exort.bus.BusFilterCodec;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.carrier.Carriers;
import com.zxcmc.exort.core.marker.*;
import com.zxcmc.exort.core.network.NetworkGraphCache;
import com.zxcmc.exort.core.sanity.DisplayCleanupService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.DisplayRefreshService;
import com.zxcmc.exort.display.ItemHologramManager;
import com.zxcmc.exort.storage.StorageTier;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.enginehub.linbus.tree.LinByteArrayTag;
import org.enginehub.linbus.tree.LinByteTag;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.LinStringTag;
import org.enginehub.linbus.tree.LinTagType;

public final class WorldEditBridge {
  private static final String EXORT_TAG = "exort";
  private static final String SECTION_STORAGE = "storage";
  private static final String SECTION_TERMINAL = "terminal";
  private static final String SECTION_BUS = "bus";
  private static final String SECTION_MONITOR = "monitor";
  private static final String SECTION_WIRE = "wire";
  private static final String SECTION_STORAGE_CORE = "storage_core";

  private static final String FAWE_ALLOWED_KEY = "extent.allowed-plugins";

  private static final String FIELD_ID = "id";
  private static final String FIELD_TIER = "tier";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_MODE = "mode";
  private static final String FIELD_FACING = "facing";
  private static final String FIELD_ITEM_KEY = "item_key";
  private static final String FIELD_ITEM_BLOB = "item_blob";
  private static final String FIELD_PRESENT = "present";
  private static final String FIELD_FILTERS = "filters";
  private static final String FIELD_NBT_ID = "id";
  private static final String STRUCTURE_BLOCK_ID = "minecraft:structure_block";

  private static final int BUS_FILTER_SLOTS = 10;

  private static final int APPLY_PER_TICK = 3000;
  private static final int Y_OFFSET = 2048;
  private static final int RETRY_DELAY_TICKS = 2;
  private static final int MAX_RETRIES = 40;
  private static final long HISTORY_TTL_MS = TimeUnit.MINUTES.toMillis(10);

  private static final Map<Class<?>, TranslateAccessor> TRANSLATE_ACCESSORS =
      new ConcurrentHashMap<>();
  private static final Set<Class<?>> TRANSLATE_SKIP = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> POSITION_METHODS = new ConcurrentHashMap<>();
  private static final Set<Class<?>> POSITION_SKIP = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, TransformAccessor> TRANSFORM_ACCESSORS =
      new ConcurrentHashMap<>();
  private static final Set<Class<?>> TRANSFORM_SKIP = ConcurrentHashMap.newKeySet();
  private static final BlockFace[] ROTATABLE_FACES = {
    BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
  };

  private final ExortPlugin plugin;
  private final Queue<PendingUpdate> updates = new ConcurrentLinkedQueue<>();
  private final Map<HistoryKey, HistoryEntry> markerHistory = new ConcurrentHashMap<>();
  private BukkitTask flushTask;
  private long tickCounter;

  private WorldEditBridge(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public static WorldEditBridge tryRegister(ExortPlugin plugin) {
    Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
    Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
    if (worldEdit == null && fawe == null) return null;
    try {
      if (fawe != null) {
        allowFaweExtent(fawe);
      }
      WorldEditBridge bridge = new WorldEditBridge(plugin);
      WorldEdit.getInstance().getEventBus().register(bridge);
      bridge.startFlushTask();
      plugin.getLogger().info("[WorldEdit] Integration enabled.");
      return bridge;
    } catch (NoClassDefFoundError err) {
      plugin.getLogger().warning("[WorldEdit] Integration disabled: missing classes.");
      return null;
    } catch (Throwable err) {
      plugin.getLogger().warning("[WorldEdit] Integration disabled: " + err.getMessage());
      return null;
    }
  }

  private static void allowFaweExtent(Plugin fawe) {
    String extentClass = MarkerExtent.class.getName();
    boolean modified = false;
    try {
      File configFile = new File(fawe.getDataFolder(), "config.yml");
      if (configFile.isFile()) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        var allowed = config.getStringList(FAWE_ALLOWED_KEY);
        if (!allowed.contains(extentClass)) {
          allowed.add(extentClass);
          config.set(FAWE_ALLOWED_KEY, allowed);
          config.save(configFile);
          modified = true;
        }
      }
    } catch (Exception ignored) {
    }
    try {
      Class<?> settingsClass = Class.forName("com.fastasyncworldedit.core.configuration.Settings");
      Object settings = settingsClass.getMethod("settings").invoke(null);
      Object extent = settingsClass.getField("EXTENT").get(settings);
      var allowedField = extent.getClass().getField("ALLOWED_PLUGINS");
      Object value = allowedField.get(extent);
      if (value instanceof java.util.List<?> list) {
        if (!list.contains(extentClass)) {
          try {
            @SuppressWarnings("unchecked")
            java.util.List<String> mutable = (java.util.List<String>) list;
            mutable.add(extentClass);
          } catch (UnsupportedOperationException ignored) {
            java.util.List<String> copy = new java.util.ArrayList<>();
            for (Object item : list) {
              if (item != null) {
                copy.add(item.toString());
              }
            }
            if (!copy.contains(extentClass)) {
              copy.add(extentClass);
            }
            allowedField.set(extent, copy);
          }
        }
      }
      if (modified) {
        try {
          File configFile = new File(fawe.getDataFolder(), "config.yml");
          settingsClass.getMethod("reload", File.class).invoke(settings, configFile);
        } catch (Exception ignored) {
        }
      }
    } catch (Throwable ignored) {
    }
  }

  public void shutdown() {
    try {
      WorldEdit.getInstance().getEventBus().unregister(this);
    } catch (Throwable ignored) {
    }
    if (flushTask != null) {
      flushTask.cancel();
      flushTask = null;
    }
  }

  private void startFlushTask() {
    if (flushTask != null) return;
    flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushUpdates, 1L, 1L);
  }

  @Subscribe
  public void onEditSession(EditSessionEvent event) {
    EditSession.Stage stage = event.getStage();
    if (stage != EditSession.Stage.BEFORE_HISTORY
        && stage != EditSession.Stage.BEFORE_REORDER
        && stage != EditSession.Stage.BEFORE_CHANGE) {
      return;
    }
    com.sk89q.worldedit.world.World weWorld = event.getWorld();
    if (weWorld == null) {
      return;
    }
    World world = Bukkit.getWorld(weWorld.getName());
    if (world == null) return;
    WorldEditDebugService debug = plugin.getWorldEditDebugService();
    if (debug != null && debug.isEnabled()) {
      debug.incSessions();
      debug.recordEvent(
          "we session stage=" + stage.name().toLowerCase(Locale.ROOT), NamedTextColor.DARK_AQUA);
    }
    Extent extent = event.getExtent();
    if (containsMarkerExtent(extent)) return;
    FacingTransform clipboardTransform = resolveClipboardFacing(event.getActor());
    event.setExtent(new MarkerExtent(extent, world, this, clipboardTransform));
  }

  private void enqueue(MarkerUpdate update) {
    if (update == null) return;
    updates.add(new PendingUpdate(update));
    WorldEditDebugService debug = plugin.getWorldEditDebugService();
    if (debug != null && debug.isEnabled()) {
      debug.incUpdatesQueued();
    }
  }

  private void flushUpdates() {
    if (updates.isEmpty()) return;
    Map<ChunkKey, ChunkUpdateBatch> batches = new HashMap<>();
    int processed = 0;
    tickCounter++;
    int polled = 0;
    int limit = updates.size();
    while (processed < APPLY_PER_TICK) {
      PendingUpdate pending = updates.poll();
      if (pending == null) break;
      polled++;
      if (pending.nextTick > tickCounter) {
        updates.add(pending);
      } else {
        MarkerUpdate update = pending.update;
        ChunkKey key = new ChunkKey(update.worldId(), update.chunkX(), update.chunkZ());
        batches.computeIfAbsent(key, ChunkUpdateBatch::new).add(pending);
        processed++;
      }
      if (polled >= limit) break;
    }
    if (batches.isEmpty()) return;

    DisplayRefreshService refreshService = plugin.getDisplayRefreshService();
    BusService busService = plugin.getBusService();
    NetworkGraphCache graphCache = plugin.getNetworkGraphCache();
    DisplayCleanupService cleanupService =
        new DisplayCleanupService(
            plugin,
            plugin.getWireMaterial(),
            plugin.getStorageCarrier(),
            plugin.getTerminalCarrier(),
            plugin.getMonitorCarrier(),
            plugin.getBusCarrier());
    boolean invalidated = false;
    for (ChunkUpdateBatch batch : batches.values()) {
      World world = Bukkit.getWorld(batch.key.worldId());
      if (world == null) {
        requeueBatch(batch);
        continue;
      }
      if (!world.isChunkLoaded(batch.key.chunkX(), batch.key.chunkZ())) {
        requeueBatch(batch);
        continue;
      }
      Set<String> removedStorageIds = new HashSet<>();
      for (PendingUpdate pending : batch.updates) {
        String removedId = pending.update.removedStorageId();
        if (removedId != null && !removedId.isBlank()) {
          removedStorageIds.add(removedId);
        }
      }
      Chunk chunk = world.getChunkAt(batch.key.chunkX(), batch.key.chunkZ());
      for (PendingUpdate pending : batch.updates) {
        MarkerUpdate update = pending.update;
        MarkerSnapshot snapshot = update.snapshot();
        if (snapshot != null && snapshot.storage() != null) {
          String storageId = snapshot.storage().storageId();
          if (storageId != null && !removedStorageIds.contains(storageId)) {
            String newId = UUID.randomUUID().toString();
            var storageManager = plugin.getStorageManager();
            if (storageManager != null) {
              storageManager.cloneStorage(storageId, newId, snapshot.storage().tier());
            }
            snapshot = withStorageId(snapshot, newId);
            update =
                new MarkerUpdate(
                    update.worldId(), update.x(), update.y(), update.z(), snapshot, null);
            pending.update = update;
          }
        }
        if (!applyUpdate(world, update)) {
          if (pending.attempts < MAX_RETRIES) {
            pending.attempts++;
            pending.nextTick = tickCounter + RETRY_DELAY_TICKS;
            updates.add(pending);
            WorldEditDebugService debug = plugin.getWorldEditDebugService();
            if (debug != null && debug.isEnabled()) {
              debug.incUpdatesRetried();
            }
          }
        } else {
          WorldEditDebugService debug = plugin.getWorldEditDebugService();
          if (debug != null && debug.isEnabled()) {
            debug.incUpdatesApplied();
          }
        }
      }
      cleanupService.cleanupDisplays(chunk);
      if (refreshService != null) {
        refreshService.refreshChunk(chunk);
      }
      if (busService != null) {
        busService.scanChunk(chunk);
      }
      invalidated = true;
    }
    if (invalidated && graphCache != null) {
      graphCache.invalidateAll();
    }
  }

  private void requeueBatch(ChunkUpdateBatch batch) {
    WorldEditDebugService debug = plugin.getWorldEditDebugService();
    for (PendingUpdate pending : batch.updates) {
      if (pending.attempts < MAX_RETRIES) {
        pending.attempts++;
        pending.nextTick = tickCounter + RETRY_DELAY_TICKS;
        updates.add(pending);
        if (debug != null && debug.isEnabled()) {
          debug.incUpdatesRetried();
        }
      } else {
        if (debug != null && debug.isEnabled()) {
          debug.incUpdatesSkipped();
        }
      }
    }
  }

  private boolean applyUpdate(World world, MarkerUpdate update) {
    Block block = world.getBlockAt(update.x(), update.y(), update.z());
    MarkerSnapshot snapshot = update.snapshot();
    if (snapshot == null) {
      ItemHologramManager hologramManager = plugin.getHologramManager();
      if (hologramManager != null) {
        if (StorageMarker.get(plugin, block).isPresent()) {
          hologramManager.unregisterStorage(block);
        }
        if (TerminalMarker.isTerminal(plugin, block)) {
          hologramManager.unregisterTerminal(block);
        }
      }
      ChunkMarkerStore.clearBlock(plugin, block);
      return true;
    }
    if (snapshot.storage() != null && !Carriers.matchesCarrier(block, plugin.getStorageCarrier())) {
      return false;
    }
    if (snapshot.storageCore() && !Carriers.matchesCarrier(block, plugin.getStorageCarrier())) {
      return false;
    }
    if (snapshot.terminal() != null
        && !Carriers.matchesCarrier(block, plugin.getTerminalCarrier())) {
      return false;
    }
    if (snapshot.monitor() != null && !Carriers.matchesCarrier(block, plugin.getMonitorCarrier())) {
      return false;
    }
    if (snapshot.bus() != null && !Carriers.matchesCarrier(block, plugin.getBusCarrier())) {
      return false;
    }
    if (snapshot.wire() && !Carriers.matchesCarrier(block, plugin.getWireMaterial())) {
      return false;
    }
    ChunkMarkerStore.clearBlock(plugin, block);
    if (snapshot.storage() != null && Carriers.matchesCarrier(block, plugin.getStorageCarrier())) {
      StorageTier tier = StorageTier.fromString(snapshot.storage().tier()).orElse(null);
      if (tier != null) {
        StorageMarker.set(
            plugin, block, snapshot.storage().storageId(), tier, parseFacing(snapshot.storage()));
        ItemHologramManager hologramManager = plugin.getHologramManager();
        if (hologramManager != null) {
          hologramManager.registerStorage(block);
        }
      }
    }
    if (snapshot.storageCore() && Carriers.matchesCarrier(block, plugin.getStorageCarrier())) {
      StorageCoreMarker.set(plugin, block);
    }
    if (snapshot.terminal() != null
        && Carriers.matchesCarrier(block, plugin.getTerminalCarrier())) {
      TerminalKind kind = parseTerminalKind(snapshot.terminal());
      TerminalMarker.set(plugin, block, kind, parseFacing(snapshot.terminal()));
      ItemHologramManager hologramManager = plugin.getHologramManager();
      if (hologramManager != null) {
        hologramManager.registerTerminal(block);
      }
    }
    if (snapshot.monitor() != null && Carriers.matchesCarrier(block, plugin.getMonitorCarrier())) {
      MonitorMarker.set(plugin, block, parseFacing(snapshot.monitor()));
      if (snapshot.monitor().itemKey() != null || snapshot.monitor().itemBlob() != null) {
        MonitorMarker.setItem(
            plugin, block, snapshot.monitor().itemKey(), snapshot.monitor().itemBlob());
      }
    }
    if (snapshot.bus() != null && Carriers.matchesCarrier(block, plugin.getBusCarrier())) {
      BusType type = BusType.fromString(snapshot.bus().type());
      BusMode mode = BusMode.fromString(snapshot.bus().mode());
      BlockFace facing = parseFacing(snapshot.bus());
      BusMarker.set(plugin, block, type, facing, mode);
      byte[] filters = snapshot.bus().filters();
      if (filters != null && filters.length > 0) {
        BusService busService = plugin.getBusService();
        if (busService != null) {
          BusState state =
              busService.getOrCreateState(
                  BusPos.of(block), new BusMarker.Data(type, facing, mode), block);
          if (state != null) {
            state.setFilters(BusFilterCodec.decode(filters, BUS_FILTER_SLOTS));
            busService.saveSettings(state);
          }
        } else {
          BusMarker.setFilters(plugin, block, filters);
        }
      }
    }
    if (snapshot.wire() && Carriers.matchesCarrier(block, plugin.getWireMaterial())) {
      WireMarker.setWire(plugin, block);
    }
    return true;
  }

  private static TerminalKind parseTerminalKind(TerminalData data) {
    String raw = data.type();
    if (raw == null) return TerminalKind.TERMINAL;
    try {
      return TerminalKind.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      return TerminalKind.TERMINAL;
    }
  }

  private static BlockFace parseFacing(FacingOwner data) {
    String raw = data.facing();
    if (raw == null) return null;
    try {
      return BlockFace.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private ChunkSnapshot loadSnapshot(World world, int chunkX, int chunkZ) {
    if (!Bukkit.isPrimaryThread()) {
      try {
        return Bukkit.getScheduler()
            .callSyncMethod(plugin, () -> loadSnapshot(world, chunkX, chunkZ))
            .get(5, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return ChunkSnapshot.empty();
      } catch (ExecutionException | TimeoutException ignored) {
        return ChunkSnapshot.empty();
      }
    }
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return ChunkSnapshot.empty();
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    Map<Long, LinCompoundTag> data = new HashMap<>();
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      return ChunkSnapshot.empty();
    }
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          LinCompoundTag tag = buildExortTag(block);
          if (tag != null) {
            data.put(blockKey(block.getX(), block.getY(), block.getZ()), tag);
          }
        });
    WorldEditDebugService debug = plugin.getWorldEditDebugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we snapshot chunk=" + chunkX + "," + chunkZ + " markers=" + data.size(),
          NamedTextColor.BLUE);
    }
    return new ChunkSnapshot(data);
  }

  private LinCompoundTag buildExortTag(Block block) {
    LinCompoundTag.Builder exort = LinCompoundTag.builder();
    boolean any = false;
    Optional<StorageMarker.Data> storage = StorageMarker.get(plugin, block);
    if (storage.isPresent()) {
      StorageMarker.Data data = storage.get();
      LinCompoundTag.Builder storageTag = LinCompoundTag.builder();
      storageTag.putString(FIELD_ID, data.storageId());
      storageTag.putString(FIELD_TIER, data.tier().key());
      if (data.facing() != null) {
        storageTag.putString(FIELD_FACING, data.facing().name());
      }
      exort.put(SECTION_STORAGE, storageTag.build());
      any = true;
    }
    if (StorageCoreMarker.isCore(plugin, block)) {
      LinCompoundTag.Builder coreTag = LinCompoundTag.builder();
      coreTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_STORAGE_CORE, coreTag.build());
      any = true;
    }
    if (TerminalMarker.isTerminal(plugin, block)) {
      LinCompoundTag.Builder terminalTag = LinCompoundTag.builder();
      terminalTag.putString(FIELD_TYPE, TerminalMarker.kind(plugin, block).name());
      TerminalMarker.facing(plugin, block)
          .ifPresent(face -> terminalTag.putString(FIELD_FACING, face.name()));
      exort.put(SECTION_TERMINAL, terminalTag.build());
      any = true;
    }
    Optional<BusMarker.Data> bus = BusMarker.get(plugin, block);
    if (bus.isPresent()) {
      BusMarker.Data data = bus.get();
      LinCompoundTag.Builder busTag = LinCompoundTag.builder();
      busTag.putString(FIELD_TYPE, data.type().name());
      busTag.putString(FIELD_FACING, data.facing().name());
      busTag.putString(FIELD_MODE, data.mode().name());
      byte[] filters = BusMarker.getFilters(plugin, block).orElse(null);
      if (filters == null || filters.length == 0) {
        BusService busService = plugin.getBusService();
        if (busService != null) {
          BusState state = busService.getOrCreateState(BusPos.of(block), data, block);
          if (state != null) {
            filters = BusFilterCodec.encode(state.filters(), BUS_FILTER_SLOTS);
          }
        }
      }
      if (filters != null && filters.length > 0) {
        busTag.putByteArray(FIELD_FILTERS, filters);
      }
      exort.put(SECTION_BUS, busTag.build());
      any = true;
    }
    if (MonitorMarker.isMonitor(plugin, block)) {
      LinCompoundTag.Builder monitorTag = LinCompoundTag.builder();
      MonitorMarker.facing(plugin, block)
          .ifPresent(face -> monitorTag.putString(FIELD_FACING, face.name()));
      MonitorMarker.itemKey(plugin, block)
          .ifPresent(value -> monitorTag.putString(FIELD_ITEM_KEY, value));
      MonitorMarker.itemBlob(plugin, block)
          .ifPresent(value -> monitorTag.putByteArray(FIELD_ITEM_BLOB, value));
      exort.put(SECTION_MONITOR, monitorTag.build());
      any = true;
    }
    if (WireMarker.isWire(plugin, block)) {
      LinCompoundTag.Builder wireTag = LinCompoundTag.builder();
      wireTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_WIRE, wireTag.build());
      any = true;
    }
    return any ? exort.build() : null;
  }

  private static LinCompoundTag buildExortTag(MarkerSnapshot snapshot) {
    if (snapshot == null) return null;
    LinCompoundTag.Builder exort = LinCompoundTag.builder();
    boolean any = false;
    if (snapshot.storage() != null) {
      LinCompoundTag.Builder storageTag = LinCompoundTag.builder();
      storageTag.putString(FIELD_ID, snapshot.storage().storageId());
      storageTag.putString(FIELD_TIER, snapshot.storage().tier());
      if (snapshot.storage().facing() != null) {
        storageTag.putString(FIELD_FACING, snapshot.storage().facing());
      }
      exort.put(SECTION_STORAGE, storageTag.build());
      any = true;
    }
    if (snapshot.storageCore()) {
      LinCompoundTag.Builder coreTag = LinCompoundTag.builder();
      coreTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_STORAGE_CORE, coreTag.build());
      any = true;
    }
    if (snapshot.terminal() != null) {
      LinCompoundTag.Builder terminalTag = LinCompoundTag.builder();
      if (snapshot.terminal().type() != null) {
        terminalTag.putString(FIELD_TYPE, snapshot.terminal().type());
      }
      if (snapshot.terminal().facing() != null) {
        terminalTag.putString(FIELD_FACING, snapshot.terminal().facing());
      }
      exort.put(SECTION_TERMINAL, terminalTag.build());
      any = true;
    }
    if (snapshot.bus() != null) {
      LinCompoundTag.Builder busTag = LinCompoundTag.builder();
      if (snapshot.bus().type() != null) {
        busTag.putString(FIELD_TYPE, snapshot.bus().type());
      }
      if (snapshot.bus().mode() != null) {
        busTag.putString(FIELD_MODE, snapshot.bus().mode());
      }
      if (snapshot.bus().facing() != null) {
        busTag.putString(FIELD_FACING, snapshot.bus().facing());
      }
      if (snapshot.bus().filters() != null && snapshot.bus().filters().length > 0) {
        busTag.putByteArray(FIELD_FILTERS, snapshot.bus().filters());
      }
      exort.put(SECTION_BUS, busTag.build());
      any = true;
    }
    if (snapshot.monitor() != null) {
      LinCompoundTag.Builder monitorTag = LinCompoundTag.builder();
      if (snapshot.monitor().facing() != null) {
        monitorTag.putString(FIELD_FACING, snapshot.monitor().facing());
      }
      if (snapshot.monitor().itemKey() != null) {
        monitorTag.putString(FIELD_ITEM_KEY, snapshot.monitor().itemKey());
      }
      if (snapshot.monitor().itemBlob() != null) {
        monitorTag.putByteArray(FIELD_ITEM_BLOB, snapshot.monitor().itemBlob());
      }
      exort.put(SECTION_MONITOR, monitorTag.build());
      any = true;
    }
    if (snapshot.wire()) {
      LinCompoundTag.Builder wireTag = LinCompoundTag.builder();
      wireTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_WIRE, wireTag.build());
      any = true;
    }
    return any ? exort.build() : null;
  }

  private static LinCompoundTag removeExort(LinCompoundTag root) {
    if (root == null) return null;
    return root.toBuilder().remove(EXORT_TAG).build();
  }

  private static LinCompoundTag readExort(LinCompoundTag root) {
    if (root == null) return null;
    return root.findTag(EXORT_TAG, LinTagType.compoundTag());
  }

  private static LinCompoundTag getCompound(LinCompoundTag root, String key) {
    if (root == null) return null;
    return root.findTag(key, LinTagType.compoundTag());
  }

  private static String readString(LinCompoundTag root, String key) {
    if (root == null) return null;
    LinStringTag tag = root.findTag(key, LinTagType.stringTag());
    return tag == null ? null : tag.value();
  }

  private static byte[] readByteArray(LinCompoundTag root, String key) {
    if (root == null) return null;
    LinByteArrayTag tag = root.findTag(key, LinTagType.byteArrayTag());
    return tag == null ? null : tag.value();
  }

  private static boolean readPresent(LinCompoundTag root, String key) {
    if (root == null) return false;
    LinByteTag tag = root.findTag(key, LinTagType.byteTag());
    return tag != null && tag.valueAsByte() == (byte) 1;
  }

  private static long blockKey(int x, int y, int z) {
    int yIndex = y + Y_OFFSET;
    return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (yIndex & 0xFFF);
  }

  private static MarkerSnapshot parseSnapshot(LinCompoundTag exort) {
    if (exort == null) return null;
    StorageData storage = null;
    TerminalData terminal = null;
    BusData bus = null;
    MonitorData monitor = null;
    boolean wire = false;
    boolean storageCore = false;

    LinCompoundTag storageTag = getCompound(exort, SECTION_STORAGE);
    if (storageTag != null) {
      String id = readString(storageTag, FIELD_ID);
      String tier = readString(storageTag, FIELD_TIER);
      String facing = readString(storageTag, FIELD_FACING);
      if (id != null && tier != null) {
        storage = new StorageData(id, tier, facing);
      }
    }

    LinCompoundTag terminalTag = getCompound(exort, SECTION_TERMINAL);
    if (terminalTag != null) {
      String type = readString(terminalTag, FIELD_TYPE);
      String facing = readString(terminalTag, FIELD_FACING);
      terminal = new TerminalData(type, facing);
    }

    LinCompoundTag busTag = getCompound(exort, SECTION_BUS);
    if (busTag != null) {
      String type = readString(busTag, FIELD_TYPE);
      String mode = readString(busTag, FIELD_MODE);
      String facing = readString(busTag, FIELD_FACING);
      byte[] filters = readByteArray(busTag, FIELD_FILTERS);
      if (type != null || mode != null || facing != null) {
        bus = new BusData(type, facing, mode, filters);
      }
    }

    LinCompoundTag monitorTag = getCompound(exort, SECTION_MONITOR);
    if (monitorTag != null) {
      String facing = readString(monitorTag, FIELD_FACING);
      String itemKey = readString(monitorTag, FIELD_ITEM_KEY);
      byte[] itemBlob = readByteArray(monitorTag, FIELD_ITEM_BLOB);
      monitor = new MonitorData(facing, itemKey, itemBlob);
    }

    LinCompoundTag wireTag = getCompound(exort, SECTION_WIRE);
    if (wireTag != null) {
      wire = readPresent(wireTag, FIELD_PRESENT);
    }

    LinCompoundTag coreTag = getCompound(exort, SECTION_STORAGE_CORE);
    if (coreTag != null) {
      storageCore = readPresent(coreTag, FIELD_PRESENT);
    }

    if (storage == null
        && terminal == null
        && bus == null
        && monitor == null
        && !wire
        && !storageCore) {
      return null;
    }
    return new MarkerSnapshot(storage, terminal, bus, monitor, wire, storageCore);
  }

  private static final class MarkerExtent extends AbstractDelegateExtent {
    private final World world;
    private final WorldEditBridge bridge;
    private final FacingTransform facingTransform;
    private final Map<ChunkKey, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<BaseBlock, MarkerSnapshot> carried =
        Collections.synchronizedMap(new IdentityHashMap<>());

    private MarkerExtent(
        Extent extent, World world, WorldEditBridge bridge, FacingTransform clipboardTransform) {
      super(extent);
      this.world = world;
      this.bridge = bridge;
      Transform transform = resolveTransform(extent);
      this.facingTransform =
          clipboardTransform != null
              ? clipboardTransform
              : resolveFacingTransform(extent, transform);
    }

    public BaseBlock getFullBlock(BlockVector3 position) {
      BaseBlock block = super.getFullBlock(position);
      if (block == null || world == null) return block;
      BlockVector3 resolved = resolvePosition(position);
      int chunkX = resolved.x() >> 4;
      int chunkZ = resolved.z() >> 4;
      ChunkSnapshot snapshot = snapshot(chunkX, chunkZ);
      LinCompoundTag exort = snapshot.get(blockKey(resolved.x(), resolved.y(), resolved.z()));
      if (exort == null) return block;
      MarkerSnapshot parsed = parseSnapshot(exort);
      if (parsed == null) return block;
      BaseBlock withNbt = buildVirtualBlock(exort);
      if (withNbt == null) {
        return block;
      }
      WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
      if (debug != null && debug.isFull()) {
        debug.recordEvent(
            "getFullBlock pos="
                + resolved.x()
                + ","
                + resolved.y()
                + ","
                + resolved.z()
                + " marker=yes",
            NamedTextColor.DARK_GREEN);
      }
      carried.put(withNbt, parsed);
      return withNbt;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
        throws com.sk89q.worldedit.WorldEditException {
      if (world == null) {
        return super.setBlock(position, block);
      }
      BlockVector3 resolved = resolvePosition(position);
      int chunkX = resolved.x() >> 4;
      int chunkZ = resolved.z() >> 4;
      ChunkSnapshot snapshot = snapshot(chunkX, chunkZ);
      long key = blockKey(resolved.x(), resolved.y(), resolved.z());
      LinCompoundTag existingTag = snapshot.get(key);
      MarkerSnapshot existingSnapshot = parseSnapshot(existingTag);
      BaseBlock base = null;
      if (block instanceof BaseBlock baseBlock) {
        base = baseBlock;
      } else if (block != null) {
        base = block.toBaseBlock();
      }
      LinCompoundTag root = readRootNbt(base);
      LinCompoundTag exort = readExort(root);
      MarkerSnapshot parsed = parseSnapshot(exort);
      boolean fromClipboard = parsed != null;
      boolean carriedHit = false;
      if (parsed == null && base != null) {
        parsed = carried.remove(base);
        if (parsed != null) {
          exort = buildExortTag(parsed);
          fromClipboard = true;
          carriedHit = true;
        }
      }
      if (parsed == null && existingSnapshot != null && base != null) {
        if (matchesCarrier(base, existingSnapshot)) {
          parsed = existingSnapshot;
          exort = existingTag;
        }
      }
      if (parsed != null && fromClipboard && facingTransform != null) {
        parsed = rotateSnapshot(parsed, facingTransform);
        exort = buildExortTag(parsed);
      }
      boolean markerPresent = existingTag != null;
      WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
      if (debug != null && debug.isFull()) {
        String baseType = base == null ? "null" : base.getBlockType().getId();
        debug.recordEvent(
            "setBlock pos="
                + resolved.x()
                + ","
                + resolved.y()
                + ","
                + resolved.z()
                + " base="
                + baseType
                + " root="
                + (root != null)
                + " exort="
                + (exort != null)
                + " parsed="
                + (parsed != null)
                + " carried="
                + carriedHit
                + " markerPresent="
                + markerPresent
                + " fromClipboard="
                + fromClipboard,
            NamedTextColor.YELLOW);
      }
      if (parsed == null && !markerPresent && base != null) {
        MarkerSnapshot history =
            bridge.consumeHistory(world.getUID(), resolved.x(), resolved.y(), resolved.z());
        if (history != null && matchesCarrier(base, history)) {
          parsed = history;
          exort = buildExortTag(parsed);
          markerPresent = true;
        }
      }
      if (parsed == null && !markerPresent) {
        return super.setBlock(position, block);
      }
      String removedStorageId = null;
      if (parsed == null && existingSnapshot != null) {
        if (existingSnapshot.storage() != null) {
          removedStorageId = existingSnapshot.storage().storageId();
        }
        bridge.rememberHistory(
            world.getUID(), resolved.x(), resolved.y(), resolved.z(), existingSnapshot);
      }
      BaseBlock toSet = base;
      if (parsed != null) {
        BaseBlock carrier = carrierBlock(parsed, base);
        if (carrier != null) {
          toSet = carrier;
        } else if (exort != null && root != null && base != null) {
          LinCompoundTag cleaned = removeExort(root);
          toSet = base.toBaseBlock(cleaned);
        }
      } else if (exort != null && root != null && base != null) {
        LinCompoundTag cleaned = removeExort(root);
        toSet = base.toBaseBlock(cleaned);
      }
      boolean result =
          toSet != null ? super.setBlock(position, toSet) : super.setBlock(position, block);
      if (debug != null && debug.isEnabled()) {
        boolean hasMarker = parsed != null;
        boolean cleared = !hasMarker && markerPresent;
        debug.recordSetBlock(hasMarker, cleared);
        if (debug.isFull() && (hasMarker || cleared)) {
          debug.recordEvent(
              "setBlock pos="
                  + resolved.x()
                  + ","
                  + resolved.y()
                  + ","
                  + resolved.z()
                  + " marker="
                  + (hasMarker ? "set" : cleared ? "clear" : "none")
                  + " result="
                  + result,
              hasMarker ? NamedTextColor.GREEN : NamedTextColor.RED);
        }
      }
      if (result) {
        if (parsed == null) {
          snapshot.remove(key);
        } else {
          snapshot.put(key, exort);
        }
        bridge.enqueue(
            new MarkerUpdate(
                world.getUID(),
                resolved.x(),
                resolved.y(),
                resolved.z(),
                parsed,
                removedStorageId));
      }
      return result;
    }

    @SuppressWarnings("unused")
    public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block)
        throws com.sk89q.worldedit.WorldEditException {
      return setBlock(BlockVector3.at(x, y, z), block);
    }

    @SuppressWarnings("unused")
    public <B extends BlockStateHolder<B>> int setBlocks(Region region, B block)
        throws MaxChangedBlocksException {
      if (world == null) {
        return delegateSetBlocks(region, block);
      }
      if (!needsProcessing(region, block)) {
        WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
        if (debug != null && debug.isEnabled()) {
          debug.recordEvent("setBlocks constant delegated", NamedTextColor.GRAY);
        }
        return delegateSetBlocks(region, block);
      }
      int changed = 0;
      int total = 0;
      for (BlockVector3 pos : region) {
        total++;
        try {
          if (setBlock(pos, block)) {
            changed++;
          }
        } catch (MaxChangedBlocksException e) {
          throw e;
        } catch (com.sk89q.worldedit.WorldEditException e) {
          throw new RuntimeException(e);
        }
      }
      WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
      if (debug != null && debug.isEnabled()) {
        debug.recordSetBlocks(total);
        debug.recordEvent(
            "setBlocks constant blocks=" + total + " changed=" + changed, NamedTextColor.AQUA);
      }
      return changed;
    }

    @SuppressWarnings({"unused", "deprecation"})
    public int setBlocks(Region region, BlockPattern pattern) throws MaxChangedBlocksException {
      return setBlocks(region, (Pattern) pattern);
    }

    public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
      if (world == null) {
        return delegateSetBlocks(region, pattern);
      }
      if (!needsProcessing(region, pattern)) {
        WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
        if (debug != null && debug.isEnabled()) {
          debug.recordEvent("setBlocks pattern delegated", NamedTextColor.GRAY);
        }
        return delegateSetBlocks(region, pattern);
      }
      int changed = 0;
      int total = 0;
      for (BlockVector3 pos : region) {
        total++;
        BaseBlock block = pattern.applyBlock(pos);
        try {
          if (setBlock(pos, block)) {
            changed++;
          }
        } catch (MaxChangedBlocksException e) {
          throw e;
        } catch (com.sk89q.worldedit.WorldEditException e) {
          throw new RuntimeException(e);
        }
      }
      WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
      if (debug != null && debug.isEnabled()) {
        debug.recordSetBlocks(total);
        debug.recordEvent(
            "setBlocks pattern blocks=" + total + " changed=" + changed, NamedTextColor.AQUA);
      }
      return changed;
    }

    @SuppressWarnings({"unused", "deprecation"})
    public int setBlocks(Set<BlockVector3> positions, BlockPattern pattern) {
      return setBlocks(positions, (Pattern) pattern);
    }

    public int setBlocks(Set<BlockVector3> positions, Pattern pattern) {
      if (world == null) {
        return delegateSetBlocksSet(positions, pattern);
      }
      if (!needsProcessing(positions, pattern)) {
        WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
        if (debug != null && debug.isEnabled()) {
          debug.recordEvent("setBlocks set delegated", NamedTextColor.GRAY);
        }
        return delegateSetBlocksSet(positions, pattern);
      }
      int changed = 0;
      int total = 0;
      for (BlockVector3 pos : positions) {
        total++;
        BaseBlock block = pattern.applyBlock(pos);
        try {
          if (setBlock(pos, block)) {
            changed++;
          }
        } catch (com.sk89q.worldedit.WorldEditException e) {
          throw new RuntimeException(e);
        }
      }
      WorldEditDebugService debug = bridge.plugin.getWorldEditDebugService();
      if (debug != null && debug.isEnabled()) {
        debug.recordSetBlocks(total);
        debug.recordEvent(
            "setBlocks set blocks=" + total + " changed=" + changed, NamedTextColor.AQUA);
      }
      return changed;
    }

    private boolean needsProcessing(Region region, BlockStateHolder<?> block) {
      if (block == null) return regionHasMarkers(region);
      BaseBlock base = block instanceof BaseBlock baseBlock ? baseBlock : block.toBaseBlock();
      LinCompoundTag root = readRootNbt(base);
      if (readExort(root) != null) {
        return true;
      }
      return regionHasMarkers(region);
    }

    private boolean needsProcessing(Region region, Pattern pattern) {
      if (pattern instanceof BlockStateHolder<?> holder) {
        BaseBlock base = holder instanceof BaseBlock baseBlock ? baseBlock : holder.toBaseBlock();
        LinCompoundTag root = readRootNbt(base);
        if (readExort(root) != null) {
          return true;
        }
        return regionHasMarkers(region);
      }
      return true;
    }

    private boolean needsProcessing(Set<BlockVector3> positions, Pattern pattern) {
      if (positions == null || positions.isEmpty()) return false;
      if (pattern instanceof BlockStateHolder<?> holder) {
        BaseBlock base = holder instanceof BaseBlock baseBlock ? baseBlock : holder.toBaseBlock();
        LinCompoundTag root = readRootNbt(base);
        if (readExort(root) != null) {
          return true;
        }
      }
      for (BlockVector3 pos : positions) {
        BlockVector3 resolved = resolvePosition(pos);
        ChunkSnapshot snapshot = snapshot(resolved.x() >> 4, resolved.z() >> 4);
        if (snapshot.get(blockKey(resolved.x(), resolved.y(), resolved.z())) != null) {
          return true;
        }
      }
      return false;
    }

    private boolean regionHasMarkers(Region region) {
      if (region == null) return false;
      BlockVector3 min = region.getMinimumPoint();
      BlockVector3 max = region.getMaximumPoint();
      int minChunkX = min.x() >> 4;
      int maxChunkX = max.x() >> 4;
      int minChunkZ = min.z() >> 4;
      int maxChunkZ = max.z() >> 4;
      for (int cx = minChunkX; cx <= maxChunkX; cx++) {
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
          ChunkSnapshot snapshot = snapshot(cx, cz);
          if (snapshot != null && !snapshot.isEmpty()) {
            return true;
          }
        }
      }
      return false;
    }

    private ChunkSnapshot snapshot(int chunkX, int chunkZ) {
      ChunkKey key = new ChunkKey(world.getUID(), chunkX, chunkZ);
      return snapshots.computeIfAbsent(key, missing -> bridge.loadSnapshot(world, chunkX, chunkZ));
    }

    private int delegateSetBlocks(Region region, Object payload) throws MaxChangedBlocksException {
      Method method = resolveSetBlocksMethod(payload);
      if (method != null) {
        try {
          Object result = method.invoke(getExtent(), region, payload);
          if (result instanceof Integer count) {
            return count;
          }
        } catch (java.lang.reflect.InvocationTargetException ex) {
          Throwable cause = ex.getCause();
          if (cause instanceof MaxChangedBlocksException max) {
            throw max;
          }
          if (cause instanceof RuntimeException runtime) {
            throw runtime;
          }
        } catch (Exception ignored) {
        }
      }
      int changed = 0;
      if (payload instanceof BlockStateHolder<?> block) {
        for (BlockVector3 pos : region) {
          BaseBlock base = block instanceof BaseBlock baseBlock ? baseBlock : block.toBaseBlock();
          try {
            if (getExtent().setBlock(pos, base)) {
              changed++;
            }
          } catch (MaxChangedBlocksException e) {
            throw e;
          } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new RuntimeException(e);
          }
        }
      } else if (payload instanceof Pattern pattern) {
        for (BlockVector3 pos : region) {
          BaseBlock block = pattern.applyBlock(pos);
          try {
            if (getExtent().setBlock(pos, block)) {
              changed++;
            }
          } catch (MaxChangedBlocksException e) {
            throw e;
          } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new RuntimeException(e);
          }
        }
      }
      return changed;
    }

    private int delegateSetBlocksSet(Set<BlockVector3> positions, Pattern pattern) {
      Method method = resolveSetBlocksSetMethod();
      if (method != null) {
        try {
          Object result = method.invoke(getExtent(), positions, pattern);
          if (result instanceof Integer count) {
            return count;
          }
        } catch (Exception ignored) {
        }
      }
      int changed = 0;
      for (BlockVector3 pos : positions) {
        BaseBlock block = pattern.applyBlock(pos);
        try {
          if (getExtent().setBlock(pos, block)) {
            changed++;
          }
        } catch (com.sk89q.worldedit.WorldEditException e) {
          throw new RuntimeException(e);
        }
      }
      return changed;
    }

    private Method resolveSetBlocksSetMethod() {
      return findSetBlocksSetMethod(getExtent().getClass());
    }

    private Method resolveSetBlocksMethod(Object payload) {
      if (payload == null) return null;
      Class<?> param = payload instanceof Pattern ? Pattern.class : BlockStateHolder.class;
      return findSetBlocks(getExtent().getClass(), param);
    }

    private BaseBlock carrierBlock(MarkerSnapshot snapshot, BaseBlock fallback) {
      Material material = null;
      if (snapshot.storage() != null || snapshot.storageCore()) {
        material = bridge.plugin.getStorageCarrier();
      } else if (snapshot.terminal() != null) {
        material = bridge.plugin.getTerminalCarrier();
      } else if (snapshot.monitor() != null) {
        material = bridge.plugin.getMonitorCarrier();
      } else if (snapshot.bus() != null) {
        material = bridge.plugin.getBusCarrier();
      } else if (snapshot.wire()) {
        material = bridge.plugin.getWireMaterial();
      }
      if (material == null) return fallback;
      BlockType type = BlockTypes.get(material.getKey().toString());
      if (type == null) return fallback;
      if (material == Carriers.CHORUS_MATERIAL) {
        BlockState state = type.getDefaultState();
        for (Property<?> property : type.getPropertyMap().values()) {
          String name = property.getName();
          if ("waterlogged".equals(name)) {
            state = withProperty(state, property, "false");
            continue;
          }
          if (property instanceof BooleanProperty) {
            state = withProperty(state, property, "true");
          }
        }
        return state.toBaseBlock();
      }
      return type.getDefaultState().toBaseBlock();
    }

    private boolean matchesCarrier(BaseBlock base, MarkerSnapshot snapshot) {
      if (base == null || snapshot == null) return false;
      Material material = null;
      if (snapshot.storage() != null || snapshot.storageCore()) {
        material = bridge.plugin.getStorageCarrier();
      } else if (snapshot.terminal() != null) {
        material = bridge.plugin.getTerminalCarrier();
      } else if (snapshot.monitor() != null) {
        material = bridge.plugin.getMonitorCarrier();
      } else if (snapshot.bus() != null) {
        material = bridge.plugin.getBusCarrier();
      } else if (snapshot.wire()) {
        material = bridge.plugin.getWireMaterial();
      }
      if (material == null) return false;
      BlockType type = BlockTypes.get(material.getKey().toString());
      return type != null && type.equals(base.getBlockType());
    }

    private BlockVector3 resolvePosition(BlockVector3 position) {
      BlockVector3 resolved = position;
      Extent current = getExtent();
      while (current != null) {
        BlockVector3 transformed = tryPositionTransform(current, resolved);
        if (transformed != null) {
          resolved = transformed;
        }
        TranslateAccessor accessor = translateAccessor(current);
        if (accessor != null) {
          int dx = accessor.dx(current);
          int dy = accessor.dy(current);
          int dz = accessor.dz(current);
          if (dx != 0 || dy != 0 || dz != 0) {
            resolved = resolved.add(dx, dy, dz);
          }
        }
        if (current instanceof AbstractDelegateExtent delegateExtent) {
          current = delegateExtent.getExtent();
        } else {
          break;
        }
      }
      return resolved;
    }
  }

  private static BlockVector3 tryPositionTransform(Extent extent, BlockVector3 position) {
    Method method = positionMethod(extent);
    if (method == null) return position;
    try {
      Object result = method.invoke(extent, position);
      return result instanceof BlockVector3 vec ? vec : position;
    } catch (Exception ignored) {
      return position;
    }
  }

  private static Method positionMethod(Object extent) {
    Class<?> type = extent.getClass();
    Method cached = POSITION_METHODS.get(type);
    if (cached != null) return cached;
    if (POSITION_SKIP.contains(type)) return null;
    try {
      Method method;
      try {
        method = type.getDeclaredMethod("getPos", BlockVector3.class);
      } catch (NoSuchMethodException ignored) {
        method = type.getMethod("getPos", BlockVector3.class);
      }
      method.setAccessible(true);
      POSITION_METHODS.put(type, method);
      return method;
    } catch (Exception ignored) {
      POSITION_SKIP.add(type);
      return null;
    }
  }

  private static TranslateAccessor translateAccessor(Object extent) {
    Class<?> type = extent.getClass();
    TranslateAccessor cached = TRANSLATE_ACCESSORS.get(type);
    if (cached != null) return cached;
    if (TRANSLATE_SKIP.contains(type)) return null;
    try {
      Field dx = type.getDeclaredField("dx");
      Field dy = type.getDeclaredField("dy");
      Field dz = type.getDeclaredField("dz");
      dx.setAccessible(true);
      dy.setAccessible(true);
      dz.setAccessible(true);
      TranslateAccessor accessor = new TranslateAccessor(dx, dy, dz);
      TRANSLATE_ACCESSORS.put(type, accessor);
      return accessor;
    } catch (Exception ignored) {
      TRANSLATE_SKIP.add(type);
      return null;
    }
  }

  private record TranslateAccessor(Field dx, Field dy, Field dz) {
    int dx(Object instance) {
      return read(dx, instance);
    }

    int dy(Object instance) {
      return read(dy, instance);
    }

    int dz(Object instance) {
      return read(dz, instance);
    }

    private static int read(Field field, Object instance) {
      try {
        return ((Number) field.get(instance)).intValue();
      } catch (Exception ignored) {
        return 0;
      }
    }
  }

  private record TransformAccessor(Method method, Field field) {
    Transform get(Object instance) {
      if (method != null) {
        try {
          Object value = method.invoke(instance);
          if (value instanceof Transform transform) {
            return transform;
          }
        } catch (Exception ignored) {
          // ignored
        }
      }
      if (field != null) {
        try {
          Object value = field.get(instance);
          if (value instanceof Transform transform) {
            return transform;
          }
        } catch (Exception ignored) {
          // ignored
        }
      }
      return null;
    }
  }

  private static final Map<Class<?>, Method> SETBLOCKS_STATE = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method> SETBLOCKS_PATTERN = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method> SETBLOCKS_SET = new ConcurrentHashMap<>();

  private static Method findSetBlocks(Class<?> extentClass, Class<?> paramType) {
    Map<Class<?>, Method> cache = paramType == Pattern.class ? SETBLOCKS_PATTERN : SETBLOCKS_STATE;
    Method cached = cache.get(extentClass);
    if (cached != null) return cached;
    try {
      Method method = extentClass.getMethod("setBlocks", Region.class, paramType);
      cache.put(extentClass, method);
      return method;
    } catch (Exception ignored) {
      cache.put(extentClass, null);
      return null;
    }
  }

  private static Method findSetBlocksSetMethod(Class<?> extentClass) {
    Method cached = SETBLOCKS_SET.get(extentClass);
    if (cached != null || SETBLOCKS_SET.containsKey(extentClass)) {
      return cached;
    }
    try {
      Method method =
          extentClass.getMethod(
              "setBlocks", Set.class, com.sk89q.worldedit.function.pattern.Pattern.class);
      SETBLOCKS_SET.put(extentClass, method);
      return method;
    } catch (Exception ignored) {
      SETBLOCKS_SET.put(extentClass, null);
      return null;
    }
  }

  private static Transform resolveTransform(Extent extent) {
    Transform combined = null;
    Extent current = extent;
    while (current != null) {
      Transform transform = null;
      if (current instanceof BlockTransformExtent blockTransformExtent) {
        transform = blockTransformExtent.getTransform();
      } else {
        TransformAccessor accessor = transformAccessor(current);
        if (accessor != null) {
          transform = accessor.get(current);
        }
      }
      if (transform != null && !transform.isIdentity()) {
        combined = combined == null ? transform : combined.combine(transform);
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        break;
      }
    }
    return combined;
  }

  private static FacingTransform resolveClipboardFacing(Actor actor) {
    if (actor == null) return null;
    try {
      LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
      if (session == null) return null;
      ClipboardHolder holder;
      try {
        holder = session.getClipboard();
      } catch (com.sk89q.worldedit.EmptyClipboardException ignored) {
        return null;
      }
      if (holder == null) return null;
      Transform transform = holder.getTransform();
      if (transform == null || transform.isIdentity()) return null;
      return face -> rotateFacing(face, transform);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static FacingTransform resolveFacingTransform(Extent extent, Transform transform) {
    if (transform != null && !transform.isIdentity()) {
      return face -> rotateFacing(face, transform);
    }
    return resolveFacingTransformFromPositions(extent);
  }

  private static FacingTransform resolveFacingTransformFromPositions(Extent extent) {
    if (extent == null) return null;
    BlockVector3 origin = BlockVector3.at(0, 0, 0);
    BlockVector3 originPos = resolvePositionForFacing(extent, origin);
    EnumMap<BlockFace, BlockFace> mapping = new EnumMap<>(BlockFace.class);
    boolean changed = false;
    for (BlockFace face : ROTATABLE_FACES) {
      BlockVector3 offset = offsetForFace(face);
      BlockVector3 transformed = resolvePositionForFacing(extent, origin.add(offset));
      BlockVector3 delta = transformed.subtract(originPos);
      BlockFace mapped = faceFromDelta(delta);
      if (mapped == null) {
        return null;
      }
      mapping.put(face, mapped);
      if (mapped != face) {
        changed = true;
      }
    }
    if (!changed) return null;
    return face -> mapping.getOrDefault(face, face);
  }

  private static BlockVector3 resolvePositionForFacing(Extent extent, BlockVector3 position) {
    BlockVector3 resolved = position;
    Extent current = extent;
    while (current != null) {
      BlockVector3 transformed = tryPositionTransform(current, resolved);
      if (transformed != null) {
        resolved = transformed;
      }
      TranslateAccessor accessor = translateAccessor(current);
      if (accessor != null) {
        int dx = accessor.dx(current);
        int dy = accessor.dy(current);
        int dz = accessor.dz(current);
        if (dx != 0 || dy != 0 || dz != 0) {
          resolved = resolved.add(dx, dy, dz);
        }
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        break;
      }
    }
    return resolved;
  }

  private static BlockVector3 offsetForFace(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockVector3.at(0, 0, -1);
      case SOUTH -> BlockVector3.at(0, 0, 1);
      case EAST -> BlockVector3.at(1, 0, 0);
      case WEST -> BlockVector3.at(-1, 0, 0);
      case UP -> BlockVector3.at(0, 1, 0);
      case DOWN -> BlockVector3.at(0, -1, 0);
      default -> BlockVector3.at(0, 0, 0);
    };
  }

  private static BlockFace faceFromDelta(BlockVector3 delta) {
    int x = delta.x();
    int y = delta.y();
    int z = delta.z();
    int ax = Math.abs(x);
    int ay = Math.abs(y);
    int az = Math.abs(z);
    if (ax == 0 && ay == 0 && az == 0) {
      return null;
    }
    if (ax >= ay && ax >= az) {
      return x >= 0 ? BlockFace.EAST : BlockFace.WEST;
    }
    if (ay >= ax && ay >= az) {
      return y >= 0 ? BlockFace.UP : BlockFace.DOWN;
    }
    return z >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
  }

  private static TransformAccessor transformAccessor(Object extent) {
    Class<?> type = extent.getClass();
    TransformAccessor cached = TRANSFORM_ACCESSORS.get(type);
    if (cached != null) return cached;
    if (TRANSFORM_SKIP.contains(type)) return null;
    try {
      Method method;
      try {
        method = type.getDeclaredMethod("getTransform");
      } catch (NoSuchMethodException ignored) {
        method = type.getMethod("getTransform");
      }
      if (Transform.class.isAssignableFrom(method.getReturnType())) {
        method.setAccessible(true);
        TransformAccessor accessor = new TransformAccessor(method, null);
        TRANSFORM_ACCESSORS.put(type, accessor);
        return accessor;
      }
    } catch (Exception ignored) {
      // fallback to field lookup
    }
    try {
      Field field = type.getDeclaredField("transform");
      if (Transform.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        TransformAccessor accessor = new TransformAccessor(null, field);
        TRANSFORM_ACCESSORS.put(type, accessor);
        return accessor;
      }
    } catch (Exception ignored) {
      // ignored
    }
    TRANSFORM_SKIP.add(type);
    return null;
  }

  private static BaseBlock buildVirtualBlock(LinCompoundTag exort) {
    LinCompoundTag.Builder rootBuilder = LinCompoundTag.builder();
    rootBuilder.putString(FIELD_NBT_ID, STRUCTURE_BLOCK_ID);
    rootBuilder.put(EXORT_TAG, exort);
    LinCompoundTag root = rootBuilder.build();
    LazyReference<LinCompoundTag> ref = LazyReference.computed(root);
    BlockType type = BlockTypes.STRUCTURE_BLOCK;
    if (type == null) {
      BlockType barrier = BlockTypes.BARRIER;
      if (barrier != null) {
        return barrier.getDefaultState().toBaseBlock(ref);
      }
      BlockType air = BlockTypes.AIR;
      if (air != null) {
        return air.getDefaultState().toBaseBlock(ref);
      }
      return null;
    }
    return type.getDefaultState().toBaseBlock(ref);
  }

  private static boolean containsMarkerExtent(Extent extent) {
    Extent current = extent;
    while (current != null) {
      if (current instanceof MarkerExtent) {
        return true;
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        return false;
      }
    }
    return false;
  }

  private static MarkerSnapshot rotateSnapshot(MarkerSnapshot snapshot, FacingTransform transform) {
    if (snapshot == null || transform == null) return snapshot;
    StorageData storage =
        snapshot.storage() == null
            ? null
            : new StorageData(
                snapshot.storage().storageId(),
                snapshot.storage().tier(),
                rotateFacing(snapshot.storage().facing(), transform));
    TerminalData terminal =
        snapshot.terminal() == null
            ? null
            : new TerminalData(
                snapshot.terminal().type(), rotateFacing(snapshot.terminal().facing(), transform));
    BusData bus =
        snapshot.bus() == null
            ? null
            : new BusData(
                snapshot.bus().type(),
                rotateFacing(snapshot.bus().facing(), transform),
                snapshot.bus().mode(),
                snapshot.bus().filters());
    MonitorData monitor =
        snapshot.monitor() == null
            ? null
            : new MonitorData(
                rotateFacing(snapshot.monitor().facing(), transform),
                snapshot.monitor().itemKey(),
                snapshot.monitor().itemBlob());
    return new MarkerSnapshot(
        storage, terminal, bus, monitor, snapshot.wire(), snapshot.storageCore());
  }

  private static MarkerSnapshot withStorageId(MarkerSnapshot snapshot, String storageId) {
    if (snapshot == null || snapshot.storage() == null) return snapshot;
    StorageData storage =
        new StorageData(storageId, snapshot.storage().tier(), snapshot.storage().facing());
    return new MarkerSnapshot(
        storage,
        snapshot.terminal(),
        snapshot.bus(),
        snapshot.monitor(),
        snapshot.wire(),
        snapshot.storageCore());
  }

  private void rememberHistory(UUID worldId, int x, int y, int z, MarkerSnapshot snapshot) {
    if (snapshot == null) return;
    markerHistory.put(
        new HistoryKey(worldId, x, y, z), new HistoryEntry(snapshot, System.currentTimeMillis()));
  }

  private MarkerSnapshot consumeHistory(UUID worldId, int x, int y, int z) {
    HistoryKey key = new HistoryKey(worldId, x, y, z);
    HistoryEntry entry = markerHistory.remove(key);
    if (entry == null) return null;
    long now = System.currentTimeMillis();
    if (now - entry.timestampMs() > HISTORY_TTL_MS) {
      return null;
    }
    return entry.snapshot();
  }

  private static String rotateFacing(String facing, FacingTransform transform) {
    if (facing == null || transform == null) return facing;
    BlockFace face;
    try {
      face = BlockFace.valueOf(facing);
    } catch (IllegalArgumentException ignored) {
      return facing;
    }
    BlockFace rotated = transform.apply(face);
    return rotated == null ? facing : rotated.name();
  }

  private static BlockFace rotateFacing(BlockFace face, Transform transform) {
    if (face == null || transform == null || transform.isIdentity()) return face;
    Vector3 dir =
        switch (face) {
          case NORTH -> Vector3.at(0, 0, -1);
          case SOUTH -> Vector3.at(0, 0, 1);
          case EAST -> Vector3.at(1, 0, 0);
          case WEST -> Vector3.at(-1, 0, 0);
          case UP -> Vector3.at(0, 1, 0);
          case DOWN -> Vector3.at(0, -1, 0);
          default -> null;
        };
    if (dir == null) return face;
    Vector3 origin = Vector3.ZERO;
    Vector3 transformedOrigin = transform.apply(origin);
    Vector3 transformedDir = transform.apply(dir).subtract(transformedOrigin);
    double ax = Math.abs(transformedDir.x());
    double ay = Math.abs(transformedDir.y());
    double az = Math.abs(transformedDir.z());
    if (ax < 1e-6 && ay < 1e-6 && az < 1e-6) {
      return face;
    }
    if (ax >= ay && ax >= az) {
      return transformedDir.x() >= 0 ? BlockFace.EAST : BlockFace.WEST;
    }
    if (ay >= ax && ay >= az) {
      return transformedDir.y() >= 0 ? BlockFace.UP : BlockFace.DOWN;
    }
    return transformedDir.z() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
  }

  private record StorageData(String storageId, String tier, String facing) implements FacingOwner {}

  private record TerminalData(String type, String facing) implements FacingOwner {}

  private record BusData(String type, String facing, String mode, byte[] filters)
      implements FacingOwner {}

  private record MonitorData(String facing, String itemKey, byte[] itemBlob)
      implements FacingOwner {}

  private interface FacingOwner {
    String facing();
  }

  @FunctionalInterface
  private interface FacingTransform {
    BlockFace apply(BlockFace face);
  }

  private record MarkerSnapshot(
      StorageData storage,
      TerminalData terminal,
      BusData bus,
      MonitorData monitor,
      boolean wire,
      boolean storageCore) {}

  private static LinCompoundTag readRootNbt(BaseBlock base) {
    if (base == null) return null;
    LazyReference<LinCompoundTag> ref = base.getNbtReference();
    if (ref != null) {
      LinCompoundTag root = ref.getValue();
      if (root != null) {
        return root;
      }
    }
    return readLegacyNbt(base);
  }

  @SuppressWarnings("deprecation")
  private static LinCompoundTag readLegacyNbt(BaseBlock base) {
    if (base == null) return null;
    com.sk89q.jnbt.CompoundTag legacy = base.getNbtData();
    if (legacy == null) return null;
    return legacy.toLinTag();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static BlockState withProperty(BlockState state, Property<?> property, String value) {
    Object resolved = property.getValueFor(value);
    return state.with((Property) property, resolved);
  }

  private record HistoryKey(UUID worldId, int x, int y, int z) {}

  private record HistoryEntry(MarkerSnapshot snapshot, long timestampMs) {}

  private record MarkerUpdate(
      UUID worldId, int x, int y, int z, MarkerSnapshot snapshot, String removedStorageId) {
    int chunkX() {
      return x >> 4;
    }

    int chunkZ() {
      return z >> 4;
    }
  }

  private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}

  private static final class ChunkSnapshot {
    private final Map<Long, LinCompoundTag> data;

    private ChunkSnapshot(Map<Long, LinCompoundTag> data) {
      this.data = data;
    }

    static ChunkSnapshot empty() {
      return new ChunkSnapshot(new HashMap<>());
    }

    boolean isEmpty() {
      return data.isEmpty();
    }

    LinCompoundTag get(long key) {
      return data.get(key);
    }

    void put(long key, LinCompoundTag tag) {
      if (tag == null) {
        data.remove(key);
      } else {
        data.put(key, tag);
      }
    }

    void remove(long key) {
      data.remove(key);
    }
  }

  private static final class ChunkUpdateBatch {
    private final ChunkKey key;
    private final Queue<PendingUpdate> updates = new ConcurrentLinkedQueue<>();

    private ChunkUpdateBatch(ChunkKey key) {
      this.key = key;
    }

    void add(PendingUpdate update) {
      updates.add(update);
    }
  }

  private static final class PendingUpdate {
    private MarkerUpdate update;
    private int attempts;
    private long nextTick;

    private PendingUpdate(MarkerUpdate update) {
      this.update = update;
    }
  }
}
