package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.math.BlockVector3;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.enginehub.linbus.tree.LinCompoundTag;

record StorageData(String storageId, String tier, Long tierMaxItems, String facing)
    implements FacingOwner {
  StorageData(String storageId, String tier, String facing) {
    this(storageId, tier, null, facing);
  }
}

record TerminalData(String type, String facing) implements FacingOwner {}

record BusData(String type, String facing, String mode, byte[] filters) implements FacingOwner {}

record MonitorData(String facing, String itemKey, byte[] itemBlob) implements FacingOwner {}

record RelayLinkData(UUID worldId, int x, int y, int z) {
  RelayLinkData {
    java.util.Objects.requireNonNull(worldId, "worldId");
  }
}

record RelayData(RelayLinkData link) {}

interface FacingOwner {
  String facing();
}

@FunctionalInterface
interface FacingTransform {
  BlockFace apply(BlockFace face);
}

enum HistoryAction {
  UNDO,
  REDO
}

record ParsedHistoryCommand(HistoryAction action, int steps) {
  ParsedHistoryCommand {
    java.util.Objects.requireNonNull(action, "action");
    steps = Math.max(1, steps);
  }
}

record MarkerSnapshot(
    StorageData storage,
    TerminalData terminal,
    BusData bus,
    MonitorData monitor,
    RelayData relay,
    boolean wire,
    boolean storageCore) {}

record CapturedMarkers(UUID sourceWorldId, Map<BlockVector3, LinCompoundTag> markers) {
  CapturedMarkers {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
  }
}

record PendingClipboardPatch(UUID sourceWorldId, Map<BlockVector3, LinCompoundTag> markers) {
  PendingClipboardPatch {
    markers = markers == null ? Map.of() : Map.copyOf(markers);
  }
}

record PendingPasteCommand(
    boolean atOrigin, boolean onlySelect, long timestampMs, int usesRemaining) {
  PendingPasteCommand consume() {
    return new PendingPasteCommand(atOrigin, onlySelect, timestampMs, usesRemaining - 1);
  }
}

record PendingHistoryCommand(HistoryAction action, long timestampMs, int usesRemaining) {
  PendingHistoryCommand consume() {
    return new PendingHistoryCommand(action, timestampMs, usesRemaining - 1);
  }
}

record PendingPastePatch(
    Map<Long, MarkerSnapshot> destinationMarkers, Map<Long, MarkerSnapshot> undoMarkers) {
  PendingPastePatch {
    destinationMarkers = destinationMarkers == null ? Map.of() : Map.copyOf(destinationMarkers);
    undoMarkers = undoMarkers == null ? Map.of() : Map.copyOf(undoMarkers);
  }

  PendingPastePatch(Map<Long, MarkerSnapshot> destinationMarkers) {
    this(destinationMarkers, Map.of());
  }

  MarkerSnapshot get(BlockVector3 position) {
    if (position == null) return null;
    return destinationMarkers.get(
        WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  MarkerSnapshot undo(BlockVector3 position) {
    if (position == null) return null;
    return undoMarkers.get(WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }
}

record PendingMovePatch(
    Map<Long, MarkerSnapshot> sourceMarkers,
    Map<Long, MarkerSnapshot> destinationMarkers,
    BlockVector3 offset,
    long timestampMs,
    int usesRemaining) {
  PendingMovePatch {
    sourceMarkers = sourceMarkers == null ? Map.of() : Map.copyOf(sourceMarkers);
    destinationMarkers = destinationMarkers == null ? Map.of() : Map.copyOf(destinationMarkers);
    offset = offset == null ? BlockVector3.at(0, 0, 0) : offset;
  }

  PendingMovePatch consume() {
    return new PendingMovePatch(
        sourceMarkers, destinationMarkers, offset, timestampMs, usesRemaining - 1);
  }

  MarkerSnapshot get(BlockVector3 position) {
    if (position == null) return null;
    return destinationMarkers.get(
        WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  MarkerSnapshot source(BlockVector3 position) {
    if (position == null) return null;
    return sourceMarkers.get(
        WorldEditMarkerMath.blockKey(position.x(), position.y(), position.z()));
  }

  String offsetText() {
    return offset.x() + "," + offset.y() + "," + offset.z();
  }
}

record HistoryKey(UUID actorId, UUID worldId, int x, int y, int z) {}

record HistoryEntry(WorldEditMarkerHistory.FrameState state, long timestampMs) {
  MarkerSnapshot snapshot() {
    return state == null ? null : state.snapshot();
  }
}

record MarkerUpdate(
    long operationId,
    UUID worldId,
    int x,
    int y,
    int z,
    MarkerSnapshot snapshot,
    String removedStorageId,
    boolean storageCloneRequired,
    boolean moveOperation) {
  int chunkX() {
    return x >> 4;
  }

  int chunkZ() {
    return z >> 4;
  }
}

record BlockRef(UUID worldId, int x, int y, int z) {
  Block block() {
    World world = Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
      return null;
    }
    return world.getBlockAt(x, y, z);
  }
}

record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}

final class ChunkSnapshot {
  private final Map<Long, LinCompoundTag> data;

  private ChunkSnapshot(Map<Long, LinCompoundTag> data) {
    this.data = data == null || data.isEmpty() ? Map.of() : Map.copyOf(data);
  }

  static ChunkSnapshot empty() {
    return new ChunkSnapshot(Map.of());
  }

  static ChunkSnapshot of(Map<Long, LinCompoundTag> data) {
    return new ChunkSnapshot(data);
  }

  boolean isEmpty() {
    return data.isEmpty();
  }

  LinCompoundTag get(long key) {
    return data.get(key);
  }
}

final class ChunkUpdateBatch {
  final ChunkKey key;
  final Queue<PendingUpdate> updates = new ConcurrentLinkedQueue<>();

  ChunkUpdateBatch(ChunkKey key) {
    this.key = key;
  }

  void add(PendingUpdate update) {
    updates.add(update);
  }
}

final class PendingUpdate {
  MarkerUpdate update;
  int attempts;
  long nextTick;

  PendingUpdate(MarkerUpdate update) {
    this.update = update;
  }
}
