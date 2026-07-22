package com.zxcmc.exort.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zxcmc.exort.infra.db.DbItem;
import com.zxcmc.exort.infra.scheduler.MainThreadWorkScheduler;
import com.zxcmc.exort.infra.scheduler.RoundRobinMainThreadScheduler;
import com.zxcmc.exort.items.ItemKeyUtil;
import com.zxcmc.exort.items.ItemStackCodec;
import com.zxcmc.exort.storage.StorageCache;
import com.zxcmc.exort.storage.StorageTierCatalog;
import com.zxcmc.exort.storage.StoredItemCodec;
import com.zxcmc.exort.storage.sort.SortMode;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("storage-gui-stress")
class StorageDisplayIndexStressTest {
  private static final int ENTRIES_PER_TICK = 256;
  private static final int BUDGET_MICROS = 2_000;
  private static final int PAGE_SIZE = 45;

  @Test
  void indexesOneThousandTenThousandAndOneHundredThousandEntriesWithinGlobalBudgets() {
    for (int size : List.of(1_000, 10_000, 100_000)) {
      runMatrix(size);
    }
  }

  private void runMatrix(int size) {
    Fixture fixture = fixture(size);
    DeterministicScheduler scheduler = new DeterministicScheduler(ENTRIES_PER_TICK, BUDGET_MICROS);
    StorageDisplayIndexService service = new StorageDisplayIndexService(scheduler);
    service.acquire(fixture.cache());
    service.acquire(fixture.cache());

    for (SortMode mode : SortMode.values()) {
      for (SearchQuery search : List.of(SearchQuery.empty(), SearchQuery.from("stone"))) {
        StorageDisplayIndexService.Result first =
            await(scheduler, service.request(request("matrix", fixture.cache(), mode, search, 0)));
        verifyCompleteIndex(fixture, first);
        int pages = Math.max(1, Math.ceilDiv(first.displayList().size(), PAGE_SIZE));
        Set<Integer> pagesToCheck = Set.of(0, pages / 2, pages - 1);
        for (int page : pagesToCheck) {
          StressItemStack.resetCloneCount();
          StorageDisplayIndexService.Result result =
              await(
                  scheduler,
                  service.request(request("matrix", fixture.cache(), mode, search, page)));
          verifyCompleteIndex(fixture, result);
          assertTrue(
              StressItemStack.cloneCount() <= PAGE_SIZE,
              () ->
                  "Index cloned " + StressItemStack.cloneCount() + " samples for one visible page");
        }
      }
    }

    CompletableFuture<StorageDisplayIndexService.Result> viewerOne =
        service.request(
            request("viewer-one", fixture.cache(), SortMode.AMOUNT, SearchQuery.empty(), 0));
    CompletableFuture<StorageDisplayIndexService.Result> viewerTwo =
        service.request(
            request("viewer-two", fixture.cache(), SortMode.NAME, SearchQuery.empty(), 0));
    scheduler.drainUntil(viewerOne, viewerTwo);
    verifyCompleteIndex(fixture, viewerOne.join());
    verifyCompleteIndex(fixture, viewerTwo.join());

    CompletableFuture<StorageDisplayIndexService.Result> replaced =
        service.request(request("replace", fixture.cache(), SortMode.ID, SearchQuery.empty(), 0));
    CompletableFuture<StorageDisplayIndexService.Result> replacement =
        service.request(
            request("replace", fixture.cache(), SortMode.CATEGORY, SearchQuery.from("stone"), 0));
    assertTrue(replaced.isCancelled());
    verifyCompleteIndex(fixture, await(scheduler, replacement));

    CompletableFuture<StorageDisplayIndexService.Result> structural =
        service.request(
            request("structural", fixture.cache(), SortMode.AMOUNT, SearchQuery.empty(), 0));
    scheduler.tick();
    StorageCache.AddResult added =
        fixture.cache().tryAddItem(null, new StressItemStack(size + 1L), 1L);
    assertTrue(added.accepted());
    fixture.keys().add(added.key());
    assertStale(scheduler, structural);
    verifyCompleteIndex(
        fixture,
        await(
            scheduler,
            service.request(
                request(
                    "after-structural",
                    fixture.cache(),
                    SortMode.AMOUNT,
                    SearchQuery.empty(),
                    0))));

    CompletableFuture<StorageDisplayIndexService.Result> changing =
        service.request(
            request("content", fixture.cache(), SortMode.AMOUNT, SearchQuery.empty(), 0));
    int mutationsBeforeCompletion = 0;
    for (int mutation = 0; mutation < 32 && !changing.isDone(); mutation++) {
      scheduler.tick();
      if (changing.isDone()) {
        break;
      }
      fixture.cache().addItem(fixture.keys().getFirst(), new StressItemStack(0L), 1L);
      mutationsBeforeCompletion++;
    }
    assertTrue(mutationsBeforeCompletion > 0, "Content job completed before mutation coverage");
    assertStale(scheduler, changing);
    verifyCompleteIndex(
        fixture,
        await(
            scheduler,
            service.request(
                request("settled", fixture.cache(), SortMode.AMOUNT, SearchQuery.empty(), 0))));

    service.release(fixture.cache());
    assertEquals(1, service.activeIndexCount());
    CompletableFuture<StorageDisplayIndexService.Result> closing =
        service.request(request("close", fixture.cache(), SortMode.NAME, SearchQuery.empty(), 0));
    service.release(fixture.cache());
    assertTrue(closing.isCancelled());
    assertEquals(0, service.activeIndexCount());
    assertEquals(0, service.pendingRequestCount());

    service.acquire(fixture.cache());
    verifyCompleteIndex(
        fixture,
        await(
            scheduler,
            service.request(
                request("reopen", fixture.cache(), SortMode.ID, SearchQuery.empty(), 0))));
    CompletableFuture<StorageDisplayIndexService.Result> runtimeClose =
        service.request(
            request("runtime-close", fixture.cache(), SortMode.NAME, SearchQuery.empty(), 0));
    service.close();
    assertTrue(runtimeClose.isDone());
    assertTrue(runtimeClose.isCancelled() || runtimeClose.isCompletedExceptionally());
    assertEquals(0, service.activeIndexCount());
    assertEquals(0, service.pendingRequestCount());
    assertTrue(scheduler.maxProcessedPerTick() <= ENTRIES_PER_TICK);
    assertFalse(scheduler.budgetViolation());
  }

  private static StorageDisplayIndexService.Request request(
      Object owner, StorageCache cache, SortMode mode, SearchQuery search, int page) {
    return new StorageDisplayIndexService.Request(
        owner,
        cache,
        mode,
        false,
        List.of(),
        search,
        null,
        null,
        null,
        StorageTierCatalog.empty(),
        "en_us",
        page,
        PAGE_SIZE,
        StorageCache.StorageItem::sample);
  }

  private static void verifyCompleteIndex(
      Fixture fixture, StorageDisplayIndexService.Result result) {
    assertNotNull(result);
    assertEquals(
        new HashSet<>(fixture.keys()),
        new HashSet<>(result.sortOrder()),
        "Every storage key must remain reachable from the final index");
    assertEquals(
        fixture.cache().beginIndexCursor().structuralVersion(), result.structuralVersion());
    assertEquals(fixture.cache().beginIndexCursor().contentVersion(), result.contentVersion());
  }

  private static void assertStale(
      DeterministicScheduler scheduler,
      CompletableFuture<StorageDisplayIndexService.Result> future) {
    scheduler.drainUntil(future);
    CompletionException failure = assertThrows(CompletionException.class, future::join);
    Throwable cause = unwrap(failure);
    assertTrue(
        cause instanceof StorageDisplayIndexService.StaleStructureException,
        () -> "Expected stale index rejection, got " + cause);
  }

  private static StorageDisplayIndexService.Result await(
      DeterministicScheduler scheduler,
      CompletableFuture<StorageDisplayIndexService.Result> future) {
    scheduler.drainUntil(future);
    return future.join();
  }

  private static Throwable unwrap(Throwable failure) {
    Throwable current = failure;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static Fixture fixture(int size) {
    StoredItemCodec codec = new StoredItemCodec(new StressCodec());
    StorageCache cache = new StorageCache("stress-" + size, null, null, null, codec);
    Map<String, DbItem> rows = new LinkedHashMap<>(Math.ceilDiv(size * 4, 3));
    List<String> keys = new ArrayList<>(size + 1);
    for (long id = 0; id < size; id++) {
      byte[] blob = StressCodec.bytes(id);
      String key = ItemKeyUtil.sha256Hex(blob);
      keys.add(key);
      rows.put(key, new DbItem(key, blob, id % 97L + 1L));
    }
    cache.loadFromDb(rows);
    return new Fixture(cache, keys);
  }

  private record Fixture(StorageCache cache, List<String> keys) {}

  private static final class StressCodec implements ItemStackCodec {
    @Override
    public byte[] encode(ItemStack stack) {
      if (!(stack instanceof StressItemStack stress)) {
        throw new IllegalArgumentException("Unexpected stress item " + stack);
      }
      return bytes(stress.id());
    }

    @Override
    public ItemStack decode(byte[] bytes) {
      return new StressItemStack(ByteBuffer.wrap(bytes).getLong());
    }

    private static byte[] bytes(long id) {
      return ByteBuffer.allocate(Long.BYTES).putLong(id).array();
    }
  }

  private static final class StressItemStack extends ItemStack {
    private static final Material[] MATERIALS = {
      Material.STONE, Material.DIRT, Material.OAK_LOG, Material.DIAMOND, Material.REDSTONE
    };
    private static final AtomicLong CLONES = new AtomicLong();
    private final long id;
    private int amount = 1;

    private StressItemStack(long id) {
      this.id = id;
    }

    private long id() {
      return id;
    }

    @Override
    public Material getType() {
      return MATERIALS[Math.floorMod(id, MATERIALS.length)];
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public int getMaxStackSize() {
      return 64;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public boolean hasItemMeta() {
      return false;
    }

    @Override
    public ItemMeta getItemMeta() {
      return null;
    }

    @Override
    public StressItemStack clone() {
      CLONES.incrementAndGet();
      StressItemStack copy = new StressItemStack(id);
      copy.amount = amount;
      return copy;
    }

    private static void resetCloneCount() {
      CLONES.set(0L);
    }

    private static long cloneCount() {
      return CLONES.get();
    }
  }

  private static final class DeterministicScheduler implements MainThreadWorkScheduler {
    private final int entriesPerTick;
    private final long budgetNanos;
    private final ArrayDeque<Queued<?>> jobs = new ArrayDeque<>();
    private boolean closed;
    private int maxProcessedPerTick;
    private boolean budgetViolation;

    private DeterministicScheduler(int entriesPerTick, int budgetMicros) {
      this.entriesPerTick = entriesPerTick;
      this.budgetNanos = budgetMicros * 1_000L;
    }

    @Override
    public <T> CompletableFuture<T> submit(RoundRobinMainThreadScheduler.Work<T> work) {
      CompletableFuture<T> future = new CompletableFuture<>();
      if (closed) {
        future.completeExceptionally(new IllegalStateException("scheduler closed"));
      } else {
        jobs.addLast(new Queued<>(work, future));
      }
      return future;
    }

    private void tick() {
      long started = System.nanoTime();
      long deadline = started + budgetNanos;
      int remaining = entriesPerTick;
      int turns = jobs.size();
      while (remaining > 0 && turns-- > 0 && System.nanoTime() < deadline) {
        Queued<?> queued = jobs.removeFirst();
        int processed = queued.run(remaining, deadline);
        if (!queued.future().isDone()) {
          jobs.addLast(queued);
        }
        remaining -= processed;
      }
      int processed = entriesPerTick - remaining;
      maxProcessedPerTick = Math.max(maxProcessedPerTick, processed);
      if (processed > entriesPerTick) {
        budgetViolation = true;
      }
    }

    private void drainUntil(CompletableFuture<?>... futures) {
      int ticks = 0;
      while (java.util.Arrays.stream(futures).anyMatch(future -> !future.isDone())) {
        if (jobs.isEmpty()) {
          throw new IllegalStateException("No scheduled work can complete the requested future");
        }
        tick();
        if (++ticks > 2_000_000) {
          throw new IllegalStateException("Deterministic scheduler did not converge");
        }
      }
    }

    private int maxProcessedPerTick() {
      return maxProcessedPerTick;
    }

    private boolean budgetViolation() {
      return budgetViolation;
    }

    @Override
    public void close() {
      closed = true;
      CancellationException failure = new CancellationException("scheduler closed");
      while (!jobs.isEmpty()) {
        jobs.removeFirst().future().completeExceptionally(failure);
      }
    }

    private record Queued<T>(
        RoundRobinMainThreadScheduler.Work<T> work, CompletableFuture<T> future) {
      private int run(int maxEntries, long deadline) {
        if (future.isDone()) {
          return 0;
        }
        try {
          RoundRobinMainThreadScheduler.Slice slice = work.runSlice(maxEntries, deadline);
          int processed = slice.processedEntries();
          if (processed < 0 || processed > maxEntries) {
            future.completeExceptionally(
                new IllegalStateException(
                    "Job exceeded count budget: " + processed + " > " + maxEntries));
            return maxEntries;
          }
          if (slice.complete()) {
            future.complete(work.result());
          } else if (!slice.madeProgress() && System.nanoTime() < deadline) {
            future.completeExceptionally(new IllegalStateException("Job made no progress"));
          }
          return processed;
        } catch (RuntimeException | LinkageError failure) {
          future.completeExceptionally(failure);
          return 0;
        }
      }
    }
  }
}
