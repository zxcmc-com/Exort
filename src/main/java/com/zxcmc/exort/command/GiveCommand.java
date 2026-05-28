package com.zxcmc.exort.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zxcmc.exort.feedback.CommandFeedback;
import com.zxcmc.exort.storage.StorageTier;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

final class GiveCommand {
  private static final String PERMISSION_ADMIN = "exort.storagenetwork.admin";
  private static final String PERMISSION_GIVE = "exort.storagenetwork.give";
  private static final String ARG_PLAYER = "player";
  private static final String ARG_TIER = "tier";
  private static final String ARG_AMOUNT = "amount";
  private static final int MAX_GIVE_AMOUNT = 512;
  private static final float GIVE_SOUND_VOLUME = 0.2F;
  private static final float GIVE_SOUND_PITCH = 1.0F;

  private final ExortBrigadierDependencies dependencies;

  GiveCommand(ExortBrigadierDependencies dependencies) {
    this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
  }

  LiteralArgumentBuilder<CommandSourceStack> build() {
    return Commands.literal("give")
        .requires(source -> hasGivePermission(sender(source)))
        .executes(this::usage)
        .then(
            Commands.argument(ARG_PLAYER, StringArgumentType.word())
                .suggests(this::suggestPlayers)
                .executes(this::usage)
                .then(
                    Commands.literal("storage")
                        .executes(this::storageUsage)
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
                        .executes(ctx -> giveItem(ctx, 1, "item.storage_core", this::storageCore))
                        .then(
                            amountArgument(
                                ctx ->
                                    giveItem(
                                        ctx, amount(ctx), "item.storage_core", this::storageCore))))
                .then(
                    Commands.literal("terminal")
                        .executes(ctx -> giveItem(ctx, 1, "item.terminal", this::terminal))
                        .then(
                            amountArgument(
                                ctx ->
                                    giveItem(ctx, amount(ctx), "item.terminal", this::terminal))))
                .then(
                    Commands.literal("crafting_terminal")
                        .executes(
                            ctx ->
                                giveItem(ctx, 1, "item.crafting_terminal", this::craftingTerminal))
                        .then(
                            amountArgument(
                                ctx ->
                                    giveItem(
                                        ctx,
                                        amount(ctx),
                                        "item.crafting_terminal",
                                        this::craftingTerminal))))
                .then(
                    Commands.literal("monitor")
                        .executes(ctx -> giveItem(ctx, 1, "item.monitor", this::monitor))
                        .then(
                            amountArgument(
                                ctx -> giveItem(ctx, amount(ctx), "item.monitor", this::monitor))))
                .then(
                    Commands.literal("import_bus")
                        .executes(ctx -> giveItem(ctx, 1, "item.import_bus", this::importBus))
                        .then(
                            amountArgument(
                                ctx ->
                                    giveItem(
                                        ctx, amount(ctx), "item.import_bus", this::importBus))))
                .then(
                    Commands.literal("export_bus")
                        .executes(ctx -> giveItem(ctx, 1, "item.export_bus", this::exportBus))
                        .then(
                            amountArgument(
                                ctx ->
                                    giveItem(
                                        ctx, amount(ctx), "item.export_bus", this::exportBus))))
                .then(
                    Commands.literal("wire")
                        .executes(ctx -> giveItem(ctx, 1, "item.wire", this::wire))
                        .then(
                            amountArgument(
                                ctx -> giveItem(ctx, amount(ctx), "item.wire", this::wire))))
                .then(
                    Commands.literal("wireless_terminal")
                        .executes(
                            ctx ->
                                giveItem(
                                    ctx,
                                    1,
                                    "item.wireless_terminal",
                                    () -> dependencies.wirelessService().create()))
                        .then(
                            amountArgument(
                                ctx ->
                                    giveItem(
                                        ctx,
                                        amount(ctx),
                                        "item.wireless_terminal",
                                        () -> dependencies.wirelessService().create())))));
  }

  private RequiredArgumentBuilder<CommandSourceStack, Integer> amountArgument(
      com.mojang.brigadier.Command<CommandSourceStack> command) {
    return Commands.argument(ARG_AMOUNT, IntegerArgumentType.integer(1, MAX_GIVE_AMOUNT))
        .executes(command);
  }

  private int amount(CommandContext<CommandSourceStack> context) {
    return IntegerArgumentType.getInteger(context, ARG_AMOUNT);
  }

  private int usage(CommandContext<CommandSourceStack> context) {
    if (!ensureGivePermission(context)) return 0;
    CommandFeedback.sendBlock(
        sender(context.getSource()),
        Component.text(dependencies.lang().tr("message.usage_give_header")),
        List.of(
            usageLine("/exort give <player> storage <tier> [amount]", "message.usage_give_storage"),
            usageLine("/exort give <player> <item> [amount]", "message.usage_give_item"),
            Component.text(dependencies.lang().tr("message.usage_give_items"))));
    return 1;
  }

  private int storageUsage(CommandContext<CommandSourceStack> context) {
    if (!ensureGivePermission(context)) return 0;
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    CommandFeedback.sendBlock(
        sender(context.getSource()),
        Component.text(dependencies.lang().tr("message.usage_give_header")),
        List.of(usageLine(storageUsageCommand(playerName), "message.usage_give_storage")));
    return 1;
  }

  private Component usageLine(String command, String descriptionKey) {
    return CommandFeedback.commandLine(
        command,
        dependencies.lang().tr(descriptionKey),
        dependencies.lang().tr("message.command_click", command));
  }

  static String storageUsageCommand(String playerName) {
    return "/exort give "
        + Objects.requireNonNull(playerName, "playerName")
        + " storage <tier> [amount]";
  }

  private int giveStorage(CommandContext<CommandSourceStack> context, int amount) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    Player target = target(sender, context);
    if (target == null) {
      return 1;
    }
    String tierArg = StringArgumentType.getString(context, ARG_TIER).toLowerCase(Locale.ROOT);
    var tierOpt = StorageTier.fromString(tierArg);
    if (tierOpt.isEmpty()) {
      sendMessage(sender, dependencies.lang().tr("message.give_unknown"));
      return 1;
    }
    StorageTier tier = tierOpt.get();
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    sendGiveResult(
        sender,
        target,
        tier.displayName(),
        giveAmount,
        CommandItemDelivery.deliver(
            target, () -> dependencies.customItems().storageItem(tier, null), giveAmount));
    return 1;
  }

  private int giveItem(
      CommandContext<CommandSourceStack> context,
      int amount,
      String itemTranslationKey,
      Supplier<ItemStack> itemSupplier) {
    if (!ensureGivePermission(context)) return 0;
    CommandSender sender = sender(context.getSource());
    Player target = target(sender, context);
    if (target == null) {
      return 1;
    }
    int giveAmount = CommandItemDelivery.clampAmount(amount, MAX_GIVE_AMOUNT);
    String label = dependencies.lang().tr(itemTranslationKey);
    sendGiveResult(
        sender,
        target,
        label,
        giveAmount,
        CommandItemDelivery.deliver(target, itemSupplier, giveAmount));
    return 1;
  }

  private Player target(CommandSender sender, CommandContext<CommandSourceStack> context) {
    String playerName = StringArgumentType.getString(context, ARG_PLAYER);
    Player target = Bukkit.getPlayerExact(playerName);
    if (target == null) {
      sendMessage(sender, dependencies.lang().tr("message.player_not_found"));
    }
    return target;
  }

  private ItemStack storageCore() {
    return dependencies.customItems().storageCoreItem();
  }

  private ItemStack terminal() {
    return dependencies.customItems().terminalItem();
  }

  private ItemStack craftingTerminal() {
    return dependencies.customItems().craftingTerminalItem();
  }

  private ItemStack monitor() {
    return dependencies.customItems().monitorItem();
  }

  private ItemStack importBus() {
    return dependencies.customItems().importBusItem();
  }

  private ItemStack exportBus() {
    return dependencies.customItems().exportBusItem();
  }

  private ItemStack wire() {
    return dependencies.customItems().wireItem();
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
    List<String> lines = new ArrayList<>();
    lines.add(
        dependencies.lang().tr("message.give_success", result.total(), itemName, target.getName()));
    if (result.dropped() > 0) {
      lines.add(dependencies.lang().tr("message.give_dropped", result.dropped(), target.getName()));
    }
    if (result.total() < requested) {
      lines.add(
          dependencies
              .lang()
              .tr("message.give_partial", result.total(), requested, target.getName()));
    }
    sendResult(sender, lines);
  }

  private void sendResult(CommandSender sender, List<String> lines) {
    if (lines.size() == 1) {
      sendMessage(sender, lines.getFirst());
      return;
    }
    CommandFeedback.sendBlock(sender, lines.getFirst(), lines.subList(1, lines.size()));
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

  private boolean ensureGivePermission(CommandContext<CommandSourceStack> context) {
    CommandSender sender = sender(context.getSource());
    if (hasGivePermission(sender)) return true;
    sendMessage(sender, dependencies.lang().tr("message.no_permission"));
    return false;
  }

  private static boolean hasGivePermission(CommandSender sender) {
    return sender.hasPermission(PERMISSION_GIVE) || sender.hasPermission(PERMISSION_ADMIN);
  }

  private static CommandSender sender(CommandSourceStack source) {
    return source.getSender();
  }

  private static void sendMessage(CommandSender sender, String message) {
    CommandFeedback.send(sender, message);
  }
}
