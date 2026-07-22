package com.zxcmc.exort.runtime;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns listeners, tasks, callbacks, workers, and services created by one runtime generation. */
public final class RuntimeGenerationScope implements AutoCloseable {
  private final Plugin plugin;
  private final RuntimeFaultController diagnostics;
  private final long generationId;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean cleanupFinished = new AtomicBoolean();
  private final AtomicLong resourceSequence = new AtomicLong();
  private final Map<Long, OwnedResource> ownedResources = new ConcurrentHashMap<>();
  private final Map<Listener, Registration> listeners = new ConcurrentHashMap<>();
  private final Map<BukkitTask, Registration> tasks = new ConcurrentHashMap<>();

  public RuntimeGenerationScope(Plugin plugin) {
    this(plugin, null);
  }

  RuntimeGenerationScope(Plugin plugin, RuntimeFaultController diagnostics) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.diagnostics = diagnostics;
    this.generationId = diagnostics == null ? 0L : diagnostics.registerGeneration(this);
  }

  public <T extends Listener> T registerListener(T listener) {
    Objects.requireNonNull(listener, "listener");
    Registration registration = own(ResourceKind.LISTENER, listener.getClass().getName());
    try {
      requireOpen();
      Bukkit.getPluginManager().registerEvents(listener, plugin);
      Registration previous = listeners.putIfAbsent(listener, registration);
      if (previous != null || closed.get()) {
        HandlerList.unregisterAll(listener);
        listeners.remove(listener, registration);
        registration.close();
        requireOpen();
        throw new IllegalStateException("Listener is already owned by this runtime generation");
      }
      return listener;
    } catch (RuntimeException | LinkageError failure) {
      registration.close();
      throw failure;
    }
  }

  public BukkitTask runTask(Runnable action) {
    return runTask(action.getClass().getName(), action);
  }

  public BukkitTask runTask(String name, Runnable action) {
    return scheduleOneShot(
        name, action, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
  }

  public BukkitTask runTaskLater(String name, Runnable action, long delayTicks) {
    if (delayTicks < 0L) {
      throw new IllegalArgumentException("delayTicks must be non-negative");
    }
    return scheduleOneShot(
        name, action, runnable -> Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
  }

  public BukkitTask runTaskTimer(String name, Runnable action, long delayTicks, long periodTicks) {
    Objects.requireNonNull(action, "action");
    if (delayTicks < 0L || periodTicks <= 0L) {
      throw new IllegalArgumentException("Timer delay must be non-negative and period positive");
    }
    Registration registration = own(ResourceKind.BUKKIT_TASK, name);
    try {
      requireOpen();
      BukkitTask task =
          Bukkit.getScheduler()
              .runTaskTimer(
                  plugin,
                  () -> {
                    if (!closed.get()) {
                      action.run();
                    }
                  },
                  delayTicks,
                  periodTicks);
      Registration previous = tasks.putIfAbsent(task, registration);
      if (previous != null || closed.get()) {
        tasks.remove(task, registration);
        task.cancel();
        registration.close();
        requireOpen();
        throw new IllegalStateException("Task is already owned by this runtime generation");
      }
      return task;
    } catch (RuntimeException | LinkageError failure) {
      registration.close();
      throw failure;
    }
  }

  public void cancelTask(BukkitTask task) {
    if (task == null) {
      return;
    }
    Registration registration = tasks.remove(task);
    try {
      task.cancel();
    } finally {
      if (registration != null) {
        registration.close();
      }
    }
  }

  private BukkitTask scheduleOneShot(String name, Runnable action, TaskSubmission submission) {
    Objects.requireNonNull(action, "action");
    Registration registration = own(ResourceKind.BUKKIT_TASK, name);
    AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
    try {
      requireOpen();
      BukkitTask task =
          submission.submit(
              () -> {
                BukkitTask current = taskRef.get();
                if (current != null) {
                  tasks.remove(current);
                }
                registration.close();
                if (!closed.get()) {
                  action.run();
                }
              });
      taskRef.set(task);
      Registration previous = tasks.putIfAbsent(task, registration);
      if (previous != null || closed.get()) {
        tasks.remove(task, registration);
        task.cancel();
        registration.close();
        requireOpen();
        throw new IllegalStateException("Task is already owned by this runtime generation");
      }
      return task;
    } catch (RuntimeException | LinkageError failure) {
      registration.close();
      throw failure;
    }
  }

  public Runnable guard(Runnable action) {
    return guard(action.getClass().getName(), action);
  }

  /**
   * Wraps a one-shot async continuation and removes it from the census when it runs or is closed.
   */
  public Runnable guard(String name, Runnable action) {
    Objects.requireNonNull(action, "action");
    Registration registration = own(ResourceKind.ASYNC_CALLBACK, name);
    return () -> {
      if (!registration.release()) {
        return;
      }
      if (!closed.get()) {
        action.run();
      }
    };
  }

  Registration own(ResourceKind kind, String name) {
    Objects.requireNonNull(kind, "kind");
    String normalizedName = Objects.requireNonNull(name, "name").strip();
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("Runtime resource name must not be blank");
    }
    requireOpen();
    long id = resourceSequence.incrementAndGet();
    ownedResources.put(id, new OwnedResource(id, kind, normalizedName));
    Registration registration = new Registration(id);
    if (closed.get()) {
      registration.close();
      requireOpen();
    }
    return registration;
  }

  public boolean isClosed() {
    return closed.get();
  }

  public CensusSnapshot snapshot() {
    List<OwnedResource> resources =
        ownedResources.values().stream()
            .sorted(
                Comparator.comparing(OwnedResource::kind)
                    .thenComparing(OwnedResource::name)
                    .thenComparingLong(OwnedResource::id))
            .toList();
    EnumMap<ResourceKind, Integer> counts = new EnumMap<>(ResourceKind.class);
    for (ResourceKind kind : ResourceKind.values()) {
      counts.put(kind, 0);
    }
    for (OwnedResource resource : resources) {
      counts.compute(resource.kind(), (ignored, count) -> count == null ? 1 : count + 1);
    }
    return new CensusSnapshot(
        generationId,
        closed.get(),
        cleanupFinished.get(),
        Map.copyOf(counts),
        List.copyOf(resources));
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
    for (Map.Entry<BukkitTask, Registration> entry : Set.copyOf(tasks.entrySet())) {
      entry.getKey().cancel();
      tasks.remove(entry.getKey(), entry.getValue());
      entry.getValue().close();
    }
    for (Map.Entry<Listener, Registration> entry : Set.copyOf(listeners.entrySet())) {
      HandlerList.unregisterAll(entry.getKey());
      listeners.remove(entry.getKey(), entry.getValue());
      entry.getValue().close();
    }
    for (OwnedResource resource : List.copyOf(ownedResources.values())) {
      if (resource.kind() == ResourceKind.ASYNC_CALLBACK) {
        ownedResources.remove(resource.id(), resource);
      }
    }
  }

  void finishCleanup() {
    close();
    if (!cleanupFinished.compareAndSet(false, true)) {
      return;
    }
    if (diagnostics != null) {
      diagnostics.generationCleanupFinished(this, snapshot());
    }
  }

  public enum ResourceKind {
    SERVICE,
    LISTENER,
    BUKKIT_TASK,
    ASYNC_CALLBACK,
    WORKER,
    INTEGRATION
  }

  public record OwnedResource(long id, ResourceKind kind, String name) {
    public OwnedResource {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(name, "name");
    }
  }

  public record CensusSnapshot(
      long generationId,
      boolean closed,
      boolean cleanupFinished,
      Map<ResourceKind, Integer> counts,
      List<OwnedResource> resources) {
    public CensusSnapshot {
      counts = Map.copyOf(counts);
      resources = List.copyOf(resources);
    }

    public int totalResources() {
      return resources.size();
    }

    public int count(ResourceKind kind) {
      return counts.getOrDefault(kind, 0);
    }
  }

  final class Registration implements AutoCloseable {
    private final long resourceId;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private Registration(long resourceId) {
      this.resourceId = resourceId;
    }

    @Override
    public void close() {
      release();
    }

    private boolean release() {
      if (!active.compareAndSet(true, false)) {
        return false;
      }
      ownedResources.remove(resourceId);
      return true;
    }
  }

  @FunctionalInterface
  private interface TaskSubmission {
    BukkitTask submit(Runnable action);
  }
}
