package com.zxcmc.exort.command;

import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.keys.StorageKeys;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.network.TerminalLinkFinder;
import com.zxcmc.exort.storage.StorageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

final class DebugCacheStatusRenderer {
  private final JavaPlugin plugin;
  private final Lang lang;
  private final StorageManager storageManager;
  private final StorageKeys keys;
  private final LongSupplier cacheIdleUnloadSeconds;
  private final IntSupplier wireLimit;
  private final IntSupplier wireHardCap;
  private final Supplier<Material> wireMaterial;
  private final Supplier<Material> storageCarrier;

  DebugCacheStatusRenderer(DebugCacheStatusRendererDependencies dependencies) {
    this.plugin = dependencies.plugin();
    this.lang = dependencies.lang();
    this.storageManager = dependencies.storageManager();
    this.keys = dependencies.keys();
    this.cacheIdleUnloadSeconds = dependencies.cacheIdleUnloadSeconds();
    this.wireLimit = dependencies.wireLimit();
    this.wireHardCap = dependencies.wireHardCap();
    this.wireMaterial = dependencies.wireMaterial();
    this.storageCarrier = dependencies.storageCarrier();
  }

  void send(CommandSender sender, String storageId) {
    List<String> lines = new ArrayList<>();
    lines.add(lang.tr("message.debug_cache_status_header", storageId));
    var cacheOpt = storageManager.getCache(storageId);
    boolean loading = storageManager.isLoading(storageId);
    long now = System.currentTimeMillis();
    if (cacheOpt.isEmpty() || !cacheOpt.get().isLoaded()) {
      lines.add(lang.tr("message.debug_cache_status_cache_unloaded"));
    } else {
      var cache = cacheOpt.get();
      long idleMs = Math.max(0L, now - cache.lastAccessMs());
      long idleThresholdMs = Math.max(0L, cacheIdleUnloadSeconds.getAsLong() * 1000L);
      boolean dirty = cache.isDirty();
      int viewers = cache.viewerCount();
      lines.add(
          lang.tr(
              "message.debug_cache_status_cache",
              "true",
              String.valueOf(dirty),
              String.valueOf(viewers),
              String.valueOf(idleMs),
              String.valueOf(idleMs / 1000L)));
      String touchSource = cache.lastTouchSource();
      if (touchSource != null) {
        long touchAge = Math.max(0L, now - cache.lastTouchMs());
        lines.add(
            lang.tr(
                "message.debug_cache_status_touch",
                String.valueOf(touchAge),
                String.valueOf(touchAge / 1000L),
                touchSource));
      }
      boolean eligible = idleMs >= idleThresholdMs && !dirty && viewers <= 0 && !loading;
      lines.add(
          lang.tr(
              "message.debug_cache_status_evict",
              String.valueOf(eligible),
              String.valueOf(idleMs),
              String.valueOf(idleThresholdMs),
              String.valueOf(dirty),
              String.valueOf(viewers),
              String.valueOf(loading)));
    }

    Location loc = findLoadedStorageLocation(storageId);
    if (loc == null || loc.getWorld() == null) {
      lines.add(lang.tr("message.debug_cache_status_marker_missing"));
      sendLines(sender, lines);
      return;
    }
    int cx = loc.getBlockX() >> 4;
    int cz = loc.getBlockZ() >> 4;
    lines.add(
        lang.tr(
            "message.debug_cache_status_marker",
            loc.getWorld().getName(),
            String.valueOf(loc.getBlockX()),
            String.valueOf(loc.getBlockY()),
            String.valueOf(loc.getBlockZ()),
            String.valueOf(cx),
            String.valueOf(cz)));
    boolean loaded = loc.getWorld().isChunkLoaded(cx, cz);
    if (!loaded) {
      lines.add(lang.tr("message.debug_cache_status_chunk_unloaded"));
      ConnectionStats stats = findLoadedConnections(storageId);
      lines.add(
          lang.tr(
              "message.debug_cache_status_connections",
              String.valueOf(stats.terminals()),
              String.valueOf(stats.monitors()),
              String.valueOf(stats.buses()),
              String.valueOf(stats.total())));
      if (stats.total() == 0) {
        lines.add(lang.tr("message.debug_cache_status_connections_empty"));
      }
      sendLines(sender, lines);
      return;
    }
    Chunk chunk = loc.getWorld().getChunkAt(cx, cz);
    var tickets = chunk.getPluginChunkTickets();
    String ticketNames =
        tickets.isEmpty()
            ? "-"
            : tickets.stream()
                .map(Plugin::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + ", " + b)
                .orElse("-");
    var players = chunk.getPlayersSeeingChunk();
    String playerNames =
        players.isEmpty()
            ? "-"
            : players.stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + ", " + b)
                .orElse("-");
    lines.add(
        lang.tr(
            "message.debug_cache_status_chunk",
            String.valueOf(chunk.isLoaded()),
            String.valueOf(chunk.getLoadLevel()),
            String.valueOf(chunk.isForceLoaded()),
            ticketNames,
            playerNames));
    String reason = buildChunkReason(chunk, ticketNames, playerNames);
    lines.add(lang.tr("message.debug_cache_status_chunk_reason", reason));
    ConnectionStats stats = findLoadedConnections(storageId);
    lines.add(
        lang.tr(
            "message.debug_cache_status_connections",
            String.valueOf(stats.terminals()),
            String.valueOf(stats.monitors()),
            String.valueOf(stats.buses()),
            String.valueOf(stats.total())));
    if (stats.total() == 0) {
      lines.add(lang.tr("message.debug_cache_status_connections_empty"));
    }
    sendLines(sender, lines);
  }

  private void sendLines(CommandSender sender, List<String> lines) {
    if (!lines.isEmpty()) {
      CommandFeedback.sendBlock(sender, lines.get(0), lines.subList(1, lines.size()));
    }
  }

  private String buildChunkReason(Chunk chunk, String ticketNames, String playerNames) {
    List<String> reasons = new ArrayList<>();
    if (chunk.isForceLoaded()) {
      reasons.add("forceLoaded");
    }
    if (ticketNames != null && !ticketNames.equals("-")) {
      reasons.add("pluginTickets=" + ticketNames);
    }
    if (playerNames != null && !playerNames.equals("-")) {
      reasons.add("players=" + playerNames);
    }
    String spawnReason = spawnReason(chunk);
    if (spawnReason != null) {
      reasons.add(spawnReason);
    }
    if (reasons.isEmpty()) {
      reasons.add("unknown (spawn/region/other)");
    }
    return String.join(", ", reasons);
  }

  private String spawnReason(Chunk chunk) {
    if (chunk == null || chunk.getWorld() == null) return null;
    World world = chunk.getWorld();
    Location spawn = world.getSpawnLocation();
    int spawnCx = spawn.getBlockX() >> 4;
    int spawnCz = spawn.getBlockZ() >> 4;
    int radiusBlocks = Bukkit.getServer().getSpawnRadius();
    int radiusChunks = Math.max(1, (int) Math.ceil(radiusBlocks / 16.0));
    int dx = Math.abs(chunk.getX() - spawnCx);
    int dz = Math.abs(chunk.getZ() - spawnCz);
    if (dx <= radiusChunks && dz <= radiusChunks) {
      return "spawn(radiusChunks=" + radiusChunks + ")";
    }
    return null;
  }

  private ConnectionStats findLoadedConnections(String storageId) {
    if (storageId == null || storageId.isBlank()) return new ConnectionStats(0, 0, 0);
    int[] counts = new int[3];
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) continue;
        ChunkMarkerStore.forEachBlock(
            plugin,
            chunk,
            (block, root) -> {
              if (TerminalMarker.isTerminal(plugin, block)) {
                if (isLinkedTerminal(block, storageId)) counts[0]++;
                return;
              }
              if (MonitorMarker.isMonitor(plugin, block)) {
                if (isLinkedMonitor(block, storageId)) counts[1]++;
                return;
              }
              if (BusMarker.isBus(plugin, block)) {
                if (isLinkedBus(block, storageId)) counts[2]++;
              }
            });
      }
    }
    return new ConnectionStats(counts[0], counts[1], counts[2]);
  }

  private boolean isLinkedTerminal(Block block, String storageId) {
    if (!TerminalMarker.isTerminal(plugin, block)) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            keys,
            plugin,
            wireLimit.getAsInt(),
            wireHardCap.getAsInt(),
            wireMaterial.get(),
            storageCarrier.get());
    return result.count() == 1
        && result.data() != null
        && storageId.equals(result.data().storageId());
  }

  private boolean isLinkedMonitor(Block block, String storageId) {
    if (!MonitorMarker.isMonitor(plugin, block)) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            keys,
            plugin,
            wireLimit.getAsInt(),
            wireHardCap.getAsInt(),
            wireMaterial.get(),
            storageCarrier.get());
    return result.count() == 1
        && result.data() != null
        && storageId.equals(result.data().storageId());
  }

  private boolean isLinkedBus(Block block, String storageId) {
    if (!BusMarker.isBus(plugin, block)) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            keys,
            plugin,
            wireLimit.getAsInt(),
            wireHardCap.getAsInt(),
            wireMaterial.get(),
            storageCarrier.get());
    return result.count() == 1
        && result.data() != null
        && storageId.equals(result.data().storageId());
  }

  private Location findLoadedStorageLocation(String storageId) {
    if (storageId == null || storageId.isBlank()) return null;
    var result = new AtomicReference<Location>();
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) continue;
        ChunkMarkerStore.forEachBlock(
            plugin,
            chunk,
            (block, root) -> {
              if (result.get() != null) return;
              var data = StorageMarker.get(plugin, block).orElse(null);
              if (data == null) return;
              if (!storageId.equals(data.storageId())) return;
              result.set(block.getLocation());
            });
        if (result.get() != null) return result.get();
      }
    }
    return result.get();
  }

  private record ConnectionStats(int terminals, int monitors, int buses) {
    int total() {
      return terminals + monitors + buses;
    }
  }
}
