package com.zxcmc.exort.core.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.zxcmc.exort.core.ExortPlugin;
import com.zxcmc.exort.core.compat.PaperChorusPlantUpdates;
import com.zxcmc.exort.core.feedback.CommandFeedback;
import com.zxcmc.exort.core.i18n.Lang;
import com.zxcmc.exort.core.items.CustomItems;
import com.zxcmc.exort.core.logging.ExortLog;
import com.zxcmc.exort.core.task.PluginTasks;
import com.zxcmc.exort.debug.CacheDebugService;
import com.zxcmc.exort.debug.PickDebugService;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.gui.GuiSession;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTier;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

public final class ExortBrigadier {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String PERMISSION_GIVE = "exort.storagenetwork.give";
  private static final String ARG_PLAYER = "player";
  private static final String ARG_STORAGE_ID = "storageId";
  private static final String ARG_TIER = "tier";
  private static final String ARG_AMOUNT = "amount";
  private static final String ARG_LANG = "lang";
  private static final String ARG_MODE = "mode";
  private static final String ARG_PACK_TARGET = "packTarget";
  private static final String ARG_PLAYERS = "players";
  private static final String ARG_SECONDS = "seconds";
  private static final String ARG_VERBOSE_MODE = "verboseMode";
  private static final int MAX_GIVE_AMOUNT = 512;
  private static final float GIVE_SOUND_VOLUME = 0.2F;
  private static final float GIVE_SOUND_PITCH = 1.0F;

  private final ExortPlugin plugin;
  private final DebugCacheStatusRenderer cacheStatusRenderer;

  public ExortBrigadier(ExortPlugin plugin) {
    this.plugin = plugin;
    this.cacheStatusRenderer = new DebugCacheStatusRenderer(plugin);
  }

  public LiteralCommandNode<CommandSourceStack> build() {
    LiteralArgumentBuilder<CommandSourceStack> root =
        Commands.literal("exort")
            .requires(source -> hasAnyPermission(sender(source)))
            .executes(this::help);
    LiteralArgumentBuilder<CommandSourceStack> cacheVerbose =
        Commands.literal("cache")
            .then(
                Commands.literal("start")
                    .executes(ctx -> cacheVerboseStart(ctx, null, null))
                    .then(
                        Commands.literal("storage")
                            .then(
                                Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                                    .executes(
                                        ctx ->
                                            cacheVerboseStart(
                                                ctx,
                                                null,
                                                StringArgumentType.getString(
                                                    ctx, ARG_STORAGE_ID)))))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    cacheVerboseStart(
                                        ctx,
                                        StringArgumentType.getString(ctx, ARG_VERBOSE_MODE),
                                        null))
                            .then(
                                Commands.literal("storage")
                                    .then(
                                        Commands.argument(ARG_STORAGE_ID, StringArgumentType.word())
                                            .executes(
                                                ctx ->
                                                    cacheVerboseStart(
                                                        ctx,
                                                        StringArgumentType.getString(
                                                            ctx, ARG_VERBOSE_MODE),
                                                        StringArgumentType.getString(
                                                            ctx, ARG_STORAGE_ID)))))))
            .then(Commands.literal("stop").executes(this::cacheVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> worldEditVerbose =
        Commands.literal("worldedit")
            .then(
                Commands.literal("start")
                    .executes(ctx -> worldEditVerboseStart(ctx, null))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    worldEditVerboseStart(
                                        ctx, StringArgumentType.getString(ctx, ARG_VERBOSE_MODE)))))
            .then(Commands.literal("stop").executes(this::worldEditVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> pickVerbose =
        Commands.literal("pick")
            .then(
                Commands.literal("start")
                    .executes(ctx -> pickVerboseStart(ctx, null))
                    .then(
                        Commands.argument(ARG_VERBOSE_MODE, StringArgumentType.word())
                            .suggests(this::suggestVerboseModes)
                            .executes(
                                ctx ->
                                    pickVerboseStart(
                                        ctx, StringArgumentType.getString(ctx, ARG_VERBOSE_MODE)))))
            .then(Commands.literal("stop").executes(this::pickVerboseStop));

    LiteralArgumentBuilder<CommandSourceStack> verbose =
        Commands.literal("verbose").then(cacheVerbose).then(worldEditVerbose).then(pickVerbose);

    LiteralArgumentBuilder<CommandSourceStack> debug =
        Commands.literal("debug")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::usageDebug)
            .then(
                Commands.literal("player")
                    .then(
                        Commands.argument(ARG_PLAYER, StringArgumentType.word())
                            .suggests(this::suggestPlayers)
                            .executes(this::debugPlayer)))
            .then(verbose)
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
                    .then(Commands.literal("stop").executes(this::loadTestStop)));

    root.then(debug);

    root.then(
        Commands.literal("give")
            .requires(source -> hasGivePermission(sender(source)))
            .executes(this::openGiveMenu)
            .then(
                Commands.argument(ARG_PLAYER, StringArgumentType.word())
                    .suggests(this::suggestPlayers)
                    .executes(this::usageGive)
                    .then(
                        Commands.literal("storage")
                            .then(
                                Commands.argument(ARG_TIER, StringArgumentType.word())
                                    .suggests(this::suggestTiers)
                                    .executes(ctx -> giveStorage(ctx, 1))
                                    .then(
                                        Commands.argument(
                                                ARG_AMOUNT,
                                                IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                            .executes(
                                                ctx ->
                                                    giveStorage(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(
                                                            ctx, ARG_AMOUNT))))))
                    .then(
                        Commands.literal("storage_core")
                            .executes(ctx -> giveStorageCore(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveStorageCore(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("terminal")
                            .executes(ctx -> giveTerminal(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveTerminal(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("crafting_terminal")
                            .executes(ctx -> giveCraftingTerminal(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveCraftingTerminal(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("monitor")
                            .executes(ctx -> giveMonitor(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveMonitor(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("import_bus")
                            .executes(ctx -> giveImportBus(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveImportBus(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("export_bus")
                            .executes(ctx -> giveExportBus(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveExportBus(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("wire")
                            .executes(ctx -> giveWire(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveWire(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, ARG_AMOUNT)))))
                    .then(
                        Commands.literal("wireless_terminal")
                            .executes(ctx -> giveWireless(ctx, 1))
                            .then(
                                Commands.argument(
                                        ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
                                    .executes(
                                        ctx ->
                                            giveWireless(
                                                ctx,
                                                IntegerArgumentType.getInteger(
                                                    ctx, ARG_AMOUNT)))))));

    root.then(
        Commands.literal("reload")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::reload));

    root.then(
        Commands.literal("lang")
            .requires(source -> hasAdminPermission(sender(source)))
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
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::usageMode)
            .then(Commands.literal("info").executes(this::modeInfo))
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument(ARG_MODE, StringArgumentType.word())
                            .suggests(this::suggestModes)
                            .executes(this::modeSet)))
            .then(
                Commands.literal("fix")
                    .executes(this::usageMode)
                    .then(
                        Commands.argument(ARG_MODE, StringArgumentType.word())
                            .suggests(this::suggestResourceMode)
                            .executes(this::modeFix))));

    root.then(
        Commands.literal("pack")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::usagePack)
            .then(Commands.literal("status").executes(this::packStatus))
            .then(Commands.literal("rebuild").executes(this::packRebuild))
            .then(
                Commands.literal("send")
                    .executes(ctx -> packSend(ctx, "@self"))
                    .then(Commands.literal("all").executes(ctx -> packSend(ctx, "all")))
                    .then(
                        Commands.argument(ARG_PACK_TARGET, StringArgumentType.word())
                            .suggests(this::suggestPackTargets)
                            .executes(
                                ctx ->
                                    packSend(
                                        ctx,
                                        StringArgumentType.getString(ctx, ARG_PACK_TARGET))))));

    root.then(
        Commands.literal("version")
            .requires(source -> hasAdminPermission(sender(source)))
            .executes(this::version));

    return root.build();
  }

  private int help(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (!hasAnyPermission(sender)) {
      sendMessage(sender, plugin.getLang().tr("message.no_permission"));
      return 0;
    }
    Lang lang = plugin.getLang();
    sendMessage(sender, lang.tr("message.help_header"));
    if (hasAdminPermission(sender)) {
      sendMessage(sender, lang.tr("message.help_debug", "exort"));
    }
    sendMessage(sender, lang.tr("message.help_give", "exort"));
    if (hasAdminPermission(sender)) {
      sendMessage(sender, lang.tr("message.help_reload", "exort"));
      sendMessage(sender, lang.tr("message.help_lang", "exort"));
      sendMessage(sender, lang.tr("message.help_mode", "exort"));
      sendMessage(sender, lang.tr("message.help_pack", "exort"));
      sendMessage(sender, lang.tr("message.help_version", "exort"));
    }
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
      runSync(() -> cacheStatusRenderer.send(sender, finalStorageId));
      return 1;
    }
    Player online = Bukkit.getPlayerExact(raw);
    OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayer(raw);
    if (offline == null || (offline.getName() == null && !offline.hasPlayedBefore())) {
      sendMessage(sender, plugin.getLang().tr("message.debug_storage_invalid"));
      return 1;
    }
    String playerName = offline.getName() != null ? offline.getName() : raw;
    plugin
        .getDatabase()
        .getPlayerLastStorage(offline.getUniqueId())
        .whenComplete(
            (result, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "read player storage history", err);
                return;
              }
              runSync(
                  () -> {
                    if (result.isEmpty()) {
                      sendMessage(
                          sender, plugin.getLang().tr("message.debug_player_none", playerName));
                      return;
                    }
                    cacheStatusRenderer.send(sender, result.get().storageId());
                  });
            });
    return 1;
  }

  private int usageDebug(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(sender(context.getSource()), plugin.getLang().tr("message.usage_debug"));
    return 1;
  }

  private int cacheVerboseStart(
      CommandContext<CommandSourceStack> context, String rawMode, String rawStorage) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = CacheDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, plugin.getLang().tr("message.debug_cache_mode_invalid", rawMode));
      return 0;
    }
    UUID filter = null;
    if (rawStorage != null && !rawStorage.isBlank()) {
      try {
        filter = UUID.fromString(rawStorage.trim());
      } catch (IllegalArgumentException e) {
        sendMessage(sender, plugin.getLang().tr("message.debug_cache_storage_invalid", rawStorage));
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
    sendMessage(sender, plugin.getLang().tr("message.debug_cache_started", modeName, filterText));
    return 1;
  }

  private int cacheVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin.getCacheDebugService().stop(sender);
    sendMessage(sender, plugin.getLang().tr("message.debug_cache_stopped"));
    return 1;
  }

  private int worldEditVerboseStart(CommandContext<CommandSourceStack> context, String rawMode) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = WorldEditDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, plugin.getLang().tr("message.debug_worldedit_mode_invalid", rawMode));
      return 0;
    }
    plugin.getWorldEditDebugService().start(sender, mode);
    String modeName =
        (mode == null ? plugin.getWorldEditDebugService().getMode() : mode)
            .name()
            .toLowerCase(Locale.ROOT);
    sendMessage(sender, plugin.getLang().tr("message.debug_worldedit_started", modeName));
    return 1;
  }

  private int worldEditVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin.getWorldEditDebugService().stop(sender);
    sendMessage(sender, plugin.getLang().tr("message.debug_worldedit_stopped"));
    return 1;
  }

  private int pickVerboseStart(CommandContext<CommandSourceStack> context, String rawMode) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    var mode = PickDebugService.Mode.fromString(rawMode);
    if (rawMode != null && mode == null) {
      sendMessage(sender, plugin.getLang().tr("message.debug_pick_mode_invalid", rawMode));
      return 0;
    }
    plugin.getPickDebugService().start(sender, mode);
    String modeName =
        (mode == null ? plugin.getPickDebugService().getMode() : mode)
            .name()
            .toLowerCase(Locale.ROOT);
    sendMessage(sender, plugin.getLang().tr("message.debug_pick_started", modeName));
    return 1;
  }

  private int pickVerboseStop(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin.getPickDebugService().stop(sender);
    sendMessage(sender, plugin.getLang().tr("message.debug_pick_stopped"));
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
    if (!ensureGivePermission(context)) return 0;
    sendMessage(sender(context.getSource()), plugin.getLang().tr("message.give_usage"));
    return 1;
  }

  private int openGiveMenu(CommandContext<CommandSourceStack> context) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    if (!(sender instanceof Player player)) {
      sendMessage(sender, plugin.getLang().tr("message.only_player"));
      return 1;
    }
    try {
      new ExortGiveMenu(plugin).open(player);
    } catch (IllegalStateException e) {
      ExortLog.log(plugin, Level.WARNING, "Failed to open Exort give menu", e);
      sendMessage(sender, plugin.getLang().tr("message.operation_failed"));
    }
    return 1;
  }

  private int reload(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin
        .reloadRuntime()
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "reload runtime", err);
                return;
              }
              runSync(() -> sendMessage(sender, plugin.getLang().tr("message.reload")));
            });
    return 1;
  }

  private int usageLang(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(sender(context.getSource()), plugin.getLang().tr("message.usage_lang"));
    return 1;
  }

  private int usageMode(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(sender(context.getSource()), plugin.getLang().tr("message.usage_mode"));
    return 1;
  }

  private int usagePack(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(sender(context.getSource()), plugin.getLang().tr("message.usage_pack"));
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
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "refresh language dictionaries", err);
                return;
              }
              runSync(
                  () -> {
                    plugin.getLang().reload(status.activeLanguage());
                    sendMessage(sender, plugin.getLang().tr("message.lang_refreshed"));
                  });
            });
    return 1;
  }

  private int langStatus(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    var status = plugin.getItemNameService().status();
    CommandSender sender = sender(context.getSource());
    Lang lang = plugin.getLang();
    sendMessage(sender, lang.tr("message.lang_status_header"));
    sendMessage(sender, lang.tr("message.lang_status_active", status.activeLanguage()));
    sendMessage(sender, lang.tr("message.lang_status_server", status.serverVersion()));
    sendMessage(sender, lang.tr("message.lang_status_paths", "Exort/lang", "Exort/lang/items"));
    String indexLine =
        status.indexCached()
            ? lang.tr("message.lang_status_index_cached", status.availableLanguages())
            : lang.tr("message.lang_status_index_missing");
    sendMessage(sender, indexLine);
    if (status.indexFetched()) {
      sendMessage(sender, lang.tr("message.lang_status_index_fetched"));
    }
    if (!status.dictVersions().isEmpty()) {
      for (var entry : status.dictVersions().entrySet()) {
        int size = status.dictSizes().getOrDefault(entry.getKey(), 0);
        sendMessage(
            sender, lang.tr("message.lang_status_dict", entry.getKey(), entry.getValue(), size));
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
      sendMessage(sender, plugin.getLang().tr("message.lang_invalid", normalized));
      return 1;
    }
    plugin.getConfig().set("language", normalized);
    plugin.saveConfig();
    plugin
        .reloadRuntime()
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "set language", err);
                return;
              }
              runSync(
                  () ->
                      sendMessage(
                          sender,
                          plugin.getLang().tr("message.lang_set", status.activeLanguage())));
            });
    return 1;
  }

  private int modeInfo(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    sendMessage(
        sender,
        plugin
            .getLang()
            .tr("message.mode_info", plugin.getConfiguredMode(), plugin.getEffectiveMode()));
    if (!plugin.getModeFallbackReason().isBlank()) {
      sendMessage(
          sender, plugin.getLang().tr("message.mode_fallback", plugin.getModeFallbackReason()));
    }
    return 1;
  }

  private int modeSet(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_MODE);
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (!normalized.equals("VANILLA") && !normalized.equals("RESOURCE")) {
      sendMessage(sender(context.getSource()), plugin.getLang().tr("message.mode_invalid", raw));
      return 1;
    }
    setMode(sender, normalized);
    return 1;
  }

  private int modeFix(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String raw = StringArgumentType.getString(context, ARG_MODE);
    String normalized = raw.toUpperCase(Locale.ROOT);
    if (!normalized.equals("RESOURCE")) {
      sendMessage(sender, plugin.getLang().tr("message.mode_fix_resource_only"));
      return 1;
    }

    PaperChorusPlantUpdates.Status status = plugin.chorusPlantUpdateStatus();
    if (status.state() == PaperChorusPlantUpdates.State.MISSING) {
      sendMessage(
          sender, plugin.getLang().tr("message.mode_fix_paper_missing", status.file().getPath()));
      return 1;
    }
    if (status.state() == PaperChorusPlantUpdates.State.ERROR) {
      sendPaperFixError(sender, status.file().getPath(), status.accessDenied(), status.error());
      return 1;
    }
    if (status.disabled()) {
      setMode(sender, "RESOURCE");
      return 1;
    }

    PaperChorusPlantUpdates.FixResult result = plugin.disableChorusPlantUpdatesInPaperConfig();
    if (result.state() == PaperChorusPlantUpdates.State.MISSING) {
      sendMessage(
          sender, plugin.getLang().tr("message.mode_fix_paper_missing", result.file().getPath()));
      return 1;
    }
    if (result.state() == PaperChorusPlantUpdates.State.ERROR) {
      sendPaperFixError(sender, result.file().getPath(), result.accessDenied(), result.error());
      return 1;
    }
    if (!result.changed()) {
      setMode(sender, "RESOURCE");
      return 1;
    }

    plugin.getConfig().set("mode", "RESOURCE");
    plugin.saveConfig();
    notifyModeFixRestart(sender, result.file().getPath());
    scheduleRestartAfterModeFix();
    return 1;
  }

  private void sendPaperFixError(
      CommandSender sender, String paperConfigPath, boolean accessDenied, String reason) {
    if (accessDenied) {
      sendMessage(
          sender,
          plugin
              .getLang()
              .tr(
                  "message.mode_fix_paper_access_denied",
                  paperConfigPath,
                  PaperChorusPlantUpdates.SETTING_PATH));
      return;
    }
    sendMessage(
        sender,
        plugin
            .getLang()
            .tr(
                "message.mode_fix_paper_error",
                paperConfigPath,
                reason,
                PaperChorusPlantUpdates.SETTING_PATH));
  }

  private void setMode(CommandSender sender, String normalized) {
    plugin.getConfig().set("mode", normalized);
    plugin.saveConfig();
    plugin
        .reloadRuntime()
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "set mode", err);
                return;
              }
              runSync(
                  () -> {
                    sendMessage(
                        sender,
                        plugin
                            .getLang()
                            .tr("message.mode_set", normalized, plugin.getEffectiveMode()));
                    if (!plugin.getModeFallbackReason().isBlank()) {
                      sendMessage(
                          sender,
                          plugin
                              .getLang()
                              .tr("message.mode_fallback", plugin.getModeFallbackReason()));
                    }
                  });
            });
  }

  private void notifyModeFixRestart(CommandSender sender, String paperConfigPath) {
    sendToSenderAndPlayers(
        sender,
        plugin
            .getLang()
            .tr(
                "message.mode_fix_paper_changed",
                PaperChorusPlantUpdates.SETTING_PATH,
                paperConfigPath));
    sendToSenderAndPlayers(sender, plugin.getLang().tr("message.mode_fix_exort_changed"));
    sendToSenderAndPlayers(sender, plugin.getLang().tr("message.mode_fix_restart_scheduled"));
  }

  private void sendToSenderAndPlayers(CommandSender sender, String message) {
    sendMessage(sender, message);
    Set<UUID> skippedPlayers = new HashSet<>();
    if (sender instanceof Player player) {
      skippedPlayers.add(player.getUniqueId());
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (skippedPlayers.contains(player.getUniqueId())) {
        continue;
      }
      sendMessage(player, message);
    }
  }

  private void scheduleRestartAfterModeFix() {
    Bukkit.getScheduler().runTaskLater(plugin, this::restartWithStopFallback, 200L);
  }

  private void restartWithStopFallback() {
    if (!plugin.isEnabled()) {
      return;
    }
    boolean restarted = false;
    try {
      restarted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
    } catch (RuntimeException e) {
      ExortLog.log(plugin, Level.WARNING, "Failed to dispatch restart; falling back to stop.", e);
    }
    if (restarted) {
      return;
    }
    ExortLog.warn("Restart command was unavailable or failed; falling back to stop.");
    try {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
    } catch (RuntimeException e) {
      ExortLog.log(plugin, Level.SEVERE, "Failed to dispatch stop after restart fallback.", e);
    }
  }

  private int packStatus(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    var service = plugin.getResourcePackService();
    CommandSender sender = sender(context.getSource());
    if (service == null) {
      sendMessage(
          sender,
          plugin
              .getLang()
              .tr(
                  "message.pack_unavailable",
                  plugin.getLang().tr("message.pack_service_not_started")));
      return 1;
    }
    service.statusLines().forEach(line -> sendMessage(sender, line));
    return 1;
  }

  private int packRebuild(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    plugin.reloadResourcePackService();
    sendMessage(sender, plugin.getLang().tr("message.pack_rebuilt"));
    packStatus(context);
    return 1;
  }

  private int packSend(CommandContext<CommandSourceStack> context, String target) {
    if (!ensurePermission(context)) return 0;
    var service = plugin.getResourcePackService();
    CommandSender sender = sender(context.getSource());
    if (service == null || !service.dispatchReady()) {
      String reason =
          service == null
              ? plugin.getLang().tr("message.pack_service_not_started")
              : service.unavailableReason();
      sendMessage(sender, plugin.getLang().tr("message.pack_unavailable", reason));
      return 1;
    }
    if ("all".equalsIgnoreCase(target)) {
      int sent = service.sendAll();
      sendMessage(sender, plugin.getLang().tr("message.pack_sent_all", sent));
      return 1;
    }
    Player player;
    if ("@self".equals(target) && sender instanceof Player self) {
      player = self;
    } else {
      player = Bukkit.getPlayerExact(target);
    }
    if (player == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    if (service.send(player)) {
      sendMessage(sender, plugin.getLang().tr("message.pack_sent", player.getName()));
    } else {
      sendMessage(
          sender, plugin.getLang().tr("message.pack_unavailable", service.unavailableReason()));
    }
    return 1;
  }

  private int version(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    sendMessage(
        sender(context.getSource()),
        plugin.getLang().tr("message.version", plugin.getPluginMeta().getVersion()));
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
        sendMessage(
            sender,
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
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    String playerName = offline.getName() != null ? offline.getName() : name;
    plugin
        .getDatabase()
        .getPlayerLastStorage(offline.getUniqueId())
        .whenComplete(
            (result, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "read player storage history", err);
                return;
              }
              runSync(
                  () -> {
                    if (result.isEmpty()) {
                      sendMessage(
                          sender, plugin.getLang().tr("message.debug_player_none", playerName));
                      return;
                    }
                    var data = result.get();
                    sendMessage(
                        sender,
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
                  });
            });
    return 1;
  }

  private int debugStorage(CommandContext<CommandSourceStack> context, boolean write) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    if (!(sender instanceof Player player)) {
      sendMessage(sender, plugin.getLang().tr("message.only_player"));
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
        sendMessage(sender, plugin.getLang().tr("message.debug_storage_invalid"));
        return 1;
      }
      String playerName = offline.getName() != null ? offline.getName() : raw;
      plugin
          .getDatabase()
          .getPlayerLastStorage(offline.getUniqueId())
          .whenComplete(
              (result, err) -> {
                if (err != null) {
                  sendAsyncFailure(sender, "read player storage history", err);
                  return;
                }
                runSync(
                    () -> {
                      if (result.isEmpty()) {
                        sendMessage(
                            sender, plugin.getLang().tr("message.debug_player_none", playerName));
                        return;
                      }
                      openStorageById(result.get().storageId(), write, player, sender);
                    });
              });
    }
    return 1;
  }

  private void openStorageById(
      String storageId, boolean write, Player viewer, CommandSender feedback) {
    plugin
        .getDatabase()
        .getStorageTier(storageId)
        .thenCompose(
            optTier -> {
              if (optTier.isEmpty()) {
                return CompletableFuture.completedFuture(new DebugStorageOpen(optTier, null));
              }
              return plugin
                  .getStorageManager()
                  .getOrLoad(storageId)
                  .thenApply(cache -> new DebugStorageOpen(optTier, cache));
            })
        .whenComplete(
            (open, err) -> {
              if (err != null) {
                sendAsyncFailure(feedback, "open debug storage " + storageId, err);
                return;
              }
              runSync(
                  () -> {
                    if (open.optTier().isEmpty() || open.cache() == null) {
                      sendMessage(
                          feedback,
                          plugin.getLang().tr("message.debug_storage_missing", storageId));
                      return;
                    }
                    StorageTier tier = StorageTier.fromString(open.optTier().get()).orElse(null);
                    if (tier == null) {
                      sendMessage(
                          feedback,
                          plugin.getLang().tr("message.debug_storage_missing", storageId));
                      return;
                    }
                    if (!viewer.isOnline()) return;
                    plugin.getSessionManager().openDebugSession(viewer, open.cache(), tier, write);
                    sendMessage(
                        feedback,
                        plugin
                            .getLang()
                            .tr(
                                "message.debug_storage_opened",
                                storageId,
                                write
                                    ? plugin.getLang().tr("debug.mode.write")
                                    : plugin.getLang().tr("debug.mode.read")));
                  });
            });
  }

  private void runSync(Runnable task) {
    PluginTasks.runSyncIfEnabled(plugin, task);
  }

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(plugin, Level.WARNING, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, plugin.getLang().tr("message.operation_failed")));
  }

  private void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }

  private void sendMessage(CommandSender sender, Component message) {
    CommandFeedback.send(sender, message);
  }

  private record DebugStorageOpen(Optional<String> optTier, StorageCache cache) {}

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
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    String tierArg = StringArgumentType.getString(context, ARG_TIER).toLowerCase(Locale.ROOT);
    var tierOpt = StorageTier.fromString(tierArg);
    if (tierOpt.isEmpty()) {
      sendMessage(sender, plugin.getLang().tr("message.give_unknown"));
      return 1;
    }
    StorageTier tier = tierOpt.get();
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    CustomItems items = plugin.getCustomItems();
    sendGiveResult(
        sender,
        target,
        tier.displayName(),
        giveAmount,
        CommandItemDelivery.deliver(target, () -> items.storageItem(tier, null), giveAmount));
    return 1;
  }

  private int giveStorageCore(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.storage_core");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getCustomItems().storageCoreItem(), giveAmount));
    return 1;
  }

  private int giveTerminal(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.terminal");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getCustomItems().terminalItem(), giveAmount));
    return 1;
  }

  private int giveCraftingTerminal(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.crafting_terminal");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getCustomItems().craftingTerminalItem(), giveAmount));
    return 1;
  }

  private int giveMonitor(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.monitor");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getCustomItems().monitorItem(), giveAmount));
    return 1;
  }

  private int giveImportBus(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.import_bus");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getCustomItems().importBusItem(), giveAmount));
    return 1;
  }

  private int giveExportBus(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.export_bus");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getCustomItems().exportBusItem(), giveAmount));
    return 1;
  }

  private int giveWire(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.wire");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(target, () -> plugin.getCustomItems().wireItem(), giveAmount));
    return 1;
  }

  private int giveWireless(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, plugin.getLang().tr("message.player_not_found"));
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = plugin.getLang().tr("item.wireless_terminal");
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> plugin.getWirelessService().create(), giveAmount));
    return 1;
  }

  private void sendGiveResult(
      CommandSender sender,
      Player target,
      String itemName,
      int requested,
      CommandItemDelivery.Result result) {
    if (result.total() > 0) {
      target.playSound(
          target.getLocation(), Sound.ENTITY_ITEM_PICKUP, GIVE_SOUND_VOLUME, GIVE_SOUND_PITCH);
    }
    sendMessage(
        sender,
        plugin.getLang().tr("message.give_success", result.total(), itemName, target.getName()));
    if (result.dropped() > 0) {
      sendMessage(
          sender, plugin.getLang().tr("message.give_dropped", result.dropped(), target.getName()));
    }
    if (result.total() < requested) {
      sendMessage(
          sender,
          plugin.getLang().tr("message.give_partial", result.total(), requested, target.getName()));
    }
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

  private CompletableFuture<Suggestions> suggestResourceMode(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = List.of("RESOURCE");
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toUpperCase(Locale.ROOT), options, new ArrayList<>());
    matches.forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> suggestPackTargets(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> options = new ArrayList<>();
    options.add("all");
    for (Player player : Bukkit.getOnlinePlayers()) {
      options.add(player.getName());
    }
    List<String> matches =
        StringUtil.copyPartialMatches(
            builder.getRemaining().toLowerCase(Locale.ROOT), options, new ArrayList<>());
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
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, plugin.getLang().tr("message.no_permission"));
    return false;
  }

  private boolean ensureGivePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasGivePermission(sender)) return true;
    sendMessage(sender, plugin.getLang().tr("message.no_permission"));
    return false;
  }

  private static boolean hasAnyPermission(CommandSender sender) {
    return hasGivePermission(sender);
  }

  private static boolean hasGivePermission(CommandSender sender) {
    return hasAdminPermission(sender) || sender.hasPermission(PERMISSION_GIVE);
  }

  private static boolean hasAdminPermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }
}
