package com.zxcmc.exort.infra.scheduler;

import com.zxcmc.exort.debug.PerfStats;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Runs cooperative jobs on the main thread under one global count and time budget per tick. */
public final class RoundRobinMainThreadScheduler implements MainThreadWorkScheduler {
  public interface Work<T> {
    Slice runSlice(int maxEntries, long deadlineNanos);

    T result();
  }

  public record Slice(int processedEntries, boolean complete, boolean madeProgress) {
    public Slice(int processedEntries, boolean complete) {
      this(processedEntries, complete, processedEntries > 0 || complete);
    }

    public Slice {
      if (processedEntries < 0) {
        throw new IllegalArgumentException("processedEntries must not be negative");
      }
    }
  }

  public record Budget(int entriesPerTick, int budgetMicros) {
    public Budget {
      if (entriesPerTick <= 0 || budgetMicros <= 0) {
        throw new IllegalArgumentException("scheduler budgets must be positive");
      }
    }
  }

  private final Plugin plugin;
  private final Supplier<Budget> budgetSource;
  private final String diagnosticsLabel;
  private final ArrayDeque<QueuedWork<?>> jobs = new ArrayDeque<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private BukkitTask tickTask;
  private boolean startPending;

  public RoundRobinMainThreadScheduler(Plugin plugin, Supplier<Budget> budgetSource) {
    this(plugin, budgetSource, null);
  }

  public RoundRobinMainThreadScheduler(
      Plugin plugin, Supplier<Budget> budgetSource, String diagnosticsLabel) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.budgetSource = Objects.requireNonNull(budgetSource, "budgetSource");
    this.diagnosticsLabel =
        diagnosticsLabel == null || diagnosticsLabel.isBlank() ? null : diagnosticsLabel.strip();
  }

  public <T> CompletableFuture<T> submit(Work<T> work) {
    Objects.requireNonNull(work, "work");
    QueuedWork<T> queued = new QueuedWork<>(work);
    synchronized (this) {
      if (closed.get()) {
        queued.future.completeExceptionally(
            new IllegalStateException("Main-thread work scheduler is closed"));
        return queued.future;
      }
      jobs.addLast(queued);
      recordPendingJobsLocked();
      requestStartLocked();
    }
    return queued.future;
  }

  private void requestStartLocked() {
    if (tickTask != null || startPending) {
      return;
    }
    startPending = true;
    Bukkit.getScheduler().runTask(plugin, this::startOnMainThread);
  }

  private synchronized void startOnMainThread() {
    startPending = false;
    if (closed.get() || jobs.isEmpty() || tickTask != null) {
      return;
    }
    tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runTick, 0L, 1L);
  }

  private void runTick() {
    long tickStarted = System.nanoTime();
    Budget budget = Objects.requireNonNull(budgetSource.get(), "budgetSource returned null");
    int remaining = budget.entriesPerTick();
    long budgetNanos = Math.max(1L, (long) budget.budgetMicros()) * 1_000L;
    // Leave half of the configured main-thread allowance for surrounding Bukkit/Purpur work and
    // for a single entry operation that crosses the cooperative deadline slightly.
    long deadline = System.nanoTime() + Math.max(1L, budgetNanos / 2L);
    int turns;
    synchronized (this) {
      turns = jobs.size();
    }
    while (remaining > 0 && turns-- > 0 && System.nanoTime() < deadline) {
      QueuedWork<?> queued;
      synchronized (this) {
        queued = jobs.pollFirst();
      }
      if (queued == null) {
        break;
      }
      boolean complete = queued.run(remaining, deadline);
      remaining -= Math.max(0, queued.lastProcessed);
      if (!complete) {
        synchronized (this) {
          jobs.addLast(queued);
        }
      }
    }
    int processed = budget.entriesPerTick() - remaining;
    int pending;
    synchronized (this) {
      if (jobs.isEmpty() && tickTask != null) {
        tickTask.cancel();
        tickTask = null;
      }
      pending = jobs.size();
      recordPendingJobsLocked();
    }
    if (diagnosticsLabel != null) {
      long elapsed = System.nanoTime() - tickStarted;
      PerfStats.record(diagnosticsLabel + ".tick", elapsed);
      PerfStats.setGauge(diagnosticsLabel + ".processedEntries", processed);
      PerfStats.addCounter(diagnosticsLabel + ".processedEntriesTotal", processed);
      if (pending > 0 && elapsed > budgetNanos) {
        PerfStats.incrementCounter(diagnosticsLabel + ".overruns");
      }
    }
  }

  @Override
  public synchronized void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    if (tickTask != null) {
      tickTask.cancel();
      tickTask = null;
    }
    IllegalStateException failure = new IllegalStateException("Main-thread work scheduler closed");
    while (!jobs.isEmpty()) {
      jobs.removeFirst().future.completeExceptionally(failure);
    }
    recordPendingJobsLocked();
  }

  private void recordPendingJobsLocked() {
    if (diagnosticsLabel != null) {
      PerfStats.setGauge(diagnosticsLabel + ".pendingJobs", jobs.size());
    }
  }

  private static final class QueuedWork<T> {
    private final Work<T> work;
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private int lastProcessed;

    private QueuedWork(Work<T> work) {
      this.work = work;
    }

    private boolean run(int maxEntries, long deadlineNanos) {
      if (future.isDone()) {
        lastProcessed = 0;
        return true;
      }
      try {
        Slice slice = Objects.requireNonNull(work.runSlice(maxEntries, deadlineNanos), "slice");
        lastProcessed = Math.min(maxEntries, slice.processedEntries());
        if (slice.complete()) {
          future.complete(work.result());
          return true;
        }
        if (!slice.madeProgress() && System.nanoTime() < deadlineNanos) {
          future.completeExceptionally(
              new IllegalStateException("Cooperative job made no progress in its slice"));
          return true;
        }
        return false;
      } catch (RuntimeException | LinkageError failure) {
        lastProcessed = 0;
        future.completeExceptionally(failure);
        return true;
      }
    }
  }
}
