package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.i18n.ItemNameService;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

final class LangCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String ARG_LANG = "lang";
  private static final int COMPACT_DICTIONARY_LIST_THRESHOLD = 6;

  private final ExortBrigadierDependencies dependencies;

  LangCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  LiteralArgumentBuilder<CommandSourceStack> build(String literal) {
    return Commands.literal(literal)
        .requires(source -> hasAdminPermission(sender(source)))
        .executes(this::usage)
        .then(Commands.literal("status").executes(this::status))
        .then(
            Commands.literal("set")
                .then(
                    Commands.argument(ARG_LANG, StringArgumentType.word())
                        .suggests(this::suggestLangs)
                        .executes(this::set)))
        .then(Commands.literal("refresh").executes(this::refresh));
  }

  private int usage(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    CommandFeedback.sendBlock(
        sender,
        Component.text(dependencies.lang().tr(sender, "message.usage_language_header")),
        List.of(
            usageLine(sender, "/exort language status", "message.usage_language_status"),
            usageLine(sender, "/exort language set <lang>", "message.usage_language_set"),
            usageLine(sender, "/exort language refresh", "message.usage_language_refresh")));
    return 1;
  }

  private int refresh(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String langCode = dependencies.configuredLanguage().get();
    String normalized = dependencies.itemNameService().normalizeLanguage(langCode);
    dependencies
        .itemNameService()
        .refresh(normalized)
        .whenComplete(
            (status, err) -> {
              if (err != null) {
                sendAsyncFailure(sender, "refresh language dictionaries", err);
                return;
              }
              runSync(
                  () -> {
                    dependencies.lang().reload(normalized);
                    sendMessage(sender, dependencies.lang().tr(sender, "message.lang_refreshed"));
                  });
            });
    return 1;
  }

  private int status(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    var status = dependencies.itemNameService().status();
    CommandSender sender = sender(context.getSource());
    Lang lang = dependencies.lang();
    var lines = new ArrayList<String>();
    lines.add(lang.tr(sender, "message.lang_status_active", status.activeLanguage()));
    lines.add(lang.tr(sender, "message.lang_status_server", status.serverVersion()));
    lines.add(lang.tr(sender, "message.lang_status_paths", "Exort/lang", "Exort/lang/items"));
    String indexLine =
        status.indexCached()
            ? lang.tr(sender, "message.lang_status_index_cached", status.availableLanguages())
            : lang.tr(sender, "message.lang_status_index_missing");
    lines.add(indexLine);
    if (status.indexFetched()) {
      lines.add(lang.tr(sender, "message.lang_status_index_fetched"));
    }
    lines.addAll(dictionaryStatusLines(lang, sender, status));
    CommandFeedback.sendBlock(sender, lang.tr(sender, "message.lang_status_header"), lines);
    return 1;
  }

  static List<String> dictionaryStatusLines(
      Lang lang, CommandSender sender, ItemNameService.Status status) {
    return dictionaryStatusEntries(status).stream().map(line -> line.render(lang, sender)).toList();
  }

  static List<DictionaryStatusLine> dictionaryStatusEntries(ItemNameService.Status status) {
    if (status == null || status.dictVersions().isEmpty()) {
      return List.of();
    }
    List<String> languages = new ArrayList<>(status.dictVersions().keySet());
    languages.sort(String::compareTo);
    if (languages.size() >= COMPACT_DICTIONARY_LIST_THRESHOLD) {
      return List.of(
          new DictionaryStatusLine(
              "message.lang_status_dict_compact",
              List.of(languages.size(), String.join(", ", languages))));
    }
    List<DictionaryStatusLine> lines = new ArrayList<>(languages.size());
    for (String language : languages) {
      int size = status.dictSizes().getOrDefault(language, 0);
      lines.add(
          new DictionaryStatusLine(
              "message.lang_status_dict",
              List.of(language, status.dictVersions().get(language), size)));
    }
    return lines;
  }

  record DictionaryStatusLine(String key, List<Object> args) {
    DictionaryStatusLine {
      args = args == null ? List.of() : List.copyOf(args);
    }

    String render(Lang lang, CommandSender sender) {
      return lang.tr(sender, key, args.toArray());
    }
  }

  private Component usageLine(CommandSender sender, String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command,
        dependencies.lang().tr(sender, descriptionKey),
        dependencies.lang().tr(sender, "message.command_click", command));
  }

  private int set(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String lang = StringArgumentType.getString(context, ARG_LANG);
    String normalized = dependencies.lang().normalizeLanguage(stripLangExtension(lang));
    if (!dependencies.lang().hasLanguage(normalized)) {
      sendMessage(sender, dependencies.lang().tr(sender, "message.lang_invalid", normalized));
      return 1;
    }
    dependencies.configuredLanguageSaver().accept(normalized);
    dependencies
        .runtimeReloader()
        .get()
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
                          dependencies
                              .lang()
                              .tr(
                                  sender,
                                  "message.lang_set",
                                  dependencies.lang().configuredLanguage())));
            });
    return 1;
  }

  private CompletableFuture<Suggestions> suggestLangs(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    var options = new ArrayList<>(dependencies.lang().availableLanguages());
    var matches =
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

  private void sendAsyncFailure(CommandSender sender, String action, Throwable err) {
    ExortLog.log(dependencies.plugin(), Level.SEVERE, "Failed to " + action, err);
    runSync(() -> sendMessage(sender, dependencies.lang().tr(sender, "message.operation_failed")));
  }

  private void runSync(Runnable action) {
    PluginTasks.runSyncIfEnabled(dependencies.plugin(), action);
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, dependencies.lang().tr(sender, "message.no_permission"));
    return false;
  }

  private static boolean hasAdminPermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }

  private static void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }
}
