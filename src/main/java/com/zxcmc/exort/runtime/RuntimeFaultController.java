package com.zxcmc.exort.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Internal one-shot runtime fault injector and generation census registry. */
public final class RuntimeFaultController {
  public static final String ENABLED_PROPERTY = "exort.test.runtimeFaults";
  public static final String STAGE_PROPERTY = "exort.test.runtimeFaultStage";
  private static final Object PROPERTY_LOCK = new Object();

  private final AtomicLong generationSequence = new AtomicLong();
  private final Map<Long, RuntimeGenerationScope> liveGenerations = new ConcurrentHashMap<>();
  private final AtomicReference<PendingFailure> pendingFailure = new AtomicReference<>();
  private final AtomicReference<InjectedFailure> lastInjectedFailure = new AtomicReference<>();

  long registerGeneration(RuntimeGenerationScope generation) {
    Objects.requireNonNull(generation, "generation");
    long id = generationSequence.incrementAndGet();
    liveGenerations.put(id, generation);
    return id;
  }

  public void checkpoint(RuntimeConstructionStage stage, RuntimeGenerationScope generation) {
    Objects.requireNonNull(stage, "stage");
    Objects.requireNonNull(generation, "generation");
    synchronized (PROPERTY_LOCK) {
      if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"))) {
        return;
      }
      RuntimeConstructionStage requested =
          RuntimeConstructionStage.parse(System.getProperty(STAGE_PROPERTY)).orElse(null);
      if (requested != stage) {
        return;
      }
      System.clearProperty(STAGE_PROPERTY);
      PendingFailure failure = new PendingFailure(stage, generation.snapshot(), System.nanoTime());
      pendingFailure.set(failure);
      throw new RuntimeFaultInjectionException(stage);
    }
  }

  void generationCleanupFinished(
      RuntimeGenerationScope generation, RuntimeGenerationScope.CensusSnapshot postCleanup) {
    liveGenerations.remove(postCleanup.generationId(), generation);
    PendingFailure pending = pendingFailure.get();
    if (pending == null
        || pending.preCleanup().generationId() != postCleanup.generationId()
        || !pendingFailure.compareAndSet(pending, null)) {
      return;
    }
    lastInjectedFailure.set(
        new InjectedFailure(
            pending.stage(), pending.preCleanup(), postCleanup, pending.injectedAtNanos()));
  }

  /** Immutable diagnostic view for the isolated harness; not part of the Exort public API. */
  public Diagnostics diagnostics() {
    List<RuntimeGenerationScope.CensusSnapshot> active =
        liveGenerations.values().stream()
            .map(RuntimeGenerationScope::snapshot)
            .sorted(Comparator.comparingLong(RuntimeGenerationScope.CensusSnapshot::generationId))
            .toList();
    return new Diagnostics(active, lastInjectedFailure.get());
  }

  public record Diagnostics(
      List<RuntimeGenerationScope.CensusSnapshot> activeGenerations,
      InjectedFailure lastInjectedFailure) {
    public Diagnostics {
      activeGenerations = List.copyOf(activeGenerations);
    }
  }

  public record InjectedFailure(
      RuntimeConstructionStage stage,
      RuntimeGenerationScope.CensusSnapshot preCleanup,
      RuntimeGenerationScope.CensusSnapshot postCleanup,
      long injectedAtNanos) {
    public InjectedFailure {
      Objects.requireNonNull(stage, "stage");
      Objects.requireNonNull(preCleanup, "preCleanup");
      Objects.requireNonNull(postCleanup, "postCleanup");
    }
  }

  private record PendingFailure(
      RuntimeConstructionStage stage,
      RuntimeGenerationScope.CensusSnapshot preCleanup,
      long injectedAtNanos) {}

  private static final class RuntimeFaultInjectionException extends IllegalStateException {
    private RuntimeFaultInjectionException(RuntimeConstructionStage stage) {
      super("Injected Exort runtime failure at " + stage.name());
    }
  }
}
