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
import com.zxcmc.exort.debug.WorldEditDebugService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.enginehub.linbus.tree.LinCompoundTag;

final class WorldEditClipboardPatcher {
  private static final int CLIPBOARD_PATCH_ATTEMPTS = 100;

  private final Plugin plugin;
  private final Function<LinCompoundTag, BaseBlock> markerBlockBuilder;
  private final Supplier<WorldEditDebugService> debugService;
  private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
  private final GenerationTracker generations = new GenerationTracker();

  WorldEditClipboardPatcher(
      Plugin plugin,
      Function<LinCompoundTag, BaseBlock> markerBlockBuilder,
      Supplier<WorldEditDebugService> debugService) {
    this.plugin = plugin;
    this.markerBlockBuilder = markerBlockBuilder;
    this.debugService = debugService;
  }

  void schedule(com.sk89q.worldedit.extension.platform.Actor actor, PendingClipboardPatch patch) {
    if (actor == null
        || actor.getUniqueId() == null
        || patch == null
        || patch.markers().isEmpty()) {
      return;
    }
    UUID actorId = actor.getUniqueId();
    Clipboard baseline = currentClipboard(actor);
    long generation = generations.next(actorId);
    BukkitTask previous = tasks.remove(actorId);
    if (previous != null) {
      previous.cancel();
    }
    BukkitRunnable runnable =
        new BukkitRunnable() {
          private int attempts;

          @Override
          public void run() {
            if (!generations.isCurrent(actorId, generation)) {
              cancel();
              return;
            }
            attempts++;
            boolean applied = apply(actor, patch, baseline);
            if (!applied && attempts >= CLIPBOARD_PATCH_ATTEMPTS) {
              plugin
                  .getLogger()
                  .warning(
                      "[WorldEdit] Exort clipboard patch did not apply after "
                          + attempts
                          + " attempts; paste may leave carrier placeholders.");
            }
            if (applied || attempts >= CLIPBOARD_PATCH_ATTEMPTS) {
              tasks.computeIfPresent(
                  actorId,
                  (ignored, current) -> current.getTaskId() == this.getTaskId() ? null : current);
              generations.complete(actorId, generation);
              cancel();
            }
          }
        };
    BukkitTask task = runnable.runTaskTimer(plugin, 1L, 2L);
    tasks.put(actorId, task);
  }

  void shutdown() {
    for (BukkitTask task : tasks.values()) {
      task.cancel();
    }
    tasks.clear();
    generations.clearAll();
  }

  void cancel(UUID actorId) {
    if (actorId == null) {
      return;
    }
    generations.clear(actorId);
    BukkitTask task = tasks.remove(actorId);
    if (task != null) {
      task.cancel();
    }
  }

  private boolean apply(
      com.sk89q.worldedit.extension.platform.Actor actor,
      PendingClipboardPatch patch,
      Clipboard baseline) {
    if (actor == null || patch == null || patch.markers().isEmpty()) return true;
    LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
    ClipboardHolder holder;
    Clipboard clipboard;
    try {
      holder = session.getClipboard();
      clipboard = holder.getClipboard();
    } catch (EmptyClipboardException e) {
      return false;
    }
    if (clipboard == null) return false;
    if (clipboard == baseline) return false;
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
    for (Map.Entry<BlockVector3, LinCompoundTag> entry : patch.markers().entrySet()) {
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
      return false;
    }
    ClipboardHolder patchedHolder = new ClipboardHolder(patched);
    patchedHolder.setTransform(holder.getTransform());
    session.setClipboard(patchedHolder);
    WorldEditDebugService debug = debugService.get();
    if (debug != null && debug.isEnabled()) {
      debug.recordEvent("we clipboard patch markers=" + applied, NamedTextColor.DARK_GREEN);
    }
    return true;
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
}
