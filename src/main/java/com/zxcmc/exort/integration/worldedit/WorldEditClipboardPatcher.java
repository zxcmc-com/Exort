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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

  WorldEditClipboardPatcher(
      Plugin plugin,
      Function<LinCompoundTag, BaseBlock> markerBlockBuilder,
      Supplier<WorldEditDebugService> debugService) {
    this.plugin = plugin;
    this.markerBlockBuilder = markerBlockBuilder;
    this.debugService = debugService;
  }

  void schedule(com.sk89q.worldedit.extension.platform.Actor actor, PendingClipboardPatch patch) {
    BukkitRunnable runnable =
        new BukkitRunnable() {
          private int attempts;

          @Override
          public void run() {
            attempts++;
            boolean applied = apply(actor, patch);
            if (!applied && attempts >= CLIPBOARD_PATCH_ATTEMPTS) {
              plugin
                  .getLogger()
                  .warning(
                      "[WorldEdit] Exort clipboard patch did not apply after "
                          + attempts
                          + " attempts; paste may leave carrier placeholders.");
            }
            if (applied || attempts >= CLIPBOARD_PATCH_ATTEMPTS) {
              tasks.removeIf(task -> task.getTaskId() == this.getTaskId());
              cancel();
            }
          }
        };
    BukkitTask task = runnable.runTaskTimer(plugin, 1L, 2L);
    tasks.add(task);
  }

  void shutdown() {
    for (BukkitTask task : tasks) {
      task.cancel();
    }
    tasks.clear();
  }

  private boolean apply(
      com.sk89q.worldedit.extension.platform.Actor actor, PendingClipboardPatch patch) {
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
}
