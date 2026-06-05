# Changelog

## 0.14.1 — 2026-06-05
- Hardened asynchronous database scheduling so writes submitted during shutdown now return failed futures, log clear rejection context, and keep the DB queue-depth metric balanced after completed work.
- Reduced default bus processing budgets to 500 global operations and 40 per chunk per tick, lowering tick spikes on new installs while keeping explicitly configured server values unchanged.
- Changed WorldEdit marker application to schedule drain work only while marker updates are queued, removing the idle per-tick task without changing bounded retries or deferred refreshes.
- Replaced RESOURCE block-proxy proxied gauge scans with a maintained counter updated on proxy, restore, and cleanup transitions.

## 0.14.0 — 2026-06-05
- Added unified territory-protection checks for Exort placement, breaking, interaction, and storage access across WorldGuard, ProtectionStones regions, GriefPrevention claims, Towny permissions, Lands, and Residence.
- Replaced the WorldGuard-only protection config with `protection.*` controls, including per-adapter toggles and a shared fail-closed option for protection-check errors.

## 0.13.4 — 2026-06-04
- Simplified `config.yml`: debug/tuning defaults for display culling, WorldEdit bulk processing, placement guard geometry, visual models, break sounds, crafting rules, bossbar durations, and resource-pack required state are now fixed in code.
- Flattened several runtime settings for clearer administration, including `updateCheck`, `placementGuard`, `performance.worldEditBulk`, `protocolLib.localizationLevel`, and moved storage/session/bus performance limits under `performance`.
- Config updates now drop removed or unknown options instead of preserving them as commented retired keys, so old tuning/debug entries are cleaned from existing server configs on reload.
- Fixed swapped Serbian bundled translations so `sr_cs` uses Latin Serbian and `sr_sp` uses Cyrillic Serbian, matching Minecraft locale codes.

## 0.13.3 — 2026-06-03
- In VANILLA mode, ProtocolLib localization now shows Exort item names and lore in each player's language when a matching translation is available, while the real server-side item stacks stay unchanged.
- Added `protocolLib.localization.level`: `SIMPLE` localizes Exort inventory items and lore, while `FULL` also localizes Exort block/display names for clients and Jade-like inspection mods without requiring the resource pack.
- RESOURCE pack exports now generate Exort item and lore translations from the same bundled language files used by the plugin, keeping server text and client resource-pack text in sync.
- The `plugins/Exort/lang` folder is no longer filled with every bundled translation by default; keep files there only for local overrides, preserved locales, or manual translation testing.
- Debug benchmark output now respects language context: players see their own localized benchmark messages, while duplicated console lines use the configured server language.

## 0.13.2 — 2026-06-03
- Exort now bundles full command, GUI, debug, item, lore, display, and resource-pack translations for every locale in the pinned Minecraft language index, including joke and constructed-language locales.
- Bundled creative-category labels now match vanilla Minecraft language files for every pinned locale, with additional cleanup for pre-reform Russian and rare/joke locale wording.
- Added `languageFiles.autoOverwriteBundled` and `languageFiles.preserveLanguages` config controls so bundled language files are refreshed by default while selected locales can be protected for manual translation testing.
- Fixed wireless-terminal lore translation keys so generated YAML language files no longer conflict with the `item.wireless_terminal` item-name key.
- Item-name dictionary refreshes no longer download every bundled Exort UI locale on startup; Exort refreshes the active/base dictionaries and existing item dictionaries, then preloads additional player locales on demand.

## 0.13.1 — 2026-06-02
- Fixed terminal and crafting-terminal search dialog lifecycle so the parent GUI remains registered while Paper closes and returns from the dialog, allowing the Search button to reopen the dialog repeatedly after applying, cancelling, or Esc-returning.
- Fixed per-player localization regressions so Exort display/item fallback text stays on the configured server language instead of being replaced by the active vanilla item dictionary.
- RESOURCE packs now bundle translated Exort item names plus client-visible lore and display-name translation keys for every locale in the pinned Minecraft language index, so players no longer fall through to `en_us` item names when their client locale has no Exort UI translation.
- Exort now resolves command, GUI, bossbar, and debug/status text per player: client locales use matching Exort translations when available, otherwise falling back to the configured server language and then `en_us`.
- Terminal item search and name sorting now use the player's Minecraft item dictionary when that locale is known, even when Exort itself has no UI translation for that language; unknown dictionaries fall back to the configured server language.
- Added a `protocolLib.localization.enabled` capability gate and diagnostics entry so ProtocolLib packet localization can be enabled only after a compatible packet rewrite implementation is available.

## 0.13.0 — 2026-06-01
- Exort now respects AuthMe and LoginSecurity unauthenticated-player restrictions for wireless terminals, monitors, and Exort GUI clicks, preventing storage access or monitor edits before login while keeping normal authenticated gameplay unchanged.
- WorldEdit/FAWE selection wand clicks are now ignored by Exort block interactions and custom breaking, so selecting a monitor/storage/terminal/bus/wire block does not open Exort UI, show Exort peek data, or set a monitor item.
- Fixed terminal and crafting-terminal category rendering when padded GUI slots are visible, preventing repeated sort/search clicks from logging task exceptions and interrupting terminal rendering.

## 0.12.11 — 2026-06-01
- RESOURCE block proxy restores now reveal the matching display before removing the client-side proxy block, and proxy entry hides displays only after the fake block change is sent, reducing blank texture pop-in while moving through the display-culling transition.
- RESOURCE block proxies now use two dedicated chorus-plant model states: storage carriers keep the storage proxy model, while terminal, monitor, and bus carriers share a lightweight terminal-textured proxy model without changing real carrier blocks.

## 0.12.10 — 2026-06-01
- RESOURCE block proxies now wait for the player's actually sent chunks before sending fake carrier block changes, preventing early proxy swaps and stale client visuals when client render distance is below the server radius with culling bypass enabled.
- RESOURCE block proxy distance now follows the current 3D display distance with a single transition near the display render edge, keeping tall storage/terminal/monitor/bus stacks from mixing real textures, proxy blocks, and visible carriers based on player height or approach direction without replacing still-visible displays far too early.
- Placed Storage Core blocks now refresh their display name after language changes.

## 0.12.9 — 2026-05-30
- RESOURCE wire placement into flowing water now refreshes nearby water immediately, preventing stale floating water visuals from lingering until another neighboring block update.

## 0.12.8 — 2026-05-30
- RESOURCE wire carriers are now protected against targeted water-flow replacement.
- `/exort mode set RESOURCE` now replaces the separate `/exort mode fix RESOURCE` flow: it checks and updates the active Paper chorus-plant update setting itself, scheduling the required restart even when `paper-global.yml` already contains the correct value.
- `/exort debug benchmark start` now builds static budgeted contained water-flow stress lanes with max-length real RESOURCE wires, water sources, floors, and borders, then starts the measured window only after setup and water-flow stabilization so construction spikes do not dominate the final verdict.
- Benchmark verdicts now grade sustained MSPT and frequent TPS loss separately from rare wall-clock scheduler stalls, keeping one-off spikes visible as warnings without misclassifying an otherwise stable run.
- Benchmark result lines now color individual verdicts, TPS/MSPT values, stall counts, synthetic shares, measured profile hotspots, queues, and metadata in chat and the Purpur console, making the final summary easier to scan on dark and light console themes.

## 0.12.7 — 2026-05-30
- RESOURCE storage, terminal, monitor, and bus carriers can now use budgeted per-player netherite-block proxies at long distance: real world blocks stay unchanged, wires are excluded, and the matching display is hidden only for that player until the carrier is restored near the configured BLOCK render range.
- Exort block placement now starts a vanilla-like hand swing immediately after the carrier and marker are written, before display and network refresh work, matching the actual hand used for main-hand and off-hand placement.

## 0.12.6 — 2026-05-30
- RESOURCE wire rendering now always uses one baked `ItemDisplay` model per connection mask, matching the previous multi-display texture alignment without per-segment display entities.
- Removed the obsolete wire render-mode and auto-render configuration keys; `resourceMode.wire.itemModel` remains the fallback model for isolated wires and the wire item.

## 0.12.5 — 2026-05-29
- Added automatic EntityCulling detection for client culling bypass: after a client brand is available, Exort probes non-vanilla clients with EntityCulling translation keys on a virtual sign placed 5 blocks behind and 5 blocks below the player, skips explicit `vanilla` clients, and enables bypass automatically when those language keys are translated. Previous same-brand matches are reused for up to 12 hours from the player's last online timestamp, avoiding repeat probes on normal reconnects.
- Removed `performance.displayCulling.clientCullingBypass.players`; manual bypass state and auto-detect history now live in the plugin database.
- `/exort debug culling client <player> status` now reports manual/auto/source state, client brand, probe response, cache-hit, and inventory-wait diagnostics.

## 0.12.4 — 2026-05-29
- Added density-based Exort display range management for dense bases and high player counts: density is counted from a section index, and role ranges change only after sustained threshold crossings. Main block visuals stay visible farther; wires, monitor contents, and holograms are reduced first. Already loaded displays in front of a moving or stopped player keep their current range while the player continues roughly the same route, with a short behind-player buffer to avoid flicker from a single step back.
- Added per-player display range control through ProtocolLib when available. With ProtocolLib, dense scenes use per-player `view_range` metadata packets; without it, the Paper fallback keeps base role ranges and hides only low-priority displays outside their reduced range.
- Made client-side EntityCulling the preferred path for players who use it: Exort displays now keep non-zero culling boxes, loaded displays are normalized on startup/reload, and `/exort debug culling client <player> status|enable|disable` can opt a player out of server-side range reduction while restoring hidden or lowered state for that player.
- Changed RESOURCE wire rendering to `resourceMode.wire.renderMode: AUTO` by default. Wires normally use the detailed center-plus-connections model, but when a 3x3 chunk wire zone becomes dense, newly created or missing wire displays spawn as compact one-display cable models. Existing wire displays are not mass-rebuilt while players are nearby; Exort converts them later only when the zone is idle and only within the configured maintenance budget.
- Added a `wire.hardCap` placement guard: a wire placement that would merge loaded cable components above the hard cap is denied before marker writes, item consume, placement sound, display refresh, or network invalidation, and the player receives localized action-bar feedback instead of creating an oversized lag zone.
- Smoothed WorldEdit/FAWE bulk post-processing after large edits: marker application, display cleanup/refresh, bus rescans, network refresh starts, and network graph invalidation now drain through bounded queues, and graph invalidation is scoped to affected chunks instead of invalidating every cached network.
- Improved diagnostics for the new performance paths: `/exort debug verbose culling` reports density levels, role counts, range metadata changes, direction-retained displays, Paper hidden counts, wire auto-render state, and maintenance queue depth; performance stats now include the related display, wire, and WorldEdit/FAWE queue/budget counters.
- Fixed config updater list handling so new list-based options such as density thresholds and role ranges are written as valid YAML while keeping manually edited values during startup/reload migration.

## 0.12.3 — 2026-05-28
- Hardened storage and wireless custom-item data reads by rejecting invalid UUID references, and tightened bus loop protection for side-sensitive inventories without changing bus settings, item PDC keys, or storage formats.
- Improved real server performance in heavy Exort mechanics: buses now run through a due scheduler with cached target context and loop-conflict snapshots, network scans use scoped cache invalidation around changed blocks/chunks, display refreshes are deduplicated through block/network queues, monitors update from storage dirty queues instead of full periodic sweeps, storage DB writes coalesce and batch dirty deltas, and terminal/crafting GUIs build only the active page window.
- Reduced hidden broad refresh work during normal place/break/storage changes while keeping chunk-wide display and network refreshes for chunk load, sanity repair, and WorldEdit/FAWE bulk operations.
- Added debug performance profiling for the same runtime bus, network, display, monitor, storage DB, GUI, placement guard, and wireless paths used by production gameplay; benchmark reports now separate measured Exort shares from the synthetic load model, include queue/budget data, runtime metadata, and recent-run summaries.
- Reworked `/exort debug benchmark start` to create benchmark-owned temporary Exort networks near the top of the world, exercise build/interact/move/teardown paths, clean up benchmark blocks/displays/storage rows, and report sustained MSPT separately from rare TPS stalls.

## 0.12.2 — 2026-05-28
- Reworked Exort command help and usage output into compact clickable chat blocks with console colors, added canonical `/exort inventory`, `/exort language`, and `/exort resourcepack` entries with short aliases, restored bare `/exort give` to item-give usage help, and added contextual storage-tier help for `/exort give <player> storage`.

## 0.12.1 — 2026-05-28
- Reworked internal plugin startup, runtime assembly, listener registration, task scheduling, and command wiring into smaller focused classes while preserving public command behavior, permissions, config keys, database format, PDC keys, resource-pack paths, and gameplay workflows.
- Reworked internal WorldEdit/FAWE wiring to remove concrete plugin coupling and isolate FAWE setup code without changing marker transport or integration behavior.

## 0.12.0 — 2026-05-28
- Fixed item dictionary reload/status handling for nested YAML keys after `/exort lang set`, and refreshes item dictionaries automatically on plugin startup.
- Fixed WorldEdit/FAWE integration lifecycle during `/exort reload` so the bridge is restored after listener cleanup.
- Fixed standalone resource-pack export after the resource-pack implementation moved out of the old `core.resourcepack` package.
- Sanitized the bStats language chart so it reports bounded locale-like categories instead of arbitrary config text.
- Public storage-tier API calls now return immutable descriptors instead of internal storage-tier objects, and loaded tier definitions are immutable snapshots.
- Reworked runtime configuration loading for storage, buses, wireless access, crafting, placement guard, display models, GUI overlays, resource/vanilla breaking visuals, WorldGuard, and benchmark defaults while preserving existing config keys and defaults.
- Refactored terminal, crafting-terminal, and bus GUI internals for shared search, pagination, writer-lock, render scheduling, and overlay handling without changing the player-facing GUI behavior.
- Reorganized source packages around feature, infrastructure, integration, platform, and shared feedback ownership; public command behavior, permissions, database format, PDC keys, resource-pack paths, and gameplay workflows are unchanged.

## 0.11.6 — 2026-05-27
- Initialized storage items now show the same short storage UUID tail in lore as linked wireless terminals, while new uninitialized storage items stay unchanged.
- Split custom break particle counts into stage-change and final-break settings, with stronger final break particles and no user-facing particle speed option.
- Resource-mode GUI overlays are configured by texture keys such as `gui/inventory` instead of raw font glyph prefixes.

## 0.11.5 — 2026-05-26
- Fixed realtime switching from RESOURCE to VANILLA so marked `CHORUS_PLANT` wires migrate to `BARRIER` carriers during the first `/exort mode set VANILLA` or `/exort reload`, instead of losing their marker until a second reload.
- Added `exort.storagenetwork.give` for give-only staff access to `/exort give`, including the existing parameterized item-give command; `exort.storagenetwork.admin` includes it as a child permission.
- Added `/exort give` as a single-page item menu for Exort admin items. The menu also accepts unwanted Exort custom items as trash, while protecting initialized storage items.
- Added an enabled-by-default startup update check that compares the running plugin version with GitHub master `build.gradle`, logs available updates, confirms current versions, and stays silent on network or parsing failures.

## 0.11.4 — 2026-05-26
- Fixed WorldEdit/FAWE overlap `//move` and repeated `//undo`/`//redo` handling so Exort markers from one edit no longer erase or overwrite marker state restored by another edit.
- WorldEdit/FAWE `//move` now preserves original storage UUIDs and moved-storage last-location records, including multiple storages and cross-chunk moves; copy/paste storage operations still clone storage data.
- WorldEdit/FAWE marker transport now keeps paste, move, and undo/redo command state isolated, prefers carried Exort NBT, and uses command sidecar data only for matching markerless carrier writes.
- Chunk sanity now keeps physically valid marker roots on configured carriers, clears only impossible marker roots whose carrier block is gone, removes stale or wrong-type display entities before refresh, and invalidates network caches after repairs.
- `/exort reload` and chunk sanity now repair full-face `CHORUS_PLANT` carriers as Exort wires, restoring wire markers and displays for markerless resource-mode cables.
- WorldEdit/FAWE display refresh now runs after all chunks in a marker flush are applied and repeats on two short deferred passes, reducing missing Exort textures after overlap moves.
- WorldEdit/FAWE full debug now reports marker source, marker sections, move offset, history use, refresh passes, and chunk sanity summaries.

## 0.11.3 — 2026-05-25
- Fixed client-side ghost placement and placement sounds when right-clicking Exort blocks or cables with placeable vanilla items, while keeping Shift+RMB placement and the target block outline available.
- Placement guard now prefers per-player ProtocolLib fake ArmorStand packets when enabled and automatically falls back to Paper entities when ProtocolLib is unavailable, incompatible, or fails during runtime packet sends.
- ProtocolLib integration now records per-feature capability diagnostics for pick bridging, entity picking, and placement guard packets; update advice is logged only when the installed ProtocolLib version is below the tested advisory minimum.
- `/exort give` now drops inventory overflow near the target player instead of leaving part of the requested admin item grant undelivered.
- `/exort give` now plays the vanilla item-pickup sound for the target player when items are granted or dropped nearby.
- Added `/exort give <player> storage_core [amount]` and updated give-command help to list all supported admin item ids.
- Expanded `/exort debug benchmark start` with distributed placement-guard ArmorStand churn, using the simulated player count to model players moving their crosshair across rows of Exort blocks.

## 0.11.2 — 2026-05-24
- Fixed custom breaking so holding left-click on another still-present block and moving the crosshair onto an Exort carrier starts Exort breaking, without reacting to placement swings or immediately breaking an Exort carrier revealed behind a just-broken block.
- Added `/exort mode fix RESOURCE`: when Paper chorus plant updates are still enabled, Exort now sets `mode: RESOURCE`, enables `block-updates.disable-chorus-plant-updates` in `config/paper-global.yml`, warns players, and restarts the server after 10 seconds with a `stop` fallback.

## 0.11.1 — 2026-05-24
- Storage blocks now begin cache loading as soon as custom breaking starts, reducing late `storage is loading, try again` denials after long survival-mode breaks.
- Added mode-aware custom-breaking feedback: RESOURCE mode shows a temporary subtly darkened world-aligned crack overlay plus netherite-block break particles, while VANILLA mode emits compact pack-free block particles.
- Break particles now also play on successful instant creative breaks, including BARRIER carrier blocks.
- Fixed custom block breaking sounds so the final break sound plays only after the block is actually broken.
- Matched custom block hit sounds to visible breaking stages so faster tools produce faster feedback and quick taps do not advance past the first break stage.
- Added optional ProtocolLib enhancement: creative middle-click now also handles Exort display entities.
- Restored vanilla breaking behavior for RESOURCE-mode wires so `CHORUS_PLANT` wire blocks no longer use Exort's slower custom break loop.
- Made resource-pack provider texture-key parsing explicitly null-safe for invalid provider aliases.

## 0.11.0 — 2026-05-23
- Added ItemsAdder resource-pack integration alongside the existing Nexo path: `resourcePack.hosting: AUTO` now prefers Nexo, then ItemsAdder, before official Exort hosting and self-hosting.
- The ItemsAdder handoff now uses IA-friendly item-atlas texture aliases for Exort block/display models, avoiding black-and-purple missing textures in provider-generated packs.
- Exort now prepares provider input at startup so provider-managed packs can include Exort assets without an Exort-side runtime rebuild.
- Fixed creative middle-click picking for Exort `BARRIER` carriers on servers with ItemsAdder by adding an optional ProtocolLib packet bridge for `PICK_ITEM_FROM_BLOCK`; Exort now reapplies its own pick result after ItemsAdder handles barrier packets.
- Fixed a rare middle-click target race by resolving Paper pick-block events from the event block instead of the player's current raycast, preventing carrier blocks from being picked when the crosshair moves during event handling.
- Hardened shutdown for bus settings: a bus-engine stop or bus-settings flush failure is now logged without aborting the rest of Exort disable cleanup.
- Added `/exort debug verbose pick start|stop` for live Paper pick-block diagnostics, including detected target blocks, marker state, and Exort pick results.

## 0.10.4 — 2026-05-22
- Completed `EXORT` resource-pack hosting through the official HTTPS metadata endpoint at `exort.zxcmc.com`; `resourcePack.hosting: AUTO` now resolves in order: enabled Nexo integration first, official Exort hosting second, and Exort self-hosting as the fallback.
- Added standalone resource-pack export support through the Gradle `exportResourcePack` task and CLI entry point, producing the bundled Exort resource pack outside a running server.

## 0.10.3 — 2026-05-21
- Fixed immediate visual refresh for one-wire storage links so newly placed terminals, crafting terminals, monitors, and buses show their cable connection/active state without `/exort reload`.
- Fixed shelf inventory visuals after bus moves so shelves no longer keep a phantom last item after Exort extracts it.

## 0.10.2 — 2026-05-20
- Moved transient wireless, terminal, and storage failure feedback from boss bars to action bars while keeping boss bars for persistent storage, wire, bus, monitor, and benchmark status.
- Added a short duplicate cooldown for identical action-bar feedback so rapid wireless/terminal validation failures do not spam players.
- Added localized wireless bind confirmation, resource-pack required-failure text, and `/exort pack status` labels.
- Centralized colored `[Exort]` console status output and `/exort` command feedback.
- Cleaned shared async scheduling, storage GUI control construction, storage placement rollback feedback, and command debug/item delivery helpers without changing player workflows.

## 0.10.1 — 2026-05-20
- Fixed crafting-terminal recipe remainder routing by using Paper's server-side craft result matrix/overflow items, so server-defined remainders return to storage first and fall back to the player's inventory or a nearby drop.
- Fixed bus GUI settings during `/exort reload` so mode/filter changes are written to the live marker immediately and cannot be overwritten by a stale asynchronous SQLite load.
- Fixed WorldEdit/FAWE marker restoration after `/exort reload` by tracking paste commands through the WorldEdit command event, prevented stale clipboard rotations from changing Exort block facing during non-paste operations such as `//move`, and refreshed adjacent wire displays after device/storage marker updates.
- Added explicit WorldGuard integration status logging on startup/reload.

## 0.10.0 — 2026-05-19
- Hardened item integrity for crafting terminals: recipe remainder items are now returned, cursor output buffers are capacity-checked, and unflushable buffered output is returned to the player or dropped instead of being lost or overfilling storage.
- Hardened storage placement failure handling: if tier persistence fails after placement, Exort now revalidates the marker, removes the failed storage block/display, and returns the consumed item when appropriate.
- Restored declared 1.21.7 runtime compatibility for custom breaking defaults by resolving new copper tools dynamically instead of directly referencing newer `Material` enum constants.
- Hardened storage/cache loading against corrupt DB rows, oversized item blobs, invalid serialized item data, weighted-total overflow, and oversized/null DB write rows.
- Moved storage post-load item refresh, default-sort resolution, and loaded-cache clone snapshots back onto the main thread while keeping SQLite I/O async.
- Hardened bus, wireless, display, and admin command edge cases: bus filters reject oversized blobs, bus GUI settings persist on change and flush on service stop, wireless unbind clears stale tier metadata, display markers verify entity type before casting, async UI/command handoffs avoid disabled-plugin scheduling, and `/exort give` caps amount and reports partial delivery.
- Added safer WorldGuard failure diagnostics with configurable `worldguard.failClosedOnError`, and moved Exort custom item/block placement later in the event pipeline so earlier protection cancellations can apply.
- Added JUnit-based regression coverage for storage tier capacity parsing, malformed bus filter blobs, corrupt DB row filtering, and recipe key parsing.

## 0.9.7 — 2026-05-18
- Fixed wireless/search session cleanup so storage and wireless error bossbars no longer remain after forced GUI/dialog closure.
- Physical terminal, crafting-terminal, and bus GUIs now close cleanly when the player leaves the configured device range.

## 0.9.6 — 2026-05-18
- Resource-pack auto-delivery now supports configuration-phase loading before players enter the world, with conservative AUTO behavior that falls back to post-join Exort delivery when `server.properties` already defines a resource pack.
- Added resource-pack delivery controls (`AUTO`/`CONFIGURATION`/`JOIN`/`MANUAL`), opt-in online resend on ready, and a placeholder `EXORT` hosting mode for a future immutable official HTTPS pack.
- Made LOBFILE hosting configuration explicit through `resourcePack.lobfile.apiKey`.
- Hardened resource-pack reloads so stale async LobFile uploads cannot overwrite newer pack state after rebuild/reload.

## 0.9.5 — 2026-05-17
- Removed the hard runtime dependency on WorldEdit/FAWE classes: Exort now starts without WorldEdit or FastAsyncWorldEdit installed, while registering the WorldEdit bridge lazily when either optional plugin is available.
- Fixed storage load de-duplication so synchronously completed loads no longer throw `IllegalStateException: Recursive update` while resolving or testing storage networks.
- Verified bus compatibility with Minecraft `1.21.9+` copper chests and shelves on Purpur `26.1.2-2583`: all copper chest and shelf variants work as block inventory targets, double copper chests expose the combined inventory to buses, shelf slot/merge/empty-slot operations pass, and non-storage copper blocks are ignored.

## 0.9.4 — 2026-05-17
- Hardened item integrity for storage, crafting, wireless items, and buses: withdrawals and crafting now reserve real cache items before delivery, failed or partial delivery rolls back leftovers, crafting output checks capacity before consuming ingredients, and wireless item imports no longer mutate rejected source stacks.
- Hardened WorldEdit/FAWE storage cloning: pasted storage markers apply only after a successful transactional storage clone, missing source storages fail instead of creating empty clones, FAWE extent allow-listing is verified at runtime, and WorldEdit reflection negative caches no longer store nulls.
- Hardened custom block placement and breaking: storage placement persists tier/load failures visibly, protected or not-yet-loaded storage breaks fail closed, and custom break sounds play only after an actual break.
- Hardened storage load and async failure paths: terminal/wireless opens revalidate current main-thread state after load, storage/debug/reload/lang/mode/bus/WorldEdit futures log failures, and players get visible failure messages where applicable.
- Load benchmarking no longer starts automatically on a fresh database; it remains available only through `/exort debug benchmark start [players] [seconds]`.

## 0.9.3 — 2026-05-14
- Category sorting now uses embedded vanilla creative-tab data for the supported Minecraft 1.21.7-26.1.2 range instead of the deprecated Bukkit creative-category API; the unused creative search tab is omitted, operator-block labels resolve correctly, and Exort custom items sort before unknown items.
- Cleaned up internal storage withdrawal accounting to avoid Java null-safety warnings; gameplay behavior is unchanged.

## 0.9.2 — 2026-05-13
- Fixed resource-pack display textures for terminals, crafting terminals, monitors, and wireless terminals to use square power-of-two atlases, removing mipmap/resolution warnings while preserving the original pixel art.
- Added RESOURCE-first resource-pack delivery: default RESOURCE mode with VANILLA effective fallback, AUTO/NEXO/SELFHOST/LOBFILE/DISABLED hosting, SIMPLE pack obfuscation, Nexo raw-pack handoff, standalone self-host/LobFile delivery, automatic player dispatch, and `/exort pack` diagnostics.

## 0.9.1 — 2026-05-02
- Updated target Paper API to 26.1.2 while keeping Java 21 bytecode and 1.21.7+ plugin compatibility metadata.
- Hardened storage/database failure handling: SQL errors now fail futures, failed loads/flushes no longer masquerade as empty or clean storages, and shutdown waits are bounded.
- Fixed storage withdrawals when the player inventory cannot accept the full stack.
- Fixed crafting ingredient removal for repeated custom-item keys, including wireless terminals.
- Fixed resource-mode wire breaking to use the Exort custom breaker path instead of hidden carrier particles.
- Hardened FAWE copy/cut/paste on unofficial 26.1 builds by transporting Exort markers through carrier blocks with a paste fallback.

## 0.9.0 — 2026-01-24
- Updated target Paper API to 1.21.11 (still supports 1.21.7+).
- FastAsyncWorldEdit (FAWE) integration: copy/move/cut/paste/undo/redo/rotate now preserve Exort block behavior, facing, filters, wire connections, and displays.
- Bus filter data is persisted alongside markers, keeping filters intact across edit operations.
- Exort marker data moved to chunk-level PDC containers (per-block nested storage) for stability and faster scans.
- Added `/exort debug verbose worldedit start|stop [compact|normal|full]` for edit-operation diagnostics.

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
