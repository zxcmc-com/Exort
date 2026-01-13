# Changelog

## 0.8.2 — 2026-01-13
- Added bStats integration.

## 0.8.1 — 2026-01-13
- Language file export no longer wraps lines, fixing invalid YAML in `lang/*.yml` (multiline values preserved).
- Codebase cleanup and formatting (imports unified, formatting standardized, unused package removed).

## 0.8.0 — 2026-01-08
- Added lightweight recipe system with `recipes.yml` (shaped/shapeless/smithing + disabled list) and `recipes.enabled` toggle.
- Recipe file is now created from defaults on first run (no config merging to preserve lists).
- Introduced **Storage Core** item/block (dummy storage base) with placement/break support and displays; storage crafting now uses core + tier materials.
- Crafting rules moved into `core.recipes` and treat `exort` recipes as internal (not blocked by external‑recipe restriction).

## 0.7.8 — 2026-01-06
- Benchmark model adjusted to better match real wire scans (cache‑miss ratio + partial coverage) and summary now shows miss/cover.
- Benchmark verdict now includes MSPT min/max/avg/p95/p99 and adds “profile hints” with dominant subsystem share.
- Benchmark verdict MSPT now uses server tick time (consistent with progress output).
- Benchmark parameters are now hardcoded in code and the `benchmark` section was removed from config.
- Bus loop guard: side‑sensitive inventories are excluded from loop checks; loop error is cleared immediately when bus mode is Disabled.
- Bus GUI: info button switches to an error barrier while loop is active and returns to normal when resolved.
- Bus placement orientation refined (inventory/storage → face target, wire → face away, other blocks → face player view).
- Resource pack export file renamed: `exort_pack.zip` → `exort.zip`.

## 0.7.7 — 2026-01-06
- Custom items now refresh names/models when mode or locale changes, on player join, and on first open of a container (epoch cache prevents repeated scans).
- Storage cache refresh now re-keys custom items to keep stacks consistent after item model/name updates.
- Wireless terminal item name and GUI title are now localized correctly.
- Placement logic now respects vanilla interactables (no placement without Shift), buses still place on inventories, and wireless GUI no longer opens on interactable blocks or when using from main hand.

## 0.7.6 — 2026-01-06
- Large internal refactor: codebase split into clearer modules (core/bus/wireless/storage), new `ExortApi`, and updated paper plugin metadata.
- Mode switch (`/exort mode set`) now invalidates network cache and force-refreshes terminals/monitors immediately.
- Info button errors now show their text directly in the button lore (including wireless self-store), and barrier tooltips are only hidden when no lore is present.

## 0.7.5 — 2026-01-06
- Reload now fully restarts scheduled tasks (flush + cache eviction) to prevent duplicated timers after `/exort reload`.
- Custom block breaker task is properly stopped on reconfigure to avoid multiple background breakers.
- Wireless session watcher now iterates over a snapshot to avoid concurrent modifications when closing inventories.
- Network link resolution now uses a persistent graph cache with targeted invalidation, reducing repeated BFS scans.
- Storage GUI renders are coalesced per tick to avoid redundant refresh storms.
- Monitor displays avoid unnecessary base/item teleports and refresh once storage data is loaded after restart.
- Terminal displays now light up immediately when wires are restored.
- Storage flush now reuses pre-serialized item blobs and writes deltas (upserts/removals) instead of full snapshots for better performance on large storages.

## 0.7.4 — 2026-01-06
- Benchmark system refined (model, verdicts, and outputs) and now covers more gameplay parameters.
- `/exort debug benchmark start` supports optional duration in seconds.
- Wireless terminal owner name is cached in PDC to avoid offline name lookups on every render.
- Bus target resolution and loop guard logic extracted into dedicated helpers (no behavior change).
- SessionManager now reconfigures from plugin getters to keep wireless settings synced after reloads.
- Bus filter key set is now immutable to prevent accidental external mutation.
- Fixed placement sounds for terminal/crafting/monitor/bus blocks.

## 0.7.3 — 2026-01-06
- Crafting terminal now checks ingredient availability atomically to avoid partial removals under concurrent changes.
- Crafting now briefly pauses export buses of the same storage and import buses that target it to avoid race removal on the craft tick.
- Added idle storage cache eviction (configurable) to reduce memory usage on large servers.
- Added `/exort debug verbose cache start [compact|normal|full] [storage <uuid>]` with filtering and a compact 10s summary (including last evict info).
- Added `/exort debug cache status <uuid|player>` showing cache state, eviction eligibility, chunk load level, plugin tickets, players, and linked network node counts.
- Cache debug now tracks last touch source in full mode to pinpoint unwanted cache keep‑alives.
- Cache debug output switched to Adventure components.
- Monitor displays now use peek reads (don’t touch or load caches when chunks are unloaded).
- Marker sanity checks no longer touch caches, so idle eviction triggers correctly even with monitors/buses present.

## 0.7.2 — 2026-01-06
- Wireless watcher now reconfigures after runtime reloads; range/disabled checks remain active after config changes.
- Wireless charging no longer double‑counts time during GUI renders (stable charge progression).
- Network scans skip unloaded chunks and respect `wireHardCap` to avoid unintended chunk loads.
- Wire status checks also skip unloaded chunks.
- Wireless GUI now validates the storage block exists before opening, preventing brief open/close on missing storages.

## 0.7.1 — 2026-01-05
- Wireless GUI charge display now refreshes automatically while charging (scheduled refresh + short post‑charge refresh window) to prevent stuck percentages.
- Wireless range checks run more often, so GUI closes reliably after teleport/out‑of‑range.
- Monitor item display now treats wireless terminal like wire for `itemConfig`.
- Wireless usage no longer consumes charge in Creative mode.
- Wireless binding now respects region protection (WorldGuard) for terminals and storages.

## 0.7.0 — 2026-01-05
- Added Wireless Terminal item: bind/unbind to storages, charge in Exort storages (2 min to full), 1% charge per use, range limit by chunks, owner safety checks.
- Wireless terminal can be linked via storage/terminal click; unlinked terminal works only for binding (no charge cost).
- Added wireless configs (enable, range), models for VANILLA/RESOURCE, and localized lore lines (battery/owner/not linked/UUID tail).
- Wireless safety fixes: self‑store blocked, off‑hand use supported, GUI closes on range loss, and crafting‑terminal unbind clears grid.
- Crafting terminal unbind now verifies the original terminal exists in storage to prevent duplication.
- Fixed lang loading edge case where nested sections produced “MemorySection[path=…]” instead of translated strings.

## 0.6.4 — 2026-01-05
- Bus loop guard logic refined: overlapping filters disable export bus only; import continues, and side‑sensitive inventories ignore import‑ALL for loop checks.

## 0.6.3 — 2026-01-05
- Storage tiers moved to a separate `storage-tiers.yml`; main `config.yml` cleaned accordingly.
- Import/Export buses: legacy export-debt TTL removed; new loop guard disables buses when import+export hit the same inventory/storage with ALL/ALL or overlapping filters, with bossbar/GUI error.
- Fixed legacy-material WARN by switching break-tool parsing to Bukkit tags (pickaxe/axe/shovel/hoe/sword etc.).
- Debug commands: `/exort debug player` now shows a clickable storage UUID (suggests `/exort debug storage <uuid>`); `/exort debug storage` accepts player name to open their last storage; player name tab-completion added.

## 0.6.2 — 2026-01-05
- Added anti-loop protection for buses (export debt TTL) and per-chunk operation limits.
- Added benchmark command `/exort debug benchmark` with 60s stress test, TPS/MSPT progress, auto verdict, and early abort on low TPS.
- Benchmark now simulates wire scans, display updates, and DB load based on current config values; safe radius enforced for display simulation.
- Added auto benchmark on first run (fresh DB) after server startup.
- Added summary line for benchmark model parameters in console/chat.
- Updated default bus limits to 6000 ops/tick and 600 ops/chunk.
- Added config blocks for `wire.limit`/`wire.hardCap` and benchmark settings.
- Monitor display update fixed when inserting item into empty storage (0% text no longer sticks).
- Updated import/export bus models and textures.

## 0.6.1 — 2026-01-04
- Bus GUI reworked to 2 rows: mode/info buttons repositioned, filter slots reduced to 10, fillers in unused slots.
- Info buttons now show storage UUID on Shift+RMB for buses and terminals; UUID persists until GUI close.
- Terminal force‑write confirmation now starts after the second Shift+RMB (first click only reveals UUID).
- Buses can connect directly to Exort storages; terminal counts refresh on bus transfer operations.
- Storage/terminal/monitor/bus front faces no longer act as wire connections; bus on wire faces away from wire.
- Added monitor empty‑state display with colored fill % + separate config offsets; empty monitor RMB shows storage bossbar.
- Added config defaults for terminal sort mode and bus default modes; bus GUI prefix/font moved to config.
- Added break settings for monitor/bus and copper tools to effective break tool lists.
- Fixed terminals staying visually disabled after switching VANILLA → RESOURCE (delayed display refresh).

## 0.6.0 — 2026-01-03
- Added Import/Export Buses with 3‑row GUI, filter slots, and 4 modes (Disabled/Whitelist/Blacklist/All).
- Bus logic follows vanilla side rules for applicable inventories (furnaces/brewing) and uses faster cursor‑based transfers.
- Bus settings are persisted in DB (`bus_settings`) with filters serialized; marker mode is mirrored to chunk data.
- Bus sleep‑mode and global per‑tick limits added for stability under high load.
- New bus displays/models for VANILLA/RESOURCE modes; supports vertical placement with full facing rotations.
- Added bossbar error feedback and GUI info button for storage/inventory connection status.
- `/exort give` now supports `import_bus` and `export_bus`; pick‑block and placement bridge updated.
- Wire connectivity visuals now include buses.
- Search now shows an empty results page when no matches; full list starts from the next page.

## 0.5.5 — 2026-01-03
- Major performance refactor for GUI sessions (shared base, cached search/sort display lists).
- Storage cache now tracks effective totals and versions to reduce repeated scans and avoid stale clean marks.
- Storage loading deduplication and batched DB snapshot writes (lower IO spikes).
- Item key hashing optimized (thread‑local SHA‑256 + fast hex; fewer clone/serialize passes).
- Search/sort pipelines optimized (cached name/id/category/creative tab positions; per-render search candidates cache).
- Terminal network scanning optimized (per-tick cache + early exit on multi-storage).
- Display refresh and cleanup optimized (skip refresh if chunk has no markers; lazy monitor UUID reads).
- Monitor display updates reduced (only refresh item/text when changed).
- GUI filler items cached and reused; inventory move loops reuse inventory handles.
- Wire display manager micro‑optimizations (EnumMap rotations, smaller entity scan radius).

## 0.5.4 — 2026-01-03
- Added `paper-plugin.yml` and resource expansion for plugin metadata.
- Improved monitor placement safety (recent‑placement guard to avoid immediate item capture).
- Terminal/storage GUI fillers fixed: only pad search result pages, not all slots.

## 0.5.3 — 2026-01-02
- Monitor RMB now shows item name + exact count (bossbar) instead of storage fill.
- Custom item naming switched to `item_name` (GUI heads still use `custom_name`).
- Monitor displays Nexo furniture items using `GROUND` transform.
- Minor search dialog optimization (cached dialog, invalidated on lang reload).
- Category sorting refinements (custom items forced to “Other”, search/category labels).

## 0.5.2 — 2026-01-02
- Search UI stabilized on Paper Dialogs (per‑session filtering, active query display).
- Monitor screens upgraded with category‑based offsets/scales (item/block/horizontal/thin/full).
- Added forced RW takeover via info button (5x Shift+RMB) with timeout and moderator lock handling.
- GUI/session refactor cleanup (GuiSession rename) and docs sync.

## 0.5.1 — 2026-01-02
- Renamed plugin to **Exort** (commands, namespaces, jar name).
- Monitor minor improvements.
- Resource pack textures updated.

## 0.5.0 — 2026-01-02
- Added monitor blocks with screen item + count display (AE2‑style).
- Search uses Paper Dialogs; search button shows active query.
- Hologram brightness adjustments (storage + monitor only).

## 0.4.3 — 2026-01-02
- Added Category sorting based on creative inventory tabs.

## 0.4.2 — 2025-12-31
- Major code optimization.
- Async improvements.
- GUI slots unification.

## 0.4.1 — 2025-12-30
- Added missing GUI textures.
- Removed unused textures.

## 0.4.0 — 2025-12-30
- Item search added to terminal.
- Sorting mode persistence.

## 0.3.3 — 2025-12-30
- Implemented loading of localized dictionaries for active lang packs.
- Improved sorting.
- Moved player error notifications to boss bar.
- Minor GUI tweaks.

## 0.3.2 — 2025-12-29
- Sorting improvements.
- Added sort and info buttons.
- Buttons in VANILLA mode now use player custom heads.
- Bossbar minor improvements.

## 0.3.1 — 2025-12-24
- Disabled shadowJar to reduce plugin size.

## 0.3.0 — 2025-12-24
- Added Crafting Terminal.

## 0.2.4 — 2025-12-24
- TerminalDisplay rework.
- Replaced old models and textures.

## 0.2.3 — 2025-12-23
- Wires in RESOURCE mode now use only center and connection models.

## 0.2.2 — 2025-12-22
- Major code, database and config refactor.
- Legacy cleanup.
- Items crafting fix.

## 0.2.1 — 2025-12-21
- Code optimization.
- Legacy cleanup.
- Localization improved.
- Holograms reworked.

## 0.2.0 — 2025-12-20
- Item Displays now used to show block textures.
- Added resource pack mode with rotating cables.

## 0.1.8 — 2025-12-17
- Added README, fixed nested storages bugs.

## 0.1.7 — 2025-12-16
- Fixed nested storages items count.
- Multiple viewers behavior adjustments.
- Minor bug fixes and optimization.

## 0.1.6 — 2025-12-15
- Major update: holograms, many bug fixes.
- Many hardcoded values moved to config.

## 0.1.5 — 2025-12-15
- Minor code improvements and player interaction fixes.

## 0.1.4 — 2025-12-15
- Config and bossbar update.
- Wires are now custom blocks.
- Added MMB support.

## 0.1.3 — 2025-12-14
- Added wires (glass) and wired connections.

## 0.1.2 — 2025-12-14
- Bossbars, items, and Shift+RMB fixes.

## 0.1.1 — 2025-12-14
- Storage block is Vault now; vanilla blocks disabled.
- GUI improvements.

## 0.1.0 — 2025-12-14
- Initial implementation and baseline plugin setup.
