package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.i18n.Lang;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

final class LangCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String ARG_LANG = "lang";

  private final ExortBrigadierDependencies dependencies;

  LangCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  LiteralArgumentBuilder<CommandSourceStack> build() {
    return Commands.literal("lang")
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
    sendMessage(sender(context.getSource()), dependencies.lang().tr("message.usage_lang"));
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
                    dependencies.lang().reload(status.activeLanguage());
                    sendMessage(sender, dependencies.lang().tr("message.lang_refreshed"));
                  });
            });
    return 1;
  }

  private int status(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    var status = dependencies.itemNameService().status();
    CommandSender sender = sender(context.getSource());
    Lang lang = dependencies.lang();
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

  private int set(CommandContext<CommandSourceStack> context) {
    if (!ensurePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    String lang = StringArgumentType.getString(context, ARG_LANG);
    String normalized = dependencies.itemNameService().normalizeLanguage(stripLangExtension(lang));
    if (!dependencies.itemNameService().isKnownLanguage(normalized)) {
      sendMessage(sender, dependencies.lang().tr("message.lang_invalid", normalized));
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
                          dependencies.lang().tr("message.lang_set", status.activeLanguage())));
            });
    return 1;
  }

  private CompletableFuture<Suggestions> suggestLangs(
      CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    var options = new ArrayList<>(dependencies.itemNameService().localLanguages());
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
    runSync(() -> sendMessage(sender, dependencies.lang().tr("message.operation_failed")));
  }

  private void runSync(Runnable action) {
    PluginTasks.runSyncIfEnabled(dependencies.plugin(), action);
  }

  private boolean ensurePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasAdminPermission(sender)) return true;
    sendMessage(sender, dependencies.lang().tr("message.no_permission"));
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
