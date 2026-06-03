package com.zxcmc.exort.i18n;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Lang {
  private static final String LANG_EXT = ".yml";
  private static final String BUNDLED_LANG_DIR = "lang/";
  private static final String BUNDLED_LANG_INDEX = BUNDLED_LANG_DIR + "index.yml";
  private static final String CONFIG_AUTO_OVERWRITE = "languageFiles.autoOverwriteBundled";
  private static final String CONFIG_PRESERVE_LANGUAGES = "languageFiles.preserveLanguages";
  private static final String DEFAULT_LANGUAGE = "en_us";
  private static final String RUSSIAN_LANGUAGE = "ru_ru";
  private final JavaPlugin plugin;
  private final Map<String, String> defaultsEn = new HashMap<>();
  private final Map<String, String> defaultsRu = new HashMap<>();
  private final Map<String, Map<String, String>> bundledDefaults = new ConcurrentHashMap<>();
  private volatile Set<String> bundledLanguages = Set.of(DEFAULT_LANGUAGE, RUSSIAN_LANGUAGE);
  private volatile Map<String, Map<String, String>> languages = Map.of();
  private volatile Map<String, String> active = new HashMap<>();
  private volatile String activeLanguage = DEFAULT_LANGUAGE;

  public Lang(JavaPlugin plugin) {
    this.plugin = plugin;
    loadDefaults();
    active = new HashMap<>(defaultsEn);
    languages = Map.of(DEFAULT_LANGUAGE, active, RUSSIAN_LANGUAGE, new HashMap<>(defaultsRu));
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
    put(defaultsEn, "message.storage_load_failed", "Failed to load storage. See console.");
    put(defaultsEn, "message.storage_loading", "Storage is loading. Try again in a moment.");
    put(defaultsEn, "message.operation_failed", "Operation failed. See console.");
    put(defaultsEn, "message.invalid_terminal", "This is not a valid terminal.");
    put(defaultsEn, "message.only_player", "Only players can use this command.");
    put(defaultsEn, "message.player_not_found", "Player not found.");
    put(defaultsEn, "message.command_click", "Click to insert {0}");
    put(defaultsEn, "message.give_unknown", "Unknown item type.");
    put(defaultsEn, "message.give_success", "Gave {0}x {1} to {2}.");
    put(
        defaultsEn,
        "message.give_dropped",
        "{1}'s inventory was full; dropped {0} item(s) nearby.");
    put(defaultsEn, "message.give_partial", "Only {0} of {1} item(s) could be created for {2}.");
    put(defaultsEn, "message.reload", "Config reloaded.");
    put(defaultsEn, "message.debug_player_none", "No storage history for {0}.");
    put(defaultsEn, "message.debug_player_active", "{0} is viewing: {1} ({2}) at {3} {4} {5} {6}");
    put(defaultsEn, "message.debug_player_last", "{0} last used: {1} ({2}) at {3} {4} {5} {6}");
    put(defaultsEn, "message.debug_player_click", "Click to open /exort debug storage {0}");
    put(defaultsEn, "message.debug_storage_invalid", "Invalid storage UUID or player name.");
    put(defaultsEn, "message.debug_culling_unavailable", "Display culling service is unavailable.");
    put(defaultsEn, "message.debug_location_unknown", "Storage location for {0} is unknown.");
    put(defaultsEn, "message.debug_storage_missing", "Storage tier not found for {0}.");
    put(defaultsEn, "message.debug_storage_opened", "Opened storage {0} in {1} mode.");
    put(defaultsEn, "debug.mode.read", "read-only");
    put(defaultsEn, "debug.mode.write", "write");
    put(defaultsEn, "message.usage_debug_header", "Debug and inspection:");
    put(defaultsEn, "message.usage_debug_player", "show a player's active or last storage");
    put(defaultsEn, "message.usage_debug_storage", "open storage by id or player's last storage");
    put(defaultsEn, "message.usage_debug_cache", "inspect storage cache and loaded network links");
    put(defaultsEn, "message.usage_debug_verbose", "toggle compact/normal/full diagnostic logs");
    put(defaultsEn, "message.usage_debug_culling_client", "manage client-side culling bypass");
    put(defaultsEn, "message.usage_debug_benchmark", "start or stop the load benchmark");
    put(defaultsEn, "message.usage_give_header", "Give Exort items:");
    put(defaultsEn, "message.usage_give_storage", "give a storage item by tier");
    put(defaultsEn, "message.usage_give_item", "give a fixed Exort item");
    put(
        defaultsEn,
        "message.usage_give_items",
        "Items: storage_core, terminal, crafting_terminal, monitor, import_bus, export_bus, wire,"
            + " wireless_terminal");
    put(defaultsEn, "message.usage_reload", "Usage: /exort reload");
    put(defaultsEn, "message.unknown_subcommand", "Unknown subcommand.");
    put(defaultsEn, "message.help_header", "Exort Storage Network commands:");
    put(defaultsEn, "message.help_inventory", "Exort item inventory");
    put(defaultsEn, "message.help_give", "give items to a player");
    put(defaultsEn, "message.help_resourcepack", "manage the resource pack");
    put(defaultsEn, "message.help_language", "language settings");
    put(defaultsEn, "message.help_mode", "display mode settings");
    put(defaultsEn, "message.help_debug", "debug and inspect internals");
    put(defaultsEn, "message.help_version", "show plugin version");
    put(defaultsEn, "message.help_reload", "reload config and runtime");
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
        "Progress {0}% • {1}s left • TPS {2} • MSPT {3}");
    put(
        defaultsEn,
        "message.debug_load_verdict",
        "Load test verdict: {0} (TPS min {1}, max {2}, avg {3}, p1 {4}; MSPT min {5}, max {6},"
            + " avg {7}, p95 {8}, p99 {9})");
    put(
        defaultsEn,
        "message.debug_load_rare_stalls",
        "Rare stalls: {0} sample(s) below TPS 12, min {1}.");
    put(
        defaultsEn,
        "message.debug_load_summary",
        "Benchmark model: players {0}, chunks {1}, storages/chunk {2}, buses/chunk {3},"
            + " monitors/chunk {4}, est ops/tick {5} (global {6}, chunk {7}), db/tick {8}, monitor"
            + " updates {9}/t{10}, wire {11}/{12} miss {13}% cover {14}%, measured window {15}s"
            + " after setup (min warmup {16}s), jitter {17}%, guard players {18}, guard entities"
            + " {19}/{20}, guard churn {21}/tick, world lanes {22}, world ops/tick {23}, world"
            + " blocks {24}, water-test wire length {25}, water sources {26}, water extra chunks"
            + " {27}");
    put(
        defaultsEn,
        "message.debug_load_measurement_started",
        "Benchmark measurement started after setup and water-flow stabilization.");
    put(
        defaultsEn,
        "message.debug_load_hints",
        "Synthetic benchmark hints: dominant {0}; CPU {1}%, WIRE {2}%, DISPLAYS {3}%, DB {4}%,"
            + " WIRELESS {5}%, GUARDS {6}%, WORLD {7}% (placements {8}).");
    put(
        defaultsEn,
        "message.debug_load_mspt_verdict",
        "Sustained MSPT verdict: {0} (avg {1}, p95 {2}, p99 {3}).");
    put(defaultsEn, "message.debug_load_measured_profile", "Measured Exort profile: {0}.");
    put(
        defaultsEn,
        "message.debug_load_metadata",
        "Benchmark metadata: server {0}, Exort {1}, Java {2}, plugins {3}.");
    put(
        defaultsEn,
        "message.debug_load_recent_runs",
        "Recent benchmark runs ({0}): TPS avg {1}, MSPT avg {2}, MSPT p95 {3}.");
    put(defaultsEn, "message.debug_load_grade_unknown", "UNKNOWN");
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
    put(
        defaultsEn,
        "message.debug_worldedit_started",
        "WorldEdit verbose logging enabled (mode: {0}).");
    put(defaultsEn, "message.debug_worldedit_stopped", "WorldEdit verbose logging disabled.");
    put(
        defaultsEn,
        "message.debug_worldedit_mode_invalid",
        "Invalid WorldEdit debug mode: {0}. Use compact/normal/full.");
    put(defaultsEn, "message.debug_pick_started", "Pick verbose logging enabled (mode: {0}).");
    put(defaultsEn, "message.debug_pick_stopped", "Pick verbose logging disabled.");
    put(
        defaultsEn,
        "message.debug_pick_mode_invalid",
        "Invalid pick verbose mode: {0}. Use compact/normal/full.");
    put(
        defaultsEn,
        "message.debug_culling_started",
        "Display culling verbose logging enabled (mode: {0}).");
    put(defaultsEn, "message.debug_culling_stopped", "Display culling verbose logging disabled.");
    put(
        defaultsEn,
        "message.debug_culling_mode_invalid",
        "Invalid display culling verbose mode: {0}. Use compact/normal/full.");
    put(
        defaultsEn,
        "message.debug_culling_client_invalid",
        "Unknown player for culling client bypass: {0}.");
    put(
        defaultsEn,
        "message.debug_culling_client_status",
        "Client culling bypass for {0}: manual={1}, auto={2}, source={3}, effective={4},"
            + " config={5}. Client probe: {6}.");
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
    put(defaultsEn, "message.version", "Exort Storage Network v{0} by phantomfighterxx");
    put(defaultsEn, "message.usage_language_header", "Language settings:");
    put(defaultsEn, "message.usage_language_status", "show active language and dictionaries");
    put(defaultsEn, "message.usage_language_set", "change Exort language");
    put(defaultsEn, "message.usage_language_refresh", "refresh item dictionaries");
    put(defaultsEn, "message.usage_mode_header", "Display mode settings:");
    put(defaultsEn, "message.usage_mode_info", "show configured and effective mode");
    put(defaultsEn, "message.usage_mode_set", "switch display mode");
    put(defaultsEn, "message.usage_resourcepack_header", "Resource pack management:");
    put(defaultsEn, "message.usage_resourcepack_status", "show resource-pack pipeline status");
    put(defaultsEn, "message.usage_resourcepack_rebuild", "rebuild and reload the pack pipeline");
    put(defaultsEn, "message.usage_resourcepack_send", "send the ready pack to a player or all");
    put(defaultsEn, "message.lang_refreshed", "Language dictionaries refreshed.");
    put(defaultsEn, "message.lang_set", "Language set to {0}.");
    put(defaultsEn, "message.lang_invalid", "Unknown language: {0}");
    put(defaultsEn, "message.mode_info", "Configured mode: {0}; effective mode: {1}");
    put(defaultsEn, "message.mode_set", "Configured mode set to {0}; effective mode: {1}.");
    put(defaultsEn, "message.mode_fallback", "Mode fallback reason: {0}");
    put(defaultsEn, "message.mode_invalid", "Unknown mode: {0}");
    put(
        defaultsEn,
        "message.mode_fix_paper_missing",
        "Cannot enable RESOURCE mode: Paper config file was not found at {0}.");
    put(
        defaultsEn,
        "message.mode_fix_paper_error",
        "Cannot enable RESOURCE mode: failed to read or update {0}: {1}. Set {2}: true manually"
            + " in this file and restart the server.");
    put(
        defaultsEn,
        "message.mode_fix_paper_access_denied",
        "Cannot enable RESOURCE mode: Exort cannot write {0}. Check file permissions, or set"
            + " {1}: true manually and restart the server.");
    put(
        defaultsEn,
        "message.mode_fix_paper_changed",
        "Paper config updated: {0} set to true in {1}.");
    put(
        defaultsEn,
        "message.mode_fix_paper_restart_required",
        "Paper config already has {0}: true in {1}, but the current Paper runtime still needs a"
            + " restart.");
    put(
        defaultsEn,
        "message.mode_fix_exort_changed",
        "Exort config updated: mode set to RESOURCE.");
    put(defaultsEn, "message.mode_fix_restart_scheduled", "Server restart will run in 10 seconds.");
    put(
        defaultsEn,
        "message.mode_blocked",
        "Cannot enable RESOURCE: Paper's block-updates.disable-chorus-plant-updates is not"
            + " enabled.");
    put(defaultsEn, "message.pack_rebuilt", "Resource pack pipeline reloaded.");
    put(defaultsEn, "message.pack_sent", "Resource pack request sent to {0}.");
    put(defaultsEn, "message.pack_sent_all", "Resource pack request sent to {0} player(s).");
    put(defaultsEn, "message.pack_unavailable", "Resource pack is not ready: {0}");
    put(defaultsEn, "message.pack_service_not_started", "service not started");
    put(defaultsEn, "message.pack_status.status", "Resource pack status: {0}");
    put(defaultsEn, "message.pack_status.configured_hosting", "Configured hosting: {0}");
    put(defaultsEn, "message.pack_status.effective_hosting", "Effective hosting: {0}");
    put(defaultsEn, "message.pack_status.configured_delivery", "Configured delivery: {0}");
    put(defaultsEn, "message.pack_status.effective_delivery", "Effective delivery: {0}");
    put(defaultsEn, "message.pack_status.raw_pack", "Raw pack: {0}");
    put(defaultsEn, "message.pack_status.pack", "Pack: {0}");
    put(defaultsEn, "message.pack_status.obfuscated", "Obfuscated: {0}");
    put(defaultsEn, "message.pack_status.handoff", "Provider handoff: {0}");
    put(defaultsEn, "message.pack_status.sha1", "SHA-1: {0}");
    put(defaultsEn, "message.pack_status.url", "URL: {0}");
    put(defaultsEn, "message.pack_status.note", "Note: {0}");
    put(
        defaultsEn,
        "message.pack_status.provider_note",
        "Resource-pack handoff is ready for {0}; the provider includes it during startup/pack"
            + " generation. If {0} was already enabled, restart or reload that provider manually.");
    put(defaultsEn, "message.pack_status.error", "Error: {0}");
    put(
        defaultsEn,
        "message.resource_pack.required_failure",
        "Required Exort resource pack failed to load. Reconnect and accept it.");
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
    put(defaultsEn, "lore.storage.id_tail", "{0}");
    put(defaultsEn, "gui.prev_page", "Prev Page");
    put(defaultsEn, "gui.next_page", "Next Page");
    put(defaultsEn, "gui.page_info", "Page {0}/{1}");
    put(defaultsEn, "gui.list_truncated", "Showing first {0} stacks; narrow search.");
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
    put(defaultsEn, "gui.category.redstone_blocks", "Redstone Blocks");
    put(defaultsEn, "gui.category.tools_and_utilities", "Tools & Utilities");
    put(defaultsEn, "gui.category.combat", "Combat");
    put(defaultsEn, "gui.category.food_and_drinks", "Food & Drinks");
    put(defaultsEn, "gui.category.ingredients", "Ingredients");
    put(defaultsEn, "gui.category.spawn_eggs", "Spawn Eggs");
    put(defaultsEn, "gui.category.operator", "Operator Utilities");
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
    put(defaultsEn, "message.wire.hard_cap", "Wire chain is too large: {0}/{1}.");
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
    put(defaultsEn, "message.wireless.bound", "Wireless terminal linked.");
    put(
        defaultsEn,
        "message.wireless.self_store",
        "Cannot store a wireless terminal inside itself.");
    put(defaultsEn, "lore.wireless_terminal.battery", "Battery: {0}%");
    put(defaultsEn, "lore.wireless_terminal.owner", "Owner: {0}");
    put(defaultsEn, "lore.wireless_terminal.not_linked", "Not linked");
    put(defaultsEn, "lore.wireless_terminal.storage_tail", "{0}");

    // Russian
    put(defaultsRu, "message.no_permission", "Нет прав.");
    put(defaultsRu, "message.no_storage_adjacent", "Рядом нет хранилища.");
    put(
        defaultsRu,
        "message.multiple_storages_adjacent",
        "Подключено несколько хранилищ. Оставьте только одно.");
    put(defaultsRu, "message.storage_missing_id", "У хранилища отсутствует идентификатор.");
    put(
        defaultsRu,
        "message.storage_load_failed",
        "Не удалось загрузить хранилище. Смотрите консоль.");
    put(
        defaultsRu,
        "message.storage_loading",
        "Хранилище загружается. Повторите через пару секунд.");
    put(defaultsRu, "message.operation_failed", "Операция не выполнена. Смотрите консоль.");
    put(defaultsRu, "message.invalid_terminal", "Это не терминал хранилища.");
    put(defaultsRu, "message.only_player", "Команда доступна только игрокам.");
    put(defaultsRu, "message.player_not_found", "Игрок не найден.");
    put(defaultsRu, "message.command_click", "Кликните, чтобы вставить {0}");
    put(defaultsRu, "message.give_unknown", "Неизвестный тип предмета.");
    put(defaultsRu, "message.give_success", "Выдано {0}x {1} игроку {2}.");
    put(
        defaultsRu,
        "message.give_dropped",
        "Инвентарь игрока {1} был заполнен, {0} предметов сброшено рядом.");
    put(
        defaultsRu,
        "message.give_partial",
        "Для игрока {2} удалось создать только {0} из {1} предметов.");
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
    put(defaultsRu, "message.debug_culling_unavailable", "Сервис display culling недоступен.");
    put(
        defaultsRu,
        "message.debug_location_unknown",
        "Местоположение хранилища для {0} неизвестно.");
    put(defaultsRu, "message.debug_storage_missing", "Не найден тир для хранилища {0}.");
    put(defaultsRu, "message.debug_storage_opened", "Открыто хранилище {0} в режиме {1}.");
    put(defaultsRu, "debug.mode.read", "только чтение");
    put(defaultsRu, "debug.mode.write", "чтение/запись");
    put(defaultsRu, "message.usage_debug_header", "Отладка и инспекция:");
    put(
        defaultsRu,
        "message.usage_debug_player",
        "показать активное или последнее хранилище игрока");
    put(defaultsRu, "message.usage_debug_storage", "открыть хранилище по id или последнему игрока");
    put(defaultsRu, "message.usage_debug_cache", "проверить кэш хранилища и загруженные связи");
    put(defaultsRu, "message.usage_debug_verbose", "включить или выключить диагностический лог");
    put(defaultsRu, "message.usage_debug_culling_client", "управлять client-side culling bypass");
    put(defaultsRu, "message.usage_debug_benchmark", "запустить или остановить тест нагрузки");
    put(defaultsRu, "message.usage_give_header", "Выдача предметов Exort:");
    put(defaultsRu, "message.usage_give_storage", "выдать хранилище по тиру");
    put(defaultsRu, "message.usage_give_item", "выдать фиксированный предмет Exort");
    put(
        defaultsRu,
        "message.usage_give_items",
        "Предметы: storage_core, terminal, crafting_terminal, monitor, import_bus, export_bus,"
            + " wire, wireless_terminal");
    put(defaultsRu, "message.usage_reload", "Использование: /exort reload");
    put(defaultsRu, "message.unknown_subcommand", "Неизвестная подкоманда.");
    put(defaultsRu, "message.help_header", "Команды Exort Storage Network:");
    put(defaultsRu, "message.help_inventory", "меню с предметами Exort");
    put(defaultsRu, "message.help_give", "выдача предметов игроку");
    put(defaultsRu, "message.help_resourcepack", "управление ресурс-паком");
    put(defaultsRu, "message.help_language", "настройки языка");
    put(defaultsRu, "message.help_mode", "настройки режима отображения");
    put(defaultsRu, "message.help_debug", "отладка и инспекция");
    put(defaultsRu, "message.help_version", "показать версию плагина");
    put(defaultsRu, "message.help_reload", "перезагрузить конфиг и runtime");
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
        "Прогресс {0}% • осталось {1}с • TPS {2} • MSPT {3}");
    put(
        defaultsRu,
        "message.debug_load_verdict",
        "Вердикт теста: {0} (TPS min {1}, max {2}, avg {3}, p1 {4}; MSPT min {5}, max {6}, avg"
            + " {7}, p95 {8}, p99 {9})");
    put(
        defaultsRu,
        "message.debug_load_rare_stalls",
        "Редкие stalls: {0} sample(s) ниже TPS 12, min {1}.");
    put(
        defaultsRu,
        "message.debug_load_summary",
        "Модель бенчмарка: игроков {0}, чанков {1}, хранилищ/чанк {2}, шин/чанк {3}, мониторов/чанк"
            + " {4}, оценка оп/тик {5} (глобал {6}, чанк {7}), БД/тик {8}, обновления мониторов"
            + " {9}/t{10}, кабель {11}/{12} miss {13}% cover {14}%, окно измерения {15}с после"
            + " сборки нагрузки (мин. прогрев {16}с), джиттер {17}%, guard-игроков {18},"
            + " guard-сущностей {19}/{20}, пересоздание guard {21}/тик, world lanes {22}, world"
            + " ops/tick {23}, world blocks {24}, длина кабеля в водном тесте {25}, источники воды"
            + " {26}, доп. чанки воды {27}");
    put(
        defaultsRu,
        "message.debug_load_measurement_started",
        "Измерение бенчмарка начато после сборки нагрузки и стабилизации потока воды.");
    put(
        defaultsRu,
        "message.debug_load_hints",
        "Синтетическая модель: доминирующая нагрузка — {0}; CPU {1}%, кабель {2}%, мониторы {3}%,"
            + " БД {4}%, wireless {5}%, guard {6}%, WORLD {7}% (placements {8}).");
    put(
        defaultsRu,
        "message.debug_load_mspt_verdict",
        "Вердикт устойчивого MSPT: {0} (avg {1}, p95 {2}, p99 {3}).");
    put(defaultsRu, "message.debug_load_measured_profile", "Измеренный профиль Exort: {0}.");
    put(
        defaultsRu,
        "message.debug_load_metadata",
        "Метаданные бенчмарка: server {0}, Exort {1}, Java {2}, plugins {3}.");
    put(
        defaultsRu,
        "message.debug_load_recent_runs",
        "Последние прогоны ({0}): TPS avg {1}, MSPT avg {2}, MSPT p95 {3}.");
    put(defaultsRu, "message.debug_load_grade_unknown", "UNKNOWN");
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
    put(
        defaultsRu,
        "message.debug_worldedit_started",
        "Подробный лог WorldEdit включен (режим: {0}).");
    put(defaultsRu, "message.debug_worldedit_stopped", "Подробный лог WorldEdit выключен.");
    put(
        defaultsRu,
        "message.debug_worldedit_mode_invalid",
        "Неверный режим лога WorldEdit: {0}. Используйте compact/normal/full.");
    put(defaultsRu, "message.debug_pick_started", "Подробный лог pick включен (режим: {0}).");
    put(defaultsRu, "message.debug_pick_stopped", "Подробный лог pick выключен.");
    put(
        defaultsRu,
        "message.debug_pick_mode_invalid",
        "Неверный режим лога pick: {0}. Используйте compact/normal/full.");
    put(
        defaultsRu,
        "message.debug_culling_started",
        "Подробный лог display culling включен (режим: {0}).");
    put(defaultsRu, "message.debug_culling_stopped", "Подробный лог display culling выключен.");
    put(
        defaultsRu,
        "message.debug_culling_mode_invalid",
        "Неверный режим лога display culling: {0}. Используйте compact/normal/full.");
    put(
        defaultsRu,
        "message.debug_culling_client_invalid",
        "Неизвестный игрок для client culling bypass: {0}.");
    put(
        defaultsRu,
        "message.debug_culling_client_status",
        "Client culling bypass для {0}: вручную={1}, auto={2}, источник={3}, действует={4},"
            + " конфиг={5}. Ответ клиента: {6}.");
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
    put(defaultsRu, "message.usage_language_header", "Настройки языка:");
    put(defaultsRu, "message.usage_language_status", "показать активный язык и словари");
    put(defaultsRu, "message.usage_language_set", "сменить язык Exort");
    put(defaultsRu, "message.usage_language_refresh", "обновить словари предметов");
    put(defaultsRu, "message.usage_mode_header", "Настройки режима отображения:");
    put(defaultsRu, "message.usage_mode_info", "показать режим из конфига и фактический режим");
    put(defaultsRu, "message.usage_mode_set", "переключить режим отображения");
    put(defaultsRu, "message.usage_resourcepack_header", "Управление ресурс-паком:");
    put(
        defaultsRu,
        "message.usage_resourcepack_status",
        "показать состояние пайплайна ресурс-пака");
    put(defaultsRu, "message.usage_resourcepack_rebuild", "пересобрать и перезагрузить пайплайн");
    put(defaultsRu, "message.usage_resourcepack_send", "отправить готовый пак игроку или всем");
    put(defaultsRu, "message.lang_refreshed", "Словари языка обновлены.");
    put(defaultsRu, "message.lang_set", "Язык установлен: {0}.");
    put(defaultsRu, "message.lang_invalid", "Неизвестный язык: {0}");
    put(defaultsRu, "message.mode_info", "Режим в конфиге: {0}; фактический режим: {1}");
    put(defaultsRu, "message.mode_set", "Режим в конфиге установлен: {0}; фактический режим: {1}.");
    put(defaultsRu, "message.mode_fallback", "Причина fallback режима: {0}");
    put(defaultsRu, "message.mode_invalid", "Неизвестный режим: {0}");
    put(
        defaultsRu,
        "message.mode_fix_paper_missing",
        "Нельзя включить режим RESOURCE: файл конфигурации Paper не найден по пути {0}.");
    put(
        defaultsRu,
        "message.mode_fix_paper_error",
        "Нельзя включить режим RESOURCE: не удалось прочитать или обновить {0}: {1}. Вручную"
            + " установите {2}: true в этом файле и перезапустите сервер.");
    put(
        defaultsRu,
        "message.mode_fix_paper_access_denied",
        "Нельзя включить режим RESOURCE: Exort не может записать {0}. Проверьте права доступа к"
            + " файлу или вручную установите {1}: true и перезапустите сервер.");
    put(
        defaultsRu,
        "message.mode_fix_paper_changed",
        "Конфиг Paper обновлен: {0} установлен в true в {1}.");
    put(
        defaultsRu,
        "message.mode_fix_paper_restart_required",
        "В конфиге Paper уже указано {0}: true в {1}, но текущему рантайму Paper все еще нужен"
            + " рестарт.");
    put(
        defaultsRu,
        "message.mode_fix_exort_changed",
        "Конфиг Exort обновлен: mode установлен в RESOURCE.");
    put(
        defaultsRu,
        "message.mode_fix_restart_scheduled",
        "Рестарт сервера будет выполнен через 10 секунд.");
    put(
        defaultsRu,
        "message.mode_blocked",
        "Нельзя включить RESOURCE: в Paper не отключены"
            + " block-updates.disable-chorus-plant-updates.");
    put(defaultsRu, "message.pack_rebuilt", "Пайплайн ресурс-пака перезагружен.");
    put(defaultsRu, "message.pack_sent", "Запрос ресурс-пака отправлен игроку {0}.");
    put(defaultsRu, "message.pack_sent_all", "Запрос ресурс-пака отправлен игрокам: {0}.");
    put(defaultsRu, "message.pack_unavailable", "Ресурс-пак не готов: {0}");
    put(defaultsRu, "message.pack_service_not_started", "сервис не запущен");
    put(defaultsRu, "message.pack_status.status", "Статус ресурс-пака: {0}");
    put(defaultsRu, "message.pack_status.configured_hosting", "Хостинг в конфиге: {0}");
    put(defaultsRu, "message.pack_status.effective_hosting", "Фактический хостинг: {0}");
    put(defaultsRu, "message.pack_status.configured_delivery", "Доставка в конфиге: {0}");
    put(defaultsRu, "message.pack_status.effective_delivery", "Фактическая доставка: {0}");
    put(defaultsRu, "message.pack_status.raw_pack", "Исходный ресурс-пак: {0}");
    put(defaultsRu, "message.pack_status.pack", "Готовый ресурс-пак: {0}");
    put(defaultsRu, "message.pack_status.obfuscated", "Обфускация: {0}");
    put(defaultsRu, "message.pack_status.handoff", "Передача провайдеру: {0}");
    put(defaultsRu, "message.pack_status.sha1", "SHA-1: {0}");
    put(defaultsRu, "message.pack_status.url", "URL: {0}");
    put(defaultsRu, "message.pack_status.note", "Примечание: {0}");
    put(
        defaultsRu,
        "message.pack_status.provider_note",
        "Передача ресурс-пака для {0} готова; провайдер включит ресурсы Exort при запуске"
            + " или генерации пака. Если {0} уже был включён, перезапустите или перезагрузите"
            + " этот провайдер вручную.");
    put(defaultsRu, "message.pack_status.error", "Ошибка: {0}");
    put(
        defaultsRu,
        "message.resource_pack.required_failure",
        "Обязательный ресурс-пак Exort не загрузился. Перезайдите и примите его.");
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
    put(defaultsRu, "lore.storage.id_tail", "{0}");
    put(defaultsRu, "gui.prev_page", "Пред. страница");
    put(defaultsRu, "gui.next_page", "След. страница");
    put(defaultsRu, "gui.page_info", "Страница {0}/{1}");
    put(defaultsRu, "gui.list_truncated", "Показаны первые {0} стаков; уточните поиск.");
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
    put(defaultsRu, "message.wire.hard_cap", "Цепь кабеля слишком большая: {0}/{1}.");
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
    put(defaultsRu, "message.wireless.bound", "Беспроводной терминал привязан.");
    put(
        defaultsRu,
        "message.wireless.self_store",
        "Нельзя положить беспроводной терминал сам в себя.");
    put(defaultsRu, "lore.wireless_terminal.battery", "Батарея: {0}%");
    put(defaultsRu, "lore.wireless_terminal.owner", "Владелец: {0}");
    put(defaultsRu, "lore.wireless_terminal.not_linked", "Не привязан");
    put(defaultsRu, "lore.wireless_terminal.storage_tail", "{0}");
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
    File langDir = new File(plugin.getDataFolder(), "lang");
    if (!langDir.exists()) {
      langDir.mkdirs();
    }
    Set<String> bundled = bundledLanguageCodes();
    Set<String> protectedBundled = writeBundledDefaults(langDir, bundled);

    String requested = normalizeLanguage(language);
    Set<String> requestedLanguages = new HashSet<>(localLanguageFiles(langDir));
    requestedLanguages.addAll(bundled);

    Map<String, Map<String, String>> loaded = new HashMap<>();
    for (String code : requestedLanguages) {
      loaded.put(code, loadLanguage(langDir, code, !protectedBundled.contains(code)));
    }
    Map<String, String> selected = loaded.get(requested);
    if (selected == null || selected.isEmpty()) {
      requested = DEFAULT_LANGUAGE;
      selected = loaded.getOrDefault(DEFAULT_LANGUAGE, defaultsEn);
    }
    languages = Map.copyOf(loaded);
    activeLanguage = requested;
    active = selected;
  }

  private Set<String> localLanguageFiles(File langDir) {
    Set<String> result = new HashSet<>();
    File[] files = langDir.listFiles((dir, name) -> name.endsWith(LANG_EXT));
    if (files == null) {
      return result;
    }
    for (File file : files) {
      String name = file.getName();
      result.add(normalizeLanguage(name.substring(0, name.length() - LANG_EXT.length())));
    }
    return result;
  }

  private Set<String> bundledLanguageCodes() {
    if (plugin == null) {
      return bundledLanguages;
    }
    Set<String> result = new TreeSet<>();
    try (InputStream in = plugin.getResource(BUNDLED_LANG_INDEX)) {
      if (in != null) {
        YamlConfiguration cfg =
            YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String code : cfg.getStringList("languages")) {
          result.add(normalizeLanguage(code));
        }
      }
    } catch (IOException e) {
      plugin.getLogger().warning("Failed to read bundled language index: " + e.getMessage());
    }
    result.add(DEFAULT_LANGUAGE);
    result.add(RUSSIAN_LANGUAGE);
    bundledLanguages = Set.copyOf(result);
    return bundledLanguages;
  }

  private Set<String> writeBundledDefaults(File langDir, Set<String> bundled) {
    boolean overwrite =
        plugin == null || plugin.getConfig().getBoolean(CONFIG_AUTO_OVERWRITE, true);
    Set<String> preserve = preservedLanguages();
    Set<String> protectedLanguages = new HashSet<>();
    for (String code : bundled) {
      File target = new File(langDir, code + LANG_EXT);
      boolean protectedExistingFile = target.exists() && (!overwrite || preserve.contains(code));
      if (protectedExistingFile) {
        protectedLanguages.add(code);
        continue;
      }
      writeDefaults(langDir, code, defaultData(code));
    }
    return protectedLanguages;
  }

  private Set<String> preservedLanguages() {
    if (plugin == null) {
      return Collections.emptySet();
    }
    Set<String> preserve = new HashSet<>();
    for (String code : plugin.getConfig().getStringList(CONFIG_PRESERVE_LANGUAGES)) {
      preserve.add(normalizeLanguage(code));
    }
    return preserve;
  }

  private Map<String, String> loadLanguage(File langDir, String code, boolean updateFile) {
    Map<String, String> base = defaultData(code);
    Map<String, String> loaded = new HashMap<>(defaultsEn);
    loaded.putAll(base);
    File target = new File(langDir, code + LANG_EXT);
    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(target);
    configureYaml(cfg);
    boolean changed = false;
    Set<String> keys = new TreeSet<>(defaultsEn.keySet());
    keys.addAll(base.keySet());
    for (String key : keys) {
      String fallback = base.getOrDefault(key, defaultsEn.getOrDefault(key, key));
      if (!cfg.contains(key)) {
        cfg.set(key, fallback);
        changed = true;
      }
      loaded.put(key, getStringOrDefault(cfg, key, fallback));
    }
    if (changed && updateFile) {
      try {
        cfg.save(target);
      } catch (IOException e) {
        plugin
            .getLogger()
            .severe("Failed to save language file " + target.getName() + ": " + e.getMessage());
      }
    }
    return loaded;
  }

  private Map<String, String> defaultData(String code) {
    String normalized = normalizeLanguage(code);
    if (DEFAULT_LANGUAGE.equals(normalized)) {
      return defaultsEn;
    }
    if (RUSSIAN_LANGUAGE.equals(normalized)) {
      Map<String, String> bundled = bundledDefaultData(normalized);
      return bundled.isEmpty() ? defaultsRu : bundled;
    }
    Map<String, String> bundled = bundledDefaultData(normalized);
    return bundled.isEmpty() ? defaultsEn : bundled;
  }

  private Map<String, String> bundledDefaultData(String code) {
    if (plugin == null) {
      return Collections.emptyMap();
    }
    String normalized = normalizeLanguage(code);
    return bundledDefaults.computeIfAbsent(normalized, this::loadBundledDefaultData);
  }

  private Map<String, String> loadBundledDefaultData(String code) {
    try (InputStream in = plugin.getResource(BUNDLED_LANG_DIR + code + LANG_EXT)) {
      if (in == null) {
        return Collections.emptyMap();
      }
      YamlConfiguration cfg =
          YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
      configureYaml(cfg);
      Map<String, String> result = new HashMap<>();
      for (String key : cfg.getKeys(true)) {
        if (cfg.isConfigurationSection(key)) {
          continue;
        }
        String value = cfg.getString(key);
        if (value != null) {
          result.put(key, value);
        }
      }
      return Map.copyOf(result);
    } catch (IOException e) {
      plugin
          .getLogger()
          .warning(
              "Failed to read bundled language file " + code + LANG_EXT + ": " + e.getMessage());
      return Collections.emptyMap();
    }
  }

  private void writeDefaults(File dir, String name, Map<String, String> data) {
    File file = new File(dir, name + LANG_EXT);
    YamlConfiguration cfg = new YamlConfiguration();
    configureYaml(cfg);
    for (String key : new TreeSet<>(data.keySet())) {
      cfg.set(key, data.get(key));
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
    return trLanguage(activeLanguage, key, params);
  }

  public String tr(CommandSender sender, String key, Object... params) {
    if (sender instanceof Player player) {
      return tr(player, key, params);
    }
    return tr(key, params);
  }

  public String tr(Player player, String key, Object... params) {
    return trLanguage(pluginTextLanguage(player), key, params);
  }

  public String trLanguage(String language, String key, Object... params) {
    String resolvedLanguage = pluginTextLanguage(language);
    Map<String, String> selected = languages.getOrDefault(resolvedLanguage, active);
    String base =
        selected.getOrDefault(key, active.getOrDefault(key, defaultsEn.getOrDefault(key, key)));
    if (params.length == 0) return base;
    return formatParams(base, params);
  }

  private String formatParams(String base, Object... params) {
    String result = base;
    for (int i = 0; i < params.length; i++) {
      result = result.replace("{" + i + "}", String.valueOf(params[i]));
    }
    return result;
  }

  public Component itemComponent(boolean clientTranslations, String key, Object... params) {
    Component component = clientComponent(clientTranslations, key, params);
    return component.decoration(TextDecoration.ITALIC, false);
  }

  public Component clientComponent(boolean clientTranslations, String key, Object... params) {
    String fallback = tr(key, params);
    if (!clientTranslations) {
      return Component.text(fallback);
    }
    return Component.translatable(clientKey(key), fallback, componentArgs(params));
  }

  private Component[] componentArgs(Object... params) {
    Component[] args = new Component[params.length];
    for (int i = 0; i < params.length; i++) {
      args[i] = Component.text(String.valueOf(params[i]));
    }
    return args;
  }

  public String clientKey(String key) {
    return "exort." + key;
  }

  public String configuredLanguage() {
    return activeLanguage;
  }

  public boolean hasLanguage(String language) {
    return languages.containsKey(normalizeLanguage(language));
  }

  public Set<String> availableLanguages() {
    return Set.copyOf(languages.keySet());
  }

  public String pluginTextLanguage(Player player) {
    if (player == null) {
      return activeLanguage;
    }
    return pluginTextLanguage(player.locale().toString());
  }

  public String pluginTextLanguage(String requestedLanguage) {
    String normalized = normalizeLanguage(requestedLanguage);
    if (languages.containsKey(normalized)) {
      return normalized;
    }
    if (languages.containsKey(activeLanguage)) {
      return activeLanguage;
    }
    return DEFAULT_LANGUAGE;
  }

  public String normalizeLanguage(String input) {
    if (input == null || input.isBlank()) return DEFAULT_LANGUAGE;
    return input.toLowerCase(Locale.ROOT).replace('-', '_');
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
