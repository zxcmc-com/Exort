package com.zxcmc.exort.core.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.marker.BusMarker;
import com.zxcmc.exort.core.marker.MarkerCoords;
import com.zxcmc.exort.core.marker.MonitorMarker;
import com.zxcmc.exort.core.marker.TerminalMarker;
import com.zxcmc.exort.core.network.TerminalLinkFinder;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.storage.StorageTier;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

public final class ExortBrigadier {
  private static final String ARG_PLAYER = "player";
  private static final String ARG_STORAGE_ID = "storageId";
  private static final String ARG_TIER = "tier";
  private static final String ARG_AMOUNT = "amount";
  private static final String ARG_LANG = "lang";
  private static final String ARG_MODE = "mode";
  private static final String ARG_PLAYERS = "players";
  private static final String ARG_SECONDS = "seconds";
  private static final String ARG_VERBOSE_MODE = "verboseMode";

  private final ExortPlugin plugin;

  public ExortBrigadier(ExortPlugin plugin) {
    this.plugin = plugin;
  }

  public LiteralCommandNode<CommandSourceStack> build() {
    LiteralArgumentBuilder<CommandSourceStack> root =
        Commands.literal("exort")
            .requires(source -> sender(source).hasPermission("exort.storagenetwork.admin"))
            .executes(this::help);

    root.then(
        Commands.literal("debug")
            .executes(this::usageDebug)
            .then(
                Commands.literal("player")
                    .then(
                        Commands.argument(ARG_PLAYER, StringArgumentType.word())
                            .suggests(this::suggestPlayers)
                            .executes(this::debugPlayer)))
            .then(
                Commands.literal("verbose")
                    .then(
                        Commands.literal("cache")
                            .then(
                                Commands.literal("start")
                                    .executes(ctx -> cacheVerboseStart(ctx, null, null))
                                    .then(
                                        Commands.literal("storage")
                                            .then(
                                                Commands.argument(
                                                        ARG_STORAGE_ID, StringArgumentType.word())
                                                    .executes(
                                                        ctx ->
                                                            cacheVerboseStart(
                                                                ctx,
                                                                null,
                                                                StringArgumentType.getString(
                                                                    ctx, ARG_STORAGE_ID)))))
                                    .then(
                                        Commands.argument(
                                                ARG_VERBOSE_MODE, StringArgumentType.word())
                                            .suggests(this::suggestVerboseModes)
                                            .executes(
                                                ctx ->
                                                    cacheVerboseStart(
                                                        ctx,
                                                        StringArgumentType.getString(
                                                            ctx, ARG_VERBOSE_MODE),
                                                        null))
                                            .then(
                                                Commands.literal("storage")
                                                    .then(
                                                        Commands.argument(
                                                                ARG_STORAGE_ID,
                                                                StringArgumentType.word())
                                                            .executes(
                                                                ctx ->
                                                                    cacheVerboseStart(
                                                                        ctx,
                                                                        StringArgumentType
                                                                            .getString(
                                                                                ctx,
                                                                                ARG_VERBOSE_MODE),
                                                                        StringArgumentType
                                                                            .getString(
                                                                                ctx,
                                                                                ARG_STORAGE_ID)))))))
                            .then(Commands.literal("stop").executes(this::cacheVerboseStop))))
            .then(
                Commands.literal("cache")
                    .then(
                        Commands.literal("status")
                            .then(
                                Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                                    .suggests(this::suggestPlayers)
                                    .executes(this::cacheStatus))))
            .then(
                Commands.literal("storage")
                    .then(
                        Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                            .suggests(this::suggestPlayers)
                            .executes(ctx -> debugStorage(ctx, false))
                            .then(
                                Commands.literal("write")
                                    .executes(ctx -> debugStorage(ctx, true)))))
            .then(
                Commands.literal("benchmark")
                    .then(
                        Commands.literal("start")
                            .executes(ctx -> loadTestStart(ctx, -1, 0))
                            .then(
                                Commands.argument(ARG_PLAYERS, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            loadTestStart(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_PLAYERS),
                                                0))
                                    .then(
                                        Commands.argument(
                                                ARG_SECONDS, IntegerArgumentType.integer(1))
                                            .executes(
                                                ctx ->
                                                    loadTestStart(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(
                                                            ctx, ARG_PLAYERS),
                                                        IntegerArgumentType.getInteger(
                                                            ctx, ARG_SECONDS))))))
                    .then(Commands.literal("stop").executes(this::loadTestStop))));

    root.then(
        Commands.literal("give")
            .executes(this::usageGive)
            .then(
                Commands.argument(ARG_PLAYER, StringArgumentType.word())
                    .suggests(this::suggestPlayers)
                    .then(
                        Commands.literal("storage")
                            .then(
                                Commands.argument(ARG_TIER, StringArgumentType.word())
                                    .suggests(this::suggestTiers)
                                    .executes(ctx -> giveStorage(ctx, 1))
                                    .then(
                                        Commands.argument(
                                                ARG_AMOUNT, IntegerArgumentType.integer(1))
                                            .executes(
                                                ctx ->
                                                    giveStorage(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(
                                                            ctx, ARG_AMOUNT))))))
                    .then(
                        Commands.literal("terminal")
                            .executes(ctx -> giveTerminal(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveTerminal(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("crafting_terminal")
                            .executes(ctx -> giveCraftingTerminal(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveCraftingTerminal(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("monitor")
                            .executes(ctx -> giveMonitor(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveMonitor(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("import_bus")
                            .executes(ctx -> giveImportBus(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveImportBus(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("export_bus")
                            .executes(ctx -> giveExportBus(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveExportBus(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("wire")
                            .executes(ctx -> giveWire(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveWire(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("wireless_terminal")
                            .executes(ctx -> giveWireless(ctx, 1))
                            .then(
                                Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1))
                                    .executes(
                                        ctx ->
                                            giveWireless(
                                                ctx,
                                                IntegerArgumentType.getInteger(
                                                    ctx, ARG_AMOUNT)))))));

    root.then(Commands.literal("reload").executes(this::reload));

    root.then(
        Commands.literal("lang")
            .executes(this::usageLang)
            .then(Commands.literal("status").executes(this::langStatus))
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument(ARG_LANG, StringArgumentType.word())
                            .suggests(this::suggestLangs)
                            .executes(this::langSet)))
            .then(Commands.literal("refresh").executes(this::langRefresh)));

    root.then(
        Commands.literal("mode")
            .executes(this::usageMode)
            .then(Commands.literal("info").executes(this::modeInfo))
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument(ARG_MODE, StringArgumentType.word())
                            .suggests(this::suggestModes)
                            .executes(this::modeSet))));

    root.then(Commands.literal("version").executes(this::version));

    return root.build();
  }

  private int help(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    Lang lang = plugin.getLang();
    sender.sendMessage(lang.tr("message.help_header"));
    sender.sendMessage(lang.tr("message.help_debug", "exort"));
    sender.sendMessage(lang.tr("message.help_give", "exort"));
    sender.sendMessage(lang.tr("message.help_reload", "exort"));
    sender.sendMessage(lang.tr("message.help_lang", "exort"));
    sender.sendMessage(lang.tr("message.help_mode", "exort"));
    sender.sendMessage(lang.tr("message.help_version", "exort"));
    return 1;
  }

  private int cacheStatus(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_STORAGE_ID);
    String storageId = null;
    try {
      storageId = UUID.fromString(raw).toString();
    } catch (IllegalArgumentException ignored) {
      storageId = null;
    }
    if (storageId != null) {
      String finalStorageId = storageId;
      Bukkit.getScheduler().runTask(plugin, () -> sendCacheStatus(sender, finalStorageId));
      return 1;
    }
    Player online = Bukkit.getPlayerExact(raw);
    OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(raw);
    if (offline == null || (offline.getName() == null && !offline.hasPlayedBefore())) {
      sender.sendMessage(plugin.getLang().tr("message.debug_storage_invalid"));
      return 1;
    }
    String playerName = offline.getName() != null ? offline.getName() : raw;
    plugin
        .getDatabase()
        .getPlayerLastStorage(offline.getUniqueId())
        .thenAccept(
            result ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (result.isEmpty()) {
                            sender.sendMessage(
                                plugin.getLang().tr("message.debug_player_none", playerName));
                            return;
                          }
                          sendCacheStatus(sender, result.get().storageId());
                        }));
    return 1;
  }

  private void sendCacheStatus(CommandSender sender, String storageId) {
    Lang lang = plugin.getLang();
    sender.sendMessage(lang.tr("message.debug_cache_status_header", storageId));
    var storageManager = plugin.getStorageManager();
    var cacheOpt = storageManager.getCache(storageId);
    boolean loading = storageManager.isLoading(storageId);
    long now = System.currentTimeMillis();
    if (cacheOpt.isEmpty() || !cacheOpt.get().isLoaded()) {
      sender.sendMessage(lang.tr("message.debug_cache_status_cache_unloaded"));
    } else {
      var cache = cacheOpt.get();
      long idleMs = Math.max(0L, now - cache.lastAccessMs());
      long idleThresholdMs =
          Math.max(0L, plugin.getConfig().getLong("cache.idleUnloadSeconds", 300L) * 1000L);
      boolean dirty = cache.isDirty();
      int viewers = cache.viewerCount();
      sender.sendMessage(
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
        sender.sendMessage(
            lang.tr(
                "message.debug_cache_status_touch",
                String.valueOf(touchAge),
                String.valueOf(touchAge / 1000L),
                touchSource));
      }
      boolean eligible = idleMs >= idleThresholdMs && !dirty && viewers <= 0 && !loading;
      sender.sendMessage(
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
      sender.sendMessage(lang.tr("message.debug_cache_status_marker_missing"));
      return;
    }
    int cx = loc.getBlockX() >> 4;
    int cz = loc.getBlockZ() >> 4;
    sender.sendMessage(
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
      sender.sendMessage(lang.tr("message.debug_cache_status_chunk_unloaded"));
      ConnectionStats stats = findLoadedConnections(storageId);
      sender.sendMessage(
          lang.tr(
              "message.debug_cache_status_connections",
              String.valueOf(stats.terminals),
              String.valueOf(stats.monitors),
              String.valueOf(stats.buses),
              String.valueOf(stats.total())));
      if (stats.total() == 0) {
        sender.sendMessage(lang.tr("message.debug_cache_status_connections_empty"));
      }
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
    sender.sendMessage(
        lang.tr(
            "message.debug_cache_status_chunk",
            String.valueOf(chunk.isLoaded()),
            String.valueOf(chunk.getLoadLevel()),
            String.valueOf(chunk.isForceLoaded()),
            ticketNames,
            playerNames));
    String reason = buildChunkReason(chunk, ticketNames, playerNames);
    sender.sendMessage(lang.tr("message.debug_cache_status_chunk_reason", reason));
    ConnectionStats stats = findLoadedConnections(storageId);
    sender.sendMessage(
        lang.tr(
            "message.debug_cache_status_connections",
            String.valueOf(stats.terminals),
            String.valueOf(stats.monitors),
            String.valueOf(stats.buses),
            String.valueOf(stats.total())));
    if (stats.total() == 0) {
      sender.sendMessage(lang.tr("message.debug_cache_status_connections_empty"));
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

  private record ConnectionStats(int terminals, int monitors, int buses) {
    int total() {
      return terminals + monitors + buses;
    }
  }

  private ConnectionStats findLoadedConnections(String storageId) {
    if (storageId == null || storageId.isBlank()) return new ConnectionStats(0, 0, 0);
    int terminals = 0;
    int monitors = 0;
    int buses = 0;
    String namespace = plugin.getName().toLowerCase(Locale.ROOT);
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        var pdc = chunk.getPersistentDataContainer();
        for (var key : pdc.getKeys()) {
          if (!namespace.equals(key.getNamespace())) continue;
          String rawKey = key.getKey();
          if (rawKey.startsWith("terminal_")) {
            if (isLinkedTerminal(world, rawKey, storageId)) terminals++;
          } else if (rawKey.startsWith("monitor_")) {
            if (isLinkedMonitor(world, rawKey, storageId)) monitors++;
          } else if (rawKey.startsWith("bus_")) {
            if (isLinkedBus(world, rawKey, storageId)) buses++;
          }
        }
      }
    }
    return new ConnectionStats(terminals, monitors, buses);
  }

  private boolean isLinkedTerminal(World world, String rawKey, String storageId) {
    int[] xyz = MarkerCoords.parseXYZ(rawKey.substring("terminal_".length()));
    if (xyz == null) return false;
    Block block = world.getBlockAt(xyz[0], xyz[1], xyz[2]);
    if (!TerminalMarker.isTerminal(plugin, block)) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            plugin.getKeys(),
            plugin,
            plugin.getWireLimit(),
            plugin.getWireHardCap(),
            plugin.getWireMaterial(),
            plugin.getStorageCarrier());
    return result.count() == 1
        && result.data() != null
        && storageId.equals(result.data().storageId());
  }

  private boolean isLinkedMonitor(World world, String rawKey, String storageId) {
    int[] xyz = MarkerCoords.parseXYZ(rawKey.substring("monitor_".length()));
    if (xyz == null) return false;
    Block block = world.getBlockAt(xyz[0], xyz[1], xyz[2]);
    if (!MonitorMarker.isMonitor(plugin, block)) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            plugin.getKeys(),
            plugin,
            plugin.getWireLimit(),
            plugin.getWireHardCap(),
            plugin.getWireMaterial(),
            plugin.getStorageCarrier());
    return result.count() == 1
        && result.data() != null
        && storageId.equals(result.data().storageId());
  }

  private boolean isLinkedBus(World world, String rawKey, String storageId) {
    int[] xyz = MarkerCoords.parseXYZ(rawKey.substring("bus_".length()));
    if (xyz == null) return false;
    Block block = world.getBlockAt(xyz[0], xyz[1], xyz[2]);
    if (!BusMarker.isBus(plugin, block)) return false;
    var result =
        TerminalLinkFinder.find(
            block,
            plugin.getKeys(),
            plugin,
            plugin.getWireLimit(),
            plugin.getWireHardCap(),
            plugin.getWireMaterial(),
            plugin.getStorageCarrier());
    return result.count() == 1
        && result.data() != null
        && storageId.equals(result.data().storageId());
  }

  private Location findLoadedStorageLocation(String storageId) {
    if (storageId == null || storageId.isBlank()) return null;
    String namespace = plugin.getName().toLowerCase(Locale.ROOT);
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        var pdc = chunk.getPersistentDataContainer();
        for (var key : pdc.getKeys()) {
          if (!namespace.equals(key.getNamespace())) continue;
          String rawKey = key.getKey();
          if (!rawKey.startsWith("storage_")) continue;
          String raw = pdc.get(key, PersistentDataType.STRING);
          if (raw == null || raw.isBlank()) continue;
          String[] parts = raw.split(":");
          if (parts.length < 2) continue;
          if (!storageId.equals(parts[0])) continue;
          int[] xyz = MarkerCoords.parseXYZ(rawKey.substring("storage_".length()));
          if (xyz == null) continue;
          return new Location(world, xyz[0], xyz[1], xyz[2]);
        }
      }
    }
    return null;
  }

  private int usageDebug(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sender(context.getSource()).sendMessage(plugin.getLang().tr("message.usage_debug"));
    return 1;
  }

  private int cacheVerboseStart(
      CommandContext<CommandSourceStack> context, String rawMode, String rawStorage) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = CacheDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sender.sendMessage(plugin.getLang().tr("message.debug_cache_mode_invalid", rawMode));
      return 0;
    }
    UUID filter = null;
    if (rawStorage != null && !rawStorage.isBlank()) {
      try {
        filter = UUID.fromString(rawStorage.trim());
      } catch (IllegalArgumentException e) {
        sender.sendMessage(plugin.getLang().tr("message.debug_cache_storage_invalid", rawStorage));
        return 0;
      }
    }
    plugin.getCacheDebugService().start(sender, mode, filter);
    String modeName =
        (mode == null ? plugin.getCacheDebugService().getMode() : mode)
            .name()
            .toLowerCase(Locale.ROOT);
    String filterText =
        filter == null ? plugin.getLang().tr("message.debug_cache_filter_none") : filter.toString();
    sender.sendMessage(plugin.getLang().tr("message.debug_cache_started", modeName, filterText));
    return 1;
  }

  private int cacheVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin.getCacheDebugService().stop(sender);
    sender.sendMessage(plugin.getLang().tr("message.debug_cache_stopped"));
    return 1;
  }

  private int loadTestStart(
      CommandContext<CommandSourceStack> context, int players, int durationSeconds) {
    if (!ensurePermission(context)) return 0;
    plugin.getLoadTestService().start(sender(context.getSource()), players, durationSeconds);
    return 1;
  }

  private int loadTestStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    plugin.getLoadTestService().stop(true);
    return 1;
  }

  private int usageGive(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sender(context.getSource()).sendMessage(plugin.getLang().tr("message.give_usage"));
    return 1;
  }

  private int reload(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin.reloadRuntime().thenRun(() -> sender.sendMessage(plugin.getLang().tr("message.reload")));
    return 1;
  }

  private int usageLang(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sender(context.getSource()).sendMessage(plugin.getLang().tr("message.usage_lang"));
    return 1;
  }

  private int usageMode(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sender(context.getSource()).sendMessage(plugin.getLang().tr("message.usage_mode"));
    return 1;
  }

  private int langRefresh(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String langCode = plugin.getConfig().getString("language", "en_us");
    String normalized = plugin.getItemNameService().normalizeLanguage(langCode);
    plugin
        .getItemNameService()
        .refresh(normalized)
        .thenAccept(
            status -> {
              plugin.getLang().reload(status.activeLanguage());
              sender.sendMessage(plugin.getLang().tr("message.lang_refreshed"));
            });
    return 1;
  }

  private int langStatus(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    var status = plugin.getItemNameService().status();
    CommandSender sender = sender(context.getSource());
    Lang lang = plugin.getLang();
    sender.sendMessage(lang.tr("message.lang_status_header"));
    sender.sendMessage(lang.tr("message.lang_status_active", status.activeLanguage()));
    sender.sendMessage(lang.tr("message.lang_status_server", status.serverVersion()));
    sender.sendMessage(lang.tr("message.lang_status_paths", "Exort/lang", "Exort/lang/items"));
    String indexLine =
        status.indexCached()
            ? lang.tr("message.lang_status_index_cached", status.availableLanguages())
            : lang.tr("message.lang_status_index_missing");
    sender.sendMessage(indexLine);
    if (status.indexFetched()) {
      sender.sendMessage(lang.tr("message.lang_status_index_fetched"));
    }
    if (!status.dictVersions().isEmpty()) {
      for (var entry : status.dictVersions().entrySet()) {
        int size = status.dictSizes().getOrDefault(entry.getKey(), 0);
        sender.sendMessage(
            lang.tr("message.lang_status_dict", entry.getKey(), entry.getValue(), size));
      }
    }
    return 1;
  }

  private int langSet(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String lang = StringArgumentType.getString(context, ARG_LANG);
    String normalized = plugin.getItemNameService().normalizeLanguage(stripLangExtension(lang));
    if (!plugin.getItemNameService().isKnownLanguage(normalized)) {
      sender(context.getSource())
          .sendMessage(plugin.getLang().tr("message.lang_invalid", normalized));
      return 1;
    }
    plugin.getConfig().set("language", normalized);
    plugin.saveConfig();
    plugin
        .reloadRuntime()
        .thenAccept(
            status ->
                sender.sendMessage(
                    plugin.getLang().tr("message.lang_set", status.activeLanguage())));
    return 1;
  }

  private int modeInfo(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    String current = plugin.getConfig().getString("mode", "VANILLA").toUpperCase(Locale.ROOT);
    sender(context.getSource()).sendMessage(plugin.getLang().tr("message.mode_info", current));
    return 1;
  }

  private int modeSet(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_MODE);
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (!normalized.equals("VANILLA") && !normalized.equals("RESOURCE")) {
      sender(context.getSource()).sendMessage(plugin.getLang().tr("message.mode_invalid", raw));
      return 1;
    }
    if (normalized.equals("RESOURCE") && !plugin.canEnableResourceMode()) {
      sender(context.getSource()).sendMessage(plugin.getLang().tr("message.mode_blocked"));
      return 1;
    }
    plugin.getConfig().set("mode", normalized);
    plugin.saveConfig();
    plugin
        .reloadRuntime()
        .thenRun(() -> sender.sendMessage(plugin.getLang().tr("message.mode_set", normalized)));
    return 1;
  }

  private int version(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sender(context.getSource())
        .sendMessage(plugin.getLang().tr("message.version", plugin.getPluginMeta().getVersion()));
    return 1;
  }

  private int debugPlayer(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    String name = StringArgumentType.getString(context, ARG_PLAYER);
    CommandSender sender = sender(context.getSource());
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      GuiSession session = plugin.getSessionManager().sessionFor(online);
      if (session != null && session.getStorageLocation() != null) {
        var loc = session.getStorageLocation();
        String storageId = session.getStorageId();
        sender.sendMessage(
            clickableDebugPlayer(
                plugin
                    .getLang()
                    .tr(
                        "message.debug_player_active",
                        online.getName(),
                        storageId,
                        session.getTier().key(),
                        loc.getWorld().getName(),
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ()),
                storageId));
        return 1;
      }
    }

    var offline = Bukkit.getOfflinePlayer(name);
    if (offline == null || offline.getUniqueId() == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    String playerName = offline.getName() != null ? offline.getName() : name;
    plugin
        .getDatabase()
        .getPlayerLastStorage(offline.getUniqueId())
        .thenAccept(
            result ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (result.isEmpty()) {
                            sender.sendMessage(
                                plugin.getLang().tr("message.debug_player_none", playerName));
                            return;
                          }
                          var data = result.get();
                          sender.sendMessage(
                              clickableDebugPlayer(
                                  plugin
                                      .getLang()
                                      .tr(
                                          "message.debug_player_last",
                                          playerName,
                                          data.storageId(),
                                          data.tier(),
                                          data.world(),
                                          data.x(),
                                          data.y(),
                                          data.z()),
                                  data.storageId()));
                        }));
    return 1;
  }

  private int debugStorage(CommandContext<CommandSourceStack> context, boolean write) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLang().tr("message.only_player"));
      return 1;
    }
    String raw = StringArgumentType.getString(context, ARG_STORAGE_ID);
    try {
      UUID.fromString(raw);
      openStorageById(raw, write, player, sender);
    } catch (IllegalArgumentException ex) {
      Player online = Bukkit.getPlayerExact(raw);
      OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(raw);
      if (offline == null || (offline.getName() == null && !offline.hasPlayedBefore())) {
        sender.sendMessage(plugin.getLang().tr("message.debug_storage_invalid"));
        return 1;
      }
      String playerName = offline.getName() != null ? offline.getName() : raw;
      plugin
          .getDatabase()
          .getPlayerLastStorage(offline.getUniqueId())
          .thenAccept(
              result ->
                  Bukkit.getScheduler()
                      .runTask(
                          plugin,
                          () -> {
                            if (result.isEmpty()) {
                              sender.sendMessage(
                                  plugin.getLang().tr("message.debug_player_none", playerName));
                              return;
                            }
                            openStorageById(result.get().storageId(), write, player, sender);
                          }));
    }
    return 1;
  }

  private void openStorageById(
      String storageId, boolean write, Player viewer, CommandSender feedback) {
    plugin
        .getDatabase()
        .getStorageTier(storageId)
        .thenAccept(
            optTier ->
                Bukkit.getScheduler()
                    .runTask(
                        plugin,
                        () -> {
                          if (optTier.isEmpty()) {
                            feedback.sendMessage(
                                plugin.getLang().tr("message.debug_storage_missing", storageId));
                            return;
                          }
                          StorageTier tier = StorageTier.fromString(optTier.get()).orElse(null);
                          if (tier == null) {
                            feedback.sendMessage(
                                plugin.getLang().tr("message.debug_storage_missing", storageId));
                            return;
                          }
                          plugin
                              .getStorageManager()
                              .getOrLoad(storageId)
                              .thenAccept(
                                  cache ->
                                      Bukkit.getScheduler()
                                          .runTask(
                                              plugin,
                                              () -> {
                                                plugin
                                                    .getSessionManager()
                                                    .openDebugSession(viewer, cache, tier, write);
                                                feedback.sendMessage(
                                                    plugin
                                                        .getLang()
                                                        .tr(
                                                            "message.debug_storage_opened",
                                                            storageId,
                                                            write
                                                                ? plugin
                                                                    .getLang()
                                                                    .tr("debug.mode.write")
                                                                : plugin
                                                                    .getLang()
                                                                    .tr("debug.mode.read")));
                                              }));
                        }));
  }

  private Component clickableDebugPlayer(String message, String storageId) {
    // Replace the first occurrence of the storageId with a clickable/hoverable component.
    int idx = message.indexOf(storageId);
    if (idx < 0) {
      return Component.text(message);
    }
    Component before = Component.text(message.substring(0, idx));
    Component idComponent =
        Component.text(storageId)
            .clickEvent(ClickEvent.suggestCommand("/exort debug storage " + storageId))
            .hoverEvent(
                net.kyori.adventure.text.event.HoverEvent.showText(
                    Component.text(plugin.getLang().tr("message.debug_player_click", storageId))));
    Component after = Component.text(message.substring(idx + storageId.length()));
    return before.append(idComponent).append(after);
  }

  private int giveStorage(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    String tierArg = StringArgumentType.getString(context, ARG_TIER).toLowerCase(Locale.ROOT);
    var tierOpt = StorageTier.fromString(tierArg);
    if (tierOpt.isEmpty()) {
      sender.sendMessage(plugin.getLang().tr("message.give_unknown"));
      return 1;
    }
    StorageTier tier = tierOpt.get();
    int giveAmount = Math.max(1, amount);
    CustomItems items = plugin.getCustomItems();
    for (int i = 0; i < giveAmount; i++) {
      target.getInventory().addItem(items.storageItem(tier, null));
    }
    sender.sendMessage(
        plugin
            .getLang()
            .tr("message.give_success", giveAmount, tier.displayName(), target.getName()));
    return 1;
  }

  private int giveTerminal(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    ItemStack item = plugin.getCustomItems().terminalItem();
    item.setAmount(giveAmount);
    target.getInventory().addItem(item);
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.terminal"),
                target.getName()));
    return 1;
  }

  private int giveCraftingTerminal(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    ItemStack item = plugin.getCustomItems().craftingTerminalItem();
    item.setAmount(giveAmount);
    target.getInventory().addItem(item);
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.crafting_terminal"),
                target.getName()));
    return 1;
  }

  private int giveMonitor(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    ItemStack item = plugin.getCustomItems().monitorItem();
    item.setAmount(giveAmount);
    target.getInventory().addItem(item);
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.monitor"),
                target.getName()));
    return 1;
  }

  private int giveImportBus(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    ItemStack item = plugin.getCustomItems().importBusItem();
    item.setAmount(giveAmount);
    target.getInventory().addItem(item);
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.import_bus"),
                target.getName()));
    return 1;
  }

  private int giveExportBus(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    ItemStack item = plugin.getCustomItems().exportBusItem();
    item.setAmount(giveAmount);
    target.getInventory().addItem(item);
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.export_bus"),
                target.getName()));
    return 1;
  }

  private int giveWire(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    ItemStack item = plugin.getCustomItems().wireItem();
    item.setAmount(giveAmount);
    target.getInventory().addItem(item);
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.wire"),
                target.getName()));
    return 1;
  }

  private int giveWireless(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sender.sendMessage(plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = Math.max(1, amount);
    for (int i = 0; i < giveAmount; i++) {
      ItemStack item = plugin.getWirelessService().create();
      target.getInventory().addItem(item);
    }
    sender.sendMessage(
        plugin
            .getLang()
            .tr(
                "message.give_success",
                giveAmount,
                plugin.getLang().tr("item.wireless_terminal"),
                target.getName()));
    return 1;
  }

  private CompletableFuture<Suggestions> suggestPlayers(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      options.add(player.getName());
    }
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestTiers(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = new ArrayList<>();
    for (StorageTier tier : StorageTier.allTiers()) {
      options.add(tier.key().toLowerCase(Locale.ROOT));
    }
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestLangs(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = new ArrayList<>(plugin.getItemNameService().localLanguages());
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestVerboseModes(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = List.of("compact", "normal", "full");
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestModes(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = List.of("VANILLA", "RESOURCE");
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toUpperCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private String stripLangExtension(String input) {
    String trimmed = input.trim();
    if (trimmed.endsWith(".yml")) {
      return trimmed.substring(0, trimmed.length() - 4);
    }
    return trimmed;
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (sender.hasPermission("exort.storagenetwork.admin")) return true;
    sender.sendMessage(plugin.getLang().tr("message.no_permission"));
    return false;
  }

  private CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }
}
