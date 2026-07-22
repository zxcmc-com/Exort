package com.zxcmc.exort.infra.scheduler;

import java.util.concurrent.CompletableFuture;

/** Submission seam shared by the Bukkit scheduler and deterministic stress tests. */
public interface MainThreadWorkScheduler extends AutoCloseable {
  <T> CompletableFuture<T> submit(RoundRobinMainThreadScheduler.Work<T> work);

  @Override
  void close();
}
