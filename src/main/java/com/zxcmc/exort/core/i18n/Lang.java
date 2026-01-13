package com.zxcmc.exort.core.i18n;

import com.zxcmc.exort.core.ExortPlugin;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public class Lang {
  private static final String LANG_EXT = ".yml";
  private final ExortPlugin plugin;
  private final Map<String, String> defaultsEn = new HashMap<>();
  private final Map<String, String> defaultsRu = new HashMap<>();
  private Map<String, String> active = new HashMap<>();

  public Lang(ExortPlugin plugin) {
    this.plugin = plugin;
    loadDefaults();
  }

  private void loadDefaults() {
    // English
    put(defaultsEn, "message.no_permission", "No permission.");
    put(defaultsEn, "message.no_storage_adjacent", "No storage block adjacent.");
    put(
        defaultsEn,
        "message.multiple_storages_adjacent",
        "Multiple storages are connected. Leave only one.");
    put(defaultsEn, "message.storage_missing_id", "Storage block is missing id.");
    put(defaultsEn, "message.invalid_terminal", "This is not a valid terminal.");
    put(defaultsEn, "message.only_player", "Only players can use this command.");
    put(defaultsEn, "message.player_not_found", "Player not found.");
    put(
        defaultsEn,
        "message.give_usage",
        "Usage: /exort give <player> storage <tier> [amount] | terminal [amount] |"
            + " crafting_terminal [amount] | monitor [amount] | import_bus [amount] | export_bus"
            + " [amount] | wire [amount]");
    put(defaultsEn, "message.give_unknown", "Unknown item type.");
    put(defaultsEn, "message.give_success", "Gave {0}x {1} to {2}.");
    put(defaultsEn, "message.reload", "Config reloaded.");
    put(defaultsEn, "message.debug_player_none", "No storage history for {0}.");
    put(defaultsEn, "message.debug_player_active", "{0} is viewing: {1} ({2}) at {3} {4} {5} {6}");
    put(defaultsEn, "message.debug_player_last", "{0} last used: {1} ({2}) at {3} {4} {5} {6}");
    put(defaultsEn, "message.debug_player_click", "Click to open /exort debug storage {0}");
    put(defaultsEn, "message.debug_storage_invalid", "Invalid storage UUID or player name.");
    put(defaultsEn, "message.debug_location_unknown", "Storage location for {0} is unknown.");
    put(defaultsEn, "message.debug_storage_missing", "Storage tier not found for {0}.");
    put(defaultsEn, "message.debug_storage_opened", "Opened storage {0} in {1} mode.");
    put(defaultsEn, "debug.mode.read", "read-only");
    put(defaultsEn, "debug.mode.write", "write");
    put(
        defaultsEn,
        "message.usage_debug",
        "Usage: /exort debug player <player> | /exort debug storage <storageId> [write] | /exort"
            + " debug cache status <storageId|player> | /exort debug verbose cache start [mode]"
            + " [storage <uuid>] | /exort debug verbose cache stop | /exort debug benchmark start"
            + " [players] [seconds] | /exort debug benchmark stop");
    put(
        defaultsEn,
        "message.usage_give",
        "Usage: /exort give <player> storage <tier> [amount] | terminal [amount] |"
            + " crafting_terminal [amount] | monitor [amount] | import_bus [amount] | export_bus"
            + " [amount] | wire [amount]");
    put(defaultsEn, "message.usage_reload", "Usage: /exort reload");
    put(defaultsEn, "message.unknown_subcommand", "Unknown subcommand.");
    put(defaultsEn, "message.help_header", "Exort commands:");
    put(
        defaultsEn,
        "message.help_debug",
        "/{0} debug player <player> | /{0} debug storage <id> [write] | /{0} debug cache status"
            + " <id|player> | /{0} debug verbose cache start [mode] [storage <uuid>] | /{0} debug"
            + " verbose cache stop | /{0} debug benchmark start [players] [seconds] | /{0} debug"
            + " benchmark stop - debug access");
    put(
        defaultsEn,
        "message.help_give",
        "/{0} give <player> storage <tier> [amount] | terminal [amount] | crafting_terminal"
            + " [amount] | monitor [amount] | import_bus [amount] | export_bus [amount] | wire"
            + " [amount] - give items");
    put(defaultsEn, "message.help_reload", "/{0} reload - reload config and language");
    put(
        defaultsEn,
        "message.help_lang",
        "/{0} lang status | /{0} lang refresh - language dictionaries");
    put(
        defaultsEn,
        "message.help_mode",
        "/{0} mode info | /{0} mode set <VANILLA | RESOURCE> - switch mode");
    put(defaultsEn, "message.help_version", "/{0} version - show plugin version");
    put(
        defaultsEn,
        "message.debug_load_usage",
        "Usage: /exort debug benchmark start [players] [seconds] | /exort debug benchmark stop");
    put(defaultsEn, "message.debug_load_running", "Load test is already running.");
    put(defaultsEn, "message.debug_load_started", "Load test started for {0} simulated players.");
    put(defaultsEn, "message.debug_load_stopped", "Load test stopped.");
    put(
        defaultsEn,
        "message.debug_load_progress",
        "Load test {0}% • {1}s left • TPS {2} • MSPT {3}");
    put(
        defaultsEn,
        "message.debug_load_verdict",
        "Load test verdict: {0} (TPS min {1}, max {2}, avg {3}, 0.1% {4}; MSPT min {5}, max {6},"
            + " avg {7}, p95 {8}, p99 {9})");
    put(
        defaultsEn,
        "message.debug_load_summary",
        "Benchmark model: players {0}, chunks {1}, storages/chunk {2}, buses/chunk {3},"
            + " monitors/chunk {4}, est ops/tick {5} (global {6}, chunk {7}), db/tick {8}, monitor"
            + " updates {9}/t{10}, wire {11}/{12} miss {13}% cover {14}%, duration {15}s (warmup"
            + " {16}s), jitter {17}%");
    put(
        defaultsEn,
        "message.debug_load_hints",
        "Benchmark hints: dominant {0}; CPU {1}%, WIRE {2}%, DISPLAYS {3}%, DB {4}%, WIRELESS"
            + " {5}%.");
    put(defaultsEn, "message.debug_load_grade_good", "GOOD");
    put(defaultsEn, "message.debug_load_grade_warn", "OK");
    put(defaultsEn, "message.debug_load_grade_poor", "POOR");
    put(defaultsEn, "message.debug_load_grade_bad", "BAD");
    put(defaultsEn, "message.debug_load_grade_awful", "AWFUL");
    put(
        defaultsEn,
        "message.debug_load_move_away",
        "Move {0} chunks away from Exort blocks or disable benchmark.simulateDisplays.");
    put(
        defaultsEn,
        "message.debug_cache_started",
        "Cache verbose logging enabled (mode: {0}, filter: {1}).");
    put(defaultsEn, "message.debug_cache_stopped", "Cache verbose logging disabled.");
    put(
        defaultsEn,
        "message.debug_cache_mode_invalid",
        "Unknown cache verbose mode: {0}. Use compact/normal/full.");
    put(defaultsEn, "message.debug_cache_storage_invalid", "Invalid storage UUID: {0}.");
    put(defaultsEn, "message.debug_cache_filter_none", "none");
    put(defaultsEn, "message.debug_cache_status_header", "Cache status for {0}:");
    put(defaultsEn, "message.debug_cache_status_cache_unloaded", "Cache: not loaded.");
    put(
        defaultsEn,
        "message.debug_cache_status_cache",
        "Cache: loaded={0} dirty={1} viewers={2} idleMs={3} ({4}s)");
    put(defaultsEn, "message.debug_cache_status_touch", "Last touch: {0}ms ({1}s) source={2}");
    put(
        defaultsEn,
        "message.debug_cache_status_evict",
        "Evict: eligible={0} idleMs={1}/{2} dirty={3} viewers={4} loading={5}");
    put(
        defaultsEn,
        "message.debug_cache_status_marker_missing",
        "Storage marker not found in loaded chunks (chunk may be unloaded).");
    put(
        defaultsEn,
        "message.debug_cache_status_marker",
        "Storage block: {0} {1} {2} {3} (chunk {4},{5})");
    put(defaultsEn, "message.debug_cache_status_chunk_unloaded", "Chunk: not loaded.");
    put(
        defaultsEn,
        "message.debug_cache_status_chunk",
        "Chunk: loaded={0} level={1} forceLoaded={2} pluginTickets={3} players={4}");
    put(defaultsEn, "message.debug_cache_status_chunk_reason", "Chunk load reason: {0}");
    put(
        defaultsEn,
        "message.debug_cache_status_connections",
        "Loaded network nodes: terminals={0} monitors={1} buses={2} total={3}");
    put(
        defaultsEn,
        "message.debug_cache_status_connections_empty",
        "No loaded terminals/monitors/buses are linked to this storage.");
    put(defaultsEn, "message.version", "Exort v{0}");
    put(defaultsEn, "message.usage_lang", "Usage: /exort lang status | /exort lang refresh");
    put(
        defaultsEn,
        "message.usage_mode",
        "Usage: /exort mode info | /exort mode set <VANILLA | RESOURCE>");
    put(defaultsEn, "message.lang_refreshed", "Language dictionaries refreshed.");
    put(defaultsEn, "message.lang_set", "Language set to {0}.");
    put(defaultsEn, "message.lang_invalid", "Unknown language: {0}");
    put(defaultsEn, "message.mode_info", "Current mode: {0}");
    put(defaultsEn, "message.mode_set", "Mode set to {0}.");
    put(defaultsEn, "message.mode_invalid", "Unknown mode: {0}");
    put(
        defaultsEn,
        "message.mode_blocked",
        "Cannot enable RESOURCE: Paper's block-updates.disable-chorus-plant-updates is not"
            + " enabled.");
    put(defaultsEn, "message.lang_status_header", "Language status:");
    put(defaultsEn, "message.lang_status_active", "Active: {0}");
    put(defaultsEn, "message.lang_status_server", "Server version: {0}");
    put(defaultsEn, "message.lang_status_index_cached", "Index cached ({0} languages)");
    put(defaultsEn, "message.lang_status_index_missing", "Index missing");
    put(defaultsEn, "message.lang_status_index_fetched", "Index fetched this run");
    put(defaultsEn, "message.lang_status_dict", "Dictionary {0}: {1} ({2} entries)");
    put(defaultsEn, "message.lang_status_paths", "Paths: {0}, {1}");
    put(defaultsEn, "item.terminal", "Storage Terminal");
    put(defaultsEn, "item.crafting_terminal", "Crafting Terminal");
    put(defaultsEn, "item.monitor", "Storage Monitor");
    put(defaultsEn, "item.wire", "Storage Wire");
    put(defaultsEn, "item.storage_core", "Storage Core");
    put(defaultsEn, "item.import_bus", "Import Bus");
    put(defaultsEn, "item.export_bus", "Export Bus");
    put(defaultsEn, "item.wireless_terminal", "Wireless Terminal");
    put(defaultsEn, "lore.storage.capacity", "{0} / {1} ({2})");
    put(defaultsEn, "gui.prev_page", "Prev Page");
    put(defaultsEn, "gui.next_page", "Next Page");
    put(defaultsEn, "gui.page_info", "Page {0}/{1}");
    put(defaultsEn, "gui.bossbar", "{0} {1} / {2} ({3})");
    put(defaultsEn, "gui.crafting.button.storage", "Craft to Storage");
    put(defaultsEn, "gui.crafting.button.player", "Craft to Inventory");
    put(defaultsEn, "gui.crafting.button.single", "Left/Right: One item");
    put(defaultsEn, "gui.crafting.button.stack", "Shift+Left: Stack");
    put(defaultsEn, "gui.crafting.button.all", "Shift+Right: All");
    put(
        defaultsEn,
        "gui.crafting.button.all_warning",
        "All stored items will be used. This cannot be undone!");
    put(defaultsEn, "gui.crafting.button.all_confirm", "Shift+Right {0} more times to confirm.");
    put(defaultsEn, "gui.crafting.clear", "Clear Craft Grid");
    put(defaultsEn, "gui.crafting.cancel", "Cancel Craft");
    put(defaultsEn, "gui.crafting.output", "Craft Result");
    put(defaultsEn, "gui.crafting.no_recipe", "No recipe");
    put(defaultsEn, "gui.crafting.no_items", "Not enough items");
    put(defaultsEn, "gui.sort.amount", "Sort: Amount");
    put(defaultsEn, "gui.sort.name", "Sort: Name");
    put(defaultsEn, "gui.sort.id", "Sort: ID");
    put(defaultsEn, "gui.sort.category", "Sort: Category");
    put(defaultsEn, "gui.sort.hint", "Click to toggle");
    put(defaultsEn, "gui.category.building_blocks", "Building Blocks");
    put(defaultsEn, "gui.category.colored_blocks", "Colored Blocks");
    put(defaultsEn, "gui.category.natural_blocks", "Natural Blocks");
    put(defaultsEn, "gui.category.functional_blocks", "Functional Blocks");
    put(defaultsEn, "gui.category.redstone_blocks", "Redstone");
    put(defaultsEn, "gui.category.tools_and_utilities", "Tools & Utilities");
    put(defaultsEn, "gui.category.combat", "Combat");
    put(defaultsEn, "gui.category.food_and_drinks", "Food & Drinks");
    put(defaultsEn, "gui.category.ingredients", "Ingredients");
    put(defaultsEn, "gui.category.spawn_eggs", "Spawn Eggs");
    put(defaultsEn, "gui.category.operator", "Operator");
    put(defaultsEn, "gui.category.custom", "Custom Items");
    put(defaultsEn, "gui.category.other", "Other");
    put(defaultsEn, "gui.search.button", "Search");
    put(defaultsEn, "gui.search.hint", "Click to search");
    put(defaultsEn, "gui.search.hint_clear", "Shift+Click to clear");
    put(defaultsEn, "gui.search.results", "Search results");
    put(defaultsEn, "gui.search.dialog.title", "Search");
    put(defaultsEn, "gui.search.dialog.body", "Enter item name:");
    put(defaultsEn, "gui.search.dialog.apply", "Search");
    put(defaultsEn, "gui.search.dialog.cancel", "Cancel");
    put(defaultsEn, "gui.monitor.item", "{0}: {1}");
    put(defaultsEn, "gui.bus.import_title", "Import Bus");
    put(defaultsEn, "gui.bus.export_title", "Export Bus");
    put(defaultsEn, "gui.bus.mode.title", "Mode");
    put(defaultsEn, "gui.bus.mode.disabled", "Disabled");
    put(defaultsEn, "gui.bus.mode.whitelist", "Only listed");
    put(defaultsEn, "gui.bus.mode.blacklist", "All except listed");
    put(defaultsEn, "gui.bus.mode.all", "All items");
    put(defaultsEn, "gui.bus.mode.hint", "Click to toggle");
    put(defaultsEn, "gui.bus.info.title", "Info");
    put(defaultsEn, "gui.bus.info.storage", "Storage: {0}");
    put(defaultsEn, "gui.bus.info.storage_id", "Storage ID: {0}");
    put(defaultsEn, "gui.bus.info.storage_missing", "Storage: not connected");
    put(defaultsEn, "gui.bus.info.storage_multiple", "Storage: multiple connected");
    put(defaultsEn, "gui.bus.info.vanilla", "Inventory: {0}");
    put(defaultsEn, "gui.bus.info.vanilla_missing", "Inventory: not connected");
    put(
        defaultsEn,
        "error.search.dialogs_unsupported",
        "Search dialog is not supported on this server version.");
    put(defaultsEn, "gui.info.used", "Used:");
    put(defaultsEn, "gui.info.storage_id", "Storage ID: {0}");
    put(defaultsEn, "gui.info.force_hint", "Shift+Right: Take control");
    put(defaultsEn, "gui.info.force_warning", "You will become the active writer.");
    put(defaultsEn, "gui.info.force_confirm", "Shift+Right {0} more times to confirm.");
    put(defaultsEn, "gui.info.force_blocked", "Storage session is occupied by moderator.");
    put(defaultsEn, "wire.status", "Wire {0}/{1}");
    put(defaultsEn, "wire.too_long", "Wire is too long! {0}/{1}");
    put(defaultsEn, "wire.storage_connected", "Storage connected");
    put(defaultsEn, "message.bus.no_storage", "No storage connected to this bus.");
    put(
        defaultsEn,
        "message.bus.multiple_storages",
        "Multiple storages connected. Leave only one.");
    put(defaultsEn, "message.bus.no_inventory", "No inventory attached to this bus.");
    put(defaultsEn, "message.bus.loop_detected", "Loop detected, bus disabled.");
    put(defaultsEn, "message.wireless.disabled", "Wireless terminals are disabled on this server.");
    put(defaultsEn, "message.wireless.not_linked", "Wireless terminal is not linked.");
    put(defaultsEn, "message.wireless.wrong_owner", "You are not the owner. Link it again.");
    put(defaultsEn, "message.wireless.out_of_range", "Wireless terminal is out of range.");
    put(defaultsEn, "message.wireless.missing_storage", "Linked storage not found.");
    put(defaultsEn, "message.wireless.empty", "Wireless terminal is out of charge.");
    put(
        defaultsEn,
        "message.wireless.self_store",
        "Cannot store a wireless terminal inside itself.");
    put(defaultsEn, "item.wireless_terminal.battery", "Battery: {0}%");
    put(defaultsEn, "item.wireless_terminal.owner", "Owner: {0}");
    put(defaultsEn, "item.wireless_terminal.not_linked", "Not linked");
    put(defaultsEn, "item.wireless_terminal.storage_tail", "{0}");

    // Russian
    put(defaultsRu, "message.no_permission", "Нет прав.");
    put(defaultsRu, "message.no_storage_adjacent", "Рядом нет хранилища.");
    put(
        defaultsRu,
        "message.multiple_storages_adjacent",
        "Подключено несколько хранилищ. Оставьте только одно.");
    put(defaultsRu, "message.storage_missing_id", "У хранилища отсутствует идентификатор.");
    put(defaultsRu, "message.invalid_terminal", "Это не терминал хранилища.");
    put(defaultsRu, "message.only_player", "Команда доступна только игрокам.");
    put(defaultsRu, "message.player_not_found", "Игрок не найден.");
    put(
        defaultsRu,
        "message.give_usage",
        "Использование: /exort give <игрок> storage <tier> [кол-во] | terminal [кол-во] |"
            + " crafting_terminal [кол-во] | monitor [кол-во] | import_bus [кол-во] | export_bus"
            + " [кол-во] | wire [кол-во]");
    put(defaultsRu, "message.give_unknown", "Неизвестный тип предмета.");
    put(defaultsRu, "message.give_success", "Выдано {0}x {1} игроку {2}.");
    put(defaultsRu, "message.reload", "Конфигурация перезагружена.");
    put(defaultsRu, "message.debug_player_none", "Нет данных о хранилищах для {0}.");
    put(
        defaultsRu,
        "message.debug_player_active",
        "{0} сейчас использует: {1} ({2}) в {3} {4} {5} {6}");
    put(
        defaultsRu,
        "message.debug_player_last",
        "{0} последний раз использовал: {1} ({2}) в {3} {4} {5} {6}");
    put(
        defaultsRu,
        "message.debug_player_click",
        "Кликните, чтобы открыть /exort debug storage {0}");
    put(defaultsRu, "message.debug_storage_invalid", "Неверный UUID или ник игрока.");
    put(
        defaultsRu,
        "message.debug_location_unknown",
        "Местоположение хранилища для {0} неизвестно.");
    put(defaultsRu, "message.debug_storage_missing", "Не найден тир для хранилища {0}.");
    put(defaultsRu, "message.debug_storage_opened", "Открыто хранилище {0} в режиме {1}.");
    put(defaultsRu, "debug.mode.read", "только чтение");
    put(defaultsRu, "debug.mode.write", "чтение/запись");
    put(
        defaultsRu,
        "message.usage_debug",
        "Использование: /exort debug player <игрок> | /exort debug storage <id> [write] | /exort"
            + " debug cache status <uuid|игрок> | /exort debug verbose cache start [mode] [storage"
            + " <uuid>] | /exort debug verbose cache stop | /exort debug benchmark start [игроки]"
            + " [секунды] | /exort debug benchmark stop");
    put(
        defaultsRu,
        "message.usage_give",
        "Использование: /exort give <игрок> storage <tier> [кол-во] | terminal [кол-во] |"
            + " crafting_terminal [кол-во] | monitor [кол-во] | import_bus [кол-во] | export_bus"
            + " [кол-во] | wire [кол-во]");
    put(defaultsRu, "message.usage_reload", "Использование: /exort reload");
    put(defaultsRu, "message.unknown_subcommand", "Неизвестная подкоманда.");
    put(defaultsRu, "message.help_header", "Команды Exort:");
    put(
        defaultsRu,
        "message.help_debug",
        "/{0} debug player <игрок> | /{0} debug storage <id> [write] | /{0} debug cache status"
            + " <uuid|игрок> | /{0} debug verbose cache start [mode] [storage <uuid>] | /{0} debug"
            + " verbose cache stop | /{0} debug benchmark start [игроки] [секунды] | /{0} debug"
            + " benchmark stop - отладка");
    put(
        defaultsRu,
        "message.help_give",
        "/{0} give <игрок> storage <tier> [кол-во] | terminal [кол-во] | crafting_terminal [кол-во]"
            + " | monitor [кол-во] | import_bus [кол-во] | export_bus [кол-во] | wire [кол-во] -"
            + " выдать предметы");
    put(defaultsRu, "message.help_reload", "/{0} reload - перезагрузить конфиг и язык");
    put(defaultsRu, "message.help_lang", "/{0} lang status | /{0} lang refresh - словари языка");
    put(
        defaultsRu,
        "message.help_mode",
        "/{0} mode info | /{0} mode set <VANILLA | RESOURCE> - сменить режим");
    put(defaultsRu, "message.help_version", "/{0} version - показать версию плагина");
    put(
        defaultsRu,
        "message.debug_load_usage",
        "Использование: /exort debug benchmark start [игроки] [секунды] | /exort debug benchmark"
            + " stop");
    put(defaultsRu, "message.debug_load_running", "Тест нагрузки уже запущен.");
    put(
        defaultsRu,
        "message.debug_load_started",
        "Тест нагрузки запущен для {0} симулируемых игроков.");
    put(defaultsRu, "message.debug_load_stopped", "Тест нагрузки остановлен.");
    put(
        defaultsRu,
        "message.debug_load_progress",
        "Тест нагрузки {0}% • осталось {1}с • TPS {2} • MSPT {3}");
    put(
        defaultsRu,
        "message.debug_load_verdict",
        "Вердикт теста: {0} (TPS min {1}, max {2}, avg {3}, 0.1% {4}; MSPT min {5}, max {6}, avg"
            + " {7}, p95 {8}, p99 {9})");
    put(
        defaultsRu,
        "message.debug_load_summary",
        "Модель бенчмарка: игроков {0}, чанков {1}, хранилищ/чанк {2}, шин/чанк {3}, мониторов/чанк"
            + " {4}, оценка оп/тик {5} (глобал {6}, чанк {7}), БД/тик {8}, обновления мониторов"
            + " {9}/t{10}, кабель {11}/{12} miss {13}% cover {14}%, длительность {15}с (прогрев"
            + " {16}с), джиттер {17}%");
    put(
        defaultsRu,
        "message.debug_load_hints",
        "Подсказки: доминирующая нагрузка — {0}; CPU {1}%, кабель {2}%, мониторы {3}%, БД {4}%,"
            + " wireless {5}%.");
    put(defaultsRu, "message.debug_load_grade_good", "ОТЛИЧНО");
    put(defaultsRu, "message.debug_load_grade_warn", "НОРМА");
    put(defaultsRu, "message.debug_load_grade_poor", "СЛАБО");
    put(defaultsRu, "message.debug_load_grade_bad", "ПЛОХО");
    put(defaultsRu, "message.debug_load_grade_awful", "УЖАСНО");
    put(
        defaultsRu,
        "message.debug_load_move_away",
        "Отойдите на {0} чанков от блоков Exort или отключите benchmark.simulateDisplays.");
    put(
        defaultsRu,
        "message.debug_cache_started",
        "Подробный лог кэша включен (режим: {0}, фильтр: {1}).");
    put(defaultsRu, "message.debug_cache_stopped", "Подробный лог кэша выключен.");
    put(
        defaultsRu,
        "message.debug_cache_mode_invalid",
        "Неизвестный режим лога кэша: {0}. Используйте compact/normal/full.");
    put(defaultsRu, "message.debug_cache_storage_invalid", "Неверный UUID хранилища: {0}.");
    put(defaultsRu, "message.debug_cache_filter_none", "нет");
    put(defaultsRu, "message.debug_cache_status_header", "Статус кэша для {0}:");
    put(defaultsRu, "message.debug_cache_status_cache_unloaded", "Кэш: не загружен.");
    put(
        defaultsRu,
        "message.debug_cache_status_cache",
        "Кэш: loaded={0} dirty={1} viewers={2} idleMs={3} ({4}с)");
    put(
        defaultsRu,
        "message.debug_cache_status_touch",
        "Последнее обращение: {0}мс ({1}с) источник={2}");
    put(
        defaultsRu,
        "message.debug_cache_status_evict",
        "Выгрузка: eligible={0} idleMs={1}/{2} dirty={3} viewers={4} loading={5}");
    put(
        defaultsRu,
        "message.debug_cache_status_marker_missing",
        "Маркер хранилища не найден в загруженных чанках (чанк может быть выгружен).");
    put(
        defaultsRu,
        "message.debug_cache_status_marker",
        "Блок хранилища: {0} {1} {2} {3} (чанк {4},{5})");
    put(defaultsRu, "message.debug_cache_status_chunk_unloaded", "Чанк: не загружен.");
    put(
        defaultsRu,
        "message.debug_cache_status_chunk",
        "Чанк: loaded={0} level={1} forceLoaded={2} pluginTickets={3} players={4}");
    put(defaultsRu, "message.debug_cache_status_chunk_reason", "Причина загрузки чанка: {0}");
    put(
        defaultsRu,
        "message.debug_cache_status_connections",
        "Загруженные узлы сети: terminals={0} monitors={1} buses={2} total={3}");
    put(
        defaultsRu,
        "message.debug_cache_status_connections_empty",
        "Нет загруженных терминалов/мониторов/шин, связанных с этим хранилищем.");
    put(defaultsRu, "message.version", "Exort Storage Network v{0} by phantomfighterxx");
    put(
        defaultsRu,
        "message.usage_lang",
        "Использование: /exort lang status | /exort lang refresh");
    put(
        defaultsRu,
        "message.usage_mode",
        "Использование: /exort mode info | /exort mode set <VANILLA | RESOURCE>");
    put(defaultsRu, "message.lang_refreshed", "Словари языка обновлены.");
    put(defaultsRu, "message.lang_set", "Язык установлен: {0}.");
    put(defaultsRu, "message.lang_invalid", "Неизвестный язык: {0}");
    put(defaultsRu, "message.mode_info", "Текущий режим: {0}");
    put(defaultsRu, "message.mode_set", "Режим установлен: {0}.");
    put(defaultsRu, "message.mode_invalid", "Неизвестный режим: {0}");
    put(
        defaultsRu,
        "message.mode_blocked",
        "Нельзя включить RESOURCE: в Paper не отключены"
            + " block-updates.disable-chorus-plant-updates.");
    put(defaultsRu, "message.lang_status_header", "Статус языка:");
    put(defaultsRu, "message.lang_status_active", "Активный: {0}");
    put(defaultsRu, "message.lang_status_server", "Версия сервера: {0}");
    put(defaultsRu, "message.lang_status_index_cached", "Индекс загружен ({0} языков)");
    put(defaultsRu, "message.lang_status_index_missing", "Индекс отсутствует");
    put(defaultsRu, "message.lang_status_index_fetched", "Индекс загружен в этот запуск");
    put(defaultsRu, "message.lang_status_dict", "Словарь {0}: {1} ({2} записей)");
    put(defaultsRu, "message.lang_status_paths", "Пути: {0}, {1}");
    put(defaultsRu, "item.terminal", "Терминал хранилища");
    put(defaultsRu, "item.crafting_terminal", "Терминал создания");
    put(defaultsRu, "item.monitor", "Монитор хранилища");
    put(defaultsRu, "item.wire", "Кабель хранилища");
    put(defaultsRu, "item.storage_core", "Основа хранилища");
    put(defaultsRu, "item.import_bus", "Шина импорта");
    put(defaultsRu, "item.export_bus", "Шина экспорта");
    put(defaultsRu, "item.wireless_terminal", "Беспроводной терминал");
    put(defaultsRu, "lore.storage.capacity", "{0} / {1} ({2})");
    put(defaultsRu, "gui.prev_page", "Пред. страница");
    put(defaultsRu, "gui.next_page", "След. страница");
    put(defaultsRu, "gui.page_info", "Страница {0}/{1}");
    put(defaultsRu, "gui.bossbar", "{0} {1} / {2} ({3})");
    put(defaultsRu, "gui.crafting.button.storage", "Крафт в хранилище");
    put(defaultsRu, "gui.crafting.button.player", "Крафт в инвентарь");
    put(defaultsRu, "gui.crafting.button.single", "ЛКМ/ПКМ: один предмет");
    put(defaultsRu, "gui.crafting.button.stack", "Shift+ЛКМ: стак");
    put(defaultsRu, "gui.crafting.button.all", "Shift+ПКМ: все");
    put(
        defaultsRu,
        "gui.crafting.button.all_warning",
        "Будут использованы все предметы из хранилища. Это действие необратимо!");
    put(defaultsRu, "gui.crafting.button.all_confirm", "Shift+ПКМ ещё {0} раз для подтверждения.");
    put(defaultsRu, "gui.crafting.clear", "Очистить верстак");
    put(defaultsRu, "gui.crafting.cancel", "Отменить крафт");
    put(defaultsRu, "gui.crafting.output", "Результат крафта");
    put(defaultsRu, "gui.crafting.no_recipe", "Нет рецепта");
    put(defaultsRu, "gui.crafting.no_items", "Недостаточно предметов");
    put(defaultsRu, "gui.sort.amount", "Сортировка: Количество");
    put(defaultsRu, "gui.sort.name", "Сортировка: Название");
    put(defaultsRu, "gui.sort.id", "Сортировка: ID");
    put(defaultsRu, "gui.sort.category", "Сортировка: Категория");
    put(defaultsRu, "gui.sort.hint", "Нажмите для переключения");
    put(defaultsRu, "gui.category.building_blocks", "Строительные блоки");
    put(defaultsRu, "gui.category.colored_blocks", "Разноцветные блоки");
    put(defaultsRu, "gui.category.natural_blocks", "Природные блоки");
    put(defaultsRu, "gui.category.functional_blocks", "Функциональные блоки");
    put(defaultsRu, "gui.category.redstone_blocks", "Редстоун-механика");
    put(defaultsRu, "gui.category.tools_and_utilities", "Инструменты и приспособления");
    put(defaultsRu, "gui.category.combat", "Оружие и доспехи");
    put(defaultsRu, "gui.category.food_and_drinks", "Еда и напитки");
    put(defaultsRu, "gui.category.ingredients", "Ингредиенты");
    put(defaultsRu, "gui.category.spawn_eggs", "Яйца призыва");
    put(defaultsRu, "gui.category.operator", "Инструменты оператора");
    put(defaultsRu, "gui.category.custom", "Пользовательские предметы");
    put(defaultsRu, "gui.category.other", "Прочее");
    put(defaultsRu, "gui.search.button", "Поиск");
    put(defaultsRu, "gui.search.hint", "Нажмите для поиска");
    put(defaultsRu, "gui.search.hint_clear", "Shift+клик для сброса");
    put(defaultsRu, "gui.search.results", "Результаты поиска");
    put(defaultsRu, "gui.search.dialog.title", "Поиск");
    put(defaultsRu, "gui.search.dialog.body", "Введите название предмета:");
    put(defaultsRu, "gui.search.dialog.apply", "Искать");
    put(defaultsRu, "gui.search.dialog.cancel", "Отмена");
    put(defaultsRu, "gui.monitor.item", "{0}: {1}");
    put(defaultsRu, "gui.bus.import_title", "Шина импорта");
    put(defaultsRu, "gui.bus.export_title", "Шина экспорта");
    put(defaultsRu, "gui.bus.mode.title", "Режим");
    put(defaultsRu, "gui.bus.mode.disabled", "Отключено");
    put(defaultsRu, "gui.bus.mode.whitelist", "Только указанные");
    put(defaultsRu, "gui.bus.mode.blacklist", "Все кроме указанных");
    put(defaultsRu, "gui.bus.mode.all", "Все предметы");
    put(defaultsRu, "gui.bus.mode.hint", "Клик для переключения");
    put(defaultsRu, "gui.bus.info.title", "Информация");
    put(defaultsRu, "gui.bus.info.storage", "Хранилище: {0}");
    put(defaultsRu, "gui.bus.info.storage_id", "UUID хранилища: {0}");
    put(defaultsRu, "gui.bus.info.storage_missing", "Хранилище: не подключено");
    put(defaultsRu, "gui.bus.info.storage_multiple", "Хранилище: подключено несколько");
    put(defaultsRu, "gui.bus.info.vanilla", "Инвентарь: {0}");
    put(defaultsRu, "gui.bus.info.vanilla_missing", "Инвентарь: не подключён");
    put(
        defaultsRu,
        "error.search.dialogs_unsupported",
        "Диалоги поиска не поддерживаются на этой версии сервера.");
    put(defaultsRu, "gui.info.used", "Занято:");
    put(defaultsRu, "gui.info.storage_id", "UUID хранилища: {0}");
    put(defaultsRu, "gui.info.force_hint", "Shift+ПКМ: взять управление");
    put(defaultsRu, "gui.info.force_warning", "Вы станете активным редактором.");
    put(defaultsRu, "gui.info.force_confirm", "Shift+ПКМ ещё {0} раз для подтверждения.");
    put(defaultsRu, "gui.info.force_blocked", "Сессия хранилища занята модератором.");
    put(defaultsRu, "wire.status", "Кабель {0}/{1}");
    put(defaultsRu, "wire.too_long", "Кабель слишком длинный! {0}/{1}");
    put(defaultsRu, "wire.storage_connected", "Хранилище подключено");
    put(defaultsRu, "message.bus.no_storage", "Эта шина не подключена к хранилищу.");
    put(
        defaultsRu,
        "message.bus.multiple_storages",
        "Подключено несколько хранилищ. Оставьте только одно.");
    put(defaultsRu, "message.bus.no_inventory", "К этой шине не подключён инвентарь.");
    put(defaultsRu, "message.bus.loop_detected", "Обнаружена петля, шина отключена.");
    put(defaultsRu, "message.wireless.disabled", "Беспроводные терминалы отключены на сервере.");
    put(defaultsRu, "message.wireless.not_linked", "Беспроводной терминал не привязан.");
    put(
        defaultsRu,
        "message.wireless.wrong_owner",
        "Вы не владелец этого терминала. Привяжите заново.");
    put(defaultsRu, "message.wireless.out_of_range", "Беспроводной терминал вне радиуса действия.");
    put(defaultsRu, "message.wireless.missing_storage", "Связанное хранилище не найдено.");
    put(defaultsRu, "message.wireless.empty", "Беспроводной терминал разряжен.");
    put(
        defaultsRu,
        "message.wireless.self_store",
        "Нельзя положить беспроводной терминал сам в себя.");
    put(defaultsRu, "item.wireless_terminal.battery", "Батарея: {0}%");
    put(defaultsRu, "item.wireless_terminal.owner", "Владелец: {0}");
    put(defaultsRu, "item.wireless_terminal.not_linked", "Не привязан");
    put(defaultsRu, "item.wireless_terminal.storage_tail", "{0}");
  }

  private void put(Map<String, String> map, String key, String value) {
    map.put(key, value);
  }

  private String getStringOrDefault(YamlConfiguration cfg, String key, String fallback) {
    if (cfg.isString(key)) {
      String value = cfg.getString(key);
      return value != null ? value : fallback;
    }
    return fallback;
  }

  public void load(String language) {
    active = new HashMap<>(defaultsEn);
    File langDir = new File(plugin.getDataFolder(), "lang");
    if (!langDir.exists()) {
      langDir.mkdirs();
    }
    writeDefaults(langDir, "en_us", defaultsEn, true);
    writeDefaults(langDir, "ru_ru", defaultsRu, true);

    String code = language.toLowerCase(Locale.ROOT);
    Map<String, String> base = code.equals("ru_ru") ? defaultsRu : defaultsEn;
    File target = new File(langDir, code + LANG_EXT);
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(target);
    configureYaml(cfg);
    boolean changed = false;
    for (Map.Entry<String, String> entry : base.entrySet()) {
      if (!cfg.contains(entry.getKey())) {
        cfg.set(entry.getKey(), entry.getValue());
        changed = true;
      }
      active.put(entry.getKey(), getStringOrDefault(cfg, entry.getKey(), entry.getValue()));
    }
    // ensure english fallback keys also exist
    for (Map.Entry<String, String> entry : defaultsEn.entrySet()) {
      if (!cfg.contains(entry.getKey())) {
        cfg.set(entry.getKey(), entry.getValue());
        changed = true;
      }
      active.putIfAbsent(entry.getKey(), getStringOrDefault(cfg, entry.getKey(), entry.getValue()));
    }
    if (changed) {
      try {
        cfg.save(target);
      } catch (IOException e) {
        plugin
            .getLogger()
            .severe("Failed to save language file " + target.getName() + ": " + e.getMessage());
      }
    }
  }

  private void writeDefaults(File dir, String name, Map<String, String> data, boolean overwrite) {
    File file = new File(dir, name + LANG_EXT);
    if (!overwrite && file.exists()) {
      return;
    }
    YamlConfiguration cfg = new YamlConfiguration();
    configureYaml(cfg);
    for (Map.Entry<String, String> entry : data.entrySet()) {
      cfg.set(entry.getKey(), entry.getValue());
    }
    try {
      cfg.save(file);
    } catch (IOException e) {
      plugin
          .getLogger()
          .severe("Failed to save default lang file " + file.getName() + ": " + e.getMessage());
    }
  }

  public String tr(String key, Object... params) {
    String base = active.getOrDefault(key, defaultsEn.getOrDefault(key, key));
    if (params.length == 0) return base;
    return MessageFormat.format(base, params);
  }

  private void configureYaml(YamlConfiguration cfg) {
    cfg.options().width(4096);
  }

  public void reload(String language) {
    try {
      load(language);
    } catch (Exception e) {
      plugin
          .getLogger()
          .severe(
              "Failed to load language '"
                  + language
                  + "', falling back to en_us: "
                  + e.getMessage());
      load("en_us");
    }
  }
}
