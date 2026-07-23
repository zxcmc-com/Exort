package com.zxcmc.exort.integration.worldedit;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.zxcmc.exort.debug.WorldEditDebugService;
import com.zxcmc.exort.runtime.RuntimeGenerationScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.enginehub.linbus.tree.LinCompoundTag;

final class WorldEditClipboardPatcher {
  private static final int CLIPBOARD_PATCH_ATTEMPTS = 100;

  private final Plugin plugin;
  private final RuntimeGenerationScope generationScope;
  private final Function<LinCompoundTag, BaseBlock> markerBlockBuilder;
  private final Supplier<WorldEditDebugService> debugService;
  private final BiConsumer<UUID, PatchResult> resultConsumer;
  private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
  private final Map<UUID, Request> requests = new ConcurrentHashMap<>();
  private final GenerationTracker generations = new GenerationTracker();

  WorldEditClipboardPatcher(
      Plugin plugin,
      RuntimeGenerationScope generationScope,
      Function<LinCompoundTag, BaseBlock> markerBlockBuilder,
      Supplier<WorldEditDebugService> debugService,
      BiConsumer<UUID, PatchResult> resultConsumer) {
    this.plugin = plugin;
    this.generationScope = generationScope;
    this.markerBlockBuilder = markerBlockBuilder;
    this.debugService = debugService;
    this.resultConsumer = resultConsumer;
  }

  void schedule(com.sk89q.worldedit.extension.platform.Actor actor, PendingClipboardPatch patch) {
    schedule(actor, patch, false);
  }

  void scheduleTrustedSchematic(
      com.sk89q.worldedit.extension.platform.Actor actor, PendingClipboardPatch patch) {
    schedule(actor, patch, true);
  }

  private void schedule(
      com.sk89q.worldedit.extension.platform.Actor actor,
      PendingClipboardPatch patch,
      boolean allowRebase) {
    if (actor == null
        || actor.getUniqueId() == null
        || patch == null
        || patch.markers().isEmpty()) {
      return;
    }
    UUID actorId = actor.getUniqueId();
    Clipboard baseline = currentClipboard(actor);
    long generation = generations.next(actorId);
    Request request = new Request(patch, baseline, generation, allowRebase);
    requests.put(actorId, request);
    BukkitTask previous = tasks.remove(actorId);
    if (previous != null) {
      generationScope.cancelTask(previous);
    }
    int[] attempts = {0};
    BukkitTask[] taskReference = new BukkitTask[1];
    BukkitTask task =
        generationScope.runTaskTimer(
            "WorldEdit clipboard patch",
            () -> {
              BukkitTask currentTask = taskReference[0];
              if (!generations.isCurrent(actorId, generation)) {
                generationScope.cancelTask(currentTask);
                return;
              }
              attempts[0]++;
              AppliedPatch applied = apply(actor, request);
              if (applied == null && attempts[0] >= CLIPBOARD_PATCH_ATTEMPTS) {
                Clipboard current = currentClipboard(actor);
                plugin
                    .getLogger()
                    .warning(
                        "[WorldEdit] Exort clipboard patch did not apply after "
                            + attempts[0]
                            + " attempts; expected="
                            + patch.expectedBounds()
                            + "/"
                            + patch.expectedOrigin()
                            + ", actual="
                            + (current == null ? "none" : WorldEditBounds.from(current.getRegion()))
                            + "/"
                            + (current == null ? "none" : current.getOrigin())
                            + ", allowRebase="
                            + allowRebase
                            + "; paste may leave carrier placeholders.");
                notifyResult(actorId, new PatchResult(null, patch, false));
              }
              if (applied != null) {
                notifyResult(actorId, new PatchResult(applied.clipboard(), applied.patch(), true));
              }
              if (applied != null || attempts[0] >= CLIPBOARD_PATCH_ATTEMPTS) {
                tasks.remove(actorId, currentTask);
                generations.complete(actorId, generation);
                requests.remove(actorId, request);
                generationScope.cancelTask(currentTask);
              }
            },
            1L,
            2L);
    taskReference[0] = task;
    tasks.put(actorId, task);
  }

  void shutdown() {
    for (BukkitTask task : tasks.values()) {
      generationScope.cancelTask(task);
    }
    tasks.clear();
    requests.clear();
    generations.clearAll();
  }

  void cancel(UUID actorId) {
    if (actorId == null) {
      return;
    }
    generations.clear(actorId);
    requests.remove(actorId);
    BukkitTask task = tasks.remove(actorId);
    if (task != null) {
      generationScope.cancelTask(task);
    }
  }

  boolean tryApplyNow(com.sk89q.worldedit.extension.platform.Actor actor) {
    if (actor == null || actor.getUniqueId() == null) return false;
    UUID actorId = actor.getUniqueId();
    Request request = requests.get(actorId);
    if (request == null || !generations.isCurrent(actorId, request.generation())) return false;
    AppliedPatch applied = apply(actor, request);
    if (applied == null) return false;
    BukkitTask task = tasks.remove(actorId);
    if (task != null) generationScope.cancelTask(task);
    requests.remove(actorId, request);
    generations.complete(actorId, request.generation());
    notifyResult(actorId, new PatchResult(applied.clipboard(), applied.patch(), true));
    return true;
  }

  boolean scrubRejected(
      com.sk89q.worldedit.extension.platform.Actor actor,
      Clipboard expectedClipboard,
      PendingClipboardPatch patch) {
    if (actor == null || expectedClipboard == null || patch == null || patch.markers().isEmpty()) {
      return false;
    }
    Clipboard clipboard = currentClipboard(actor);
    if (clipboard != expectedClipboard || !patch.matches(clipboard)) {
      return false;
    }
    Region region = clipboard.getRegion();
    if (region == null) {
      return false;
    }
    Map<BlockVector3, BaseBlock> originals = new LinkedHashMap<>();
    for (BlockVector3 position : patch.markers().keySet()) {
      if (!region.contains(position)) {
        return false;
      }
      originals.put(position, clipboard.getFullBlock(position));
    }
    if (originals.isEmpty()) {
      return false;
    }
    BaseBlock air = BlockTypes.AIR.getDefaultState().toBaseBlock();
    List<BlockVector3> attempted = new ArrayList<>(originals.size());
    try {
      for (BlockVector3 position : originals.keySet()) {
        attempted.add(position);
        if (clipboard.getBlock(position).getBlockType() != BlockTypes.AIR) {
          clipboard.setBlock(position, air);
        }
        if (clipboard.getBlock(position).getBlockType() != BlockTypes.AIR) {
          throw new IllegalStateException(
              "Clipboard rejected the Exort marker scrub at " + position);
        }
      }
    } catch (Exception failure) {
      rollbackScrub(clipboard, originals, attempted, failure);
      return false;
    }
    WorldEditDebugService debug = debugService.get();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent("we rejected clipboard scrubbed=" + originals.size(), NamedTextColor.RED);
    }
    return true;
  }

  private void rollbackScrub(
      Clipboard clipboard,
      Map<BlockVector3, BaseBlock> originals,
      List<BlockVector3> attempted,
      Exception failure) {
    Exception rollbackFailure = null;
    for (int index = attempted.size() - 1; index >= 0; index--) {
      BlockVector3 position = attempted.get(index);
      try {
        clipboard.setBlock(position, originals.get(position));
      } catch (Exception exception) {
        if (rollbackFailure == null) {
          rollbackFailure = exception;
        } else {
          rollbackFailure.addSuppressed(exception);
        }
      }
    }
    if (rollbackFailure != null) {
      failure.addSuppressed(rollbackFailure);
    }
    plugin
        .getLogger()
        .warning(
            "[WorldEdit] Rejected clipboard scrub failed and paste will be blocked: "
                + failure.getMessage());
  }

  private AppliedPatch apply(com.sk89q.worldedit.extension.platform.Actor actor, Request request) {
    PendingClipboardPatch patch = request.patch();
    Clipboard baseline = request.baseline();
    if (actor == null || patch == null || patch.markers().isEmpty()) return null;
    LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
    ClipboardHolder holder;
    Clipboard clipboard;
    try {
      holder = session.getClipboard();
      clipboard = holder.getClipboard();
    } catch (EmptyClipboardException e) {
      return null;
    }
    if (clipboard == null || clipboard == baseline && !request.allowRebase()) return null;
    PendingClipboardPatch effectivePatch = patch;
    if (!patch.matches(clipboard)) {
      effectivePatch = request.allowRebase() ? patch.rebaseTo(clipboard) : null;
    }
    if (effectivePatch == null) {
      WorldEditDebugService debug = debugService.get();
      if (debug != null && debug.isFull()) {
        debug.recordEvent(
            "we clipboard correlation expected="
                + patch.expectedBounds()
                + "/"
                + patch.expectedOrigin()
                + " actual="
                + WorldEditBounds.from(clipboard.getRegion())
                + "/"
                + clipboard.getOrigin(),
            NamedTextColor.YELLOW);
      }
      return null;
    }
    Region clipboardRegion = clipboard.getRegion();
    BlockArrayClipboard patched = new BlockArrayClipboard(clipboardRegion);
    patched.setOrigin(clipboard.getOrigin());
    for (BlockVector3 pos : clipboardRegion) {
      patched.setBlock(pos, clipboard.getFullBlock(pos));
      if (clipboard.hasBiomes()) {
        patched.setBiome(pos, clipboard.getBiome(pos));
      }
    }
    for (Entity entity : clipboard.getEntities()) {
      BaseEntity state = entity.getState();
      if (state != null) {
        patched.createEntity(entity.getLocation(), new BaseEntity(state));
      }
    }
    int applied = 0;
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : effectivePatch.markers().entrySet()) {
      BlockVector3 pos = entry.getKey();
      if (clipboardRegion != null && !clipboardRegion.contains(pos)) {
        continue;
      }
      BaseBlock markerBlock = markerBlockBuilder.apply(entry.getValue());
      if (markerBlock == null) {
        continue;
      }
      if (patched.setBlock(pos, markerBlock)) {
        applied++;
      }
    }
    if (applied <= 0) {
      WorldEditDebugService debug = debugService.get();
      if (debug != null && debug.isFull()) {
        debug.recordEvent(
            "we clipboard patch matched but applied=0 markers="
                + patch.markers().size()
                + " region="
                + WorldEditBounds.from(clipboardRegion),
            NamedTextColor.YELLOW);
      }
      return null;
    }
    ClipboardHolder patchedHolder = new ClipboardHolder(patched);
    patchedHolder.setTransform(holder.getTransform());
    session.setClipboard(patchedHolder);
    WorldEditDebugService debug = debugService.get();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent("we clipboard patch markers=" + applied, NamedTextColor.DARK_GREEN);
    }
    return new AppliedPatch(patched, effectivePatch);
  }

  private void notifyResult(UUID actorId, PatchResult result) {
    if (resultConsumer != null) {
      resultConsumer.accept(actorId, result);
    }
  }

  private Clipboard currentClipboard(com.sk89q.worldedit.extension.platform.Actor actor) {
    try {
      return WorldEdit.getInstance().getSessionManager().get(actor).getClipboard().getClipboard();
    } catch (EmptyClipboardException | RuntimeException e) {
      return null;
    }
  }

  static final class GenerationTracker {
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    long next(UUID actorId) {
      long generation = sequence.incrementAndGet();
      generations.put(actorId, generation);
      return generation;
    }

    boolean isCurrent(UUID actorId, long generation) {
      return Long.valueOf(generation).equals(generations.get(actorId));
    }

    void complete(UUID actorId, long generation) {
      generations.remove(actorId, generation);
    }

    void clear(UUID actorId) {
      generations.remove(actorId);
    }

    void clearAll() {
      generations.clear();
    }
  }

  record PatchResult(Clipboard clipboard, PendingClipboardPatch patch, boolean applied) {}

  private record AppliedPatch(Clipboard clipboard, PendingClipboardPatch patch) {}

  private record Request(
      PendingClipboardPatch patch, Clipboard baseline, long generation, boolean allowRebase) {}
}
