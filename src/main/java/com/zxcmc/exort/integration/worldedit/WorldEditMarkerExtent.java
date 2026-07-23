package com.zxcmc.exort.integration.worldedit;

import static com.zxcmc.exort.integration.worldedit.WorldEditMarkerCodec.*;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.BooleanProperty;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.zxcmc.exort.carrier.Carriers;
import com.zxcmc.exort.debug.PerfStats;
import com.zxcmc.exort.debug.WorldEditDebugService;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.enginehub.linbus.tree.LinCompoundTag;

class WorldEditMarkerExtent extends AbstractDelegateExtent {
  private final World world;
  private final WorldEditBridge bridge;
  private final long operationId;
  private final UUID actorId;
  final EditSession.Stage stage;
  private final HistoryAction historyAction;
  private final WorldEditMarkerHistory.Frame normalHistoryFrame;
  private final WorldEditMarkerHistory.Frame replayHistoryFrame;
  private final FacingTransform facingTransform;
  private final PendingPastePatch pastePatch;
  private final PendingMovePatch movePatch;
  private final PendingClipboardPatch cutSourcePatch;
  private final PendingOperationSnapshot operationSnapshot;
  private final boolean commandContextPresent;
  private final boolean moveOperation;
  private final Map<ChunkKey, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();
  private final Set<Long> rememberedHistory = ConcurrentHashMap.newKeySet();
  private final Set<Long> observedMoveSourceClears = ConcurrentHashMap.newKeySet();
  private final Set<Long> appliedMoveDestinations = ConcurrentHashMap.newKeySet();
  private final AtomicInteger appliedMoveDestinationCount = new AtomicInteger();
  private final AtomicBoolean moveSourceCleanupQueued = new AtomicBoolean();
  private final Map<BaseBlock, MarkerSnapshot> carried =
      Collections.synchronizedMap(new IdentityHashMap<>());

  WorldEditMarkerExtent(
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
    super(extent);
    this.world = world;
    this.bridge = bridge;
    this.operationId = operationId;
    this.actorId = actorId;
    this.stage = stage;
    this.historyAction = historyAction;
    this.normalHistoryFrame = normalHistoryFrame;
    this.replayHistoryFrame = replayHistoryFrame;
    this.pastePatch = pastePatch;
    this.movePatch = movePatch;
    this.cutSourcePatch = cutSourcePatch;
    this.operationSnapshot = operationSnapshot;
    this.commandContextPresent = commandContextPresent;
    this.moveOperation =
        movePatch != null || pastePatch != null && pastePatch.preserveStorageIdentity();
    if (stage == EditSession.Stage.BEFORE_HISTORY && historyAction == null) {
      WorldEditBridge.seedMoveSourceHistory(
          bridge.markerHistory(),
          rememberedHistory,
          actorId,
          world == null ? null : world.getUID(),
          movePatch,
          normalHistoryFrame);
    }
    Transform transform = WorldEditExtentCapabilities.resolveTransform(extent);
    this.facingTransform =
        clipboardTransform != null
            ? clipboardTransform
            : WorldEditExtentCapabilities.resolveFacingTransform(extent, transform);
  }

  public BaseBlock getFullBlock(BlockVector3 position) {
    BaseBlock block = super.getFullBlock(position);
    if (block == null || world == null) return block;
    BlockVector3 resolved = resolvePosition(position);
    int chunkX = resolved.x() >> 4;
    int chunkZ = resolved.z() >> 4;
    ChunkSnapshot snapshot = snapshot(chunkX, chunkZ);
    LinCompoundTag exort =
        snapshot.get(WorldEditMarkerMath.blockKey(resolved.x(), resolved.y(), resolved.z()));
    MarkerSnapshot parsed = parseSnapshot(exort);
    if (parsed == null && operationSnapshot != null) {
      parsed = operationSnapshot.get(world.getUID(), resolved);
      exort = WorldEditMarkerCodec.buildExortTag(parsed);
    }
    if (parsed == null) return block;
    BaseBlock withNbt = bridge.buildMarkerBlock(exort);
    if (withNbt == null) {
      return block;
    }
    WorldEditDebugService debug = bridge.dependencies().debugService();
    if (debug != null && debug.isFull()) {
      debug.recordEvent(
          "getFullBlock pos="
              + resolved.x()
              + ","
              + resolved.y()
              + ","
              + resolved.z()
              + " marker=yes",
          NamedTextColor.DARK_GREEN);
    }
    carried.put(withNbt, parsed);
    return withNbt;
  }

  @Override
  public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
      throws com.sk89q.worldedit.WorldEditException {
    if (world == null) {
      return super.setBlock(position, block);
    }
    ensureHistoryCapacity();
    BlockVector3 resolved = resolvePosition(position);
    if (!commandContextPresent && !Bukkit.isPrimaryThread()) {
      BaseBlock current = super.getFullBlock(position);
      if (bridge.carrierPolicy().isCarrierCandidate(current)
          && !bridge.reserveDirectReconciliation(world, resolved)) {
        PerfStats.incrementCounter("worldedit.direct.rejected");
        return false;
      }
    }
    int chunkX = resolved.x() >> 4;
    int chunkZ = resolved.z() >> 4;
    ChunkSnapshot snapshot = snapshot(chunkX, chunkZ);
    long key = WorldEditMarkerMath.blockKey(resolved.x(), resolved.y(), resolved.z());
    LinCompoundTag existingTag = snapshot.get(key);
    MarkerSnapshot existingSnapshot = parseSnapshot(existingTag);
    existingSnapshot =
        WorldEditBridge.chooseExistingSnapshot(
            existingSnapshot, operationSnapshot, world.getUID(), resolved);
    if (WorldEditMarkerTrustPolicy.rejectExisting(existingSnapshot, commandContextPresent)) {
      PerfStats.incrementCounter("worldedit.direct.rejected");
      return false;
    }
    if (existingTag == null && existingSnapshot != null) {
      existingTag = WorldEditMarkerCodec.buildExortTag(existingSnapshot);
    }
    BaseBlock base = null;
    if (block instanceof BaseBlock baseBlock) {
      base = baseBlock;
    } else if (block != null) {
      base = block.toBaseBlock();
    }
    LinCompoundTag root = WorldEditBridge.readRootNbt(base);
    LinCompoundTag exort = readExort(root);
    MarkerSnapshot parsed = parseSnapshot(exort);
    MarkerSnapshot carriedSnapshot = base == null ? null : carried.remove(base);
    boolean trustedIncomingMarker =
        historyAction != null
            || carriedSnapshot != null
            || pastePatch != null && pastePatch.get(resolved) != null
            || movePatch != null && movePatch.get(resolved) != null;
    if (WorldEditMarkerTrustPolicy.rejectIncoming(exort, parsed, trustedIncomingMarker)) {
      PerfStats.incrementCounter("worldedit.schematic.rejectedMarkers");
      return false;
    }
    if (parsed != null && historyAction == null) {
      MarkerSnapshot sanitized = parsed.withoutTransmitterStoredItems();
      if (sanitized != parsed) {
        parsed = sanitized;
        exort = WorldEditMarkerCodec.buildExortTag(parsed);
      }
    }
    boolean fromClipboard = parsed != null;
    String markerSource = parsed != null ? "nbt" : "none";
    boolean carriedHit = carriedSnapshot != null;
    if (parsed == null && carriedSnapshot != null) {
      parsed = carriedSnapshot;
      exort = WorldEditMarkerCodec.buildExortTag(parsed);
      fromClipboard = true;
      markerSource = "carried";
    } else if (parsed != null && carriedSnapshot != null) {
      markerSource = "carried_nbt";
    }
    boolean sidecarHit = false;
    MarkerSnapshot pasteUndoSnapshot = null;
    if (base != null && pastePatch != null) {
      MarkerSnapshot sidecar = pastePatch.get(resolved);
      if (sidecar != null && bridge.shouldApplyPasteSidecar(base)) {
        parsed = sidecar;
        exort = WorldEditMarkerCodec.buildExortTag(parsed);
        fromClipboard = true;
        sidecarHit = true;
        pasteUndoSnapshot = pastePatch.undo(resolved);
        markerSource = "paste_sidecar";
      }
    }
    if (parsed != null && fromClipboard && facingTransform != null) {
      parsed = WorldEditBridge.rotateSnapshot(parsed, facingTransform);
      exort = WorldEditMarkerCodec.buildExortTag(parsed);
    }
    boolean moveHit = false;
    MarkerSnapshot moveDestinationExistingSnapshot = null;
    if (base != null && movePatch != null) {
      MarkerSnapshot moved = movePatch.get(resolved);
      if (moved != null && matchesCarrier(base, moved)) {
        parsed = moved;
        exort = WorldEditMarkerCodec.buildExortTag(parsed);
        fromClipboard = true;
        moveHit = true;
        moveDestinationExistingSnapshot = movePatch.source(resolved);
        markerSource = "move_sidecar";
      }
    }
    MarkerSnapshot moveSourceHistory = null;
    if (parsed == null && shouldRememberMoveSourceHistory(base)) {
      moveSourceHistory =
          WorldEditBridge.rememberMoveSourceHistory(
              bridge.markerHistory(),
              rememberedHistory,
              actorId,
              world.getUID(),
              resolved,
              movePatch,
              normalHistoryFrame);
      if (moveSourceHistory != null) {
        observedMoveSourceClears.add(key);
        markerSource = "move_source_history";
      }
    }
    MarkerSnapshot cutSourceHistory = null;
    if (parsed == null && cutSourcePatch != null && isAirBlock(base)) {
      LinCompoundTag sourceTag = cutSourcePatch.markers().get(resolved);
      cutSourceHistory = parseSnapshot(sourceTag);
      if (cutSourceHistory != null) {
        markerSource = "cut_source_history";
      }
    }
    if (WorldEditBridge.shouldClearRelayLinkFromClipboard(
        parsed, fromClipboard, sidecarHit, moveHit, historyAction, stage)) {
      parsed = WorldEditBridge.withRelay(parsed, new RelayData(null));
      exort = WorldEditMarkerCodec.buildExortTag(parsed);
    }
    boolean markerPresent = existingTag != null;
    boolean historyHit = false;
    boolean historyStorageCloneRequired = false;
    BaseBlock historyClearBlock = null;
    MarkerSnapshot historyRemovalSnapshot = null;
    if (parsed == null && base != null && actorId != null && historyAction != null) {
      WorldEditMarkerHistory.FrameState historyState =
          bridge
              .markerHistory()
              .peekState(
                  replayHistoryFrame, historyAction, resolved.x(), resolved.y(), resolved.z());
      boolean historyFromStack = false;
      if (!isUsableHistoryState(base, historyState)) {
        WorldEditMarkerHistory.FrameState stackedState =
            bridge
                .markerHistory()
                .peekState(
                    actorId,
                    historyAction,
                    world.getUID(),
                    resolved.x(),
                    resolved.y(),
                    resolved.z());
        if (isUsableHistoryState(base, stackedState)) {
          historyState = stackedState;
          historyFromStack = true;
        }
      }
      if (historyState != null) {
        MarkerSnapshot history = historyState.snapshot();
        if (history != null && matchesCarrier(base, history)) {
          if (historyFromStack) {
            bridge
                .markerHistory()
                .consumeState(
                    actorId,
                    historyAction,
                    world.getUID(),
                    resolved.x(),
                    resolved.y(),
                    resolved.z());
          }
          parsed = history;
          exort = WorldEditMarkerCodec.buildExortTag(parsed);
          historyHit = true;
          historyStorageCloneRequired = historyState.storageCloneRequired();
          if (historyFromStack) {
            markerSource = historyAction == HistoryAction.REDO ? "redo_history" : "undo_history";
          } else {
            markerSource = historyAction == HistoryAction.REDO ? "redo_frame" : "undo_frame";
          }
        } else if (historyState.clear()) {
          if (historyFromStack) {
            bridge
                .markerHistory()
                .consumeState(
                    actorId,
                    historyAction,
                    world.getUID(),
                    resolved.x(),
                    resolved.y(),
                    resolved.z());
          }
          if (isHistoryCarrier(base)) {
            historyClearBlock = airBlock();
          }
          HistoryAction reverseAction =
              historyAction == HistoryAction.REDO ? HistoryAction.UNDO : HistoryAction.REDO;
          WorldEditMarkerHistory.FrameState reverseState =
              historyFromStack
                  ? bridge
                      .markerHistory()
                      .peekState(
                          actorId,
                          reverseAction,
                          world.getUID(),
                          resolved.x(),
                          resolved.y(),
                          resolved.z())
                  : bridge
                      .markerHistory()
                      .peekState(
                          replayHistoryFrame,
                          reverseAction,
                          resolved.x(),
                          resolved.y(),
                          resolved.z());
          historyRemovalSnapshot = reverseState == null ? null : reverseState.snapshot();
          historyHit = true;
          markerSource =
              historyFromStack
                  ? (historyAction == HistoryAction.REDO
                      ? "redo_clear_history"
                      : "undo_clear_history")
                  : (historyAction == HistoryAction.REDO ? "redo_clear_frame" : "undo_clear_frame");
        }
      }
    }
    if (historyAction == null && actorId != null && stage == EditSession.Stage.BEFORE_HISTORY) {
      MarkerSnapshot undoSnapshot =
          WorldEditBridge.chooseUndoSnapshot(
              existingSnapshot,
              moveDestinationExistingSnapshot,
              pasteUndoSnapshot,
              cutSourceHistory);
      undoSnapshot = WorldEditBridge.richerTransmitterSnapshot(undoSnapshot, moveSourceHistory);
      if (normalHistoryFrame != null && parsed != null && undoSnapshot == null) {
        bridge
            .markerHistory()
            .rememberUndoClear(
                actorId,
                world.getUID(),
                normalHistoryFrame,
                resolved.x(),
                resolved.y(),
                resolved.z());
      }
      bridge
          .markerHistory()
          .clearRedoTarget(actorId, world.getUID(), resolved.x(), resolved.y(), resolved.z());
      if (parsed != null) {
        boolean redoStorageCloneRequired =
            !moveOperation
                && shouldCloneStorageForNormalSet(parsed, fromClipboard, moveHit, historyAction);
        bridge
            .markerHistory()
            .rememberRedoTarget(
                actorId,
                world.getUID(),
                resolved.x(),
                resolved.y(),
                resolved.z(),
                parsed,
                normalHistoryFrame,
                redoStorageCloneRequired);
      } else if (existingSnapshot != null
          || moveSourceHistory != null
          || cutSourceHistory != null) {
        bridge
            .markerHistory()
            .rememberRedoClear(
                actorId,
                world.getUID(),
                normalHistoryFrame,
                resolved.x(),
                resolved.y(),
                resolved.z());
      }
    }
    MarkerSnapshot undoSnapshot =
        WorldEditBridge.chooseUndoSnapshot(
            existingSnapshot, moveDestinationExistingSnapshot, pasteUndoSnapshot, cutSourceHistory);
    undoSnapshot = WorldEditBridge.richerTransmitterSnapshot(undoSnapshot, moveSourceHistory);
    WorldEditBridge.rememberHistorySnapshotOnce(
        bridge.markerHistory(),
        rememberedHistory,
        actorId,
        historyAction,
        world.getUID(),
        resolved,
        undoSnapshot,
        normalHistoryFrame);
    ensureHistoryCapacity();
    boolean storageCloneRequired =
        parsed != null
            && parsed.storage() != null
            && (historyHit
                ? historyStorageCloneRequired
                : !moveOperation
                    && shouldCloneStorageForNormalSet(
                        parsed, fromClipboard, moveHit, historyAction));
    WorldEditDebugService debug = bridge.dependencies().debugService();
    if (debug != null && debug.isFull()) {
      String baseType = base == null ? "null" : base.getBlockType().getId();
      debug.recordEvent(
          "setBlock pos="
              + resolved.x()
              + ","
              + resolved.y()
              + ","
              + resolved.z()
              + " stage="
              + stage.name().toLowerCase(Locale.ROOT)
              + " base="
              + baseType
              + " root="
              + (root != null)
              + " exort="
              + (exort != null)
              + " parsed="
              + (parsed != null)
              + " source="
              + markerSource
              + " sections="
              + snapshotSections(
                  parsed != null
                      ? parsed
                      : moveSourceHistory != null ? moveSourceHistory : cutSourceHistory)
              + " carried="
              + carriedHit
              + " sidecar="
              + sidecarHit
              + " move="
              + moveHit
              + " commandMove="
              + moveOperation
              + " history="
              + historyHit
              + " historyAction="
              + (historyAction == null ? "none" : historyAction.name().toLowerCase(Locale.ROOT))
              + " markerPresent="
              + markerPresent
              + " fromClipboard="
              + fromClipboard
              + " storageClone="
              + storageCloneRequired
              + " moveOffset="
              + (movePatch == null ? "none" : movePatch.offsetText()),
          NamedTextColor.YELLOW);
    }
    if (parsed == null
        && !markerPresent
        && !historyHit
        && moveSourceHistory == null
        && cutSourceHistory == null) {
      if (historyClearBlock != null) {
        return super.setBlock(position, historyClearBlock);
      }
      return super.setBlock(position, block);
    }
    String removedStorageId = null;
    MarkerSnapshot removalSnapshot = undoSnapshot != null ? undoSnapshot : historyRemovalSnapshot;
    if (removalSnapshot != null) {
      boolean markerChanged = parsed == null || !removalSnapshot.equals(parsed);
      if (markerChanged && removalSnapshot.storage() != null) {
        removedStorageId = removalSnapshot.storage().storageId();
      }
    }
    BaseBlock toSet = historyClearBlock != null ? historyClearBlock : base;
    if (parsed != null) {
      BaseBlock carrier = carrierBlockForStage(parsed, exort, base);
      if (carrier != null) {
        toSet = carrier;
      } else if (exort != null && root != null && base != null) {
        LinCompoundTag cleaned = removeExort(root);
        toSet = base.toBaseBlock(cleaned);
      }
    } else if (exort != null && root != null && base != null) {
      LinCompoundTag cleaned = removeExort(root);
      toSet = base.toBaseBlock(cleaned);
    }
    boolean result =
        toSet != null ? super.setBlock(position, toSet) : super.setBlock(position, block);
    boolean markerCleanupRequested =
        parsed == null
            && (markerPresent
                || historyHit
                || moveSourceHistory != null
                || cutSourceHistory != null);
    if (debug != null && debug.isEnabled()) {
      boolean hasMarker = parsed != null;
      boolean cleared = !hasMarker && markerPresent;
      debug.recordSetBlock(hasMarker, cleared);
      if (debug.isFull() && (hasMarker || cleared)) {
        debug.recordEvent(
            "setBlock pos="
                + resolved.x()
                + ","
                + resolved.y()
                + ","
                + resolved.z()
                + " marker="
                + (hasMarker ? "set" : cleared ? "clear" : "none")
                + " result="
                + result,
            hasMarker ? NamedTextColor.GREEN : NamedTextColor.RED);
      }
    }
    if (result || markerCleanupRequested) {
      boolean identityRelocation =
          moveOperation
              || historyAction != null
                  && parsed != null
                  && parsed.storage() != null
                  && !storageCloneRequired;
      bridge.enqueue(
          new MarkerUpdate(
              operationId,
              world.getUID(),
              resolved.x(),
              resolved.y(),
              resolved.z(),
              parsed,
              removedStorageId,
              storageCloneRequired,
              identityRelocation));
      if (stage == EditSession.Stage.BEFORE_HISTORY
          && moveHit
          && movePatch != null
          && appliedMoveDestinations.add(key)
          && appliedMoveDestinationCount.incrementAndGet() == movePatch.destinationMarkers().size()
          && moveSourceCleanupQueued.compareAndSet(false, true)) {
        bridge.enqueueMoveSourceCleanup(
            operationId, world.getUID(), movePatch, observedMoveSourceClears);
      }
    }
    return result || markerCleanupRequested;
  }

  private void ensureHistoryCapacity() throws WorldEditHistoryLimitException {
    if (normalHistoryFrame != null && normalHistoryFrame.overflowed()) {
      throw new WorldEditHistoryLimitException();
    }
  }

  private boolean isUsableHistoryState(BaseBlock base, WorldEditMarkerHistory.FrameState state) {
    if (base == null || state == null) return false;
    MarkerSnapshot snapshot = state.snapshot();
    if (snapshot != null) {
      return matchesCarrier(base, snapshot);
    }
    return state.clear();
  }

  private boolean shouldRememberMoveSourceHistory(BaseBlock base) {
    return base != null
        && actorId != null
        && historyAction == null
        && stage == EditSession.Stage.BEFORE_HISTORY
        && movePatch != null
        && isAirBlock(base);
  }

  private static boolean shouldCloneStorageForNormalSet(
      MarkerSnapshot snapshot,
      boolean fromClipboard,
      boolean moveHit,
      HistoryAction historyAction) {
    return snapshot != null
        && snapshot.storage() != null
        && fromClipboard
        && !moveHit
        && historyAction == null;
  }

  private static boolean isAirBlock(BaseBlock base) {
    return base != null && WorldEditBridge.airType().equals(base.getBlockType());
  }

  private BaseBlock carrierBlockForStage(
      MarkerSnapshot snapshot, LinCompoundTag exort, BaseBlock fallback) {
    if (stage == EditSession.Stage.BEFORE_HISTORY) {
      Material material = bridge.carrierMaterial(snapshot);
      BaseBlock withNbt = WorldEditBridge.markerHistoryCarrierBlock(material, exort, fallback);
      if (withNbt != null) {
        return withNbt;
      }
    }
    return carrierBlock(snapshot, fallback);
  }

  public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block)
      throws com.sk89q.worldedit.WorldEditException {
    return setBlock(BlockVector3.at(x, y, z), block);
  }

  public <B extends BlockStateHolder<B>> int setBlocks(Region region, B block)
      throws MaxChangedBlocksException {
    if (world == null) {
      return delegateSetBlocks(region, block);
    }
    if (!needsProcessing(region, block)) {
      WorldEditDebugService debug = bridge.dependencies().debugService();
      if (debug != null && debug.isEnabled()) {
        debug.recordEvent("setBlocks constant delegated", NamedTextColor.GRAY);
      }
      return delegateSetBlocks(region, block);
    }
    int changed = 0;
    int total = 0;
    for (BlockVector3 pos : region) {
      total++;
      try {
        if (setBlock(pos, block)) {
          changed++;
        }
      } catch (MaxChangedBlocksException e) {
        throw e;
      } catch (com.sk89q.worldedit.WorldEditException e) {
        throw new RuntimeException(e);
      }
    }
    WorldEditDebugService debug = bridge.dependencies().debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordSetBlocks(total);
      debug.recordEvent(
          "setBlocks constant blocks=" + total + " changed=" + changed, NamedTextColor.AQUA);
    }
    return changed;
  }

  @SuppressWarnings("deprecation")
  public int setBlocks(Region region, BlockPattern pattern) throws MaxChangedBlocksException {
    return setBlocks(region, (Pattern) pattern);
  }

  public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
    if (world == null) {
      return delegateSetBlocks(region, pattern);
    }
    if (!needsProcessing(region, pattern)) {
      WorldEditDebugService debug = bridge.dependencies().debugService();
      if (debug != null && debug.isEnabled()) {
        debug.recordEvent("setBlocks pattern delegated", NamedTextColor.GRAY);
      }
      return delegateSetBlocks(region, pattern);
    }
    int changed = 0;
    int total = 0;
    for (BlockVector3 pos : region) {
      total++;
      BaseBlock block = pattern.applyBlock(pos);
      try {
        if (setBlock(pos, block)) {
          changed++;
        }
      } catch (MaxChangedBlocksException e) {
        throw e;
      } catch (com.sk89q.worldedit.WorldEditException e) {
        throw new RuntimeException(e);
      }
    }
    WorldEditDebugService debug = bridge.dependencies().debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordSetBlocks(total);
      debug.recordEvent(
          "setBlocks pattern blocks=" + total + " changed=" + changed, NamedTextColor.AQUA);
    }
    return changed;
  }

  @SuppressWarnings("deprecation")
  public int setBlocks(Set<BlockVector3> positions, BlockPattern pattern) {
    return setBlocks(positions, (Pattern) pattern);
  }

  public int setBlocks(Set<BlockVector3> positions, Pattern pattern) {
    if (world == null) {
      return delegateSetBlocksSet(positions, pattern);
    }
    if (!needsProcessing(positions, pattern)) {
      WorldEditDebugService debug = bridge.dependencies().debugService();
      if (debug != null && debug.isEnabled()) {
        debug.recordEvent("setBlocks set delegated", NamedTextColor.GRAY);
      }
      return delegateSetBlocksSet(positions, pattern);
    }
    int changed = 0;
    int total = 0;
    for (BlockVector3 pos : positions) {
      total++;
      BaseBlock block = pattern.applyBlock(pos);
      try {
        if (setBlock(pos, block)) {
          changed++;
        }
      } catch (com.sk89q.worldedit.WorldEditException e) {
        throw new RuntimeException(e);
      }
    }
    WorldEditDebugService debug = bridge.dependencies().debugService();
    if (debug != null && debug.isEnabled()) {
      debug.recordSetBlocks(total);
      debug.recordEvent(
          "setBlocks set blocks=" + total + " changed=" + changed, NamedTextColor.AQUA);
    }
    return changed;
  }

  private boolean needsProcessing(Region region, BlockStateHolder<?> block) {
    if (block == null) return regionHasMarkers(region);
    BaseBlock base = block instanceof BaseBlock baseBlock ? baseBlock : block.toBaseBlock();
    LinCompoundTag root = WorldEditBridge.readRootNbt(base);
    if (readExort(root) != null) {
      return true;
    }
    return regionHasMarkers(region);
  }

  private boolean needsProcessing(Region region, Pattern pattern) {
    if (pattern instanceof BlockStateHolder<?> holder) {
      BaseBlock base = holder instanceof BaseBlock baseBlock ? baseBlock : holder.toBaseBlock();
      LinCompoundTag root = WorldEditBridge.readRootNbt(base);
      if (readExort(root) != null) {
        return true;
      }
      return regionHasMarkers(region);
    }
    return true;
  }

  private boolean needsProcessing(Set<BlockVector3> positions, Pattern pattern) {
    if (positions == null || positions.isEmpty()) return false;
    if (pattern instanceof BlockStateHolder<?> holder) {
      BaseBlock base = holder instanceof BaseBlock baseBlock ? baseBlock : holder.toBaseBlock();
      LinCompoundTag root = WorldEditBridge.readRootNbt(base);
      if (readExort(root) != null) {
        return true;
      }
    }
    for (BlockVector3 pos : positions) {
      BlockVector3 resolved = resolvePosition(pos);
      if (movePatch != null && movePatch.hasMarkerAt(resolved)) {
        return true;
      }
      ChunkSnapshot snapshot = snapshot(resolved.x() >> 4, resolved.z() >> 4);
      if (snapshot.get(WorldEditMarkerMath.blockKey(resolved.x(), resolved.y(), resolved.z()))
          != null) {
        return true;
      }
      if (operationSnapshot != null && operationSnapshot.get(world.getUID(), resolved) != null) {
        return true;
      }
    }
    return false;
  }

  private boolean regionHasMarkers(Region region) {
    if (region == null) return false;
    if (movePatch != null && movePatch.hasMarkerIn(region)) {
      return true;
    }
    if (operationSnapshot != null && operationSnapshot.hasMarkerIn(world.getUID(), region)) {
      return true;
    }
    BlockVector3 min = region.getMinimumPoint();
    BlockVector3 max = region.getMaximumPoint();
    int minChunkX = min.x() >> 4;
    int maxChunkX = max.x() >> 4;
    int minChunkZ = min.z() >> 4;
    int maxChunkZ = max.z() >> 4;
    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
      for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
        ChunkSnapshot snapshot = snapshot(cx, cz);
        if (snapshot != null && !snapshot.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private ChunkSnapshot snapshot(int chunkX, int chunkZ) {
    ChunkKey key = new ChunkKey(world.getUID(), chunkX, chunkZ);
    return snapshots.computeIfAbsent(key, missing -> bridge.loadSnapshot(world, chunkX, chunkZ));
  }

  private int delegateSetBlocks(Region region, Object payload) throws MaxChangedBlocksException {
    Method method = resolveSetBlocksMethod(payload);
    if (method != null) {
      try {
        Object result = method.invoke(getExtent(), region, payload);
        if (result instanceof Integer count) {
          return count;
        }
      } catch (java.lang.reflect.InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof MaxChangedBlocksException max) {
          throw max;
        }
        if (cause instanceof RuntimeException runtime) {
          throw runtime;
        }
      } catch (Exception ignored) {
      }
    }
    int changed = 0;
    if (payload instanceof BlockStateHolder<?> block) {
      for (BlockVector3 pos : region) {
        BaseBlock base = block instanceof BaseBlock baseBlock ? baseBlock : block.toBaseBlock();
        try {
          if (getExtent().setBlock(pos, base)) {
            changed++;
          }
        } catch (MaxChangedBlocksException e) {
          throw e;
        } catch (com.sk89q.worldedit.WorldEditException e) {
          throw new RuntimeException(e);
        }
      }
    } else if (payload instanceof Pattern pattern) {
      for (BlockVector3 pos : region) {
        BaseBlock block = pattern.applyBlock(pos);
        try {
          if (getExtent().setBlock(pos, block)) {
            changed++;
          }
        } catch (MaxChangedBlocksException e) {
          throw e;
        } catch (com.sk89q.worldedit.WorldEditException e) {
          throw new RuntimeException(e);
        }
      }
    }
    return changed;
  }

  private int delegateSetBlocksSet(Set<BlockVector3> positions, Pattern pattern) {
    Method method = resolveSetBlocksSetMethod();
    if (method != null) {
      try {
        Object result = method.invoke(getExtent(), positions, pattern);
        if (result instanceof Integer count) {
          return count;
        }
      } catch (Exception ignored) {
      }
    }
    int changed = 0;
    for (BlockVector3 pos : positions) {
      BaseBlock block = pattern.applyBlock(pos);
      try {
        if (getExtent().setBlock(pos, block)) {
          changed++;
        }
      } catch (com.sk89q.worldedit.WorldEditException e) {
        throw new RuntimeException(e);
      }
    }
    return changed;
  }

  private Method resolveSetBlocksSetMethod() {
    return WorldEditExtentCapabilities.findSetBlocksSetMethod(getExtent().getClass());
  }

  private Method resolveSetBlocksMethod(Object payload) {
    if (payload == null) return null;
    Class<?> param = payload instanceof Pattern ? Pattern.class : BlockStateHolder.class;
    return WorldEditExtentCapabilities.findSetBlocks(getExtent().getClass(), param);
  }

  private BaseBlock carrierBlock(MarkerSnapshot snapshot, BaseBlock fallback) {
    Material material = null;
    if (snapshot.storage() != null || snapshot.storageCore()) {
      material = bridge.dependencies().storageCarrier();
    } else if (snapshot.terminal() != null) {
      material = bridge.dependencies().terminalCarrier();
    } else if (snapshot.monitor() != null) {
      material = bridge.dependencies().monitorCarrier();
    } else if (snapshot.bus() != null) {
      material = bridge.dependencies().busCarrier();
    } else if (snapshot.relay() != null) {
      material = bridge.dependencies().relayCarrier();
    } else if (snapshot.transmitter()) {
      material = bridge.dependencies().transmitterCarrier();
    } else if (snapshot.chunkLoader() != null) {
      material = bridge.dependencies().chunkLoaderCarrier();
    } else if (snapshot.wire()) {
      material = bridge.dependencies().wireMaterial();
    }
    if (material == null) return fallback;
    BlockType type = BlockTypes.get(material.getKey().toString());
    if (type == null) return fallback;
    if (material == Carriers.CHORUS_MATERIAL) {
      BlockState state = type.getDefaultState();
      for (Property<?> property : type.getPropertyMap().values()) {
        String name = property.getName();
        if ("waterlogged".equals(name)) {
          state = WorldEditBridge.withProperty(state, property, "false");
          continue;
        }
        if (property instanceof BooleanProperty) {
          state = WorldEditBridge.withProperty(state, property, "true");
        }
      }
      return state.toBaseBlock();
    }
    return type.getDefaultState().toBaseBlock();
  }

  private boolean matchesCarrier(BaseBlock base, MarkerSnapshot snapshot) {
    if (base == null || snapshot == null) return false;
    Material material = null;
    if (snapshot.storage() != null || snapshot.storageCore()) {
      material = bridge.dependencies().storageCarrier();
    } else if (snapshot.terminal() != null) {
      material = bridge.dependencies().terminalCarrier();
    } else if (snapshot.monitor() != null) {
      material = bridge.dependencies().monitorCarrier();
    } else if (snapshot.bus() != null) {
      material = bridge.dependencies().busCarrier();
    } else if (snapshot.relay() != null) {
      material = bridge.dependencies().relayCarrier();
    } else if (snapshot.transmitter()) {
      material = bridge.dependencies().transmitterCarrier();
    } else if (snapshot.chunkLoader() != null) {
      material = bridge.dependencies().chunkLoaderCarrier();
    } else if (snapshot.wire()) {
      material = bridge.dependencies().wireMaterial();
    }
    if (material == null) return false;
    BlockType type = BlockTypes.get(material.getKey().toString());
    return type != null && type.equals(base.getBlockType());
  }

  private boolean isHistoryCarrier(BaseBlock base) {
    if (base == null) return false;
    BlockType type = base.getBlockType();
    if (type == null) return false;
    return matchesMaterial(type, bridge.dependencies().storageCarrier())
        || matchesMaterial(type, bridge.dependencies().terminalCarrier())
        || matchesMaterial(type, bridge.dependencies().monitorCarrier())
        || matchesMaterial(type, bridge.dependencies().busCarrier())
        || matchesMaterial(type, bridge.dependencies().relayCarrier())
        || matchesMaterial(type, bridge.dependencies().transmitterCarrier())
        || matchesMaterial(type, bridge.dependencies().chunkLoaderCarrier())
        || matchesMaterial(type, bridge.dependencies().wireMaterial());
  }

  private boolean matchesMaterial(BlockType type, Material material) {
    if (type == null || material == null) return false;
    BlockType materialType = BlockTypes.get(material.getKey().toString());
    return type.equals(materialType);
  }

  private BaseBlock airBlock() {
    return WorldEditBridge.airType().getDefaultState().toBaseBlock();
  }

  private BlockVector3 resolvePosition(BlockVector3 position) {
    BlockVector3 resolved = position;
    Extent current = getExtent();
    while (current != null) {
      BlockVector3 transformed =
          WorldEditExtentCapabilities.tryPositionTransform(current, resolved);
      if (transformed != null) {
        resolved = transformed;
      }
      WorldEditExtentCapabilities.TranslateAccessor accessor =
          WorldEditExtentCapabilities.translateAccessor(current);
      if (accessor != null) {
        int dx = accessor.dx(current);
        int dy = accessor.dy(current);
        int dz = accessor.dz(current);
        if (dx != 0 || dy != 0 || dz != 0) {
          resolved = resolved.add(dx, dy, dz);
        }
      }
      if (current instanceof AbstractDelegateExtent delegateExtent) {
        current = delegateExtent.getExtent();
      } else {
        break;
      }
    }
    return resolved;
  }
}
