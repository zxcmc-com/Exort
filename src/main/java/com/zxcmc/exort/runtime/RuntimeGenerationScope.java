package com.zxcmc.exort.runtime;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns Bukkit listeners and scheduled callbacks created by one runtime generation. */
public final class RuntimeGenerationScope implements AutoCloseable {
  private final Plugin plugin;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Set<Listener> listeners = ConcurrentHashMap.newKeySet();
  private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

  public RuntimeGenerationScope(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public <T extends Listener> T registerListener(T listener) {
    Objects.requireNonNull(listener, "listener");
    requireOpen();
    Bukkit.getPluginManager().registerEvents(listener, plugin);
    if (!listeners.add(listener) || closed.get()) {
      HandlerList.unregisterAll(listener);
      requireOpen();
    }
    return listener;
  }

  public BukkitTask runTask(Runnable action) {
    Objects.requireNonNull(action, "action");
    requireOpen();
    AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
    BukkitTask task =
        Bukkit.getScheduler()
            .runTask(
                plugin,
                () -> {
                  BukkitTask current = taskRef.get();
                  if (current != null) {
                    tasks.remove(current);
                  }
                  if (!closed.get()) {
                    action.run();
                  }
                });
    taskRef.set(task);
    return track(task);
  }

  public Runnable guard(Runnable action) {
    Objects.requireNonNull(action, "action");
    return () -> {
      if (!closed.get()) {
        action.run();
      }
    };
  }

  public boolean isClosed() {
    return closed.get();
  }

  private BukkitTask track(BukkitTask task) {
    if (!tasks.add(task) || closed.get()) {
      tasks.remove(task);
      task.cancel();
      requireOpen();
    }
    return task;
  }

  private void requireOpen() {
    if (closed.get()) {
      throw new IllegalStateException("Runtime generation is already closed");
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    for (BukkitTask task : tasks) {
      task.cancel();
    }
    tasks.clear();
    for (Listener listener : listeners) {
      HandlerList.unregisterAll(listener);
    }
    listeners.clear();
  }
}
