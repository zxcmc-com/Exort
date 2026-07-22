package com.zxcmc.exort.integration.worldedit;

import static com.zxcmc.exort.integration.worldedit.WorldEditMarkerCodec.*;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.zxcmc.exort.bus.BusFilterCodec;
import com.zxcmc.exort.bus.BusMode;
import com.zxcmc.exort.bus.BusPos;
import com.zxcmc.exort.bus.BusService;
import com.zxcmc.exort.bus.BusState;
import com.zxcmc.exort.bus.BusType;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.chunkloader.ChunkLoaderService;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.display.device.ItemHologramManager;
import com.zxcmc.exort.display.refresh.DisplayRefreshService;
import com.zxcmc.exort.infra.logging.ExortLog;
import com.zxcmc.exort.infra.scheduler.PluginTasks;
import com.zxcmc.exort.integration.worldedit.fawe.FaweExtentAccess;
import com.zxcmc.exort.marker.BusMarker;
import com.zxcmc.exort.marker.ChunkLoaderMarker;
import com.zxcmc.exort.marker.ChunkMarkerStore;
import com.zxcmc.exort.marker.MonitorMarker;
import com.zxcmc.exort.marker.RelayMarker;
import com.zxcmc.exort.marker.StorageCoreMarker;
import com.zxcmc.exort.marker.StorageMarker;
import com.zxcmc.exort.marker.TerminalKind;
import com.zxcmc.exort.marker.TerminalMarker;
import com.zxcmc.exort.marker.TransmitterMarker;
import com.zxcmc.exort.marker.WireMarker;
import com.zxcmc.exort.storage.StorageClaim;
import com.zxcmc.exort.storage.StorageClaimLocation;
import com.zxcmc.exort.storage.StorageClaimRegistry;
import com.zxcmc.exort.storage.StorageTierResolver;
import com.zxcmc.exort.wireless.transmitter.TransmitterMode;
import com.zxcmc.exort.wireless.transmitter.TransmitterSessionManager;
import com.zxcmc.exort.wireless.transmitter.TransmitterStoredBooster;
import com.zxcmc.exort.wireless.transmitter.TransmitterStoredTerminal;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.enginehub.linbus.tree.LinCompoundTag;

public final class WorldEditBridge implements Listener {
  private static final String ORAXEN_PLUGIN_NAME = "Oraxen";
  private static final String ORAXEN_WORLDEDIT_EXTENT =
      "io.th0rgal.oraxen.compatibilities.provided.worldedit.WorldEditHandlers$1";
  private static final String NEXO_PLUGIN_NAME = "Nexo";
  private static final String NEXO_WORLDEDIT_EXTENT =
      "com.nexomc.nexo.compatibilities.worldedit.NexoWorldEditExtent";

  private static final int BUS_FILTER_SLOTS = 10;

  private static final int APPLY_PER_TICK = 3000;
  private static final int DIRECT_RECONCILIATION_CAP = 100_000;
  private static final int MARKER_CAPTURE_CHUNK_CAP = 100_000;
  private static final int RETRY_DELAY_TICKS = 2;
  private static final int MAX_RETRIES = 40;
  private static final long PASTE_COMMAND_TTL_MS = TimeUnit.SECONDS.toMillis(5);
  private static final long HISTORY_COMMAND_TTL_MS = TimeUnit.SECONDS.toMillis(5);
  private static final long MOVE_COMMAND_TTL_MS = TimeUnit.SECONDS.toMillis(5);
  private static final long OPERATION_CONTEXT_TTL_MS = TimeUnit.SECONDS.toMillis(5);
  private static final int HISTORY_STAGE_USES_PER_STEP = 3;

  private final Plugin plugin;
  private final WorldEditBridgeDependencies deps;
  private final WorldEditCarrierPolicy carrierPolicy;
  private final WorldEditRefreshScheduler refreshScheduler;
  private final WorldEditClipboardPatcher clipboardPatcher;
  private final Queue<PendingUpdate> updates = new ConcurrentLinkedQueue<>();
  private final WorldEditDeferredUpdates deferredUpdates = new WorldEditDeferredUpdates();
  private final Set<ChunkKey> deferredSnapshotChunks = ConcurrentHashMap.newKeySet();
  private final Set<ChunkKey> warnedDeferredUpdateChunks = ConcurrentHashMap.newKeySet();
  private final WorldEditDirectReconciliationQueue directReconciliation =
      new WorldEditDirectReconciliationQueue(DIRECT_RECONCILIATION_CAP);
  private final AtomicBoolean directReconciliationOverflowWarned = new AtomicBoolean();
  private final AtomicBoolean markerCaptureOverflowWarned = new AtomicBoolean();
  private final Set<WorldEditLoadedMarkerChunkCursor> entityRefreshCursors =
      ConcurrentHashMap.newKeySet();
  private final Set<BukkitTask> lifecycleTasks = ConcurrentHashMap.newKeySet();
  private final WorldEditMarkerHistory markerHistory = new WorldEditMarkerHistory();
  private final Map<UUID, PendingClipboardPatch> clipboardPatches = new ConcurrentHashMap<>();
  private final Map<UUID, PendingClipboardPatch> pendingCutSourcePatches =
      new ConcurrentHashMap<>();
  private final Set<UUID> pendingCutClipboardTransfers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, PendingPasteCommand> pendingPasteCommands = new ConcurrentHashMap<>();
  private final Map<UUID, PendingHistoryCommand> pendingHistoryCommands = new ConcurrentHashMap<>();
  private final Map<UUID, PendingMovePatch> pendingMovePatches = new ConcurrentHashMap<>();
  private final WorldEditOperationContexts operationContexts =
      new WorldEditOperationContexts(OPERATION_CONTEXT_TTL_MS);
  private final WorldEditTrustedClipboards trustedClipboards = new WorldEditTrustedClipboards();
  private final WorldEditOperationTracker operationTracker = new WorldEditOperationTracker();
  private final Map<StoragePreparationKey, CompletableFuture<Boolean>> storagePreparations =
      new ConcurrentHashMap<>();
  private final Object flushTaskLock = new Object();
  private BukkitTask flushTask;
  private BukkitTask directReconciliationTask;
  private volatile boolean shuttingDown;
  private long tickCounter;

  private WorldEditBridge(WorldEditBridgeDependencies deps) {
    this.deps = Objects.requireNonNull(deps, "deps");
    this.plugin = deps.plugin();
    this.carrierPolicy = new WorldEditCarrierPolicy(deps.materials());
    this.refreshScheduler = new WorldEditRefreshScheduler(deps, this::updateQueueDepthGauge);
    this.clipboardPatcher =
        new WorldEditClipboardPatcher(
            plugin,
            deps.generationScope(),
            this::buildMarkerBlock,
            deps::debugService,
            this::onClipboardPatchResult);
  }

  WorldEditBridgeDependencies dependencies() {
    return deps;
  }

  WorldEditCarrierPolicy carrierPolicy() {
    return carrierPolicy;
  }

  WorldEditMarkerHistory markerHistory() {
    return markerHistory;
  }

  /** Preserves the externally configured FAWE allowlist class while delegating all extent logic. */
  private static final class MarkerExtent extends WorldEditMarkerExtent {
    MarkerExtent(
        Extent extent,
        World world,
        WorldEditBridge bridge,
        long operationId,
        UUID actorId,
        EditSession.Stage stage,
        HistoryAction historyAction,
        WorldEditMarkerHistory.Frame normalHistoryFrame,
        WorldEditMarkerHistory.Frame replayHistoryFrame,
        FacingTransform clipboardTransform,
        PendingPastePatch pastePatch,
        PendingMovePatch movePatch,
        PendingClipboardPatch cutSourcePatch,
        PendingOperationSnapshot operationSnapshot,
        boolean commandContextPresent) {
      super(
          extent,
          world,
          bridge,
          operationId,
          actorId,
          stage,
          historyAction,
          normalHistoryFrame,
          replayHistoryFrame,
          clipboardTransform,
          pastePatch,
          movePatch,
          cutSourcePatch,
          operationSnapshot,
          commandContextPresent);
    }
  }

  public static WorldEditBridge tryRegister(WorldEditBridgeDependencies deps) {
    if (deps == null) return null;
    Plugin plugin = deps.plugin();
    Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
    Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
    if (!isEnabled(worldEdit) && !isEnabled(fawe)) return null;
    WorldEditBridge bridge = null;
    boolean eventBusRegistered = false;
    try {
      if (isEnabled(fawe)) {
        allowFaweExtent(
            plugin, fawe, "marker", MarkerExtent.class.getName(), deps.autoConfigureFawe());
        if (Bukkit.getPluginManager().isPluginEnabled(ORAXEN_PLUGIN_NAME)) {
          allowFaweExtent(
              plugin, fawe, "Oraxen", ORAXEN_WORLDEDIT_EXTENT, deps.autoConfigureFawe());
        }
        if (Bukkit.getPluginManager().isPluginEnabled(NEXO_PLUGIN_NAME)) {
          allowFaweExtent(plugin, fawe, "Nexo", NEXO_WORLDEDIT_EXTENT, deps.autoConfigureFawe());
        }
      }
      bridge = new WorldEditBridge(deps);
      WorldEdit.getInstance().getEventBus().register(bridge);
      eventBusRegistered = true;
      deps.generationScope().registerListener(bridge);
      ExortLog.success("[WorldEdit] Integration enabled.");
      return bridge;
    } catch (NoClassDefFoundError err) {
      rollbackRegistration(bridge, eventBusRegistered);
      ExortLog.warn("[WorldEdit] Integration disabled: missing classes.");
      return null;
    } catch (RuntimeException | LinkageError err) {
      rollbackRegistration(bridge, eventBusRegistered);
      ExortLog.warn("[WorldEdit] Integration disabled: " + err.getMessage());
      return null;
    }
  }

  private static void allowFaweExtent(
      Plugin plugin, Plugin fawe, String label, String extentClass, boolean autoConfigureFawe) {
    FaweExtentAccess.Result result =
        FaweExtentAccess.allowExtent(fawe, extentClass, autoConfigureFawe);
    if (result == null) {
      return;
    }
    if (result.hasFailure()) {
      if (FaweExtentAccess.shouldLogWarning(result, extentClass)) {
        String optIn =
            autoConfigureFawe
                ? ""
                : "; add the class to FAWE extent.allowed-plugins manually or set "
                    + "integrations.fawe.autoConfigure=true in Exort config";
        plugin.getLogger().warning(result.warningMessage(label, extentClass) + optIn);
      }
      return;
    }
    if (result.shouldLogInfo()) {
      plugin.getLogger().info(result.infoMessage(label, extentClass));
    }
  }

  private static boolean isEnabled(Plugin plugin) {
    return plugin != null && plugin.isEnabled();
  }

  private static void rollbackRegistration(WorldEditBridge bridge, boolean eventBusRegistered) {
    if (bridge == null) {
      return;
    }
    HandlerList.unregisterAll(bridge);
    if (eventBusRegistered) {
      try {
        WorldEdit.getInstance().getEventBus().unregister(bridge);
      } catch (RuntimeException | LinkageError ignored) {
        // The original registration failure remains the actionable error.
      }
    }
  }

  public void shutdown() {
    shuttingDown = true;
    try {
      WorldEdit.getInstance().getEventBus().unregister(this);
    } catch (RuntimeException | LinkageError ignored) {
    }
    HandlerList.unregisterAll(this);
    synchronized (flushTaskLock) {
      if (flushTask != null) {
        deps.generationScope().cancelTask(flushTask);
        flushTask = null;
      }
      if (directReconciliationTask != null) {
        deps.generationScope().cancelTask(directReconciliationTask);
        directReconciliationTask = null;
      }
    }
    for (BukkitTask task : lifecycleTasks) {
      deps.generationScope().cancelTask(task);
    }
    lifecycleTasks.clear();
    refreshScheduler.shutdown();
    clipboardPatcher.shutdown();
    deferredUpdates.clear();
    deferredSnapshotChunks.clear();
    warnedDeferredUpdateChunks.clear();
    directReconciliation.clear();
    for (WorldEditLoadedMarkerChunkCursor cursor : entityRefreshCursors) {
      cursor.cancel();
    }
    entityRefreshCursors.clear();
    markerHistory.clear();
    clipboardPatches.clear();
    pendingCutSourcePatches.clear();
    pendingCutClipboardTransfers.clear();
    pendingPasteCommands.clear();
    pendingHistoryCommands.clear();
    pendingMovePatches.clear();
    operationContexts.clear();
    trustedClipboards.clear();
    storagePreparations.clear();
    operationTracker.clear();
  }

  @Subscribe
  public void onEditSession(EditSessionEvent event) {
    EditSession.Stage stage = event.getStage();
    if (stage != EditSession.Stage.BEFORE_HISTORY
        && stage != EditSession.Stage.BEFORE_REORDER
        && stage != EditSession.Stage.BEFORE_CHANGE) {
      return;
    }
    com.sk89q.worldedit.world.World weWorld = event.getWorld();
    if (weWorld == null) {
      return;
    }
    World world = Bukkit.getWorld(weWorld.getName());
    if (world == null) return;
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.incSessions();
      debug.recordEvent(
          "we session stage=" + stage.name().toLowerCase(Locale.ROOT), NamedTextColor.DARK_AQUA);
    }
    Extent extent = event.getExtent();
    if (containsMarkerExtent(extent, stage)) return;
    Actor actor = event.getActor();
    UUID eventActorId = actor == null ? null : actor.getUniqueId();
    WorldEditOperationContexts.Resolution resolution =
        operationContexts.resolve(eventActorId, world.getUID(), System.currentTimeMillis());
    if (resolution != null && resolution.ambiguous()) {
      PerfStats.incrementCounter("worldedit.operation.ambiguous");
    }
    WorldEditOperationContext operationContext =
        resolution == null || resolution.ambiguous() ? null : resolution.context();
    if ((actor == null || actor.getUniqueId() == null) && resolution != null) {
      actor = resolution.actor();
    }
    UUID actorId = actor == null ? null : actor.getUniqueId();
    HistoryAction historyAction = resolvePendingHistoryCommand(actor);
    long operationId =
        operationContext == null
            ? operationTracker.nextOperationId()
            : operationContext.operationId();
    WorldEditMarkerHistory.Frame normalHistoryFrame = null;
    WorldEditMarkerHistory.Frame replayHistoryFrame = null;
    if (actorId != null) {
      if (historyAction == null && stage == EditSession.Stage.BEFORE_HISTORY) {
        normalHistoryFrame =
            markerHistory.beginNormalOperation(actorId, world.getUID(), operationId);
      } else if (historyAction != null && stage == EditSession.Stage.BEFORE_CHANGE) {
        replayHistoryFrame = markerHistory.beginReplay(actorId, world.getUID(), historyAction);
      }
    }
    boolean pasteCommandPending = historyAction == null && hasPendingPasteCommand(actor);
    PendingPastePatch pastePatch =
        pasteCommandPending ? resolvePendingPastePatch(actor, world.getUID()) : null;
    PendingMovePatch movePatch =
        historyAction == null && !pasteCommandPending ? resolvePendingMovePatch(actor) : null;
    PendingClipboardPatch cutSourcePatch =
        historyAction == null && !pasteCommandPending && movePatch == null
            ? pendingCutSourcePatch(actor)
            : null;
    PendingOperationSnapshot operationSnapshot =
        historyAction == null && !pasteCommandPending && movePatch == null && cutSourcePatch == null
            ? operationContext == null ? null : operationContext.markerSnapshot()
            : null;
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we session context actor="
              + actorId
              + " world="
              + world.getUID()
              + " operationSnapshot="
              + (operationSnapshot == null
                  ? "none"
                  : operationSnapshot.worldId() + "/" + operationSnapshot.markers().size()),
          NamedTextColor.GRAY);
    }
    FacingTransform clipboardTransform =
        pasteCommandPending ? WorldEditExtentCapabilities.resolveClipboardFacing(actor) : null;
    event.setExtent(
        new MarkerExtent(
            extent,
            world,
            this,
            operationId,
            actorId,
            stage,
            historyAction,
            normalHistoryFrame,
            replayHistoryFrame,
            clipboardTransform,
            pastePatch,
            movePatch,
            cutSourcePatch,
            operationSnapshot,
            operationContext != null));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    Player player = event.getPlayer();
    Actor actor = wrapBukkitActor(player);
    prepareCommand(actor, event.getMessage(), player);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onServerCommand(ServerCommandEvent event) {
    if (event == null || event.getSender() == null || event.getSender() instanceof Player) {
      return;
    }
    if (!Bukkit.isPrimaryThread()) {
      ExortLog.warn("Skipped asynchronous WorldEdit console command preparation.");
      return;
    }
    Actor actor = wrapBukkitActor(event.getSender());
    prepareCommand(actor, event.getCommand(), null);
  }

  private boolean prepareCommand(Actor actor, String command, Player player) {
    if (actor == null || actor.getUniqueId() == null || !Bukkit.isPrimaryThread()) {
      return false;
    }
    UUID actorId = actor.getUniqueId();
    if (WorldEditCommandParser.invalidatesClipboardTrust(command)) {
      trustedClipboards.clear(actorId);
      clipboardPatcher.cancel(actorId);
    }
    ParsedHistoryCommand historyCommand = WorldEditCommandParser.parseHistoryCommand(command);
    if (historyCommand != null) {
      rememberHistoryCommand(actorId, historyCommand);
      rememberOperationContext(actor, command, null, null);
      return true;
    }
    if (WorldEditCommandParser.isClipboardCopyCommand(command)) {
      ClipboardCapture capture = captureClipboardPatches(actor);
      PendingClipboardPatch patch = capture.copyPatch();
      if (patch == null || patch.markers().isEmpty()) {
        clipboardPatches.remove(actorId);
        pendingCutSourcePatches.remove(actorId);
        rememberOperationContext(actor, command, patch, null);
        return true;
      }
      rememberClipboardPatch(actor, patch);
      rememberCutSourcePatchIfNeeded(actor, command, capture.cutPatch());
      rememberOperationContext(actor, command, patch, null);
      clipboardPatcher.schedule(actor, patch);
      return true;
    }
    if (WorldEditCommandParser.isClipboardPasteCommand(command)) {
      clipboardPatcher.tryApplyNow(actor);
      rememberPasteCommand(actorId, WorldEditCommandParser.parsePasteCommand(command));
      rememberOperationContext(actor, command, clipboardPatches.get(actorId), null);
      return true;
    }
    if (WorldEditCommandParser.isMoveCommand(command)) {
      rememberMovePatch(actorId, captureMovePatch(actor, command, player));
      rememberOperationContext(actor, command, null, null);
      return true;
    }
    if (WorldEditCommandParser.isOperationSnapshotCommand(command)) {
      PendingOperationSnapshot snapshot = captureOperationSnapshot(actor, command, player);
      rememberOperationSnapshot(snapshot);
      rememberOperationContext(actor, command, null, snapshot);
      return true;
    }
    if (WorldEditCommandParser.isEntityRefreshCommand(command)) {
      scheduleEntityRefresh(actor, command);
      rememberOperationContext(actor, command, null, null);
      return true;
    }
    if (WorldEditCommandParser.isClipboardClearCommand(command)) {
      clearActorClipboardState(actorId);
      return true;
    }
    if (WorldEditCommandParser.invalidatesClipboardTrust(command)) {
      operationContexts.clear(actorId);
      clipboardPatches.remove(actorId);
      return true;
    }
    return false;
  }

  private void rememberOperationContext(
      Actor actor,
      String command,
      PendingClipboardPatch clipboardPatch,
      PendingOperationSnapshot operationSnapshot) {
    if (actor == null || actor.getUniqueId() == null) return;
    SelectionGeometry geometry = selectionGeometry(actor);
    UUID worldId =
        operationSnapshot != null
            ? operationSnapshot.worldId()
            : clipboardPatch != null ? clipboardPatch.sourceWorldId() : geometry.worldId();
    WorldEditBounds selection =
        clipboardPatch != null ? clipboardPatch.expectedBounds() : geometry.bounds();
    BlockVector3 origin =
        clipboardPatch != null ? clipboardPatch.expectedOrigin() : geometry.origin();
    WorldEditBounds affected = operationSnapshot != null ? operationSnapshot.bounds() : selection;
    if (worldId == null) return;
    long now = System.currentTimeMillis();
    WorldEditOperationContext context =
        new WorldEditOperationContext(
            actor.getUniqueId(),
            0L,
            operationSnapshotReason(command),
            WorldEditCommandParser.commandSignature(command),
            worldId,
            origin,
            selection,
            affected,
            operationSnapshot,
            currentClipboard(actor),
            operationTracker.nextOperationId(),
            now);
    WorldEditOperationContext prepared = operationContexts.remember(actor, context, now);
    if (prepared != null) {
      PerfStats.incrementCounter("worldedit.operation.prepared");
      PerfStats.addCounter(
          "worldedit.operation.capturedMarkers",
          operationSnapshot == null ? 0L : operationSnapshot.markers().size());
      PerfStats.addCounter(
          "worldedit.operation.capturedChunks",
          operationSnapshot == null ? 0L : operationSnapshot.chunks().size());
    }
  }

  private Clipboard currentClipboard(Actor actor) {
    if (actor == null) return null;
    try {
      return WorldEdit.getInstance().getSessionManager().get(actor).getClipboard().getClipboard();
    } catch (EmptyClipboardException | RuntimeException ignored) {
      return null;
    }
  }

  private void onClipboardPatchResult(UUID actorId, WorldEditClipboardPatcher.PatchResult result) {
    if (actorId == null || result == null) return;
    if (result.applied()) {
      trustedClipboards.trust(actorId, result.clipboard(), result.patch());
      return;
    }
    trustedClipboards.clear(actorId);
    clipboardPatches.remove(actorId, result.patch());
    PerfStats.incrementCounter("worldedit.clipboard.correlationFailures");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    if (event == null || event.getChunk() == null) {
      return;
    }
    Chunk chunk = event.getChunk();
    if (chunk.getWorld() == null) {
      return;
    }
    ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    ChunkUpdateBatch batch = deferredUpdates.remove(key);
    if (batch != null) {
      int requeued = requeueDeferredBatch(batch);
      if (requeued > 0) {
        scheduleFlushIfNeeded();
        WorldEditDebugService debug = deps.debugService();
        if (debug != null && debug.isEnabled()) {
          debug.recordEvent(
              "we deferred marker retry chunk="
                  + key.chunkX()
                  + ","
                  + key.chunkZ()
                  + " updates="
                  + requeued,
              NamedTextColor.YELLOW);
        }
      }
    }
    reconcileDeferredSnapshotChunk(key, "chunk_load");
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    if (event == null || event.getPlayer() == null) {
      return;
    }
    UUID actorId = event.getPlayer().getUniqueId();
    operationContexts.clear(actorId);
    clearActorClipboardState(actorId);
    pendingHistoryCommands.remove(actorId);
    markerHistory.clearActor(actorId);
  }

  private void rememberPasteCommand(UUID actorId, PendingPasteCommand pasteCommand) {
    if (actorId == null || pasteCommand == null) return;
    pendingCutSourcePatches.remove(actorId);
    pendingMovePatches.remove(actorId);
    pendingHistoryCommands.remove(actorId);
    pendingPasteCommands.put(actorId, pasteCommand);
    scheduleLifecycleTask(() -> pendingPasteCommands.remove(actorId, pasteCommand), 100L);
  }

  private void rememberMovePatch(UUID actorId, PendingMovePatch movePatch) {
    if (actorId == null) return;
    pendingPasteCommands.remove(actorId);
    pendingCutSourcePatches.remove(actorId);
    pendingHistoryCommands.remove(actorId);
    if (movePatch == null || movePatch.destinationMarkers().isEmpty()) {
      pendingMovePatches.remove(actorId);
      return;
    }
    pendingMovePatches.put(actorId, movePatch);
    scheduleLifecycleTask(() -> pendingMovePatches.remove(actorId, movePatch), 100L);
  }

  private void rememberHistoryCommand(UUID actorId, ParsedHistoryCommand historyCommand) {
    if (actorId == null || historyCommand == null) return;
    pendingPasteCommands.remove(actorId);
    pendingMovePatches.remove(actorId);
    pendingCutSourcePatches.remove(actorId);
    pendingCutClipboardTransfers.remove(actorId);
    PendingHistoryCommand command =
        new PendingHistoryCommand(
            historyCommand.action(),
            System.currentTimeMillis(),
            historyStageUses(historyCommand.steps()));
    pendingHistoryCommands.put(actorId, command);
    scheduleLifecycleTask(() -> pendingHistoryCommands.remove(actorId, command), 100L);
  }

  private static int historyStageUses(int steps) {
    long uses = Math.max(1L, steps) * (long) HISTORY_STAGE_USES_PER_STEP;
    return uses > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) uses;
  }

  private Actor wrapBukkitActor(CommandSender sender) {
    if (sender == null) return null;
    Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
    if (worldEdit == null) {
      worldEdit = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
    }
    if (worldEdit == null) return null;
    if (sender instanceof Player player) {
      try {
        Method method = worldEdit.getClass().getMethod("wrapPlayer", Player.class);
        Object actor = method.invoke(worldEdit, player);
        if (actor instanceof Actor wrapped) {
          return wrapped;
        }
      } catch (Exception ignored) {
        // Fall back to wrapCommandSender below.
      }
    }
    try {
      Method method =
          worldEdit
              .getClass()
              .getMethod("wrapCommandSender", org.bukkit.command.CommandSender.class);
      Object actor = method.invoke(worldEdit, sender);
      return actor instanceof Actor wrapped ? wrapped : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void rememberClipboardPatch(Actor actor, PendingClipboardPatch patch) {
    if (actor == null || actor.getUniqueId() == null) return;
    if (patch == null || patch.markers().isEmpty()) {
      clipboardPatches.remove(actor.getUniqueId());
      return;
    }
    clipboardPatches.put(actor.getUniqueId(), patch);
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent(
          "we clipboard remember markers=" + patch.markers().size(), NamedTextColor.BLUE);
    }
  }

  private void rememberCutSourcePatchIfNeeded(
      Actor actor, String command, PendingClipboardPatch patch) {
    if (actor == null || actor.getUniqueId() == null) return;
    UUID actorId = actor.getUniqueId();
    if (!WorldEditCommandParser.isClipboardCutCommand(command)
        || patch == null
        || patch.markers().isEmpty()) {
      pendingCutSourcePatches.remove(actorId);
      pendingCutClipboardTransfers.remove(actorId);
      return;
    }
    pendingCutSourcePatches.put(actorId, patch);
    pendingCutClipboardTransfers.add(actorId);
    PendingClipboardPatch retainedPatch = patch;
    scheduleLifecycleTask(() -> pendingCutSourcePatches.remove(actorId, retainedPatch), 100L);
  }

  private PendingClipboardPatch pendingCutSourcePatch(Actor actor) {
    if (actor == null || actor.getUniqueId() == null) return null;
    return pendingCutSourcePatches.get(actor.getUniqueId());
  }

  private void clearActorClipboardState(UUID actorId) {
    if (actorId == null) return;
    clipboardPatcher.cancel(actorId);
    clipboardPatches.remove(actorId);
    pendingCutSourcePatches.remove(actorId);
    pendingCutClipboardTransfers.remove(actorId);
    pendingPasteCommands.remove(actorId);
    pendingMovePatches.remove(actorId);
    operationContexts.clear(actorId);
    trustedClipboards.clear(actorId);
  }

  private PendingPastePatch resolvePendingPastePatch(Actor actor, UUID destinationWorldId) {
    if (actor == null || actor.getUniqueId() == null) return null;
    UUID actorId = actor.getUniqueId();
    PendingPasteCommand command = pendingPasteCommands.get(actorId);
    if (command == null) return null;
    long now = System.currentTimeMillis();
    if (now - command.timestampMs() > PASTE_COMMAND_TTL_MS) {
      pendingPasteCommands.remove(actorId);
      return null;
    }
    if (command.onlySelect()) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    PendingClipboardPatch clipboardPatch = clipboardPatches.get(actorId);
    if (clipboardPatch == null || clipboardPatch.markers().isEmpty()) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
    ClipboardHolder holder;
    Clipboard clipboard;
    try {
      holder = session.getClipboard();
      clipboard = holder.getClipboard();
    } catch (EmptyClipboardException e) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    if (clipboard == null) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    if (!trustedClipboards.matches(actorId, clipboard)) {
      pendingPasteCommands.remove(actorId, command);
      PerfStats.incrementCounter("worldedit.clipboard.correlationFailures");
      return null;
    }
    BlockVector3 target;
    try {
      target = command.atOrigin() ? clipboard.getOrigin() : session.getPlacementPosition(actor);
    } catch (IncompleteRegionException e) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    if (target == null) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    Region region = clipboard.getRegion();
    BlockVector3 origin = clipboard.getOrigin();
    Transform transform = holder.getTransform();
    Map<BlockVector3, MarkerSnapshot> snapshots = new HashMap<>();
    Map<BlockVector3, BlockVector3> destinations = new HashMap<>();
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : clipboardPatch.markers().entrySet()) {
      BlockVector3 sourcePos = entry.getKey();
      if (region != null && !region.contains(sourcePos)) {
        continue;
      }
      MarkerSnapshot snapshot = parseSnapshot(entry.getValue());
      if (snapshot == null) {
        continue;
      }
      BaseBlock sourceBlock = clipboard.getFullBlock(sourcePos);
      if (!isPotentialClipboardMarker(sourceBlock, snapshot)) {
        continue;
      }
      BlockVector3 offset = sourcePos.subtract(origin);
      BlockVector3 destination =
          transform == null || transform.isIdentity()
              ? offset.add(target)
              : transform.apply(offset.toVector3()).toBlockPoint().add(target);
      snapshots.put(sourcePos, snapshot);
      destinations.put(sourcePos, destination);
    }
    Map<Long, MarkerSnapshot> destinationMarkers = new HashMap<>();
    Map<Long, MarkerSnapshot> undoMarkers = new HashMap<>();
    UUID sourceWorldId = clipboardPatch.sourceWorldId();
    World destinationWorld = Bukkit.getWorld(destinationWorldId);
    for (Map.Entry<BlockVector3, MarkerSnapshot> entry : snapshots.entrySet()) {
      BlockVector3 destination = destinations.get(entry.getKey());
      if (destination == null) {
        continue;
      }
      MarkerSnapshot snapshot =
          RelayLinkRewrite.rewrite(
              entry.getValue(),
              entry.getKey(),
              sourceWorldId,
              destinationWorldId,
              destinations,
              snapshots);
      long destinationKey =
          WorldEditMarkerMath.blockKey(destination.x(), destination.y(), destination.z());
      destinationMarkers.put(destinationKey, snapshot);
      MarkerSnapshot undoSnapshot = captureMarkerSnapshot(destinationWorld, destination);
      if (undoSnapshot == null && Objects.equals(sourceWorldId, destinationWorldId)) {
        undoSnapshot = snapshots.get(destination);
      }
      if (undoSnapshot != null) {
        undoMarkers.put(destinationKey, undoSnapshot);
      }
    }
    if (destinationMarkers.isEmpty()) {
      pendingPasteCommands.remove(actorId, command);
      return null;
    }
    boolean preserveStorageIdentity = pendingCutClipboardTransfers.contains(actorId);
    boolean finalPasteStage = command.usesRemaining() <= 1;
    consumePasteCommand(actorId, command);
    if (finalPasteStage) {
      pendingCutClipboardTransfers.remove(actorId);
    }
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent(
          "we paste sidecar markers=" + destinationMarkers.size(), NamedTextColor.DARK_GREEN);
    }
    return new PendingPastePatch(destinationMarkers, undoMarkers, preserveStorageIdentity);
  }

  private boolean hasPendingPasteCommand(Actor actor) {
    if (actor == null || actor.getUniqueId() == null) return false;
    UUID actorId = actor.getUniqueId();
    PendingPasteCommand command = pendingPasteCommands.get(actorId);
    if (command == null) return false;
    long now = System.currentTimeMillis();
    if (now - command.timestampMs() > PASTE_COMMAND_TTL_MS) {
      pendingPasteCommands.remove(actorId);
      return false;
    }
    return !command.onlySelect();
  }

  private void consumePasteCommand(UUID actorId, PendingPasteCommand command) {
    if (actorId == null || command == null) return;
    if (command.usesRemaining() <= 1) {
      pendingPasteCommands.remove(actorId, command);
      return;
    }
    pendingPasteCommands.replace(actorId, command, command.consume());
  }

  private HistoryAction resolvePendingHistoryCommand(Actor actor) {
    if (actor == null || actor.getUniqueId() == null) return null;
    UUID actorId = actor.getUniqueId();
    PendingHistoryCommand command = pendingHistoryCommands.get(actorId);
    if (command == null) return null;
    long now = System.currentTimeMillis();
    if (now - command.timestampMs() > HISTORY_COMMAND_TTL_MS) {
      pendingHistoryCommands.remove(actorId);
      return null;
    }
    consumeHistoryCommand(actorId, command);
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent(
          "we history restore armed action=" + command.action().name().toLowerCase(Locale.ROOT),
          NamedTextColor.DARK_GREEN);
    }
    return command.action();
  }

  private void consumeHistoryCommand(UUID actorId, PendingHistoryCommand command) {
    if (actorId == null || command == null) return;
    if (command.usesRemaining() <= 1) {
      pendingHistoryCommands.remove(actorId, command);
      return;
    }
    pendingHistoryCommands.replace(actorId, command, command.consume());
  }

  private PendingMovePatch resolvePendingMovePatch(Actor actor) {
    if (actor == null || actor.getUniqueId() == null) return null;
    UUID actorId = actor.getUniqueId();
    PendingMovePatch patch = pendingMovePatches.get(actorId);
    if (patch == null) return null;
    long now = System.currentTimeMillis();
    if (now - patch.timestampMs() > MOVE_COMMAND_TTL_MS) {
      pendingMovePatches.remove(actorId);
      return null;
    }
    if (patch.usesRemaining() <= 1) {
      pendingMovePatches.remove(actorId, patch);
    } else {
      pendingMovePatches.replace(actorId, patch, patch.consume());
    }
    return patch;
  }

  private ClipboardCapture captureClipboardPatches(Actor actor) {
    CapturedMarkers captured = captureSelectionMarkers(actor, true);
    if (captured.markers().isEmpty()) {
      return new ClipboardCapture(null, null);
    }
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent(
          "we clipboard capture markers=" + captured.markers().size(), NamedTextColor.BLUE);
    }
    Map<BlockVector3, LinCompoundTag> copyMarkers = new HashMap<>();
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : captured.markers().entrySet()) {
      MarkerSnapshot snapshot = parseSnapshot(entry.getValue());
      if (snapshot == null) continue;
      LinCompoundTag copyTag =
          WorldEditMarkerCodec.buildExortTag(snapshot.withoutTransmitterStoredItems());
      if (copyTag != null) copyMarkers.put(entry.getKey(), copyTag);
    }
    PendingClipboardPatch copyPatch =
        new PendingClipboardPatch(
            captured.sourceWorldId(), captured.bounds(), captured.origin(), copyMarkers);
    PendingClipboardPatch cutPatch =
        new PendingClipboardPatch(
            captured.sourceWorldId(), captured.bounds(), captured.origin(), captured.markers());
    return new ClipboardCapture(copyPatch, cutPatch);
  }

  private record ClipboardCapture(
      PendingClipboardPatch copyPatch, PendingClipboardPatch cutPatch) {}

  private PendingMovePatch captureMovePatch(Actor actor, String command, Player player) {
    BlockVector3 vector = WorldEditCommandParser.parseMoveVector(command, player);
    if (vector.equals(BlockVector3.at(0, 0, 0))) {
      return null;
    }
    CapturedMarkers captured = captureSelectionMarkers(actor, true);
    if (captured.markers().isEmpty()) {
      return null;
    }
    Map<Long, MarkerSnapshot> destinationMarkers = new HashMap<>();
    Map<Long, MarkerSnapshot> sourceMarkers = new HashMap<>();
    Map<BlockVector3, MarkerSnapshot> snapshots = parseSnapshots(captured.markers());
    Map<BlockVector3, BlockVector3> destinations = new HashMap<>();
    for (BlockVector3 source : snapshots.keySet()) {
      destinations.put(source, source.add(vector));
    }
    for (Map.Entry<BlockVector3, MarkerSnapshot> entry : snapshots.entrySet()) {
      MarkerSnapshot snapshot = entry.getValue();
      BlockVector3 destination = destinations.get(entry.getKey());
      if (destination == null) {
        continue;
      }
      sourceMarkers.put(
          WorldEditMarkerMath.blockKey(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()),
          snapshot);
      snapshot =
          RelayLinkRewrite.rewrite(
              snapshot,
              entry.getKey(),
              captured.sourceWorldId(),
              captured.sourceWorldId(),
              destinations,
              snapshots);
      destinationMarkers.put(
          WorldEditMarkerMath.blockKey(destination.x(), destination.y(), destination.z()),
          snapshot);
    }
    if (destinationMarkers.isEmpty()) {
      return null;
    }
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent(
          "we move sidecar markers="
              + destinationMarkers.size()
              + " offset="
              + vector.x()
              + ","
              + vector.y()
              + ","
              + vector.z(),
          NamedTextColor.DARK_GREEN);
    }
    return new PendingMovePatch(
        sourceMarkers, destinationMarkers, vector, System.currentTimeMillis(), 3);
  }

  private PendingOperationSnapshot captureOperationSnapshot(
      Actor actor, String command, Player player) {
    if (!Bukkit.isPrimaryThread()) {
      return null;
    }
    SelectionGeometry geometry = selectionGeometry(actor);
    if (geometry.world() == null) {
      return null;
    }
    BlockVector3 stackDirection = WorldEditCommandParser.parseStackDirection(command, player);
    WorldEditBounds affected =
        WorldEditCommandParser.affectedBounds(
            command, geometry.bounds(), geometry.origin(), stackDirection);
    if (affected == null) {
      PerfStats.incrementCounter("worldedit.operation.correlationFailures");
      return null;
    }
    Region exactRegion =
        WorldEditCommandParser.isBroadOperationSnapshotCommand(command) ? null : geometry.region();
    CapturedMarkers captured = captureMarkers(geometry, affected, exactRegion, true);
    Map<Long, MarkerSnapshot> markers = new HashMap<>();
    Set<ChunkKey> chunks = new HashSet<>();
    UUID worldId = captured.sourceWorldId();
    addCapturedSnapshots(captured, markers, chunks);
    if (worldId == null || markers.isEmpty()) {
      return null;
    }
    return new PendingOperationSnapshot(
        worldId, markers, chunks, affected, "worldedit_" + operationSnapshotReason(command));
  }

  private void addCapturedSnapshots(
      CapturedMarkers captured, Map<Long, MarkerSnapshot> markers, Set<ChunkKey> chunks) {
    if (captured == null || captured.sourceWorldId() == null || captured.markers().isEmpty()) {
      return;
    }
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : captured.markers().entrySet()) {
      MarkerSnapshot snapshot = parseSnapshot(entry.getValue());
      if (snapshot == null) {
        continue;
      }
      BlockVector3 pos = entry.getKey();
      markers.put(WorldEditMarkerMath.blockKey(pos.x(), pos.y(), pos.z()), snapshot);
      chunks.add(new ChunkKey(captured.sourceWorldId(), pos.x() >> 4, pos.z() >> 4));
    }
  }

  private void rememberOperationSnapshot(PendingOperationSnapshot snapshot) {
    if (snapshot == null || snapshot.markers().isEmpty()) return;
    schedulePostCommandRefresh(snapshot.chunks(), snapshot.reason());
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent(
          "we operation snapshot markers=" + snapshot.markers().size(), NamedTextColor.BLUE);
    }
  }

  private void scheduleEntityRefresh(Actor actor, String command) {
    World world = actorWorld(actor);
    if (world == null) {
      CapturedMarkers captured = captureSelectionMarkers(actor);
      Set<ChunkKey> chunks = new HashSet<>();
      addCapturedChunks(captured, chunks);
      schedulePostCommandRefresh(chunks, "worldedit_" + operationSnapshotReason(command));
      return;
    }
    final WorldEditLoadedMarkerChunkCursor[] reference = new WorldEditLoadedMarkerChunkCursor[1];
    WorldEditLoadedMarkerChunkCursor cursor =
        new WorldEditLoadedMarkerChunkCursor(
            plugin,
            deps.generationScope(),
            world,
            chunks -> {
              entityRefreshCursors.remove(reference[0]);
              schedulePostCommandRefresh(chunks, "worldedit_" + operationSnapshotReason(command));
            });
    reference[0] = cursor;
    entityRefreshCursors.add(cursor);
    cursor.start();
  }

  private void addCapturedChunks(CapturedMarkers captured, Set<ChunkKey> chunks) {
    if (captured == null || captured.sourceWorldId() == null || captured.markers().isEmpty()) {
      return;
    }
    for (BlockVector3 pos : captured.markers().keySet()) {
      chunks.add(new ChunkKey(captured.sourceWorldId(), pos.x() >> 4, pos.z() >> 4));
    }
  }

  private void schedulePostCommandRefresh(Set<ChunkKey> chunks, String reason) {
    if (shuttingDown || chunks == null || chunks.isEmpty()) {
      return;
    }
    Set<ChunkKey> affected = Set.copyOf(chunks);
    scheduleLifecycleTask(
        () -> {
          refreshScheduler.refreshAffectedChunks(affected, Set.of(), reason);
          refreshScheduler.scheduleDeferredRefresh(affected);
        },
        1L);
  }

  private void scheduleLifecycleTask(Runnable action, long delayTicks) {
    if (shuttingDown) {
      return;
    }
    AtomicReference<BukkitTask> reference = new AtomicReference<>();
    try {
      BukkitTask task =
          deps.generationScope()
              .runTaskLater(
                  "WorldEdit lifecycle callback",
                  () -> {
                    BukkitTask current = reference.get();
                    if (current != null) {
                      lifecycleTasks.remove(current);
                    }
                    if (shuttingDown || !plugin.isEnabled()) {
                      return;
                    }
                    action.run();
                  },
                  delayTicks);
      reference.set(task);
      lifecycleTasks.add(task);
      if (shuttingDown && lifecycleTasks.remove(task)) {
        deps.generationScope().cancelTask(task);
      }
    } catch (RuntimeException ignored) {
      // Plugin shutdown may reject scheduled work; chunk-load reconciliation remains available.
    }
  }

  private World actorWorld(Actor actor) {
    if (actor instanceof com.sk89q.worldedit.entity.Player worldEditPlayer) {
      com.sk89q.worldedit.world.World weWorld = worldEditPlayer.getWorld();
      if (weWorld != null) {
        World world = Bukkit.getWorld(weWorld.getName());
        if (world != null) {
          return world;
        }
      }
    }
    if (actor != null && actor.getUniqueId() != null) {
      Player player = Bukkit.getPlayer(actor.getUniqueId());
      if (player != null) {
        return player.getWorld();
      }
    }
    return null;
  }

  private static String operationSnapshotReason(String command) {
    if (command == null) {
      return "operation";
    }
    String trimmed = command.trim();
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    int space = trimmed.indexOf(' ');
    if (space >= 0) {
      trimmed = trimmed.substring(0, space);
    }
    int colon = trimmed.lastIndexOf(':');
    if (colon >= 0 && colon + 1 < trimmed.length()) {
      trimmed = trimmed.substring(colon + 1);
    }
    if (trimmed.isBlank()) {
      return "operation";
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  private CapturedMarkers captureSelectionMarkers(Actor actor) {
    return captureSelectionMarkers(actor, false);
  }

  private CapturedMarkers captureSelectionMarkers(Actor actor, boolean includeTransmitterTerminal) {
    SelectionGeometry geometry = selectionGeometry(actor);
    if (geometry.world() == null || geometry.bounds() == null) {
      return CapturedMarkers.empty(geometry.worldId());
    }
    return captureMarkers(
        geometry, geometry.bounds(), geometry.region(), includeTransmitterTerminal);
  }

  private SelectionGeometry selectionGeometry(Actor actor) {
    if (actor == null) return SelectionGeometry.empty();
    LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
    com.sk89q.worldedit.world.World weWorld = session.getSelectionWorld();
    if (weWorld == null && actor instanceof com.sk89q.worldedit.entity.Player player) {
      weWorld = player.getWorld();
    }
    if (weWorld == null) return SelectionGeometry.empty();
    World world = Bukkit.getWorld(weWorld.getName());
    if (world == null) return SelectionGeometry.empty();
    Region region;
    try {
      region = session.getSelection(weWorld).clone();
    } catch (IncompleteRegionException e) {
      return new SelectionGeometry(
          world.getUID(), world, null, null, placementPosition(session, actor));
    }
    return new SelectionGeometry(
        world.getUID(),
        world,
        region,
        WorldEditBounds.from(region),
        placementPosition(session, actor));
  }

  private static BlockVector3 placementPosition(LocalSession session, Actor actor) {
    try {
      return session == null || actor == null ? null : session.getPlacementPosition(actor);
    } catch (IncompleteRegionException | RuntimeException ignored) {
      return null;
    }
  }

  private CapturedMarkers captureMarkers(
      SelectionGeometry geometry,
      WorldEditBounds bounds,
      Region exactRegion,
      boolean includeTransmitterTerminal) {
    if (!Bukkit.isPrimaryThread()
        || geometry == null
        || geometry.world() == null
        || bounds == null) {
      return CapturedMarkers.empty(geometry == null ? null : geometry.worldId());
    }
    World world = geometry.world();
    Map<BlockVector3, LinCompoundTag> markers = new HashMap<>();
    if (bounds.chunkCountCapped(MARKER_CAPTURE_CHUNK_CAP + 1) > MARKER_CAPTURE_CHUNK_CAP) {
      PerfStats.incrementCounter("worldedit.operation.captureOverflow");
      if (markerCaptureOverflowWarned.compareAndSet(false, true)) {
        ExortLog.warn(
            "[WorldEdit] Exort marker capture exceeded its 100000-chunk safety cap; marker "
                + "correlation for this operation was rejected.");
      }
      return CapturedMarkers.empty(world.getUID());
    }
    int capturedChunks = 0;
    for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
      for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) continue;
        capturedChunks++;
        ChunkMarkerStore.forEachBlock(
            plugin,
            chunk,
            (block, root) -> {
              BlockVector3 pos = BlockVector3.at(block.getX(), block.getY(), block.getZ());
              if (!bounds.contains(pos) || exactRegion != null && !exactRegion.contains(pos)) {
                return;
              }
              LinCompoundTag tag = buildExortTag(block, includeTransmitterTerminal);
              if (tag != null) {
                markers.put(pos, tag);
              }
            });
      }
    }
    return new CapturedMarkers(world.getUID(), bounds, geometry.origin(), markers, capturedChunks);
  }

  private record SelectionGeometry(
      UUID worldId, World world, Region region, WorldEditBounds bounds, BlockVector3 origin) {
    static SelectionGeometry empty() {
      return new SelectionGeometry(null, null, null, null, null);
    }
  }

  private static Map<BlockVector3, MarkerSnapshot> parseSnapshots(
      Map<BlockVector3, LinCompoundTag> markers) {
    if (markers == null || markers.isEmpty()) {
      return Map.of();
    }
    Map<BlockVector3, MarkerSnapshot> snapshots = new HashMap<>();
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : markers.entrySet()) {
      MarkerSnapshot snapshot = parseSnapshot(entry.getValue());
      if (snapshot != null) {
        snapshots.put(entry.getKey(), snapshot);
      }
    }
    return snapshots;
  }

  static MarkerSnapshot rememberMoveSourceHistory(
      WorldEditMarkerHistory markerHistory,
      UUID actorId,
      UUID worldId,
      BlockVector3 source,
      PendingMovePatch movePatch,
      WorldEditMarkerHistory.Frame normalHistoryFrame) {
    return rememberMoveSourceHistory(
        markerHistory, null, actorId, worldId, source, movePatch, normalHistoryFrame);
  }

  static MarkerSnapshot rememberMoveSourceHistory(
      WorldEditMarkerHistory markerHistory,
      Set<Long> rememberedHistory,
      UUID actorId,
      UUID worldId,
      BlockVector3 source,
      PendingMovePatch movePatch,
      WorldEditMarkerHistory.Frame normalHistoryFrame) {
    if (markerHistory == null
        || actorId == null
        || worldId == null
        || source == null
        || movePatch == null) {
      return null;
    }
    MarkerSnapshot moved = movePatch.source(source);
    if (moved == null) {
      return null;
    }
    long key = WorldEditMarkerMath.blockKey(source.x(), source.y(), source.z());
    if (rememberedHistory != null && !rememberedHistory.add(key)) {
      return moved;
    }
    markerHistory.remember(
        actorId, null, worldId, source.x(), source.y(), source.z(), moved, normalHistoryFrame);
    return moved;
  }

  static void seedMoveSourceHistory(
      WorldEditMarkerHistory markerHistory,
      Set<Long> rememberedHistory,
      UUID actorId,
      UUID worldId,
      PendingMovePatch movePatch,
      WorldEditMarkerHistory.Frame normalHistoryFrame) {
    if (markerHistory == null
        || rememberedHistory == null
        || actorId == null
        || worldId == null
        || movePatch == null
        || normalHistoryFrame == null) {
      return;
    }
    for (Map.Entry<Long, MarkerSnapshot> entry : movePatch.sourceMarkers().entrySet()) {
      long key = entry.getKey();
      if (movePatch.destinationMarkers().containsKey(key) || !rememberedHistory.add(key)) {
        continue;
      }
      int x = WorldEditMarkerMath.blockX(key);
      int y = WorldEditMarkerMath.blockY(key);
      int z = WorldEditMarkerMath.blockZ(key);
      markerHistory.remember(actorId, null, worldId, x, y, z, entry.getValue(), normalHistoryFrame);
      if (normalHistoryFrame.overflowed()) {
        return;
      }
      markerHistory.rememberRedoClear(actorId, worldId, normalHistoryFrame, x, y, z);
      if (normalHistoryFrame.overflowed()) {
        return;
      }
    }
  }

  static boolean rememberHistorySnapshotOnce(
      WorldEditMarkerHistory markerHistory,
      Set<Long> rememberedHistory,
      UUID actorId,
      HistoryAction historyAction,
      UUID worldId,
      BlockVector3 position,
      MarkerSnapshot snapshot,
      WorldEditMarkerHistory.Frame normalHistoryFrame) {
    if (markerHistory == null
        || rememberedHistory == null
        || actorId == null
        || worldId == null
        || position == null
        || snapshot == null) {
      return false;
    }
    long key = WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z());
    if (!rememberedHistory.add(key)) {
      return false;
    }
    markerHistory.remember(
        actorId,
        historyAction,
        worldId,
        position.x(),
        position.y(),
        position.z(),
        snapshot,
        normalHistoryFrame);
    return true;
  }

  static MarkerSnapshot chooseUndoSnapshot(
      MarkerSnapshot existingSnapshot,
      MarkerSnapshot moveDestinationExistingSnapshot,
      MarkerSnapshot pasteUndoSnapshot,
      MarkerSnapshot cutSourceHistory) {
    MarkerSnapshot chosen = existingSnapshot;
    chosen = richerTransmitterSnapshot(chosen, moveDestinationExistingSnapshot);
    chosen = richerTransmitterSnapshot(chosen, pasteUndoSnapshot);
    return richerTransmitterSnapshot(chosen, cutSourceHistory);
  }

  static MarkerSnapshot chooseExistingSnapshot(
      MarkerSnapshot liveSnapshot,
      PendingOperationSnapshot operationSnapshot,
      UUID worldId,
      BlockVector3 position) {
    if (operationSnapshot == null) {
      return liveSnapshot;
    }
    return richerTransmitterSnapshot(liveSnapshot, operationSnapshot.get(worldId, position));
  }

  static MarkerSnapshot richerTransmitterSnapshot(
      MarkerSnapshot current, MarkerSnapshot candidate) {
    if (current == null) {
      return candidate;
    }
    if (candidate == null) {
      return current;
    }
    if (current.transmitter() && candidate.transmitter()) {
      TransmitterData currentData = current.transmitterData();
      TransmitterData candidateData = candidate.transmitterData();
      if (currentData == null) {
        return candidateData == null ? current : candidate;
      }
      TransmitterData merged = currentData.mergeMissingFrom(candidateData);
      if (!merged.equals(currentData)) {
        return new MarkerSnapshot(
            current.storage(),
            current.terminal(),
            current.bus(),
            current.monitor(),
            current.relay(),
            true,
            merged,
            current.chunkLoader(),
            current.wire(),
            current.storageCore());
      }
    }
    return current;
  }

  void enqueue(MarkerUpdate update) {
    if (update == null) return;
    operationTracker.record(update);
    updates.add(new PendingUpdate(update));
    updateQueueDepthGauge();
    scheduleFlushIfNeeded();
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isEnabled()) {
      debug.incUpdatesQueued();
    }
  }

  void enqueueMoveSourceCleanup(
      long operationId, UUID worldId, PendingMovePatch movePatch, Set<Long> observedSourceClears) {
    if (worldId == null || movePatch == null) {
      return;
    }
    for (Map.Entry<Long, MarkerSnapshot> entry : movePatch.sourceMarkers().entrySet()) {
      long key = entry.getKey();
      if (movePatch.destinationMarkers().containsKey(key)
          || observedSourceClears != null && observedSourceClears.contains(key)) {
        continue;
      }
      MarkerSnapshot snapshot = entry.getValue();
      String removedStorageId =
          snapshot != null && snapshot.storage() != null ? snapshot.storage().storageId() : null;
      enqueue(
          new MarkerUpdate(
              operationId,
              worldId,
              WorldEditMarkerMath.blockX(key),
              WorldEditMarkerMath.blockY(key),
              WorldEditMarkerMath.blockZ(key),
              null,
              removedStorageId,
              false,
              true));
    }
  }

  private void scheduleFlushIfNeeded() {
    if (updates.isEmpty()) {
      updateQueueDepthGauge();
      return;
    }
    synchronized (flushTaskLock) {
      if (shuttingDown || flushTask != null || updates.isEmpty()) {
        return;
      }
      try {
        flushTask =
            deps.generationScope().runTaskLater("WorldEdit marker flush", this::flushUpdates, 1L);
      } catch (IllegalStateException ignored) {
        flushTask = null;
      }
    }
  }

  private void flushUpdates() {
    synchronized (flushTaskLock) {
      flushTask = null;
      if (shuttingDown) {
        return;
      }
    }
    if (updates.isEmpty()) {
      updateQueueDepthGauge();
      return;
    }
    try {
      PerfStats.measure("worldedit.markerApply", this::flushUpdatesMeasured);
    } finally {
      scheduleFlushIfNeeded();
    }
  }

  private void flushUpdatesMeasured() {
    operationTracker.purge(System.currentTimeMillis());
    Map<ChunkKey, ChunkUpdateBatch> batches = new HashMap<>();
    int processed = 0;
    tickCounter++;
    int polled = 0;
    int limit = updates.size();
    int applyLimit = markerUpdatesPerTick(limit);
    while (processed < applyLimit) {
      PendingUpdate pending = updates.poll();
      if (pending == null) break;
      polled++;
      if (pending.nextTick > tickCounter) {
        updates.add(pending);
      } else {
        MarkerUpdate update = pending.update;
        ChunkKey key = new ChunkKey(update.worldId(), update.chunkX(), update.chunkZ());
        batches.computeIfAbsent(key, ChunkUpdateBatch::new).add(pending);
        processed++;
      }
      if (polled >= limit) break;
    }
    if (batches.isEmpty()) {
      updateQueueDepthGauge();
      return;
    }

    Map<Long, Set<String>> removedStorageIdsByOperation =
        operationTracker.removedStorageIdsByOperation(batches);
    Set<ChunkKey> changedChunks = new HashSet<>();
    Set<BlockRef> networkRefreshStarts = new HashSet<>();
    for (ChunkUpdateBatch batch : batches.values()) {
      World world = Bukkit.getWorld(batch.key.worldId());
      if (world == null) {
        requeueBatch(batch);
        continue;
      }
      if (!world.isChunkLoaded(batch.key.chunkX(), batch.key.chunkZ())) {
        requeueBatch(batch);
        continue;
      }
      Iterable<PendingUpdate> updatesToApply = coalesceByBlock(batch.updates);
      for (PendingUpdate pending : updatesToApply) {
        MarkerUpdate update = pending.update;
        MarkerSnapshot snapshot = update.snapshot();
        if (snapshot != null && snapshot.storage() != null && update.storageCloneRequired()) {
          String storageId = snapshot.storage().storageId();
          Set<String> removedStorageIds =
              removedStorageIdsByOperation.getOrDefault(update.operationId(), Set.of());
          boolean moveStorage =
              storageIdentityAction(update, removedStorageIds)
                  == StorageIdentityAction.PRESERVE_IDENTITY;
          if (moveStorage) {
            if (!update.moveOperation()) {
              update = update.asMove();
              pending.update = update;
            }
            WorldEditDebugService debug = deps.debugService();
            if (debug != null && debug.isFull()) {
              debug.recordEvent(
                  "we storage move operation="
                      + update.operationId()
                      + " pos="
                      + update.x()
                      + ","
                      + update.y()
                      + ","
                      + update.z()
                      + " storage="
                      + storageId
                      + " reason="
                      + (update.moveOperation() ? "command" : "removed"),
                  NamedTextColor.DARK_GREEN);
            }
          } else if (storageId != null) {
            String newId = UUID.randomUUID().toString();
            MarkerSnapshot clonedSnapshot = withStorageId(resolveStorageTier(snapshot), newId);
            MarkerUpdate clonedUpdate =
                new MarkerUpdate(
                    update.operationId(),
                    update.worldId(),
                    update.x(),
                    update.y(),
                    update.z(),
                    clonedSnapshot,
                    null,
                    false,
                    false);
            WorldEditDebugService debug = deps.debugService();
            if (debug != null && debug.isFull()) {
              debug.recordEvent(
                  "we storage clone operation="
                      + update.operationId()
                      + " pos="
                      + update.x()
                      + ","
                      + update.y()
                      + ","
                      + update.z()
                      + " source="
                      + storageId
                      + " target="
                      + newId,
                  NamedTextColor.LIGHT_PURPLE);
            }
            prepareEmptyStorageClone(world, clonedUpdate, storageId);
            continue;
          }
        }
        if (!applyUpdate(world, update)) {
          if (pending.attempts < MAX_RETRIES) {
            pending.attempts++;
            pending.nextTick = tickCounter + RETRY_DELAY_TICKS;
            updates.add(pending);
            WorldEditDebugService debug = deps.debugService();
            if (debug != null && debug.isEnabled()) {
              debug.incUpdatesRetried();
            }
          }
        } else {
          WorldEditDebugService debug = deps.debugService();
          if (debug != null && debug.isEnabled()) {
            debug.incUpdatesApplied();
          }
          changedChunks.add(batch.key);
          if (shouldRefreshWireNetworkAfterUpdate(update)) {
            networkRefreshStarts.add(
                new BlockRef(update.worldId(), update.x(), update.y(), update.z()));
          }
        }
      }
    }
    if (!changedChunks.isEmpty()) {
      refreshScheduler.refreshAffectedChunks(changedChunks, networkRefreshStarts, "immediate");
      refreshScheduler.scheduleDeferredRefresh(changedChunks);
    }
    if (updates.isEmpty()) {
      refreshScheduler.finishNetworkBatch();
    }
    updateQueueDepthGauge();
  }

  private int markerUpdatesPerTick(int queued) {
    WorldEditBulkConfig config = deps.bulkConfig();
    if (config.enabled() && queued >= config.bulkThresholdBlocks()) {
      return config.markerUpdatesPerTick();
    }
    return APPLY_PER_TICK;
  }

  private void updateQueueDepthGauge() {
    if (PerfStats.isEnabled()) {
      int markerDepth = updates.size();
      PerfStats.setGauge("worldedit.markerQueueDepth", markerDepth);
      PerfStats.setGauge("worldedit.markerDeferredChunks", deferredUpdates.chunkCount());
      PerfStats.setGauge("worldedit.markerDeferredUpdates", deferredUpdates.updateCount());
      PerfStats.setGauge("worldedit.snapshotDeferredChunks", deferredSnapshotChunks.size());
      PerfStats.setGauge("worldedit.directReconciliationDepth", directReconciliation.size());
      PerfStats.setGauge(
          "worldedit.queueDepth",
          markerDepth
              + deferredUpdates.updateCount()
              + deferredSnapshotChunks.size()
              + directReconciliation.size()
              + refreshScheduler.queuedTaskCount());
    }
  }

  private static Iterable<PendingUpdate> coalesceByBlock(Iterable<PendingUpdate> pendingUpdates) {
    Map<Long, PendingUpdate> latest = new LinkedHashMap<>();
    for (PendingUpdate pending : pendingUpdates) {
      if (pending == null || pending.update == null) {
        continue;
      }
      MarkerUpdate update = pending.update;
      long key = WorldEditMarkerMath.blockKey(update.x(), update.y(), update.z());
      latest.remove(key);
      latest.put(key, pending);
    }
    return latest.values();
  }

  private void requeueBatch(ChunkUpdateBatch batch) {
    WorldEditDebugService debug = deps.debugService();
    List<PendingUpdate> deferred = new ArrayList<>();
    for (PendingUpdate pending : batch.updates) {
      if (pending.attempts < MAX_RETRIES) {
        pending.attempts++;
        pending.nextTick = tickCounter + RETRY_DELAY_TICKS;
        updates.add(pending);
        if (debug != null && debug.isEnabled()) {
          debug.incUpdatesRetried();
        }
      } else {
        if (debug != null && debug.isEnabled()) {
          debug.incUpdatesSkipped();
        }
        deferred.add(pending);
      }
    }
    if (!deferred.isEmpty()) {
      int count = deferredUpdates.defer(batch.key, deferred);
      logDeferredUpdates(batch.key, count);
    }
  }

  private int requeueDeferredBatch(ChunkUpdateBatch batch) {
    if (batch == null || batch.updates.isEmpty()) {
      return 0;
    }
    int requeued = 0;
    for (PendingUpdate pending : batch.updates) {
      if (pending == null) {
        continue;
      }
      pending.attempts = 0;
      pending.nextTick = tickCounter;
      updates.add(pending);
      requeued++;
    }
    updateQueueDepthGauge();
    return requeued;
  }

  private void logDeferredUpdates(ChunkKey key, int count) {
    if (key == null || count <= 0 || !warnedDeferredUpdateChunks.add(key)) {
      return;
    }
    plugin
        .getLogger()
        .warning(
            "[WorldEdit] Delayed "
                + count
                + " marker update(s) for chunk "
                + key.chunkX()
                + ","
                + key.chunkZ()
                + " after retry budget; Exort will retry when the chunk loads.");
  }

  private boolean applyUpdate(World world, MarkerUpdate update) {
    Block block = world.getBlockAt(update.x(), update.y(), update.z());
    MarkerSnapshot snapshot = update.snapshot();
    if (snapshot == null) {
      releaseRemovedStorageClaim(block, update.removedStorageId());
      removeExistingBlockState(block);
      ChunkMarkerStore.clearBlock(plugin, block);
      return true;
    }
    if (snapshot.storage() != null && !Carriers.matchesCarrier(block, deps.storageCarrier())) {
      return false;
    }
    if (snapshot.storageCore() && !Carriers.matchesCarrier(block, deps.storageCarrier())) {
      return false;
    }
    if (snapshot.terminal() != null && !Carriers.matchesCarrier(block, deps.terminalCarrier())) {
      return false;
    }
    if (snapshot.monitor() != null && !Carriers.matchesCarrier(block, deps.monitorCarrier())) {
      return false;
    }
    if (snapshot.bus() != null && !Carriers.matchesCarrier(block, deps.busCarrier())) {
      return false;
    }
    if (snapshot.relay() != null && !Carriers.matchesCarrier(block, deps.relayCarrier())) {
      return false;
    }
    if (snapshot.transmitter() && !Carriers.matchesCarrier(block, deps.transmitterCarrier())) {
      return false;
    }
    if (snapshot.chunkLoader() != null
        && !Carriers.matchesCarrier(block, deps.chunkLoaderCarrier())) {
      return false;
    }
    if (snapshot.wire() && !Carriers.matchesCarrier(block, deps.wireMaterial())) {
      return false;
    }
    if (snapshot.storage() != null && !ensureStorageClaim(block, update)) {
      return false;
    }
    removeExistingBlockState(block);
    ChunkMarkerStore.clearBlock(plugin, block);
    if (snapshot.storage() != null && Carriers.matchesCarrier(block, deps.storageCarrier())) {
      StorageTierResolver.Resolution resolution =
          StorageTierResolver.resolve(
                  deps.storageTierCatalog(),
                  snapshot.storage().tier(),
                  snapshot.storage().tierMaxItems())
              .orElse(null);
      String storageId = snapshot.storage().storageId();
      if (resolution != null) {
        StorageMarker.set(
            plugin,
            block,
            storageId,
            resolution.tier(),
            parseFacing(snapshot.storage()),
            snapshot.storage().displayName());
        ItemHologramManager hologramManager = deps.hologramManager();
        if (hologramManager != null) {
          hologramManager.registerStorage(block);
        }
        updateMovedStorageLocation(storageId, block);
      } else {
        StorageMarker.setRaw(
            plugin,
            block,
            storageId,
            snapshot.storage().tier(),
            snapshot.storage().tierMaxItems(),
            parseFacing(snapshot.storage()),
            snapshot.storage().displayName());
      }
    }
    if (snapshot.storageCore() && Carriers.matchesCarrier(block, deps.storageCarrier())) {
      StorageCoreMarker.set(plugin, block);
    }
    if (snapshot.terminal() != null && Carriers.matchesCarrier(block, deps.terminalCarrier())) {
      TerminalKind kind = parseTerminalKind(snapshot.terminal());
      TerminalMarker.set(plugin, block, kind, parseFacing(snapshot.terminal()));
      ItemHologramManager hologramManager = deps.hologramManager();
      if (hologramManager != null) {
        hologramManager.registerTerminal(block);
      }
    }
    if (snapshot.monitor() != null && Carriers.matchesCarrier(block, deps.monitorCarrier())) {
      MonitorMarker.set(plugin, block, parseFacing(snapshot.monitor()));
      if (snapshot.monitor().itemKey() != null || snapshot.monitor().itemBlob() != null) {
        MonitorMarker.setItem(
            plugin, block, snapshot.monitor().itemKey(), snapshot.monitor().itemBlob());
      }
    }
    if (snapshot.bus() != null && Carriers.matchesCarrier(block, deps.busCarrier())) {
      BusType type = BusType.fromString(snapshot.bus().type());
      BusMode mode = BusMode.fromString(snapshot.bus().mode());
      BlockFace facing = parseFacing(snapshot.bus());
      BusMarker.set(plugin, block, type, facing, mode);
      byte[] filters = snapshot.bus().filters();
      if (filters != null && filters.length > 0) {
        BusService busService = deps.busService();
        if (busService != null) {
          BusState state =
              busService.getOrCreateState(
                  BusPos.of(block), new BusMarker.Data(type, facing, mode), block);
          if (state != null) {
            state.setFilters(BusFilterCodec.decode(filters, BUS_FILTER_SLOTS));
            busService.saveSettings(state);
          }
        } else {
          BusMarker.setFilters(plugin, block, filters);
        }
      }
    }
    if (snapshot.relay() != null && Carriers.matchesCarrier(block, deps.relayCarrier())) {
      RelayMarker.set(plugin, block);
      RelayLinkData link = snapshot.relay().link();
      if (link != null) {
        RelayMarker.setLink(
            plugin, block, new RelayMarker.Link(link.worldId(), link.x(), link.y(), link.z()));
      }
    }
    if (snapshot.transmitter() && Carriers.matchesCarrier(block, deps.transmitterCarrier())) {
      TransmitterMarker.set(plugin, block);
      TransmitterData transmitterData = snapshot.transmitterData();
      if (transmitterData != null) {
        TransmitterStoredTerminal.restore(
            plugin,
            block,
            TransmitterMode.fromString(transmitterData.mode()),
            transmitterData.terminalBlob());
        TransmitterStoredBooster.restore(plugin, block, transmitterData.boosterBlob());
      }
      deps.wirelessTransmitterService().register(block);
    }
    if (snapshot.chunkLoader() != null
        && Carriers.matchesCarrier(block, deps.chunkLoaderCarrier())) {
      ChunkLoaderData data = snapshot.chunkLoader();
      ChunkLoaderMarker.set(
          plugin,
          block,
          data.id(),
          data.type(),
          data.placedByUuid(),
          data.placedByName(),
          data.createdAt(),
          data.enabled(),
          data.bypassLimits());
      ChunkLoaderService chunkLoaderService = deps.chunkLoaderService();
      if (chunkLoaderService != null) {
        chunkLoaderService.reconcileBlock(block);
      }
    }
    if (snapshot.wire() && Carriers.matchesCarrier(block, deps.wireMaterial())) {
      WireMarker.setWire(plugin, block);
    }
    return true;
  }

  private void removeExistingBlockState(Block block) {
    DisplayRefreshService displayRefreshService = deps.displayRefreshService();
    if (displayRefreshService != null) {
      displayRefreshService.removeBlockDisplays(block);
    }
    ItemHologramManager hologramManager = deps.hologramManager();
    if (hologramManager != null) {
      if (StorageMarker.get(plugin, block).isPresent() || StorageCoreMarker.isCore(plugin, block)) {
        hologramManager.unregisterStorage(block);
      }
      if (TerminalMarker.isTerminal(plugin, block)) {
        hologramManager.unregisterTerminal(block);
      }
    }
    if (BusMarker.isBus(plugin, block) && deps.busService() != null) {
      deps.busService().unregisterBus(block);
    }
    if (TransmitterMarker.isTransmitter(plugin, block)) {
      TransmitterSessionManager transmitterSessionManager = deps.transmitterSessionManager();
      if (transmitterSessionManager != null) {
        transmitterSessionManager.closeSessionsForTransmitter(block);
        transmitterSessionManager.cancelChargingRefresh(block);
      }
      deps.wirelessTransmitterService().unregister(block);
    }
    unlinkExistingRelayForReplacement(plugin, block);
    if (ChunkLoaderMarker.isChunkLoader(plugin, block)) {
      ChunkLoaderService chunkLoaderService = deps.chunkLoaderService();
      if (chunkLoaderService != null) {
        chunkLoaderService.cleanupAt(block, "worldedit_replace");
      } else {
        ChunkLoaderMarker.clear(plugin, block);
      }
    }
  }

  private void prepareEmptyStorageClone(
      World world, MarkerUpdate clonedUpdate, String sourceStorageId) {
    MarkerSnapshot snapshot = clonedUpdate == null ? null : clonedUpdate.snapshot();
    StorageData storage = snapshot == null ? null : snapshot.storage();
    if (world == null
        || storage == null
        || storage.tierMaxItems() == null
        || storage.tierMaxItems() <= 0L) {
      if (clonedUpdate != null) {
        removeFailedStorageCarrier(clonedUpdate, "missing tier capacity");
      }
      return;
    }
    if (sourceStorageId == null
        || sourceStorageId.isBlank()
        || deps.storageClaimRegistry().claim(sourceStorageId).isEmpty()) {
      removeFailedStorageCarrier(clonedUpdate, "source storage claim is missing");
      PerfStats.incrementCounter("worldedit.storage.missingSource");
      return;
    }
    Block target = world.getBlockAt(clonedUpdate.x(), clonedUpdate.y(), clonedUpdate.z());
    StorageClaimLocation destination = StorageClaimLocation.fromBlock(target);
    releaseDifferentStorageAt(target, storage.storageId())
        .thenCompose(
            released -> {
              if (!released) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException("destination storage claim could not be released"));
              }
              StorageClaimRegistry.ReservationResult reservation =
                  deps.storageClaimRegistry().reserve(storage.storageId(), destination);
              if (!reservation.allowed()) {
                return CompletableFuture.failedFuture(
                    new IllegalStateException(
                        "destination storage claim was denied: " + reservation.denial()));
              }
              return deps.storageClaimRegistry()
                  .persist(
                      reservation.reservation(),
                      storage.tier(),
                      storage.tierMaxItems(),
                      storage.displayName());
            })
        .whenComplete(
            (ignored, error) -> {
              if (error == null) {
                enqueue(clonedUpdate);
                return;
              }
              plugin
                  .getLogger()
                  .log(
                      Level.WARNING,
                      "Failed to create empty WorldEdit storage copy from "
                          + sourceStorageId
                          + " at "
                          + clonedUpdate.x()
                          + ","
                          + clonedUpdate.y()
                          + ","
                          + clonedUpdate.z(),
                      unwrap(error));
              removeFailedStorageCarrier(clonedUpdate, "empty copy persistence failed");
            });
  }

  private boolean ensureStorageClaim(Block block, MarkerUpdate update) {
    StorageData storage = update.snapshot().storage();
    StorageClaimLocation destination = StorageClaimLocation.fromBlock(block);
    StorageClaimRegistry registry = deps.storageClaimRegistry();
    if (registry.exactClaim(storage.storageId(), destination)
        == StorageClaimRegistry.ExactClaim.MATCHED) {
      return true;
    }
    StoragePreparationKey key =
        new StoragePreparationKey(
            update.operationId(),
            update.worldId(),
            update.x(),
            update.y(),
            update.z(),
            storage.storageId());
    storagePreparations.computeIfAbsent(
        key,
        ignored ->
            releaseDifferentStorageAt(block, storage.storageId())
                .thenCompose(
                    released -> {
                      if (!released) return CompletableFuture.completedFuture(false);
                      if (registry.exactClaim(storage.storageId(), destination)
                          == StorageClaimRegistry.ExactClaim.MATCHED) {
                        return CompletableFuture.completedFuture(true);
                      }
                      StorageClaim source = registry.claim(storage.storageId()).orElse(null);
                      if (source != null) {
                        return registry.moveExact(storage.storageId(), destination);
                      }
                      if (storage.tierMaxItems() == null || storage.tierMaxItems() <= 0L) {
                        return CompletableFuture.completedFuture(false);
                      }
                      StorageClaimRegistry.ReservationResult reservation =
                          registry.reserve(storage.storageId(), destination);
                      if (!reservation.allowed()) {
                        return CompletableFuture.completedFuture(false);
                      }
                      return registry
                          .persist(
                              reservation.reservation(),
                              storage.tier(),
                              storage.tierMaxItems(),
                              storage.displayName())
                          .thenApply(nothing -> true);
                    })
                .whenComplete(
                    (prepared, error) -> {
                      storagePreparations.remove(key);
                      if (error != null || !Boolean.TRUE.equals(prepared)) {
                        plugin
                            .getLogger()
                            .log(
                                Level.WARNING,
                                "WorldEdit could not claim storage "
                                    + storage.storageId()
                                    + " at "
                                    + update.x()
                                    + ","
                                    + update.y()
                                    + ","
                                    + update.z(),
                                error == null
                                    ? new IllegalStateException("physical claim was denied")
                                    : unwrap(error));
                      }
                    }));
    return false;
  }

  private CompletableFuture<Boolean> releaseDifferentStorageAt(
      Block target, String incomingStorageId) {
    Optional<StorageMarker.Data> existing = StorageMarker.get(plugin, target);
    if (existing.isEmpty() || incomingStorageId.equals(existing.get().storageId())) {
      return CompletableFuture.completedFuture(true);
    }
    StorageClaimLocation location = StorageClaimLocation.fromBlock(target);
    StorageClaimRegistry registry = deps.storageClaimRegistry();
    StorageClaimRegistry.ExactClaim exact =
        registry.exactClaim(existing.get().storageId(), location);
    if (exact == StorageClaimRegistry.ExactClaim.ABSENT) {
      return CompletableFuture.completedFuture(true);
    }
    if (exact != StorageClaimRegistry.ExactClaim.MATCHED) {
      return CompletableFuture.completedFuture(false);
    }
    return registry.releaseExact(existing.get().storageId(), location);
  }

  private void releaseRemovedStorageClaim(Block block, String capturedStorageId) {
    String storageId = capturedStorageId;
    if (storageId == null || storageId.isBlank()) {
      storageId = StorageMarker.get(plugin, block).map(StorageMarker.Data::storageId).orElse(null);
    }
    if (storageId == null || storageId.isBlank()) return;
    StorageClaimLocation location = StorageClaimLocation.fromBlock(block);
    String releasedStorageId = storageId;
    deps.storageClaimRegistry()
        .releaseExact(releasedStorageId, location)
        .whenComplete(
            (released, error) -> {
              if (error != null) {
                plugin
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "Failed to release WorldEdit-removed storage claim " + releasedStorageId,
                        unwrap(error));
              }
            });
  }

  private void removeFailedStorageCarrier(MarkerUpdate update, String reason) {
    PluginTasks.runSyncIfEnabled(
        plugin,
        () -> {
          World world = Bukkit.getWorld(update.worldId());
          if (world == null || !world.isChunkLoaded(update.chunkX(), update.chunkZ())) return;
          Block block = world.getBlockAt(update.x(), update.y(), update.z());
          if (Carriers.matchesCarrier(block, deps.storageCarrier())
              && StorageMarker.get(plugin, block).isEmpty()) {
            block.setType(Material.AIR, false);
            ChunkMarkerStore.clearBlock(plugin, block);
            plugin
                .getLogger()
                .warning(
                    "Removed bare WorldEdit storage carrier at "
                        + update.x()
                        + ","
                        + update.y()
                        + ","
                        + update.z()
                        + ": "
                        + reason);
          }
        });
  }

  static void unlinkExistingRelayForReplacement(Plugin plugin, Block block) {
    if (RelayMarker.isRelay(plugin, block)) {
      RelayMarker.unlinkLoadedPair(plugin, block);
    }
  }

  private void updateMovedStorageLocation(String storageId, Block block) {
    if (storageId == null || storageId.isBlank() || block == null || block.getWorld() == null) {
      return;
    }
    deps.database()
        .updatePlayerLastStorageLocation(
            storageId, block.getWorld().getName(), block.getX(), block.getY(), block.getZ())
        .whenComplete(
            (ignored, err) -> {
              if (err != null) {
                plugin
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "Failed to update moved WorldEdit storage location for " + storageId,
                        unwrap(err));
              }
            });
  }

  private static boolean shouldRefreshWireNetworkAfterUpdate(MarkerUpdate update) {
    return update != null;
  }

  enum StorageIdentityAction {
    PRESERVE_IDENTITY,
    CREATE_EMPTY
  }

  static StorageIdentityAction storageIdentityAction(
      MarkerUpdate update, Set<String> removedStorageIds) {
    if (update == null || update.snapshot() == null || update.snapshot().storage() == null) {
      return StorageIdentityAction.CREATE_EMPTY;
    }
    String storageId = update.snapshot().storage().storageId();
    return update.moveOperation()
            || (storageId != null
                && removedStorageIds != null
                && removedStorageIds.contains(storageId))
        ? StorageIdentityAction.PRESERVE_IDENTITY
        : StorageIdentityAction.CREATE_EMPTY;
  }

  private record StoragePreparationKey(
      long operationId, UUID worldId, int x, int y, int z, String storageId) {}

  private static TerminalKind parseTerminalKind(TerminalData data) {
    String raw = data.type();
    if (raw == null) return TerminalKind.TERMINAL;
    try {
      return TerminalKind.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      return TerminalKind.TERMINAL;
    }
  }

  private static BlockFace parseFacing(FacingOwner data) {
    String raw = data.facing();
    if (raw == null) return null;
    try {
      return BlockFace.valueOf(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  ChunkSnapshot loadSnapshot(World world, int chunkX, int chunkZ) {
    if (!Bukkit.isPrimaryThread()) {
      deferSnapshotReconcile(world, chunkX, chunkZ);
      return ChunkSnapshot.empty();
    }
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return ChunkSnapshot.empty();
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    Map<Long, LinCompoundTag> data = new HashMap<>();
    if (!ChunkMarkerStore.hasAnyBlockData(plugin, chunk)) {
      return ChunkSnapshot.empty();
    }
    ChunkMarkerStore.forEachBlock(
        plugin,
        chunk,
        (block, root) -> {
          LinCompoundTag tag = buildExortTag(block);
          if (tag != null) {
            data.put(WorldEditMarkerMath.blockKey(block.getX(), block.getY(), block.getZ()), tag);
          }
        });
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we snapshot chunk=" + chunkX + "," + chunkZ + " markers=" + data.size(),
          NamedTextColor.BLUE);
    }
    return ChunkSnapshot.of(data);
  }

  private void deferSnapshotReconcile(World world, int chunkX, int chunkZ) {
    if (shuttingDown || world == null) {
      return;
    }
    ChunkKey key = new ChunkKey(world.getUID(), chunkX, chunkZ);
    if (!deferredSnapshotChunks.add(key)) {
      return;
    }
    updateQueueDepthGauge();
    scheduleLifecycleTask(() -> reconcileDeferredSnapshotChunk(key, "async_snapshot"), 2L);
  }

  private void reconcileDeferredSnapshotChunk(ChunkKey key, String reason) {
    if (key == null) {
      return;
    }
    World world = Bukkit.getWorld(key.worldId());
    if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
      updateQueueDepthGauge();
      return;
    }
    if (!deferredSnapshotChunks.remove(key)) {
      return;
    }
    Set<ChunkKey> chunks = Set.of(key);
    refreshScheduler.refreshAffectedChunks(chunks, Set.of(), reason);
    refreshScheduler.scheduleDeferredRefresh(chunks);
    updateQueueDepthGauge();
  }

  boolean reserveDirectReconciliation(World world, BlockVector3 position) {
    if (world == null || position == null) return false;
    BlockRef ref = new BlockRef(world.getUID(), position.x(), position.y(), position.z());
    WorldEditDirectReconciliationQueue.ReserveResult reserveResult =
        directReconciliation.reserve(ref);
    if (reserveResult == WorldEditDirectReconciliationQueue.ReserveResult.ALREADY_PRESENT) {
      return true;
    }
    if (reserveResult == WorldEditDirectReconciliationQueue.ReserveResult.FULL) {
      PerfStats.incrementCounter("worldedit.direct.reconciliationOverflow");
      if (directReconciliationOverflowWarned.compareAndSet(false, true)) {
        plugin
            .getLogger()
            .warning(
                "[WorldEdit] Direct mutation reconciliation reached its 100000-position safety "
                    + "cap; additional Exort carrier writes are being rejected.");
      }
      return false;
    }
    synchronized (flushTaskLock) {
      if (!shuttingDown && directReconciliationTask == null) {
        try {
          directReconciliationTask =
              deps.generationScope()
                  .runTaskLater(
                      "WorldEdit direct reconciliation", this::drainDirectReconciliation, 1L);
        } catch (IllegalStateException ignored) {
          directReconciliationTask = null;
        }
      }
    }
    updateQueueDepthGauge();
    return true;
  }

  private void drainDirectReconciliation() {
    synchronized (flushTaskLock) {
      directReconciliationTask = null;
      if (shuttingDown) return;
    }
    int examined = 0;
    while (examined < APPLY_PER_TICK) {
      BlockRef ref = directReconciliation.poll();
      if (ref == null) break;
      examined++;
      Block block = ref.block();
      if (block == null) continue;
      MarkerSnapshot snapshot = parseSnapshot(buildExortTag(block, true, false));
      if (snapshot == null) continue;
      Material carrier = carrierMaterial(snapshot);
      if (carrier == null) continue;
      if (!Carriers.matchesCarrier(block, carrier)) {
        block.setType(carrier, false);
      }
      enqueue(
          new MarkerUpdate(
              operationTracker.nextOperationId(),
              ref.worldId(),
              ref.x(),
              ref.y(),
              ref.z(),
              snapshot,
              null,
              false,
              false));
      PerfStats.incrementCounter("worldedit.direct.restored");
    }
    if (!directReconciliation.isEmpty()) {
      synchronized (flushTaskLock) {
        if (!shuttingDown && directReconciliationTask == null) {
          directReconciliationTask =
              deps.generationScope()
                  .runTaskLater(
                      "WorldEdit direct reconciliation", this::drainDirectReconciliation, 1L);
        }
      }
    }
    updateQueueDepthGauge();
  }

  private LinCompoundTag buildExortTag(Block block) {
    return buildExortTag(block, false);
  }

  private LinCompoundTag buildExortTag(Block block, boolean includeTransmitterTerminal) {
    return buildExortTag(block, includeTransmitterTerminal, true);
  }

  private LinCompoundTag buildExortTag(
      Block block, boolean includeTransmitterTerminal, boolean validateCarrier) {
    LinCompoundTag.Builder exort = LinCompoundTag.builder();
    boolean any = false;
    Optional<StorageMarker.Data> storage = StorageMarker.get(plugin, block);
    if (storage.isPresent()) {
      StorageMarker.Data data = storage.get();
      LinCompoundTag.Builder storageTag = LinCompoundTag.builder();
      storageTag.putString(FIELD_ID, data.storageId());
      storageTag.putString(FIELD_TIER, data.tier().key());
      storageTag.putString(FIELD_TIER_MAX_ITEMS, Long.toString(data.tierMaxItems()));
      if (data.displayName() != null) {
        storageTag.putString(FIELD_NAME, data.displayName());
      }
      if (data.facing() != null) {
        storageTag.putString(FIELD_FACING, data.facing().name());
      }
      exort.put(SECTION_STORAGE, storageTag.build());
      any = true;
    }
    if (StorageCoreMarker.isCore(plugin, block)) {
      LinCompoundTag.Builder coreTag = LinCompoundTag.builder();
      coreTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_STORAGE_CORE, coreTag.build());
      any = true;
    }
    if (TerminalMarker.isTerminal(plugin, block)) {
      LinCompoundTag.Builder terminalTag = LinCompoundTag.builder();
      terminalTag.putString(FIELD_TYPE, TerminalMarker.kind(plugin, block).name());
      TerminalMarker.facing(plugin, block)
          .ifPresent(face -> terminalTag.putString(FIELD_FACING, face.name()));
      exort.put(SECTION_TERMINAL, terminalTag.build());
      any = true;
    }
    Optional<BusMarker.Data> bus = BusMarker.get(plugin, block);
    if (bus.isPresent()) {
      BusMarker.Data data = bus.get();
      LinCompoundTag.Builder busTag = LinCompoundTag.builder();
      busTag.putString(FIELD_TYPE, data.type().name());
      busTag.putString(FIELD_FACING, data.facing().name());
      busTag.putString(FIELD_MODE, data.mode().name());
      byte[] filters = BusMarker.getFilters(plugin, block).orElse(null);
      if (filters == null || filters.length == 0) {
        BusService busService = deps.busService();
        if (busService != null) {
          BusState state = busService.getOrCreateState(BusPos.of(block), data, block);
          if (state != null) {
            filters = BusFilterCodec.encode(state.filters(), BUS_FILTER_SLOTS);
          }
        }
      }
      if (filters != null && filters.length > 0) {
        busTag.putByteArray(FIELD_FILTERS, filters);
      }
      exort.put(SECTION_BUS, busTag.build());
      any = true;
    }
    if (MonitorMarker.isMonitor(plugin, block)) {
      LinCompoundTag.Builder monitorTag = LinCompoundTag.builder();
      MonitorMarker.facing(plugin, block)
          .ifPresent(face -> monitorTag.putString(FIELD_FACING, face.name()));
      MonitorMarker.itemKey(plugin, block)
          .ifPresent(value -> monitorTag.putString(FIELD_ITEM_KEY, value));
      MonitorMarker.itemBlob(plugin, block)
          .ifPresent(value -> monitorTag.putByteArray(FIELD_ITEM_BLOB, value));
      exort.put(SECTION_MONITOR, monitorTag.build());
      any = true;
    }
    if (RelayMarker.isRelay(plugin, block)) {
      LinCompoundTag.Builder relayTag = LinCompoundTag.builder();
      relayTag.putByte(FIELD_PRESENT, (byte) 1);
      RelayMarker.link(plugin, block)
          .ifPresent(
              link -> {
                relayTag.putString(FIELD_LINK_WORLD, link.worldId().toString());
                relayTag.putString(FIELD_LINK_X, Integer.toString(link.x()));
                relayTag.putString(FIELD_LINK_Y, Integer.toString(link.y()));
                relayTag.putString(FIELD_LINK_Z, Integer.toString(link.z()));
              });
      exort.put(SECTION_RELAY, relayTag.build());
      any = true;
    }
    if (TransmitterMarker.isTransmitter(plugin, block)) {
      LinCompoundTag.Builder transmitterTag = LinCompoundTag.builder();
      transmitterTag.putByte(FIELD_PRESENT, (byte) 1);
      WorldEditMarkerCodec.writeTransmitterData(
          transmitterTag, captureTransmitterData(block, includeTransmitterTerminal));
      exort.put(SECTION_TRANSMITTER, transmitterTag.build());
      any = true;
    }
    Optional<ChunkLoaderMarker.Data> chunkLoader = ChunkLoaderMarker.get(plugin, block);
    if (chunkLoader.isPresent()) {
      ChunkLoaderMarker.Data data = chunkLoader.get();
      LinCompoundTag.Builder chunkLoaderTag = LinCompoundTag.builder();
      chunkLoaderTag.putString(FIELD_ID, data.id().toString());
      chunkLoaderTag.putString(FIELD_TYPE, data.type().id());
      if (data.placedByUuid() != null) {
        chunkLoaderTag.putString(FIELD_PLACED_BY_UUID, data.placedByUuid().toString());
      }
      if (data.placedByName() != null) {
        chunkLoaderTag.putString(FIELD_PLACED_BY_NAME, data.placedByName());
      }
      if (data.createdAt() > 0L) {
        chunkLoaderTag.putString(FIELD_CREATED_AT, Long.toString(data.createdAt()));
      }
      chunkLoaderTag.putString(FIELD_ENABLED, Boolean.toString(data.enabled()));
      chunkLoaderTag.putString(FIELD_BYPASS_LIMITS, Boolean.toString(data.bypassLimits()));
      exort.put(SECTION_CHUNK_LOADER, chunkLoaderTag.build());
      any = true;
    }
    if (WireMarker.isWire(plugin, block)) {
      LinCompoundTag.Builder wireTag = LinCompoundTag.builder();
      wireTag.putByte(FIELD_PRESENT, (byte) 1);
      exort.put(SECTION_WIRE, wireTag.build());
      any = true;
    }
    if (!any) return null;
    LinCompoundTag tag = exort.build();
    MarkerSnapshot snapshot = parseSnapshot(tag);
    if (snapshot == null || primaryMarkerCount(snapshot) != 1) return null;
    if (!validateCarrier) return tag;
    if (carrierPolicy.matchesWorldCarrier(block, snapshot) && primaryMarkerCount(snapshot) == 1) {
      return tag;
    }
    boolean cleared = false;
    if (!carrierPolicy.isCarrierCandidate(block)) {
      ChunkMarkerStore.clearBlock(plugin, block);
      cleared = true;
    }
    WorldEditDebugService debug = deps.debugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "we snapshot stale pos="
              + block.getX()
              + ","
              + block.getY()
              + ","
              + block.getZ()
              + " base="
              + block.getType().getKey()
              + " sections="
              + snapshotSections(snapshot)
              + " action="
              + (cleared ? "clear" : "skip"),
          cleared ? NamedTextColor.RED : NamedTextColor.GOLD);
    }
    return null;
  }

  private MarkerSnapshot captureMarkerSnapshot(World world, BlockVector3 position) {
    if (!Bukkit.isPrimaryThread() || world == null || position == null) {
      return null;
    }
    if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
      return null;
    }
    return parseSnapshot(
        buildExortTag(world.getBlockAt(position.x(), position.y(), position.z()), true));
  }

  private TransmitterData captureTransmitterData(Block block, boolean includeStoredItems) {
    byte[] terminalBlob =
        includeStoredItems
            ? TransmitterStoredTerminal.terminalBlob(plugin, block).orElse(null)
            : null;
    byte[] boosterBlob =
        includeStoredItems
            ? TransmitterStoredBooster.boosterBlob(plugin, block).orElse(null)
            : null;
    return new TransmitterData(
        TransmitterStoredTerminal.mode(plugin, block).id(), terminalBlob, boosterBlob);
  }

  BaseBlock buildMarkerBlock(LinCompoundTag exort) {
    MarkerSnapshot snapshot = parseSnapshot(exort);
    if (snapshot == null) return null;
    Material material = carrierMaterial(snapshot);
    if (material == null) return null;
    LinCompoundTag root = buildMarkerRoot(material, exort);
    return carrierBlock(material, root);
  }

  private static LinCompoundTag buildMarkerRoot(Material material, LinCompoundTag exort) {
    if (material == null || exort == null) return null;
    LinCompoundTag.Builder rootBuilder = LinCompoundTag.builder();
    rootBuilder.putString(FIELD_NBT_ID, material.getKey().toString());
    rootBuilder.put(EXORT_TAG, exort);
    return rootBuilder.build();
  }

  Material carrierMaterial(MarkerSnapshot snapshot) {
    if (snapshot == null) return null;
    if (snapshot.storage() != null || snapshot.storageCore()) {
      return deps.storageCarrier();
    }
    if (snapshot.terminal() != null) {
      return deps.terminalCarrier();
    }
    if (snapshot.monitor() != null) {
      return deps.monitorCarrier();
    }
    if (snapshot.bus() != null) {
      return deps.busCarrier();
    }
    if (snapshot.relay() != null) {
      return deps.relayCarrier();
    }
    if (snapshot.transmitter()) {
      return deps.transmitterCarrier();
    }
    if (snapshot.chunkLoader() != null) {
      return deps.chunkLoaderCarrier();
    }
    if (snapshot.wire()) {
      return deps.wireMaterial();
    }
    return null;
  }

  private static BaseBlock carrierBlock(Material material, LinCompoundTag root) {
    if (material == null) return null;
    BlockType type = BlockTypes.get(material.getKey().toString());
    if (type == null) return null;
    BlockState state = type.getDefaultState();
    if (material == Carriers.CHORUS_MATERIAL) {
      for (Property<?> property : type.getPropertyMap().values()) {
        String name = property.getName();
        if ("waterlogged".equals(name)) {
          state = withProperty(state, property, "false");
          continue;
        }
        if (property instanceof BooleanProperty) {
          state = withProperty(state, property, "true");
        }
      }
    }
    if (root == null) {
      return state.toBaseBlock();
    }
    return state.toBaseBlock(LazyReference.computed(root));
  }

  static BaseBlock markerHistoryCarrierBlock(
      Material material, LinCompoundTag exort, BaseBlock fallback) {
    LinCompoundTag root = markerHistoryCarrierRoot(material, exort);
    if (root == null) return fallback;
    BaseBlock withNbt = carrierBlock(material, root);
    return withNbt == null ? fallback : withNbt;
  }

  static LinCompoundTag markerHistoryCarrierRoot(Material material, LinCompoundTag exort) {
    return material == null || exort == null ? null : buildMarkerRoot(material, exort);
  }

  static boolean shouldClearRelayLinkFromClipboard(
      MarkerSnapshot snapshot,
      boolean fromClipboard,
      boolean sidecarHit,
      boolean moveHit,
      HistoryAction historyAction,
      EditSession.Stage stage) {
    return snapshot != null
        && snapshot.relay() != null
        && fromClipboard
        && !sidecarHit
        && !moveHit
        && historyAction == null
        && stage == EditSession.Stage.BEFORE_HISTORY;
  }

  private boolean matchesCarrierBlock(BaseBlock base, MarkerSnapshot snapshot) {
    if (base == null || snapshot == null) return false;
    Material material = carrierMaterial(snapshot);
    if (material == null) return false;
    BlockType type = BlockTypes.get(material.getKey().toString());
    return type != null && type.equals(base.getBlockType());
  }

  private boolean isPotentialClipboardMarker(BaseBlock base, MarkerSnapshot snapshot) {
    if (base == null || snapshot == null) return false;
    LinCompoundTag root = readRootNbt(base);
    MarkerSnapshot parsed = parseSnapshot(readExort(root));
    return parsed != null || matchesCarrierBlock(base, snapshot);
  }

  boolean shouldApplyPasteSidecar(BaseBlock base) {
    if (base == null) return false;
    BlockType type = base.getBlockType();
    return type != null && !type.equals(airType());
  }

  static BlockType airType() {
    return Objects.requireNonNull(BlockTypes.AIR, "WorldEdit AIR block type");
  }

  private static boolean containsMarkerExtent(Extent extent, EditSession.Stage stage) {
    Extent current = extent;
    while (current != null) {
      if (current instanceof WorldEditMarkerExtent markerExtent && markerExtent.stage == stage) {
        return true;
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        return false;
      }
    }
    return false;
  }

  static MarkerSnapshot rotateSnapshot(MarkerSnapshot snapshot, FacingTransform transform) {
    if (snapshot == null || transform == null) return snapshot;
    StorageData resolvedStorage = snapshot.storage();
    StorageData storage =
        resolvedStorage == null
            ? null
            : new StorageData(
                resolvedStorage.storageId(),
                resolvedStorage.tier(),
                resolvedStorage.tierMaxItems(),
                rotateFacing(resolvedStorage.facing(), transform),
                resolvedStorage.displayName());
    TerminalData terminal =
        snapshot.terminal() == null
            ? null
            : new TerminalData(
                snapshot.terminal().type(), rotateFacing(snapshot.terminal().facing(), transform));
    BusData bus =
        snapshot.bus() == null
            ? null
            : new BusData(
                snapshot.bus().type(),
                rotateFacing(snapshot.bus().facing(), transform),
                snapshot.bus().mode(),
                snapshot.bus().filters());
    MonitorData monitor =
        snapshot.monitor() == null
            ? null
            : new MonitorData(
                rotateFacing(snapshot.monitor().facing(), transform),
                snapshot.monitor().itemKey(),
                snapshot.monitor().itemBlob());
    return new MarkerSnapshot(
        storage,
        terminal,
        bus,
        monitor,
        snapshot.relay(),
        snapshot.transmitter(),
        snapshot.transmitterData(),
        snapshot.chunkLoader(),
        snapshot.wire(),
        snapshot.storageCore());
  }

  private static MarkerSnapshot withStorageId(MarkerSnapshot snapshot, String storageId) {
    if (snapshot == null || snapshot.storage() == null) return snapshot;
    StorageData resolvedStorage = snapshot.storage();
    if (resolvedStorage == null) return snapshot;
    StorageData storage =
        new StorageData(
            storageId,
            resolvedStorage.tier(),
            resolvedStorage.tierMaxItems(),
            resolvedStorage.facing(),
            resolvedStorage.displayName());
    return new MarkerSnapshot(
        storage,
        snapshot.terminal(),
        snapshot.bus(),
        snapshot.monitor(),
        snapshot.relay(),
        snapshot.transmitter(),
        snapshot.transmitterData(),
        snapshot.chunkLoader(),
        snapshot.wire(),
        snapshot.storageCore());
  }

  private MarkerSnapshot resolveStorageTier(MarkerSnapshot snapshot) {
    if (snapshot == null || snapshot.storage() == null) return snapshot;
    StorageData storage = resolveStorageTier(snapshot.storage());
    if (storage == null) return snapshot;
    return new MarkerSnapshot(
        storage,
        snapshot.terminal(),
        snapshot.bus(),
        snapshot.monitor(),
        snapshot.relay(),
        snapshot.transmitter(),
        snapshot.transmitterData(),
        snapshot.chunkLoader(),
        snapshot.wire(),
        snapshot.storageCore());
  }

  private StorageData resolveStorageTier(StorageData storage) {
    if (storage == null) return null;
    StorageTierResolver.Resolution resolution =
        StorageTierResolver.resolve(
                deps.storageTierCatalog(), storage.tier(), storage.tierMaxItems())
            .orElse(null);
    if (resolution == null) return storage;
    return new StorageData(
        storage.storageId(),
        resolution.tier().key(),
        resolution.tierMaxItems(),
        storage.facing(),
        storage.displayName());
  }

  static MarkerSnapshot withRelay(MarkerSnapshot snapshot, RelayData relay) {
    if (snapshot == null) return null;
    return new MarkerSnapshot(
        snapshot.storage(),
        snapshot.terminal(),
        snapshot.bus(),
        snapshot.monitor(),
        relay,
        snapshot.transmitter(),
        snapshot.transmitterData(),
        snapshot.chunkLoader(),
        snapshot.wire(),
        snapshot.storageCore());
  }

  private static String rotateFacing(String facing, FacingTransform transform) {
    if (facing == null || transform == null) return facing;
    BlockFace face;
    try {
      face = BlockFace.valueOf(facing);
    } catch (IllegalArgumentException ignored) {
      return facing;
    }
    BlockFace rotated = transform.apply(face);
    return rotated == null ? facing : rotated.name();
  }

  static BlockFace rotateFacing(BlockFace face, Transform transform) {
    if (face == null || transform == null || transform.isIdentity()) return face;
    Vector3 dir =
        switch (face) {
          case NORTH -> Vector3.at(0, 0, -1);
          case SOUTH -> Vector3.at(0, 0, 1);
          case EAST -> Vector3.at(1, 0, 0);
          case WEST -> Vector3.at(-1, 0, 0);
          case UP -> Vector3.at(0, 1, 0);
          case DOWN -> Vector3.at(0, -1, 0);
          default -> null;
        };
    if (dir == null) return face;
    Vector3 origin = Vector3.ZERO;
    Vector3 transformedOrigin = transform.apply(origin);
    Vector3 transformedDir = transform.apply(dir).subtract(transformedOrigin);
    double ax = Math.abs(transformedDir.x());
    double ay = Math.abs(transformedDir.y());
    double az = Math.abs(transformedDir.z());
    if (ax < 1e-6 && ay < 1e-6 && az < 1e-6) {
      return face;
    }
    if (ax >= ay && ax >= az) {
      return transformedDir.x() >= 0 ? BlockFace.EAST : BlockFace.WEST;
    }
    if (ay >= ax && ay >= az) {
      return transformedDir.y() >= 0 ? BlockFace.UP : BlockFace.DOWN;
    }
    return transformedDir.z() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
  }

  static LinCompoundTag readRootNbt(BaseBlock base) {
    if (base == null) return null;
    LazyReference<LinCompoundTag> ref = base.getNbtReference();
    if (ref != null) {
      LinCompoundTag root = ref.getValue();
      if (root != null) {
        return root;
      }
    }
    return readLegacyNbt(base);
  }

  @SuppressWarnings("deprecation")
  private static LinCompoundTag readLegacyNbt(BaseBlock base) {
    if (base == null) return null;
    com.sk89q.jnbt.CompoundTag legacy = base.getNbtData();
    if (legacy == null) return null;
    return legacy.toLinTag();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static BlockState withProperty(BlockState state, Property<?> property, String value) {
    Object resolved = property.getValueFor(value);
    return state.with((Property) property, resolved);
  }

  private Throwable unwrap(Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }
}
